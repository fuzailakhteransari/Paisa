const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{spawnSync}=require('node:child_process');
const root=path.resolve(__dirname,'..'),privateDir=path.resolve(process.env.PAISA_SMS_DATA_DIR||path.join(root,'../private-paisa-sms'));
const S=require('../app/src/main/assets/sync-core.js'),C=require('../app/src/main/assets/finance.js');
if(!fs.existsSync(path.join(privateDir,'app-state.json'))){if(process.argv.includes('--required'))throw Error('Actual data required');console.log('Native sync interoperability skipped: private actual-data corpus is absent.');process.exit(0);}
const state=JSON.parse(fs.readFileSync(path.join(privateDir,'app-state.json'),'utf8')),row=state.entries.find(e=>e.kind==='expense');
const cases=[],add=(mutate,resolution='')=>{const c={base:S.clone(state),local:S.clone(state),remote:S.clone(state),resolution,edit:{...row,note:null,person:null}};mutate(c);cases.push(c);};
add(()=>{});add(c=>{c.local.entries.find(e=>e.id===row.id).note='Actual-data local sandbox';c.remote.entries.find(e=>e.id===row.id).category='Other';});
for(const resolution of ['','local','remote'])add(c=>{c.local.entries.find(e=>e.id===row.id).note='Device sandbox';c.remote.entries.find(e=>e.id===row.id).note='Cloud sandbox';},resolution);
add(c=>{c.local.entries.find(e=>e.id===row.id).deleted=true;c.remote.entries.find(e=>e.id===row.id).note='Cloud changed';},'remote');
add(c=>{c.local.settings.monthBudget=state.entries[0].amount;c.remote.settings.cycleStartDay=25;});
add(c=>{c.local.settings.syncPreference='google';c.local.settings.notificationCapture=true;c.remote.settings.syncPreference='local';});
const input=path.join(privateDir,'sync-interop-input.json'),output=path.join(privateDir,'sync-interop-output.json');fs.writeFileSync(input,JSON.stringify(cases));
const jar=path.join(root,'.cache/testlibs/json-20240303.jar'),build=path.join(root,'tests/build'),bin=name=>process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin',name+(process.platform==='win32'?'.exe':'')):name;
const run=(name,args)=>{const r=spawnSync(bin(name),args,{cwd:root,encoding:'utf8'});if(r.status!==0)throw Error(r.stderr||'Native sync check failed');return r.stdout.trim();};
run('javac',['-encoding','UTF-8','-cp',jar,'-d',build,'app/src/main/java/in/paisa/personal/Ledger.java','app/src/main/java/in/paisa/personal/SyncCore.java','tests/SyncInterop.java']);run('java',['-cp',[build,jar].join(path.delimiter),'in.paisa.personal.SyncInterop',input,output]);
const results=JSON.parse(fs.readFileSync(output,'utf8'));let checks=0;
cases.forEach((c,i)=>{const expected=S.merge(c.base,c.local,c.remote,c.resolution);assert(S.same(expected.state,results[i].state),'Native and web financial state differ in case '+i);assert.equal(expected.conflicts.length,results[i].conflicts,'Native and web conflict count differ in case '+i);assert(S.same(C.editableEntry(c.edit),results[i].edit),'Stale-edit guard differs in case '+i);checks+=3;});
console.log(`Native/web actual-data sync interoperability passed: ${checks} checks. Private records stayed local.`);
