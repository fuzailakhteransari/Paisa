// Synthetic privacy-reviewed fixtures; all identities, notes and amounts are fictional.
const {chromium}=require('playwright'),assert=require('node:assert/strict'),path=require('node:path');
(async()=>{
 const browser=await chromium.launch({...(process.env.PAISA_BROWSER_CHANNEL?{channel:process.env.PAISA_BROWSER_CHANNEL}:{}),headless:true});
 const context=await browser.newContext({viewport:{width:390,height:940},timezoneId:'Asia/Kolkata'}),page=await context.newPage();
 const errors=[];page.on('pageerror',error=>errors.push(error.message));
 await page.clock.setFixedTime(new Date('2026-11-19T12:00:00+05:30'));
 const date=day=>Date.parse(`2026-11-${day}T12:00:00+05:30`);
 const entry=(id,kind,amount,note,extra={})=>({id,kind,amount,note,date:date('19'),title:'Payment',category:'Other',person:'',account:'Bank',accountId:'bank:default',source:'Manual',pending:false,...extra});
 const fixture={entries:[
  entry('s1','expense',100000,'Alice at sample restaurant'),entry('s2','expense',40000,'Coffee with ALICE',{date:date('18')}),
  entry('sl','lend',200000,'Alice ko diya',{person:'Alice',date:date('17')}),entry('sr','receive',50000,'Part returned',{person:'Alice',loanId:'sl'}),
  entry('al','lend',400000,'Loan to Bob',{person:'Bob',date:date('16')}),entry('as','expense',60000,'Restaurant with Bob',{date:date('18')}),entry('ar','receive',100000,'Returned',{person:'Bob',loanId:'al'}),
  entry('f1','expense',240000,'Fuel/Petrol',{accountId:'card',account:'Credit card'}),entry('f2','expense',160000,'Petrol refill',{date:date('18')}),entry('fr','refund',20000,'Petrol refund'),
  entry('other-sms','expense',100,'',{title:'Unknown merchant',source:'SMS',pending:true,category:'Unsorted',merchantKey:'name:unknown'}),
  entry('possible','expense',100000,'Alice restaurant',{duplicateOf:'s1',pending:true})
 ],accounts:[{id:'bank:default',name:'Bank',type:'bank',opening:0,openingAt:0},{id:'cash',name:'Cash',type:'cash',opening:0,openingAt:0},{id:'card',name:'Credit card',type:'credit',opening:0,openingAt:0}],bills:[],subscriptions:[],settings:{cycleStartDay:1,monthBudget:0,categoryBudgets:{}}};
 await page.addInitScript(value=>{if(!localStorage.getItem('paisa-preview'))localStorage.setItem('paisa-preview',JSON.stringify(value));},fixture);
 let checks=0;const check=(value,label)=>{assert(value,label);checks++;};
 const total=key=>page.locator(`[data-total="${key}"]`).textContent();
 const search=text=>page.locator('#searchEntries').fill(text);
 const range=async(from,to)=>{await page.getByRole('button',{name:'Choose date range',exact:true}).click();await page.locator('#rangeFrom').fill(from);await page.locator('#rangeTo').fill(to);await page.getByRole('button',{name:'Apply date range',exact:true}).click();};
 const menu=async name=>{await page.locator('#settings').click();await page.locator(`[data-menu="${name}"]`).click();};
 await page.goto('http://127.0.0.1:8774/');
 await page.locator('[data-nav="activity"]').click();await page.locator('[data-entry="other-sms"]').click();
 check(await page.getByRole('button',{name:'Other',exact:true}).isVisible(),'Other is available in quick payment review');
 await page.getByRole('button',{name:'Other',exact:true}).click();
 check(await page.evaluate(()=>JSON.parse(localStorage.getItem('paisa-preview')).entries.find(e=>e.id==='other-sms').category)==='Other','quick Other category is saved');
 await page.locator('#searchEntries').evaluate(element=>window.originalSearchInput=element);await search('Alice');
 check(await page.locator('#searchEntries').evaluate(element=>element===window.originalSearchInput&&document.activeElement===element),'typing retains the same search input and keyboard focus');
 check(await total('spent')==='₹1,400','Alice matching spending total');
 check(await total('lent')==='₹2,000','Alice loans are separate from spending');
 check(await total('received')==='₹500','Alice repayments match person name');
 check((await page.locator('#searchSummary').textContent()).includes('4 counted'),'possible duplicate does not inflate the search totals');
 await page.locator('[data-period="day"]').click();check(await total('spent')==='₹1,000','Today search filter');
 await page.locator('[data-period="week"]').click();check(await total('spent')==='₹1,400','Week search filter');
 await page.locator('[data-period="month"]').click();check(await total('spent')==='₹1,400','Month search filter');
 await search('Bob');await page.locator('#searchEntries').press('Enter');check(await page.locator('#searchEntries').evaluate(e=>document.activeElement!==e),'Search keyboard action shows results without keeping the keyboard focused');check(await total('spent')==='₹600'&&await total('lent')==='₹4,000'&&await total('received')==='₹1,000','Bob spending, lending and repayments shown separately');
 await page.screenshot({path:path.resolve(__dirname,'../test-results/search-bob.png')});
 await search('Fuel/Petrol');check(await total('spent')==='₹3,800','Fuel/Petrol search includes both descriptions and refunds');
 await search('Peetrol');check(await total('spent')==='₹3,800','alternate spelling searches the same fuel group');
 await search('Alice');await range('2026-11-18','2026-11-18');check(await total('spent')==='₹400','single-date inclusive search range');
 check(await page.locator('[data-total="lent"]').count()===0,'out-of-range lending does not appear in range totals');
 await page.locator('[data-nav="home"]').click();check(await page.locator('.hero-amount').textContent()==='₹2,600','same date range applies to overall Home spending');
 check((await page.locator('.period-nav').textContent()).includes('18 Nov 2026'),'active overall date range is visible');
 await page.locator('#viewAll').click();check(await total('spent')==='₹400','Activity retains the global range and search');
 await page.getByRole('button',{name:'Choose date range',exact:true}).click();await page.locator('#rangeFrom').fill('2026-11-19');await page.locator('#rangeTo').fill('2026-11-18');await page.getByRole('button',{name:'Apply date range',exact:true}).click();
 check((await page.locator('#formError').textContent()).includes('end date'),'inverted date range has a clear inline error');
 await page.locator('#cancelRange').click();check(await total('spent')==='₹400','cancelled invalid range leaves the active report unchanged');
 await page.locator('[data-period="all"]').click();await page.locator('#clearSearch').click();
 await page.locator('.tag-browser summary').click();check(await page.locator('[data-tag="Alice"]').count()===1,'old note words appear as deduplicated browseable tags');
 await page.locator('[data-tag="Alice"]').click();check(await total('spent')==='₹1,400','tap a note tag to search');
 await page.locator('[data-entry="s1"]').click();check((await page.locator('.note-tags').textContent()).includes('#Alice'),'edit form derives note tags without rewriting notes');
 await page.locator('#fNote').fill('Alice at sample restaurant #weekend');check((await page.locator('.note-tags').textContent()).includes('#weekend'),'optional hashtag preview updates as notes are typed');
 await page.getByRole('button',{name:'Save entry',exact:true}).click();await page.reload();
 check(await page.evaluate(()=>JSON.parse(localStorage.getItem('paisa-preview')).entries.find(e=>e.id==='s1').note)==='Alice at sample restaurant #weekend','full note and hashtag persist after reload');
 await page.locator('[data-nav="activity"]').click();await search('#weekend');check(await total('spent')==='₹1,000','saved hashtags are searchable');
 await search('NoSuchName');check((await page.locator('#activityResults').textContent()).includes('Try a shorter name or a wider period'),'empty search has a useful recovery hint');
 await menu('accounts');await page.locator('[data-history="bank:default"]').click();await range('2026-11-18','2026-11-19');await search('Fuel');
 check(await total('spent')==='₹1,400','account history combines account, date range and note search');
 await page.locator('#clearAccount').click();check(await total('spent')==='₹3,800','clearing account restores all-account search totals');
 await page.screenshot({path:path.resolve(__dirname,'../test-results/search-fuel.png')});
 for(const viewport of [{width:320,height:740},{width:375,height:812},{width:844,height:390}]){
  await page.setViewportSize(viewport);check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'search/range layout fits '+viewport.width+'px');
 }
 await page.emulateMedia({colorScheme:'dark',reducedMotion:'reduce'});await page.getByRole('button',{name:'Choose date range',exact:true}).click();
 check(await page.locator('#rangeFrom').isVisible(),'date range form works in dark mode and landscape');await page.locator('#cancelRange').click();
 await page.evaluate(()=>document.documentElement.style.fontSize='24px');check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'search respects larger text without horizontal overflow');
 check(errors.length===0,'no search/date-range browser errors: '+errors.join(';'));
 await browser.close();console.log(`${checks} search totals / tags / date-range UI checks passed`);
})().catch(error=>{console.error(error);process.exit(1);});
