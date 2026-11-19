// Desktop domain checks. JSON-java is a test-only dependency, verified before use.
const fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto');
const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),cache=path.join(root,'.cache','testlibs'),jar=path.join(cache,'json-20240303.jar');
const digest='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed';
function run(name,args){const executable=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',name+(process.platform==='win32'?'.exe':'')):name;const result=spawnSync(executable,args,{cwd:root,stdio:'inherit'});if(result.error)throw result.error;if(result.status!==0)throw Error(`${name} failed (${result.status})`);}
(async()=>{
 await fs.mkdir(cache,{recursive:true});let bytes;try{bytes=await fs.readFile(jar);}catch{
  const response=await fetch('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar');if(!response.ok)throw Error(`Could not download JSON-java (${response.status})`);bytes=Buffer.from(await response.arrayBuffer());
 }
 if(crypto.createHash('sha256').update(bytes).digest('hex')!==digest)throw Error('JSON-java checksum mismatch');await fs.writeFile(jar,bytes);
 const out=path.join(root,'tests','build');await fs.mkdir(out,{recursive:true});
 const classes=['Ledger','PreviousSmsParser','SmsParser','NoticeParser','BackupCipher','EmailText','MutationGuard','CaptureMatcher','CapturePolicy','TransferLink','SyncCore'].map(name=>`app/src/main/java/in/paisa/personal/${name}.java`);
 run('javac',['-encoding','UTF-8','-cp',jar,'-d',out,...classes,'tests/ParserTest.java','tests/UpgradeTest.java','tests/CardSmsTest.java']);
 for(const name of ['ParserTest','UpgradeTest','CardSmsTest'])run('java',['-cp',[out,jar].join(path.delimiter),`in.paisa.personal.${name}`]);
})().catch(error=>{console.error(error.message);process.exitCode=1;});
