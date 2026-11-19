const DAY=86400000,MAX=16*1024*1024;
const fail=(status,message)=>{throw Object.assign(new Error(message),{status});};
const hash=async text=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text))),b=>b.toString(16).padStart(2,'0')).join('');
const urlHash=async text=>btoa(String.fromCharCode(...new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(text))))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');
const valid=(s,n=255)=>typeof s==='string'&&s.length>0&&s.length<=n;
export async function rpc(db,p){
 const now=Date.now(),q=(sql,...args)=>db.prepare(sql).bind(...args),first=(sql,...args)=>q(sql,...args).first(),all=async(sql,...args)=>(await q(sql,...args).all()).results;
 async function auth(){if(!/^[a-f0-9]{64}$/.test(p.sessionHash||''))fail(401,'Sign in again to use cloud sync');const s=await first('SELECT * FROM paisa_sessions WHERE hash=? AND expires>?',p.sessionHash,now);if(!s)fail(401,'Your cloud session expired. Local records are safe.');if(s.expires-now<15*DAY)await q('UPDATE paisa_sessions SET expires=? WHERE hash=?',now+30*DAY,p.sessionHash).run();return s;}
 async function meta(uid){const h=await first('SELECT * FROM paisa_heads WHERE uid=?',uid);const history=await all('SELECT revision,updated,entry_count AS entryCount FROM paisa_revisions WHERE uid=? ORDER BY revision DESC LIMIT 5',uid);return {revision:h?.revision||0,parts:h?.parts||0,hash:h?.hash||'',bytes:h?.bytes||0,updated:h?.updated||0,entryCount:h?.entry_count||0,history};}
 if(p.op==='rate'){
  if(!valid(p.key,180)||!Number.isInteger(p.limit)||p.limit<1||p.limit>1000)fail(400,'Invalid request limit');
  await q('INSERT INTO paisa_limits (key,hits,expires) VALUES (?,1,?) ON CONFLICT(key) DO UPDATE SET hits=CASE WHEN expires<=? THEN 1 ELSE hits+1 END,expires=CASE WHEN expires<=? THEN ? ELSE expires END',p.key,now+p.window,now,now,now+p.window).run();const r=await first('SELECT hits FROM paisa_limits WHERE key=?',p.key);if(r.hits>p.limit)fail(429,'Too many attempts. Please wait a little.');return {ok:true};
 }
 if(p.op==='session.create'){
  if(!valid(p.uid)||!valid(p.email,320)||!/^[a-f0-9]{64}$/.test(p.hash||''))fail(400,'Invalid account');
  await db.batch([q('INSERT INTO paisa_sessions(hash,uid,email,kind,expires) VALUES (?,?,?,?,?)',p.hash,p.uid,p.email,'web',now+30*DAY),q('INSERT OR IGNORE INTO paisa_heads(uid,revision,parts,hash,bytes,updated,entry_count) VALUES (?,0,0,\'\',0,0,0)',p.uid)]);return {ok:true};
 }
 if(p.op==='pair.start'){
  if(!/^[A-Za-z0-9_-]{43}$/.test(p.challenge||'')||!valid(p.device,80))fail(400,'Invalid phone connection request');
  const id=crypto.randomUUID(),code=String(crypto.getRandomValues(new Uint32Array(1))[0]%1000000).padStart(6,'0');await q('INSERT INTO paisa_pairs(id,challenge,code,device,expires) VALUES (?,?,?,?,?)',id,p.challenge,code,p.device,now+10*60000).run();return {id,code,device:p.device,expires:now+10*60000};
 }
 if(p.op==='pair.info'){
  const pair=await first('SELECT code,device,expires FROM paisa_pairs WHERE id=? AND expires>?',p.id,now);if(!pair)fail(410,'This phone connection link expired. Start again in Paisa.');return pair;
 }
 if(p.op==='pair.claim'||p.op==='pair.cancel'){
  if(!/^[A-Za-z0-9_-]{43,128}$/.test(p.verifier||''))fail(401,'Invalid phone connection proof');const pair=await first('SELECT * FROM paisa_pairs WHERE id=? AND expires>?',p.id,now);if(!pair||await urlHash(p.verifier)!==pair.challenge)fail(401,'This phone connection link expired or is invalid');
  if(p.op==='pair.cancel'){await db.batch([q('DELETE FROM paisa_sessions WHERE hash IN (SELECT claimed_hash FROM paisa_pairs WHERE id=?)',p.id),q('DELETE FROM paisa_pairs WHERE id=?',p.id)]);return {ok:true};}
  if(!pair.approved_uid)return {pending:true};
  const token=await hash('paisa-device|'+p.verifier+'|'+p.id),tokenHash=await hash(token);if(pair.claimed_hash&&pair.claimed_hash!==tokenHash)fail(401,'This phone connection was already claimed');if(pair.claimed_hash){const issued=await first('SELECT uid FROM paisa_sessions WHERE hash=? AND expires>?',tokenHash,now);if(!issued)fail(410,'This phone connection was revoked. Start again.');return {token,user:{id:pair.approved_uid,email:pair.approved_email}};}
  await db.batch([q("INSERT OR IGNORE INTO paisa_sessions(hash,uid,email,kind,expires) SELECT ?,approved_uid,approved_email,'phone',? FROM paisa_pairs WHERE id=? AND expires>? AND approved_uid IS NOT NULL AND claimed_hash IS NULL",tokenHash,now+30*DAY,p.id,now),q('UPDATE paisa_pairs SET claimed_hash=? WHERE id=? AND expires>? AND approved_uid IS NOT NULL AND claimed_hash IS NULL',tokenHash,p.id,now),q("INSERT OR IGNORE INTO paisa_heads(uid,revision,parts,hash,bytes,updated,entry_count) VALUES (?,0,0,'',0,0,0)",pair.approved_uid)]);
  if(!await first('SELECT uid FROM paisa_sessions WHERE hash=? AND expires>?',tokenHash,now))fail(410,'This phone connection was cancelled. Start again.');return {token,user:{id:pair.approved_uid,email:pair.approved_email}};
 }
 const session=await auth(),uid=session.uid;
 if(p.op==='session'){return {user:{id:uid,email:session.email},kind:session.kind,expires:session.expires};}
 if(p.op==='logout'){await q('DELETE FROM paisa_sessions WHERE hash=?',p.sessionHash).run();return {ok:true};}
 if(p.op==='pair.approve'){
  if(session.kind!=='web')fail(403,'Approve this connection in your browser');const result=await q('UPDATE paisa_pairs SET approved_uid=?,approved_email=? WHERE id=? AND expires>? AND (approved_uid IS NULL OR approved_uid=?)',uid,session.email,p.id,now,uid).run();if(!result.meta.changes)fail(410,'This phone connection expired or was approved by another account');return {ok:true};
 }
 if(p.op==='meta')return meta(uid);
 if(p.op==='read'){
  const head=await meta(uid),revision=p.revision||head.revision;if(!Number.isInteger(revision)||revision<1)fail(404,'No cloud copy is available');const record=await first('SELECT * FROM paisa_revisions WHERE uid=? AND revision=?',uid,revision);if(!record)fail(404,'That saved cloud copy is no longer available');if(p.part===undefined)return {revision,parts:record.parts,hash:record.hash,bytes:record.bytes,updated:record.updated,entryCount:record.entry_count};
  if(!Number.isInteger(p.part)||p.part<0||p.part>=record.parts)fail(400,'Invalid cloud part');const part=await first('SELECT data FROM paisa_chunks WHERE upload_id=? AND part=?',record.upload_id,p.part);if(!part)fail(404,'Cloud copy changed. Please retry.');return {data:part.data};
 }
 if(p.op==='upload.begin'){
  const head=await meta(uid);if(p.baseRevision!==head.revision)fail(409,'Cloud changes arrived. Retry sync to merge them.');if(!Number.isInteger(p.parts)||p.parts<1||p.parts>512||!Number.isInteger(p.bytes)||p.bytes<1||p.bytes>MAX||!/^[a-f0-9]{64}$/.test(p.hash||'')||!Number.isInteger(p.entryCount)||p.entryCount<0||p.entryCount>100000)fail(400,'Cloud copy is invalid or too large');
  await db.batch([q('DELETE FROM paisa_chunks WHERE upload_id IN (SELECT id FROM paisa_uploads WHERE uid=? AND committed<>1 AND created<?)',uid,now-30*60000),q('DELETE FROM paisa_uploads WHERE uid=? AND committed<>1 AND created<?',uid,now-30*60000)]);
  if((await first('SELECT COUNT(*) AS n FROM paisa_uploads WHERE uid=? AND committed<>1',uid)).n>=8)fail(429,'Other sync attempts are still finishing. Please retry shortly.');
  const id=crypto.randomUUID();await q('INSERT INTO paisa_uploads(id,uid,base_revision,parts,hash,bytes,entry_count,created,committed) VALUES (?,?,?,?,?,?,?,?,0)',id,uid,p.baseRevision,p.parts,p.hash,p.bytes,p.entryCount,now).run();return {id};
 }
 if(p.op.startsWith('upload.')){
  const upload=await first('SELECT * FROM paisa_uploads WHERE id=? AND uid=? AND committed=0 AND created>?',p.id,uid,now-30*60000);if(!upload)fail(404,'This cloud upload expired. Retry sync.');
  if(p.op==='upload.abort'){await db.batch([q('DELETE FROM paisa_chunks WHERE upload_id=? AND EXISTS (SELECT 1 FROM paisa_uploads WHERE id=? AND uid=? AND committed=0)',upload.id,upload.id,uid),q('DELETE FROM paisa_uploads WHERE id=? AND uid=? AND committed=0',upload.id,uid)]);return {ok:true};}
  if(p.op==='upload.part'){
   if(!Number.isInteger(p.part)||p.part<0||p.part>=upload.parts||typeof p.data!=='string'||p.data.length>65536||!p.data.length||!/^[A-Za-z0-9+/=]+$/.test(p.data))fail(400,'Invalid cloud upload part');const written=await q('INSERT INTO paisa_chunks(upload_id,part,data) SELECT ?,?,? WHERE EXISTS (SELECT 1 FROM paisa_uploads WHERE id=? AND uid=? AND committed=0) ON CONFLICT(upload_id,part) DO UPDATE SET data=excluded.data WHERE EXISTS (SELECT 1 FROM paisa_uploads WHERE id=? AND uid=? AND committed=0)',upload.id,p.part,p.data,upload.id,uid,upload.id,uid).run();if(!written.meta.changes)fail(409,'This upload is already being committed');return {ok:true};
  }
  if(p.op!=='upload.commit')fail(400,'Unknown cloud upload action');const sealed=await q('UPDATE paisa_uploads SET committed=2 WHERE id=? AND uid=? AND committed=0',upload.id,uid).run();if(!sealed.meta.changes)fail(409,'This upload is already being committed');const pieces=await all('SELECT part,data FROM paisa_chunks WHERE upload_id=? ORDER BY part',upload.id),encoded=pieces.map(v=>v.data).join('');if(pieces.length!==upload.parts||encoded.length!==upload.bytes||await hash(encoded)!==upload.hash){await q('UPDATE paisa_uploads SET committed=0 WHERE id=? AND committed=2',upload.id).run();fail(400,'Cloud upload is incomplete or damaged');}
  const rev=upload.base_revision+1,results=await db.batch([
   q('INSERT INTO paisa_revisions(uid,revision,upload_id,parts,hash,bytes,updated,entry_count) SELECT h.uid,h.revision+1,u.id,u.parts,u.hash,u.bytes,?,u.entry_count FROM paisa_heads h JOIN paisa_uploads u ON u.id=? AND u.uid=h.uid AND u.committed=2 WHERE h.uid=? AND h.revision=? AND EXISTS (SELECT 1 FROM paisa_sessions WHERE hash=? AND uid=h.uid AND expires>?)',now,upload.id,uid,upload.base_revision,p.sessionHash,now),
   q('UPDATE paisa_heads SET revision=?,upload_id=?,parts=?,hash=?,bytes=?,updated=?,entry_count=? WHERE uid=? AND revision=? AND EXISTS (SELECT 1 FROM paisa_revisions WHERE uid=? AND revision=? AND upload_id=?)',rev,upload.id,upload.parts,upload.hash,upload.bytes,now,upload.entry_count,uid,upload.base_revision,uid,rev,upload.id),
   q('UPDATE paisa_uploads SET committed=1 WHERE id=? AND EXISTS (SELECT 1 FROM paisa_revisions WHERE uid=? AND revision=? AND upload_id=?)',upload.id,uid,rev,upload.id)
  ]);if(!results[1].meta.changes){await q('UPDATE paisa_uploads SET committed=0 WHERE id=? AND committed=2',upload.id).run();fail(409,'Cloud changes arrived. Retry sync to merge them.');}
  await db.batch([q('DELETE FROM paisa_revisions WHERE uid=? AND revision<?',uid,rev-4),q('DELETE FROM paisa_chunks WHERE upload_id IN (SELECT id FROM paisa_uploads WHERE uid=? AND committed=1 AND id NOT IN (SELECT upload_id FROM paisa_revisions WHERE uid=?))',uid,uid),q('DELETE FROM paisa_uploads WHERE uid=? AND committed=1 AND id NOT IN (SELECT upload_id FROM paisa_revisions WHERE uid=?)',uid,uid)]);return meta(uid);
 }
 if(p.op==='delete'){
  const head=await meta(uid);if(head.revision!==p.revision)fail(409,'Cloud data changed. Review the latest copy before deleting.');
  const guard=' AND EXISTS (SELECT 1 FROM paisa_heads WHERE uid=? AND revision=?)';const deleted=await db.batch([q('DELETE FROM paisa_chunks WHERE upload_id IN (SELECT id FROM paisa_uploads WHERE uid=?)'+guard,uid,uid,p.revision),q('DELETE FROM paisa_uploads WHERE uid=?'+guard,uid,uid,p.revision),q('DELETE FROM paisa_revisions WHERE uid=?'+guard,uid,uid,p.revision),q('DELETE FROM paisa_sessions WHERE uid=?'+guard,uid,uid,p.revision),q('DELETE FROM paisa_pairs WHERE approved_uid=?'+guard,uid,uid,p.revision),q('DELETE FROM paisa_heads WHERE uid=? AND revision=?',uid,p.revision)]);if(!deleted[5].meta.changes)fail(409,'Cloud data changed. Review before deleting.');return {ok:true};
 }
 fail(400,'Unknown cloud action');
}
export async function handleRpc(request,db){
 const headers={'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'};
 if(request.method!=='POST'||request.headers.get('X-Paisa-RPC')!=='1'||!request.headers.get('Content-Type')?.startsWith('application/json'))return new Response(JSON.stringify({error:'Private storage endpoint'}),{status:405,headers});
 try{const text=await request.text();if(text.length>100000)fail(413,'Cloud request is too large');const p=JSON.parse(text);if(typeof p.op!=='string')fail(400,'Invalid cloud action');return new Response(JSON.stringify(await rpc(db,p)),{headers});}catch(error){return new Response(JSON.stringify({error:error.status?error.message:'Cloud storage request failed'}),{status:error.status||500,headers});}
}
