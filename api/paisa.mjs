import { OAuth2Client } from 'google-auth-library';
import { handlePaisa } from '../cloud/paisa-api.mjs';

const google=new OAuth2Client();
export default async function handler(req,res){
 const origin=process.env.PAISA_ORIGIN||'https://paisa-fuzail.vercel.app';
 const storage=async payload=>{
  const base=process.env.PAISA_STORAGE_URL,token=process.env.PAISA_STORAGE_TOKEN;
  if(!base||!token)throw Error('Paisa private storage is not configured');
  const reply=await fetch(base+'/api/rpc',{method:'POST',headers:{'Content-Type':'application/json','X-Paisa-RPC':'1','OAI-Sites-Authorization':'Bearer '+token},body:JSON.stringify(payload),signal:AbortSignal.timeout(25000)});
  const value=await reply.json();if(!reply.ok)throw Object.assign(Error(value.error||'Paisa storage unavailable'),{status:reply.status});return value;
 };
 try{
  const headers=new Headers();for(const [key,value] of Object.entries(req.headers))if(value!==undefined)headers.set(key,Array.isArray(value)?value.join(','):String(value));
  const method=req.method||'GET',request=new Request(origin+req.url,{method,headers,...(method==='GET'||method==='HEAD'?{}:{body:typeof req.body==='string'?req.body:JSON.stringify(req.body||{})})});
  const response=await handlePaisa(request,{origin,clientId:process.env.PAISA_GOOGLE_CLIENT_ID||'',secret:process.env.PAISA_SESSION_SECRET||'',storage,verifyGoogle:async(credential,audience)=>(await google.verifyIdToken({idToken:credential,audience})).getPayload()});
  res.statusCode=response.status;response.headers.forEach((v,k)=>{if(k!=='set-cookie')res.setHeader(k,v);});const cookies=response.headers.getSetCookie();if(cookies.length)res.setHeader('Set-Cookie',cookies);res.end(Buffer.from(await response.arrayBuffer()));
 }catch{res.statusCode=503;res.setHeader('Cache-Control','no-store');res.setHeader('Content-Type','application/json');res.end(JSON.stringify({error:'Cloud sync is temporarily unavailable. Local records are safe.'}));}
}
