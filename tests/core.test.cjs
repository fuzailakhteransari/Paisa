const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/finance.js');
let checks=0;const equal=(a,b)=>{assert.deepEqual(a,b);checks++;};
equal(C.paise('1234.50'),123450);equal(C.paise('0.01'),1);equal(C.paise('99'),9900);
for(const invalid of ['-1','0','1.999','1e3','1,000','Infinity','']){assert.throws(()=>C.paise(invalid));checks++;}
const date=+new Date(2026,10,18,15,0),e=(kind,amount,extra={})=>({kind,amount,date,account:'Bank',category:'Other',...extra});
const rows=[e('income',5000000),e('expense',100000),e('expense',300000,{account:'Credit card · 1234'}),e('lend',200000,{person:'Alice'}),e('receive',50000,{person:'ALICE'}),e('borrow',70000,{person:'Bob'}),e('repay',20000,{person:'Bob'}),e('card_payment',300000,{cardId:'c1'}),e('transfer',1000000)];
const s=C.stats(rows,'day',date);equal(s.expense,400000);equal(s.income,5000000);equal(s.moneyIn,5120000);equal(s.moneyOut,620000);
equal(C.debts(rows),[{name:'Alice',receive:150000,pay:0},{name:'Bob',receive:0,pay:50000}]);
equal(C.billRemaining({id:'c1',amount:500000},rows),200000);
const [ws,we]=C.range('week',date);equal(new Date(ws).getDay(),1);equal(new Date(we).getDay(),1);equal(C.inRange(e('expense',100,{date:ws}),'week',date),true);equal(C.inRange(e('expense',100,{date:we}),'week',date),false);
const [ms,me]=C.range('month',date);equal(new Date(ms).getDate(),1);equal(new Date(me).getMonth(),11);equal(C.inRange(e('expense',100,{date:ms-1}),'month',date),false);
const refund=C.stats([...rows,e('refund',25000,{category:'Other'}),e('refund',40000,{category:'Other',account:'Credit card'})],'day',date);
equal(refund.expense,335000);equal(refund.income,5000000);equal(refund.moneyIn,5145000);
C.validateLedger(rows);checks++;
assert.throws(()=>C.validateLedger(rows.filter(e=>e.kind!=='lend')));checks++;
assert.throws(()=>C.validateLedger(rows.map(e=>e.kind==='borrow'?{...e,amount:1000}:e)));checks++;
console.log(`${checks} accounting / amount / period checks passed`);
