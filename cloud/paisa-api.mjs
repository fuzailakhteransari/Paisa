import { createHash, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';
const COOKIE='__Host-paisa-session',LOGIN='__Host-paisa-login',AGE=30*86400;
const sha=s=>createHash('sha256').update(s).digest('hex');
const cookie=(name,value,age)=>`${name}=${value}; Path=/; Max-Age=${age}; HttpOnly; Secure; SameSite=Lax`;
const cookies=h=>Object.fromEntries((h||'').split(';').map(v=>v.trim().split(/=(.*)/s).slice(0,2)).filter(v=>v.length===2));
const failure=(status,message)=>{throw Object.assign(new Error(message),{status});};
export async function handlePaisa(request,{origin,clientId,secret,storage,verifyGoogle}){
 const responseHeaders=new Headers({'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Vary':'Cookie, Authorization'});
 const json=(value,status=200)=>new Response(JSON.stringify(value),{status,headers:responseHeaders});
 try{
  if(!origin||!clientId||!secret||secret.length<32)failure(503,'Google sync setup is not ready. Local mode still works.');
  const url=new URL(request.url),action=url.searchParams.get('action')||'session',isAndroid=request.headers.get('X-Paisa-Client')==='android',jar=cookies(request.headers.get('Cookie'));
  const sign=s=>createHmac('sha256',secret).update('paisa-login|'+s).digest('base64url');
  function readLogin(){try{const [payload,signature,extra]=(jar[LOGIN]||'').split('.');if(extra||!payload||!signature||!/^[\w-]+$/.test(signature))return null;const expected=sign(payload);if(expected.length!==signature.length||!timingSafeEqual(Buffer.from(signature),Buffer.from(expected)))return null;const data=JSON.parse(Buffer.from(payload,'base64url'));return data.expires>Date.now()?data:null;}catch{return null;}}
  let login=readLogin();
  const token=request.headers.get('Authorization')?.match(/^Bearer ([a-f0-9]{64})$/)?.[1]||jar[COOKIE]||'';
  let sessionHash=/^[a-f0-9]{64}$/.test(token)?sha(token):'';
  const rpc=async p=>{if(!isAndroid&&p.op!=='session'){const expected=request.headers.get('X-Paisa-Owner'),active=await storage({op:'session',sessionHash});if(!expected||active.user.id!==expected)failure(409,'Google account changed. Refresh Paisa before syncing.');}return storage({...p,sessionHash});};
  if(!['GET','POST'].includes(request.method))failure(405,'Unsupported request');
  const anonymous=['session','login','pair.start','pair.info','pair.claim','pair.cancel'].includes(action);
  if(!anonymous&&!sessionHash)failure(401,'Sign in to use cloud sync');
  if(request.method==='POST'){
   if(!request.headers.get('Content-Type')?.startsWith('application/json'))failure(415,'Use a JSON request');
   const from=request.headers.get('Origin');if(from&&from!==origin)failure(403,'This request came from a different website');
   if(!isAndroid&&(!from||from!==origin||!login||request.headers.get('X-Paisa-CSRF')!==login.csrf))failure(403,'Refresh Paisa before making changes');
  }
  const text=request.method==='POST'?await request.text():'';if(text.length>100000)failure(413,'Cloud request is too large');let body={};if(text)try{body=JSON.parse(text);}catch{failure(400,'Invalid cloud request');}
  const ip=request.headers.get('x-vercel-forwarded-for')||request.headers.get('x-real-ip')||'unknown';
  const limited=['login','pair.start','pair.claim','pair.cancel','pair.approve'].includes(action);if(limited)await storage({op:'rate',key:sha(ip+'|'+action),limit:action==='pair.claim'?180:30,window:600000});
  if(action==='session'&&request.method==='GET'){
   if(!login){login={nonce:randomBytes(24).toString('base64url'),csrf:randomBytes(24).toString('base64url'),expires:Date.now()+600000};const payload=Buffer.from(JSON.stringify(login)).toString('base64url');responseHeaders.append('Set-Cookie',cookie(LOGIN,payload+'.'+sign(payload),600));}
   let account=null;if(sessionHash)try{account=await rpc({op:'session'});if(!isAndroid)responseHeaders.append('Set-Cookie',cookie(COOKIE,token,AGE));}catch(error){if(error.status!==401)throw error;responseHeaders.append('Set-Cookie',cookie(COOKIE,'',0));}
   return json({user:account?.user||null,csrf:login.csrf,nonce:login.nonce,googleClientId:clientId,origin,ready:true});
  }
  if(action==='login'&&request.method==='POST'){
   if(isAndroid)failure(403,'Sign in with Google in your browser');if(typeof body.credential!=='string'||body.credential.length>12000)failure(400,'Invalid Google sign-in');
   let profile;try{profile=await verifyGoogle(body.credential,clientId);}catch{failure(401,'Google sign-in could not be verified. Please try again.');}
   if(!profile?.sub||!profile.email||profile.email_verified!==true||profile.nonce!==login.nonce)failure(401,'Google sign-in proof expired or did not match. Please try again.');
   const fresh=randomBytes(32).toString('hex');await storage({op:'session.create',hash:sha(fresh),uid:profile.sub,email:profile.email});responseHeaders.append('Set-Cookie',cookie(COOKIE,fresh,AGE));responseHeaders.append('Set-Cookie',cookie(LOGIN,'',0));return json({user:{id:profile.sub,email:profile.email}});
  }
  if(action==='pair.start'&&request.method==='POST'){if(!isAndroid)failure(403,'Start this connection in the Android app');return json(await storage({op:'pair.start',challenge:body.challenge,device:body.device}));}
  if(action==='pair.info'&&request.method==='GET')return json(await storage({op:'pair.info',id:url.searchParams.get('id')}));
  if((action==='pair.claim'||action==='pair.cancel')&&request.method==='POST'){if(!isAndroid)failure(403,'Finish this connection in the Android app');return json(await storage({op:action,id:body.id,verifier:body.verifier}));}
  if(action==='pair.approve'&&request.method==='POST'){if(isAndroid)failure(403,'Approve this connection in your browser');return json(await rpc({op:action,id:body.id}));}
  if(action==='logout'&&request.method==='POST'){if(sessionHash)try{await rpc({op:'logout'});}catch(error){if(error.status!==401)throw error;}responseHeaders.append('Set-Cookie',cookie(COOKIE,'',0));responseHeaders.append('Set-Cookie',cookie(LOGIN,'',0));return json({ok:true});}
  if(action==='meta'&&request.method==='GET')return json(await rpc({op:'meta'}));
  if(action==='read'&&request.method==='GET'){
   const revision=url.searchParams.has('revision')?Number(url.searchParams.get('revision')):0,part=url.searchParams.has('part')?Number(url.searchParams.get('part')):undefined;return json(await rpc({op:'read',revision,part}));
  }
  if(action==='upload.begin'&&request.method==='POST')return json(await rpc({op:action,baseRevision:body.baseRevision,parts:body.parts,hash:body.hash,bytes:body.bytes,entryCount:body.entryCount}));
  if(action==='upload.part'&&request.method==='POST')return json(await rpc({op:action,id:body.id,part:body.part,data:body.data}));
  if(['upload.commit','upload.abort'].includes(action)&&request.method==='POST')return json(await rpc({op:action,id:body.id}));
  if(action==='delete'&&request.method==='POST'){const result=await rpc({op:'delete',revision:body.revision});responseHeaders.append('Set-Cookie',cookie(COOKIE,'',0));return json(result);}
  failure(400,'Unknown Paisa cloud action');
 }catch(error){return json({error:error.status?error.message:'Cloud sync is temporarily unavailable. Your local data is safe.'},error.status||503);}
}
