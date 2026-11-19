(function(root){
 'use strict';
 const TYPES={expense:'Expense',income:'Income',refund:'Refund',lend:'Money lent',borrow:'Money borrowed',receive:'Repayment received',repay:'Loan repayment',card_payment:'Card bill payment',transfer:'Own transfer'},MAX=100000000000;
 const clone=x=>JSON.parse(JSON.stringify(x));
 const day=value=>{const d=new Date(value);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;};
 const localDate=s=>new Date(s+'T12:00:00');
 const active=rows=>rows.filter(x=>!x.deleted&&!x.duplicateOf);
 function paise(value){const s=String(value).trim();if(!/^\d+(\.\d{1,2})?$/.test(s))throw Error('Enter a valid amount with up to 2 decimal places.');const [a,b='']=s.split('.'),n=Number(a)*100+Number(b.padEnd(2,'0'));if(!Number.isSafeInteger(n)||n<=0||n>MAX)throw Error('Enter a valid amount.');return n;}
 function cycleDate(y,m,start){const d=new Date(y,m,1);d.setDate(Math.min(start,new Date(y,m+1,0).getDate()));d.setHours(0,0,0,0);return d;}
 function range(period,anchor=Date.now(),cycleStart=1,dates={}){
  if(period==='custom'){
   const parse=value=>{
    if(!/^\d{4}-\d{2}-\d{2}$/.test(value||''))throw Error('Choose a valid start and end date.');
    const [year,month,date]=value.split('-').map(Number),result=new Date(year,month-1,date);
    if(year<2000||day(result)!==value)throw Error('Choose a valid start and end date.');
    return result;
   };
   const start=parse(dates.from),end=parse(dates.to);
   if(end<start)throw Error('The end date must be on or after the start date.');
   end.setDate(end.getDate()+1);
   return [+start,+end];
  }
  const d=new Date(anchor);d.setHours(0,0,0,0);let s=new Date(d),t=new Date(d);
  if(period==='all')return [0,Number.MAX_SAFE_INTEGER];
  if(period==='month'){
   const n=Math.min(31,Math.max(1,Number(cycleStart)||1));s=cycleDate(d.getFullYear(),d.getMonth(),n);
   if(d<s)s=cycleDate(d.getFullYear(),d.getMonth()-1,n);
   t=cycleDate(s.getFullYear(),s.getMonth()+1,n);
  }else if(period==='week'){
   s.setDate(d.getDate()-(d.getDay()+6)%7);t=new Date(s);t.setDate(s.getDate()+7);
  }else t.setDate(t.getDate()+1);
  return [+s,+t];
 }
 function inRange(e,period,anchor,cycleStart=1,dates={}){const [s,t]=range(period,anchor,cycleStart,dates);return e.date>=s&&e.date<t;}
 function isCredit(e,accounts=[]){return accounts.find(a=>a.id===e.accountId)?.type==='credit'||String(e.account||'').startsWith('Credit card');}
 function stats(entries,period='month',anchor=Date.now(),settings={},accounts=[],dates={}){let expense=0,income=0,moneyIn=0,moneyOut=0,pending=0;const categories={};for(const e of active(entries).filter(x=>inRange(x,period,anchor,settings.cycleStartDay||1,dates))){if(e.pending)pending++;if(e.kind==='expense'||e.kind==='refund'){const n=e.kind==='refund'?-e.amount:e.amount;expense+=n;const c=e.category||'Unsorted';categories[c]=(categories[c]||0)+n;}if(e.kind==='income')income+=e.amount;if(['income','borrow','receive'].includes(e.kind)||(e.kind==='refund'&&!isCredit(e,accounts)))moneyIn+=e.amount;if(['lend','repay','card_payment'].includes(e.kind)||(e.kind==='expense'&&!isCredit(e,accounts)))moneyOut+=e.amount;}return {expense,income,moneyIn,moneyOut,pending,categories};}
 function loanPositions(entries){const rows=active(entries),loans=rows.filter(e=>['lend','borrow'].includes(e.kind)).map(e=>({...e,remaining:e.amount,repayments:[]})).sort((a,b)=>a.date-b.date),repayments=rows.filter(e=>['receive','repay'].includes(e.kind)).sort((a,b)=>a.date-b.date);for(const r of repayments.filter(r=>r.loanId)){const l=loans.find(l=>l.id===r.loanId);if(!l||l.kind!==(r.kind==='receive'?'lend':'borrow')||l.person.trim().toLowerCase()!==r.person.trim().toLowerCase())throw Error('The repayment must match a valid original loan.');l.remaining-=r.amount;l.repayments.push({...r,allocated:r.amount});if(l.remaining<0)throw Error('The repayment exceeds the original loan balance.');}for(const r of repayments.filter(r=>!r.loanId)){let n=r.amount;for(const l of loans.filter(l=>l.kind===(r.kind==='receive'?'lend':'borrow')&&l.person.trim().toLowerCase()===r.person.trim().toLowerCase())){const used=Math.min(n,l.remaining);if(used>0){l.remaining-=used;l.repayments.push({...r,allocated:used});n-=used;}if(!n)break;}if(n>0)throw Error('Repayments exceed the recorded loan balance.');}return loans;}
 function debts(entries){const map={};for(const l of loanPositions(entries)){const k=l.person.trim().toLowerCase();map[k]||={name:l.person,receive:0,pay:0};map[k][l.kind==='lend'?'receive':'pay']+=l.remaining;}return Object.values(map).filter(x=>x.receive||x.pay);}
 function validateLedger(entries){loanPositions(entries);}
 function billRemaining(b,entries){return Math.max(0,b.amount-active(entries).filter(e=>e.kind==='card_payment'&&e.cardId===b.id||e.kind==='expense'&&e.billId===b.id).reduce((n,e)=>n+e.amount,0));}
 function accountFlow(e,id){if(e.duplicateOf||e.deleted)return 0;if(e.kind==='transfer')return (e.toAccountId===id?e.amount:0)-(e.accountId===id?e.amount:0);let n=e.accountId===id?(['income','borrow','receive','refund'].includes(e.kind)?1:-1)*e.amount:0;if(e.kind==='card_payment'&&e.toAccountId===id)n+=e.amount;return n;}
 function balance(a,entries){const snapshot=Number.isSafeInteger(a.snapshot),base=snapshot?a.snapshot:(a.opening||0),asOf=snapshot?(a.snapshotAt||0):(a.openingAt||0);return {amount:base+active(entries).filter(e=>snapshot?e.date>asOf:e.date>=asOf).reduce((s,e)=>s+accountFlow(e,a.id),0),asOf,source:snapshot?'Last SMS + later recorded entries':'Opening balance + recorded entries'};}
 function budgetStatus(data,anchor=Date.now()){const s=stats(data.entries,'month',anchor,data.settings,data.accounts),[start,end]=range('month',anchor,data.settings.cycleStartDay),rows=[];if(data.settings.monthBudget>0)rows.push({id:'total',name:'Monthly budget',limit:data.settings.monthBudget,spent:s.expense});for(const [name,limit] of Object.entries(data.settings.categoryBudgets||{}))if(limit>0)rows.push({id:name,name,limit,spent:s.categories[name]||0});return rows.map(b=>({...b,left:b.limit-b.spent,ratio:b.spent/b.limit,start,end}));}
 function advanceDate(date,cycle,anchorDay){const d=localDate(date);if(cycle==='weekly'){d.setDate(d.getDate()+7);return day(d);}return day(cycleDate(d.getFullYear(),d.getMonth()+(cycle==='yearly'?12:1),anchorDay||d.getDate()));}
 function nextDue(sub,paidAt){let next=sub.nextDue,steps=0;const paid=day(paidAt),early=(localDate(next)-localDate(paid))/86400000;if(early>0&&early<=(sub.cycle==='weekly'?2:7))return advanceDate(next,sub.cycle,sub.anchorDay);while(next<=paid&&steps++<1200)next=advanceDate(next,sub.cycle,sub.anchorDay);return next;}
 function recurringCandidates(data){const groups={};for(const e of active(data.entries).filter(e=>e.kind==='expense'&&!e.subId)){const k=e.merchantKey||e.title.trim().toLowerCase();if(!k||/^(payment|upi payment)$/.test(k))continue;(groups[k]||=[]).push(e);}const out=[];for(const [k,rows] of Object.entries(groups)){if(data.subscriptions.some(s=>s.merchantKey===k||s.name.toLowerCase()===rows[0].title.toLowerCase()))continue;rows.sort((a,b)=>a.date-b.date);if(rows.length<2)continue;const a=rows.at(-2),b=rows.at(-1),days=(b.date-a.date)/86400000,cycle=days>=5&&days<=9?'weekly':days>=21&&days<=40?'monthly':days>=330&&days<=400?'yearly':'';if(!cycle||Math.abs(a.amount-b.amount)>Math.max(100,a.amount*.1))continue;out.push({name:b.title,amount:b.amount,cycle,merchantKey:k,nextDue:advanceDate(day(b.date),cycle,new Date(b.date).getDate()),anchorDay:new Date(b.date).getDate(),category:b.category,accountId:b.accountId});}return out;}
 function normalize(input={}){const d={...input,entries:input.entries||[],bills:input.bills||[],accounts:input.accounts||[],subscriptions:input.subscriptions||[],rules:input.rules||{},settings:{cycleStartDay:1,monthBudget:0,categoryBudgets:{},reminders:true,emailApps:['com.google.android.gm'],bankApps:[],...input.settings}};if(!d.accounts.some(a=>a.id==='cash'))d.accounts.push({id:'cash',name:'Cash',type:'cash',opening:0,openingAt:0});for(const e of d.entries){e.note||='';e.person||='';if(!e.accountId){const label=e.account||'Bank';e.accountId=label==='Cash'?'cash':'legacy:'+label.toLowerCase().replace(/[^a-z0-9]+/g,'-');}if(!d.accounts.some(a=>a.id===e.accountId))d.accounts.push({id:e.accountId,name:e.account||'Bank',type:String(e.account).startsWith('Credit card')?'credit':e.accountId==='cash'?'cash':'bank',opening:0,openingAt:0});}if(!d.accounts.some(a=>a.type==='bank'))d.accounts.push({id:'bank:default',name:'Bank',type:'bank',opening:0,openingAt:0});return d;}
 function validate(d){if(d.entries.length>100000)throw Error('Too many entries.');const ids=new Set();for(const e of d.entries){if(!e.id||ids.has(e.id))throw Error('Duplicate transaction ID.');ids.add(e.id);if(!Number.isSafeInteger(e.amount)||e.amount<=0||e.amount>MAX||!TYPES[e.kind])throw Error('Invalid transaction amount/type.');if(!Number.isFinite(e.date)||e.date<946684800000)throw Error('Invalid date.');if((e.note||'').length>1000)throw Error('Note maximum 1000 characters.');if(['lend','borrow','receive','repay'].includes(e.kind)&&!e.person?.trim())throw Error('Enter the person name.');if(e.kind==='transfer'&&(!e.toAccountId||e.toAccountId===e.accountId))throw Error('Choose different source and destination accounts.');}validateLedger(d.entries);for(const b of d.bills)if(active(d.entries).filter(e=>e.kind==='card_payment'&&e.cardId===b.id).reduce((s,e)=>s+e.amount,0)>b.amount)throw Error('The payment exceeds the remaining bill amount.');return d;}

 // Notes stay intact. Search tags are derived locally, including for older entries.
 const fuelWords=new Set(['fuel','petrol','peetrol']);
 const tagStopWords=new Set('a an the and or to of in on at for from with is was were this that my me i it as by ko ka ki ke k saath sath mein ne se pe par liye diya diye di tha thi itne itna kra kiya kar kr kare hua hui bharwaya'.split(' '));
 function searchTokens(value){return (String(value||'').normalize('NFKC').toLowerCase().match(/[\p{L}\p{M}\p{N}]+/gu)||[]).map(word=>fuelWords.has(word)?'fuel':word);}
 function noteTags(note){
  const found=new Map();
  for(const match of String(note||'').matchAll(/#?[\p{L}][\p{L}\p{M}\p{N}_-]*/gu)){
   const raw=match[0],label=raw.replace(/^#/,''),words=searchTokens(label),key=words.join(' ');
   if(!key||key.length<2||key.length>40||!raw.startsWith('#')&&words.every(word=>tagStopWords.has(word)))continue;
   if(!found.has(key))found.set(key,{key,label:key==='fuel'?'Fuel':label});
   if(found.size===8)break;
  }
  return [...found.values()];
 }
 function matchesQuery(entry,query){
  const terms=[...new Set(searchTokens(query))];if(!terms.length)return true;
  const fields=[entry.title,entry.note,entry.person,entry.category,entry.account,TYPES[entry.kind],String(entry.amount/100)];
  const words=searchTokens(fields.join(' '));
  return terms.every(term=>words.some(word=>word.startsWith(term)));
 }
 function tagIndex(entries){
  const index=new Map();
  for(const entry of active(entries))for(const tag of noteTags(entry.note)){
   const old=index.get(tag.key);if(old)old.count++;else index.set(tag.key,{...tag,count:1});
  }
  return [...index.values()].sort((a,b)=>b.count-a.count||a.label.localeCompare(b.label)).slice(0,30);
 }
 function searchSummary(entries){
  const rows=active(entries),summary={count:rows.length,spent:0,lent:0,borrowed:0,received:0,repaid:0,income:0,cardPayments:0,transfers:0};
  const keys={lend:'lent',borrow:'borrowed',receive:'received',repay:'repaid',income:'income',card_payment:'cardPayments',transfer:'transfers'};
  for(const entry of rows){
   if(entry.kind==='expense')summary.spent+=entry.amount;
   else if(entry.kind==='refund')summary.spent-=entry.amount;
   else if(keys[entry.kind])summary[keys[entry.kind]]+=entry.amount;
  }
  return summary;
 }
 const api={searchTokens,noteTags,matchesQuery,tagIndex,searchSummary,TYPES,clone,day,active,paise,range,inRange,stats,loanPositions,debts,validateLedger,billRemaining,accountFlow,balance,budgetStatus,advanceDate,nextDue,recurringCandidates,normalize,validate};if(typeof module!=='undefined')module.exports=api;root.PaisaCore=api;
})(typeof window==='undefined'?globalThis:window);
