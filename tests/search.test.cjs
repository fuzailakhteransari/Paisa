// Synthetic privacy-reviewed fixtures; all identities, notes and amounts are fictional.
const assert=require('node:assert/strict'),path=require('node:path'),{spawnSync}=require('node:child_process');
const C=require('../app/src/main/assets/finance.js');
let checks=0;const eq=(a,b,label)=>{assert.deepEqual(a,b,label);checks++;};
const date=(day,time='12:00:00')=>Date.parse(`2026-11-${day}T${time}`),now=date('19');
const entry=(id,kind,amount,note,extra={})=>({id,kind,amount,note,date:now,title:'Payment',category:'Other',person:'',account:'Bank',...extra});
const rows=[
 entry('s1','expense',100000,'Alice at sample restaurant'),
 entry('s2','expense',40000,'Coffee with ALICE',{date:date('18')}),
 entry('sl','lend',200000,'Alice ko diya',{person:'Alice',date:date('17')}),
 entry('sr','receive',50000,'Part returned',{person:'Alice',loanId:'sl'}),
 entry('al','lend',400000,'Loan to Bob',{person:'Bob',date:date('16')}),
 entry('as','expense',60000,'Restaurant with Bob',{date:date('18')}),
 entry('ar','receive',100000,'Returned',{person:'Bob',loanId:'al'}),
 entry('f1','expense',240000,'Fuel/Petrol'),
 entry('f2','expense',160000,'Petrol refill',{date:date('18')}),
 entry('fr','refund',20000,'Petrol refund'),
 entry('possible','expense',100000,'Alice restaurant',{duplicateOf:'s1'}),
 entry('deleted','expense',198000,'Alice',{deleted:true}),
];
const find=query=>rows.filter(e=>C.matchesQuery(e,query));
eq(C.searchSummary(find('alice')).spent,140000,'old notes are searchable without a migration');
eq(C.searchSummary(find('ALICE')).lent,200000,'person search separates loans from spending');
eq(C.searchSummary(find('alice')).received,50000,'repayment matches the person even when the note omits the name');
eq(C.searchSummary(find('alice')).count,4,'deleted records and possible duplicates are excluded from counted totals');
eq(C.searchSummary(find('Bob')).spent,60000,'Bob spending is not inflated by lending');
eq(C.searchSummary(find('Bob')).lent,400000,'Bob original money lent is reported separately');
eq(C.searchSummary(find('Bob')).received,100000,'Bob repayments stay separate from ordinary income');
eq(C.searchSummary(find('Bob')).income,0,'loan repayment is not salary');
eq(C.searchSummary(find('fuel')).spent,380000,'Fuel finds Petrol and nets the refund');
eq(C.searchSummary(find('Petrol')).spent,380000,'Petrol finds Fuel');
eq(C.searchSummary(find('Fuel/Petrol')).spent,380000,'slash-separated aliases are one concept');
eq(C.searchSummary(find('Peetrol')).spent,380000,'the requested alternate spelling is supported');
eq(C.matchesQuery(rows[0],'ali'),true,'partial person names work while typing');
eq(C.matchesQuery(rows[0],'Alice restaurant'),true,'multiple words narrow the results');
eq(C.matchesQuery(rows[1],'Alice restaurant'),false,'all query words must match');
eq(C.matchesQuery(entry('name','expense',100,'Salary'),'Ali'),false,'a person query is not a substring in salary');
eq(C.matchesQuery(rows[0],'Other'),true,'category is searchable');
eq(C.matchesQuery(rows[0],'1000'),true,'amount remains searchable');
eq(C.noteTags('Alice ko diya').map(t=>t.label),['Alice'],'Hinglish filler words are not tag clutter');
eq(C.noteTags('Fuel/Petrol').map(t=>t.label),['Fuel'],'Fuel/Petrol gets one automatic tag');
eq(C.noteTags('Lunch #weekend').some(t=>t.key==='weekend'),true,'optional explicit hashtags work');
eq(C.noteTags('अलिस के साथ').some(t=>t.label==='अलिस'),true,'Unicode names remain supported');
eq(C.tagIndex([rows[0],rows[1],rows[0]]).find(t=>t.key==='alice').count,3,'tag counts are per matching transaction');
eq(rows[0].note,'Alice at sample restaurant','tag extraction never rewrites the original note');
const dates={from:'2026-11-18',to:'2026-11-19'},[start,end]=C.range('custom',now,25,dates);
eq(C.day(start),'2026-11-18','custom start ignores the salary cycle');
eq(C.day(end),'2026-11-20','selected end date is inclusive');
eq(C.inRange({date:date('19','23:59:59.999')},'custom',now,1,dates),true,'the last millisecond of the end date is included');
eq(C.inRange({date:date('20','00:00:00')},'custom',now,1,dates),false,'the following midnight is excluded');
eq(C.inRange({date:date('17','23:59:59.999')},'custom',now,1,dates),false,'the prior day is excluded');
eq(C.stats(rows,'custom',now,{},[],dates).expense,580000,'global custom range totals use the same boundaries as search');
eq(C.searchSummary(find('Alice').filter(e=>C.inRange(e,'day',now))).spent,100000,'Today search totals');
eq(C.searchSummary(find('Alice').filter(e=>C.inRange(e,'week',now))).spent,140000,'Week search totals');
eq(C.searchSummary(find('Alice').filter(e=>C.inRange(e,'month',now))).spent,140000,'Month search totals');
for(const bad of [{from:'2026-11-20',to:'2026-11-19'},{from:'2026-02-30',to:'2026-03-01'},{from:'',to:'2026-11-19'}]){
 assert.throws(()=>C.range('custom',now,1,bad));checks++;
}
eq(C.range('custom',now,1,{from:'2024-02-29',to:'2024-02-29'})[1]-C.range('custom',now,1,{from:'2024-02-29',to:'2024-02-29'})[0],86400000,'leap-day same-date range works');
for(const [day,hours] of [['2026-03-08',23],['2026-11-01',25]]){
 const source=`const C=require(${JSON.stringify(path.resolve(__dirname,'../app/src/main/assets/finance.js'))});const r=C.range('custom',0,1,{from:'${day}',to:'${day}'});process.stdout.write(String((r[1]-r[0])/3600000));`;
 const result=spawnSync(process.execPath,['-e',source],{encoding:'utf8',env:{...process.env,TZ:'America/New_York'}});
 assert.equal(result.status,0,result.stderr);eq(Number(result.stdout),hours,'local calendar dates survive daylight-saving boundaries');
}
console.log(`${checks} note search / totals / date-range checks passed`);
