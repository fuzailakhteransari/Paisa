(function(root){
 'use strict';
 const TABLES=['entries','accounts','bills','subscriptions','rules'];
 const FIELDS={
  entries:'id amount kind title category account accountId toAccountId creditDate transferPairId transferLinkedTo person loanId subId cardId billId date pending source sourcePackage sources note merchantKey reference bank parserRevision captureReview userEdited duplicateOf deleted unresolvedTransfer originalKind fingerprint',
  accounts:'id name type opening openingAt snapshot snapshotAt deleted',
  bills:'id name amount due kind accountId createdAt source deleted',
  subscriptions:'id name amount cycle nextDue anchorDay category accountId active merchantKey lastPaidAt deleted',
  rules:'id category deleted'
 };
 const MONEY='amount kind accountId toAccountId loanId person cardId billId subId date creditDate duplicateOf captureReview unresolvedTransfer transferPairId transferLinkedTo'.split(' ');
 const clone=x=>x===undefined?undefined:JSON.parse(JSON.stringify(x));
 const object=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
 function canonical(value){if(value===undefined)return 'undefined';if(Array.isArray(value))return '['+value.map(canonical).join(',')+']';if(object(value))return '{'+Object.keys(value).sort().map(k=>JSON.stringify(k)+':'+canonical(value[k])).join(',')+'}';return JSON.stringify(value);}
 const same=(a,b)=>canonical(a)===canonical(b);
 function rows(value){if(Array.isArray(value))return value;if(object(value))return Object.entries(value).map(([id,category])=>({id,category}));return [];}
 function snapshot(input={}){
  const out={version:3};for(const table of TABLES){const fields=FIELDS[table].split(' ');out[table]=rows(input[table]).map(row=>Object.fromEntries(fields.filter(k=>Object.hasOwn(row,k)).map(k=>[k,clone(row[k])]))).sort((a,b)=>String(a.id)<String(b.id)?-1:String(a.id)>String(b.id)?1:0);}
  const settings=input.settings||{};out.settings={cycleStartDay:settings.cycleStartDay??1,monthBudget:settings.monthBudget??0,categoryBudgets:clone(settings.categoryBudgets||{})};
  const identities=new Map();for(const row of rows(input.seen)){if(typeof row.fingerprint!=='string'||!row.fingerprint||typeof row.entryId!=='string'||!row.entryId)throw Error('Invalid capture identity');const old=identities.get(row.fingerprint);if(old&&old.entryId!==row.entryId)throw Error('A capture identity points to different payments');identities.set(row.fingerprint,{fingerprint:row.fingerprint,entryId:row.entryId});}out.seen=[...identities.values()].sort((a,b)=>a.fingerprint<b.fingerprint?-1:a.fingerprint>b.fingerprint?1:0);
  return out;
 }
 function merge(baseInput,localInput,remoteInput,resolution=''){
  const base=snapshot(baseInput),local=snapshot(localInput),remote=snapshot(remoteInput),out={version:3},conflicts=[];
  function conflict(b,l,r,meta){conflicts.push({...meta,base:clone(b),local:clone(l),remote:clone(r)});return clone(resolution==='remote'?r:l);}
  function value(b,l,r,meta){
   if(same(l,r))return clone(l);if(same(l,b))return clone(r);if(same(r,b))return clone(l);
   if(meta.field==='sources'&&Array.isArray(l)&&Array.isArray(r))return [...new Set([...l,...r])].sort();
   if(object(l)&&object(r)&&(object(b)||b===undefined)){
    const result={};for(const key of new Set([...Object.keys(b||{}),...Object.keys(l),...Object.keys(r)])){const v=value(b?.[key],l[key],r[key],{...meta,field:meta.field?meta.field+'.'+key:key});if(v!==undefined)result[key]=v;}return result;
   }
   return conflict(b,l,r,meta);
  }
  for(const table of TABLES){
   const bm=new Map(base[table].map(v=>[v.id,v])),lm=new Map(local[table].map(v=>[v.id,v])),rm=new Map(remote[table].map(v=>[v.id,v]));out[table]=[];
   for(const id of new Set([...bm.keys(),...lm.keys(),...rm.keys()])){
    const b=bm.get(id),l=lm.get(id),r=rm.get(id),meta={table,id,label:l?.title||r?.title||l?.name||r?.name||id,field:''};let row;
    if(table==='entries'&&object(l)&&object(r)&&!same(l,r)&&!same(l,b)&&!same(r,b)){
     if(Boolean(l.deleted)!==Boolean(r.deleted)){row=conflict(b,l,r,{...meta,field:'$record'});}
     else{
      const group=v=>Object.fromEntries(MONEY.filter(k=>v&&Object.hasOwn(v,k)).map(k=>[k,v[k]])),bg=group(b),lg=group(l),rg=group(r);
      if(!same(lg,bg)&&!same(rg,bg)&&!same(lg,rg)){
       const selected=conflict(bg,lg,rg,{...meta,field:'$accounting'});row={};
       for(const key of new Set([...Object.keys(b||{}),...Object.keys(l),...Object.keys(r)]))if(!MONEY.includes(key)){const v=value(b?.[key],l[key],r[key],{...meta,field:key});if(v!==undefined)row[key]=v;}
       Object.assign(row,selected);
      }else row=value(b,l,r,meta);
     }
    }else row=value(b,l,r,meta);
    if(row!==undefined)out[table].push(row);
   }
  }
  out.settings=value(base.settings,local.settings,remote.settings,{table:'settings',id:'settings',label:'Budget and monthly cycle',field:''});
  // Capture identities are append-only, including those of deleted transactions.
  const seen=new Map();for(const row of [...base.seen,...local.seen,...remote.seen]){const old=seen.get(row.fingerprint);if(old&&old.entryId!==row.entryId){const chosen=conflict(undefined,old,row,{table:'seen',id:row.fingerprint,label:'Captured-payment identity',field:'entryId'});seen.set(row.fingerprint,chosen);}else seen.set(row.fingerprint,row);}
  out.seen=[...seen.values()];return {state:snapshot(out),conflicts};
 }
 function compose(local,shared){const next=clone(local),clean=snapshot(shared);for(const table of TABLES)next[table]=clean[table];next.seen=clean.seen;next.settings={...(local.settings||{}),...clean.settings};return next;}
 function validate(input,core=root.PaisaCore){
  const s=snapshot(input);if(!core)throw Error('Accounting rules are unavailable');const full=core.normalize(clone(s));core.validate(full);
  for(const table of TABLES){const ids=new Set();if(s[table].length>100000)throw Error('Cloud workspace has too many records');for(const row of s[table]){if(typeof row.id!=='string'||!row.id||row.id.length>120||ids.has(row.id))throw Error('Invalid or repeated cloud record ID');ids.add(row.id);}}
  const account=id=>s.accounts.find(a=>a.id===id&&!a.deleted),bill=id=>s.bills.find(b=>b.id===id&&!b.deleted);
  for(const e of core.active(s.entries)){
   if(!account(e.accountId))throw Error('A cloud payment account is missing');
   if(e.kind==='transfer'&&!e.unresolvedTransfer&&(!account(e.toAccountId)||e.accountId===e.toAccountId))throw Error('A transfer needs two valid accounts');
   if(e.kind==='card_payment'&&e.toAccountId&&(!account(e.toAccountId)||account(e.toAccountId).type!=='credit'||e.toAccountId===e.accountId))throw Error('Card payment destination is invalid');
   if(e.loanId&&!['receive','repay'].includes(e.kind))throw Error('Only repayments can link an original loan');
   if(e.billId&&(!bill(e.billId)||e.kind!=='expense'||bill(e.billId).kind!=='utility'))throw Error('Cloud utility-bill link is invalid');
   if(e.cardId&&(!bill(e.cardId)||e.kind!=='card_payment'))throw Error('Cloud statement link is invalid');
   if(e.subId&&!s.subscriptions.some(v=>v.id===e.subId&&!v.deleted))throw Error('Cloud subscription link is invalid');
  }
  if(s.settings.cycleStartDay<1||s.settings.cycleStartDay>31||!Number.isSafeInteger(s.settings.monthBudget)||s.settings.monthBudget<0)throw Error('Cloud budget settings are invalid');
  for(const r of s.rules)if(typeof r.category!=='string'||r.category.length>40)throw Error('Cloud category rule is invalid');
  return s;
 }
 const api={TABLES,FIELDS,MONEY,clone,canonical,same,snapshot,merge,compose,validate,rows};if(typeof module!=='undefined')module.exports=api;root.PaisaSyncCore=api;
})(typeof window==='undefined'?globalThis:window);
