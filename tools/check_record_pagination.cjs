const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '..');
(async () => {
 const server = http.createServer((req,res) => {
  const u = new URL(req.url,'http://localhost');
  const file = path.resolve(root,'.'+(u.pathname.endsWith('/') ? u.pathname+'index.html' : u.pathname));
  if(!file.startsWith(root+path.sep)||!fs.existsSync(file)) return res.writeHead(404).end();
  res.setHeader('Content-Type',file.endsWith('.js')?'application/javascript':file.endsWith('.css')?'text/css':'text/html');
  fs.createReadStream(file).pipe(res);
 });
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 let browser;
 try {
 browser=await chromium.launch({headless:true,channel:'chrome'});
 for(const width of [1080,1440]) {
 const context=await browser.newContext({viewport:{width,height:1000}});
 await context.addInitScript(()=>localStorage.setItem('xinyuan_admin_token','mock-only'));
 const page=await context.newPage(); const errors=[]; const queries=[];
 let fail=false; let delay=false;
 page.on('pageerror',e=>errors.push(e.message));
 await page.route('**/api/**',async route=>{
  const u=new URL(route.request().url());const endpoint=u.pathname.replace('/api','');let data;
  if(endpoint==='/auth/me') data={user:{id:1,role:'hq_admin',name:'分页测试',companyId:1}};
  else if(endpoint==='/admin/companies')data={companies:[]};
  else if(endpoint==='/admin/organization')data={regions:[],stores:[]};
  else if(endpoint==='/export/jobs')data={jobs:[]};
  else if(endpoint==='/records') {
   queries.push(u.searchParams); const n=Number(u.searchParams.get('page'));assert.equal(u.searchParams.get('pageSize'),'50');
   if(fail){fail=false;return route.fulfill({status:500,json:{ok:false,message:'模拟查询失败'}});}
   const keyword=u.searchParams.get('keyword');const total=keyword==='empty'?0:keyword==='filtered'?53:1205;
   const current=Math.min(n,Math.max(1,Math.ceil(total/50)));const start=(current-1)*50;
   const records=Array.from({length:Math.min(50,total-start)},(_,i)=>({id:start+i+1,customer_name:'QA_'+(start+i+1),status:'done',inspection_date:'2026-10-25'}));
   data={records,pagination:{page:current,pageSize:50,total,pageCount:Math.max(1,Math.ceil(total/50))},summary:{total,pending:total?5:0,done:Math.max(0,total-5),trend:total?[{date:'2026-10-25',count:total}]:[],pendingRecords:total?[{id:1205,customer_name:'OLDER_PENDING',status:'pending'}]:[]}};
   if(delay&&n===2)await new Promise(r=>setTimeout(r,500));
  } else return route.fulfill({status:404,json:{ok:false,message:endpoint}});
  await route.fulfill({json:{ok:true,data}});
 });
 await page.goto(`http://127.0.0.1:${server.address().port}/frontend/admin/`);
 await page.locator('#totalCount').filter({hasText:'1205'}).waitFor();
 assert.equal(await page.locator('#pendingCount').textContent(),'5');
 assert.match(await page.locator('#dashboardPendingRows').textContent(),/OLDER_PENDING/);
 assert.match(await page.locator('#dashboardTrend').textContent(),/1205/);
 await page.locator('#sideNav [data-module="records"]').click();
 await page.locator('#recordRows tr').first().filter({hasText:'QA_1'}).waitFor();
 assert.equal(await page.locator('#recordRows tr').count(),50);
 for(let n=2;n<=25;n++) {
  await page.locator('[data-page="next"]').click();
  await page.locator('#recordPager').filter({hasText:`第 ${n} / 25 页`}).waitFor();
 }
 assert.match(await page.locator('#recordPager').textContent(),/1201-1205/);
 assert.equal(await page.locator('#recordRows tr').count(),5);
 assert.equal(await page.locator('[data-page="next"]').isDisabled(),true);
 assert.match(await page.locator('#recordRows').textContent(),/QA_1205/);
 const output=process.env.PAGINATION_QA_OUTPUT||path.join(root,'work','pagination-qa');fs.mkdirSync(output,{recursive:true});
 await page.screenshot({path:path.join(output,`last-page-${width}.png`),fullPage:true});
 await page.locator('[name="keyword"]').fill('filtered');
 await page.locator('#filtersForm button[type="submit"]').click();
 await page.locator('#recordPager').filter({hasText:'共 53 条'}).waitFor();
 assert.match(await page.locator('#recordPager').textContent(),/第 1 \/ 2 页/);
 // Edits to an unsubmitted filter must not change the active pagination query.
 await page.locator('[name="keyword"]').fill('unapplied');
 await page.locator('[data-page="next"]').click();
 await page.locator('#recordPager').filter({hasText:'第 2 / 2 页'}).waitFor();
 assert.equal(queries.at(-1).get('keyword'),'filtered');
 fail=true;await page.locator('[data-page="prev"]').click();
 await page.locator('#toast').filter({hasText:'模拟查询失败'}).waitFor();
 assert.match(await page.locator('#recordPager').textContent(),/第 2 \/ 2 页/);
 await page.locator('[name="keyword"]').fill('empty');
 await page.locator('#filtersForm button[type="submit"]').click();
 await page.locator('#recordPager').filter({hasText:'共 0 条'}).waitFor();
 assert.match(await page.locator('#recordPager').textContent(),/当前 0-0 条/);
 assert.equal(await page.locator('[data-page="next"]').isDisabled(),true);
 // A slow old page must not replace a newer filter result.
 await page.locator('[name="keyword"]').fill('filtered');
 await page.locator('#filtersForm button[type="submit"]').click();
 await page.locator('#recordPager').filter({hasText:'共 53 条'}).waitFor();
 delay=true;
 const slow=page.waitForRequest(r=>r.url().includes('/api/records?')&&new URL(r.url()).searchParams.get('page')==='2');
 await page.locator('[data-page="next"]').click();await slow;
 await page.locator('[name="keyword"]').fill('empty');
 await page.locator('#filtersForm button[type="submit"]').click();
 await page.locator('#recordPager').filter({hasText:'共 0 条'}).waitFor();
 await page.waitForTimeout(700);
 assert.match(await page.locator('#recordPager').textContent(),/共 0 条/);
 assert.deepEqual(errors,[]);
 console.log(`Pagination browser checks passed ${width}px: 1205 records, last page, statistics, filters, failure, empty.`);
 await context.close();
 }
 } finally {await browser?.close();server.close();}
})().catch(e=>{console.error(e);process.exitCode=1});
