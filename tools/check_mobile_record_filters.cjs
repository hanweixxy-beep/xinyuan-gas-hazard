const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '..');
const output = process.env.RECORD_FILTER_QA_OUTPUT || path.join(root, 'work', 'mobile-record-filter-qa');
const target = {
  id: 1001, customer_name: '目标用户', customer_phone: '', customer_address: '测试地址',
  status: 'pending', inspection_date: '2026-11-10', delivery_person: '王配送工',
  inspector_name: '李检查人、另一检查人', company_name: '甲公司', region_id: 11,
  region_name: '东片区', store_id: 101, store_name: '同名门店'
};
const records = [
  ...Array.from({ length: 1000 }, (_, index) => ({
    id: index + 1, customer_name: `普通测试记录 ${index + 1}`, status: 'pending'
  })),
  target,
  { ...target, id: 1002, company_name: '乙公司', region_id: 22, store_id: 202 },
  { ...target, id: 1003, inspector_name: '其他检查人', creator_name: '李检查人' },
  { ...target, id: 1004, status: 'rectified' },
  { ...target, id: 1005, customer_name: '历史记录', region_id: null, region_name: null, store_id: null, store_name: null }
];

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const server = http.createServer((req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname;
    const file = path.resolve(root, `.${pathname.endsWith('/') ? pathname + 'index.html' : pathname}`);
    if (!file.startsWith(root + path.sep) || !fs.existsSync(file)) return res.writeHead(404).end();
    res.setHeader('Content-Type', ({ '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css' })[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  let browser;
  try {
    browser = await chromium.launch({ channel: 'chrome', headless: true });
    for (const width of [375, 430]) {
      const context = await browser.newContext({ viewport: { width, height: 900 } });
      await context.addInitScript(() => localStorage.setItem('xinyuan_h5_token', 'mock-record-filters'));
      const page = await context.newPage();
      const requestedPages = [];
      const errors = [];
      let duplicatePage = false;
      page.on('pageerror', (error) => errors.push(error.message));
      await page.route('**/api/**', async (route) => {
        const url = new URL(route.request().url());
        let data;
        if (url.pathname === '/api/auth/me') {
          data = { user: { id: 1, name: '模拟管理员', role: 'hq_admin', companyName: '模拟公司' } };
        } else if (url.pathname === '/api/records') {
          const number = Number(url.searchParams.get('page'));
          const size = Number(url.searchParams.get('pageSize'));
          assert([1, 3, 100].includes(size));
          if (size === 100) requestedPages.push(number);
          const date = url.searchParams.get('dateFrom');
          const matches = date ? records.filter((record) => record.inspection_date === date) : records;
          const offset = duplicatePage && number === 2 ? 0 : (number - 1) * size;
          data = { records: matches.slice(offset, offset + size),
            pagination: { page: number, pageSize: size, total: matches.length, pageCount: Math.max(1, Math.ceil(matches.length / size)) },
            summary: { total: matches.length, pending: matches.filter((record) => record.status === 'pending').length } };
        } else throw new Error(`Unexpected test API: ${url.pathname}`);
        await route.fulfill({ json: { ok: true, data } });
      });

      await page.goto(`http://127.0.0.1:${server.address().port}/frontend/mobile/`);
      await page.locator('#homeRecordCount').getByText('1005', { exact: true }).waitFor();
      await page.locator('[data-nav="records"]').click();
      const results = page.locator('#recordFilterResult');
      const cards = page.locator('#mobileRecordList [data-record-id]');
      await results.getByText('共 1005 条记录', { exact: true }).waitFor();
      assert.deepEqual(requestedPages, Array.from({ length: 11 }, (_, index) => index + 1));
      assert.equal(await cards.count(), 50);
      await page.locator('#recordsMoreBtn').click();
      assert.equal(await cards.count(), 100);

      const form = page.locator('#mobileRecordFilters');
      const region = form.getByLabel('片区', { exact: true });
      const store = form.getByLabel('门店', { exact: true });
      await page.locator('#recordFilterSummary').click();
      assert.deepEqual(await region.locator('option').allTextContents(), ['全部片区', '甲公司 / 东片区', '乙公司 / 东片区']);
      await region.selectOption('11');
      assert.deepEqual(await store.locator('option').allTextContents(), ['全部门店', '同名门店 / 东片区 / 甲公司']);
      await store.selectOption('101');
      await form.getByLabel('配送工', { exact: true }).fill(' 王 ');
      await form.getByLabel('检查人', { exact: true }).fill('李');
      await form.locator('[name="keyword"]').fill('目标');
      await form.getByText(/待整改/).click();
      await form.getByRole('button', { name: '查询' }).click();
      await results.getByText('共 1 条记录', { exact: true }).waitFor();
      assert.deepEqual(await cards.evaluateAll((items) => items.map((item) => item.dataset.recordId)), ['1001']);
      assert.equal(await page.locator('#recordFilterSummary').textContent(), '筛选（4）');

      await page.locator('#recordFilterSummary').click();
      await page.locator('#recordExtraFilters').screenshot({ path: path.join(output, `mobile-record-filters-${width}.png`) });
      await region.selectOption('22');
      assert.equal(await store.inputValue(), '');
      assert.deepEqual(await store.locator('option').allTextContents(), ['全部门店', '同名门店 / 东片区 / 乙公司']);
      await store.selectOption('202');
      await form.getByRole('button', { name: '查询' }).click();
      assert.deepEqual(await cards.evaluateAll((items) => items.map((item) => item.dataset.recordId)), ['1002']);

      duplicatePage = true;
      await page.locator('#recordsRefreshBtn').click();
      await page.locator('#toast').getByText('记录加载不完整，请刷新后重试。', { exact: true }).waitFor();
      assert.deepEqual(await cards.evaluateAll((items) => items.map((item) => item.dataset.recordId)), ['1002']);
      await page.locator('#recordFilterSummary').click();
      await form.getByRole('button', { name: '重置筛选' }).click();
      assert.equal(await region.inputValue(), '');
      assert.equal(await store.inputValue(), '');
      await form.locator('[name="keyword"]').fill('历史');
      await form.getByRole('button', { name: '查询' }).click();
      assert.deepEqual(await cards.evaluateAll((items) => items.map((item) => item.dataset.recordId)), ['1005']);
      assert.equal(requestedPages.length, 13);
      assert.deepEqual(errors, []);
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      await context.close();
      console.log(`H5 ${width}px: all 1005 records, combined filters, dependent organization selectors, same names, history, reset and unstable page protection passed.`);
    }
  } finally {
    if (browser) await browser.close();
    await new Promise((resolve) => server.close(resolve));
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });
