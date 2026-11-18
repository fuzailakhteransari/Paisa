// Run UI checks against a local-only static server; no separate server setup needed.
const http=require('node:http'),fs=require('node:fs/promises'),path=require('node:path');
const {spawn}=require('node:child_process');
const root=path.resolve(__dirname,'..'),assets=path.join(root,'app','src','main','assets');
const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8'};
const server=http.createServer(async(req,res)=>{try{
 const pathname=decodeURIComponent(new URL(req.url,'http://127.0.0.1').pathname);if(pathname==='/favicon.ico'){res.writeHead(204).end();return;}
 const file=path.resolve(assets,'.'+(pathname==='/'?'/index.html':pathname));if(!file.startsWith(assets+path.sep)){res.writeHead(403).end();return;}
 const bytes=await fs.readFile(file);res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store'}).end(bytes);
 }catch{res.writeHead(404).end();}});
function run(file){return new Promise((resolve,reject)=>{const child=spawn(process.execPath,[file],{cwd:root,stdio:'inherit'});child.on('error',reject);child.on('exit',code=>code===0?resolve():reject(Error(`${file} failed (${code})`)));});}
(async()=>{await fs.mkdir(path.join(root,'test-results'),{recursive:true});await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(8774,'127.0.0.1',resolve);});try{for(const test of ['tests/v3-ui.test.cjs','tests/payments-v3.test.cjs'])await run(test);}finally{await new Promise(resolve=>server.close(resolve));}})().catch(error=>{console.error(error.message);process.exitCode=1;});
