// Private data stays outside the checkout and is never uploaded by CI.
const fs=require('node:fs/promises'),path=require('node:path'),crypto=require('node:crypto');
const {spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),directory=path.resolve(process.env.PAISA_SMS_DATA_DIR||path.join(root,'../private-paisa-sms'));
const file=name=>{const value=path.resolve(directory,name);if(!value.startsWith(directory+path.sep))throw Error('Invalid private corpus path');return value;};
const readRows=async name=>(await fs.readFile(file(name),'utf8')).trim().split(/\r?\n/).filter(Boolean).map(line=>JSON.parse(line));
const sha=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
function run(name,args){const executable=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',name+(process.platform==='win32'?'.exe':'')):name;const result=spawnSync(executable,args,{cwd:root,encoding:'utf8',maxBuffer:1024*1024});if(result.status!==0||result.error)throw Error(result.error?.message||`${name} private checks failed; ${result.stderr.trim()}`);return result.stdout.trim();}
(async()=>{
 let manifest;try{manifest=JSON.parse(await fs.readFile(file('manifest.json'),'utf8'));}catch(error){if(process.argv.includes('--required')||process.env.PAISA_SMS_DATA_DIR)throw Error('Approved private SMS corpus is required. Set PAISA_SMS_DATA_DIR.');console.log('Private SMS corpus is absent: real-data checks skipped. CI has no private data.');return;}
 for(const [name,digest] of [[manifest.source,manifest.sha256],[manifest.messages,manifest.messagesSha256],[manifest.expected,manifest.expectedSha256],[manifest.baseline,manifest.baselineSha256]]){
  if(!digest||sha(await fs.readFile(file(name)))!==digest)throw Error('Private corpus integrity mismatch; review the immutable dataset/labels locally.');
 }
 const jar=path.join(root,'.cache/testlibs/json-20240303.jar');try{await fs.access(jar);}catch{throw Error('Run npm run test:native first to prepare the verified desktop JSON test dependency.');}
 const build=path.join(root,'tests/build');await fs.mkdir(build,{recursive:true});
 const classes=['Ledger','PreviousSmsParser','SmsParser','NoticeParser','CaptureMatcher','CapturePolicy','TransferLink','MutationGuard','BackupCipher'];
 run('javac',['-encoding','UTF-8','-cp',jar,'-d',build,...classes.map(name=>`app/src/main/java/in/paisa/personal/${name}.java`),'tests/RealSmsRunner.java','tests/RealSmsScenarios.java']);
 const classpath=[build,jar].join(path.delimiter),results='current-results.jsonl';
 const scan=JSON.parse(run('java',['-cp',classpath,'in.paisa.personal.RealSmsRunner',file(manifest.messages),file(results)]));
 const actual=await readRows(results),expected=await readRows(manifest.expected),byId=new Map(actual.map(row=>[row.id,row]));
 const mismatches=[];let fields=0;
 for(const label of expected){const row=byId.get(label.id),bad=[];fields++;if(!row)bad.push('missing_case');else{
  fields++;if(row.error)bad.push('exception');
  if(label.expected===null){fields++;if(row.parsed)bad.push('false_positive');}
  else {fields++;if(!row.parsed)bad.push('missed');else for(const [key,value] of Object.entries(label.expected)){fields++;if(row.parsed[key]!==value)bad.push(key);}}
  if(label.bill){fields+=3;if(!row.bill||row.bill.amount!==label.billAmount||row.bill.due!==label.billDue)bad.push('bill_notice');}
 }
 if(bad.length)mismatches.push({id:label.id,reason:label.reason,fields:bad,expected:label.expected,actual:row?.parsed});}
 await fs.writeFile(file('last-mismatches.json'),JSON.stringify(mismatches,null,2));
 if(actual.length!==manifest.smsCount||expected.length!==manifest.smsCount||mismatches.length)throw Error(`Private corpus comparison failed (${mismatches.length} cases); see private last-mismatches.json. Raw SMS are not printed.`);
 const native=JSON.parse(run('java',['-cp',classpath,'in.paisa.personal.RealSmsScenarios',file(manifest.messages),file(manifest.baseline),file('app-state.json'),file('scenario-summary.json'),file('upgraded-state.json')]));
 const state=JSON.parse(await fs.readFile(file('app-state.json'),'utf8'));
 const workflows=await require('./test-real-sms-ui.cjs')({state,directory,sourceCount:manifest.smsCount});
 const summary={smsCases:manifest.smsCount,inbox:manifest.types['1'],outgoing:manifest.types['2'],fieldChecks:fields,comparisonMismatches:0,parser:scan,native,workflows};
 await fs.writeFile(file('last-audit-summary.json'),JSON.stringify(summary,null,2));
 console.log(`Private actual-data checks passed: ${manifest.smsCount} SMS, ${fields} comparisons, ${native.checks} native checks, ${workflows.checks} UI/accounting checks. Private records were not logged or uploaded.`);
})().catch(error=>{console.error(error.message);process.exitCode=1;});
