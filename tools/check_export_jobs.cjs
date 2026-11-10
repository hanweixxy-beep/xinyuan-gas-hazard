const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '..');
const output = process.env.EXPORT_QA_OUTPUT || path.join(root, 'work', 'export-jobs-qa');

(async () => {
  fs.mkdirSync(output, { recursive: true });
  let downloadRequests = 0;
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const pathname = url.pathname;
    if (/^\/api\/export\/jobs\/1\/files\/\d+$/.test(pathname)) {
      assert.equal(url.searchParams.get('ticket'), 'mock-signed-download-ticket');
      assert(!url.search.includes('mock-only'));
      downloadRequests++;
      res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
      res.setHeader('Content-Disposition', 'attachment; filename="test-export.xlsx"');
      return res.end('mock download only');
    }
    const file = path.resolve(root, `.${pathname.endsWith('/') ? `${pathname}index.html` : pathname}`);
    if (!file.startsWith(`${root}${path.sep}`) || !fs.existsSync(file)) return res.writeHead(404).end();
    res.setHeader('Content-Type', ({ '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css' })[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  let browser;
  try {
    browser = await chromium.launch({ headless: true, channel: 'chrome' });
    for (const width of [1080, 1440]) {
      const context = await browser.newContext({ viewport: { width, height: 1100 } });
      await context.addInitScript(() => localStorage.setItem('xinyuan_admin_token', 'mock-only'));
      const page = await context.newPage();
      const errors = [];
      const queries = [];
      const exports = [];
      let jobs = [];
      let failRefresh = false;
      let rejectTicket = false;
      downloadRequests = 0;
      page.on('pageerror', (error) => errors.push(error.message));
      await page.route('**/api/**', async (route) => {
        const url = new URL(route.request().url());
        const endpoint = url.pathname.replace('/api', '');
        let data;
        if (endpoint === '/auth/me') data = { user: { id: 1, role: 'hq_admin', name: '测试管理员', companyId: 1, companyName: '测试公司' } };
        else if (endpoint === '/admin/companies') data = { companies: [{ id: 1, name: '测试公司', code: 'TEST', status: 'active' }] };
        else if (endpoint === '/admin/organization') data = { regions: [], stores: [] };
        else if (endpoint === '/records') {
          const parsed = {};
          for (const [key, value] of url.searchParams) {
            if (key.endsWith('[]')) (parsed[key.slice(0, -2)] ||= []).push(value);
            else parsed[key] = value;
          }
          queries.push(parsed);
          data = { records: [], pagination: { page: 1, pageSize: 50, total: 0, pageCount: 1 }, summary: { total: 0, pending: 0, done: 0, trend: [], pendingRecords: [] } };
        } else if (endpoint === '/export/jobs' && route.request().method() === 'POST') {
          const filters = route.request().postDataJSON();
          exports.push(filters);
          const job = { id: 1, photoMode: filters.photoMode || 'embedded', status: 'queued', total: 6001, processed: 0, photos: 0, missingPhotos: 0, files: [], filters: Object.fromEntries(Object.entries(filters).filter(([key]) => key !== 'photoMode')), createdAt: '2026-10-20 10:00:00' };
          jobs = [job];
          data = { job };
        } else if (endpoint === '/export/jobs') {
          if (failRefresh) return route.fulfill({ status: 503, json: { ok: false, message: '模拟网络中断' } });
          data = { jobs };
        } else if (endpoint === '/export/jobs/1/cancel') {
          jobs[0].status = 'cancelled';
          data = { cancelled: true };
        } else if (/\/export\/jobs\/1\/files\/\d+\/ticket/.test(endpoint)) {
          if (rejectTicket) return route.fulfill({ status: 403, json: { ok: false, message: '账号权限已变化，请重新提交导出任务。' } });
          data = { ticket: 'mock-signed-download-ticket' };
        } else if (/\/export\/jobs\/1\/files\/\d+$/.test(endpoint)) {
          downloadRequests++;
          assert.equal(url.searchParams.get('ticket'), 'mock-signed-download-ticket');
          assert(!url.search.includes('mock-only'), 'Login token must not appear in URL');
          return route.fulfill({ headers: { 'Content-Disposition': 'attachment; filename="test-export.xlsx"' }, contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', body: Buffer.from('mock download only') });
        } else throw new Error(`Unexpected endpoint ${endpoint}`);
        await route.fulfill({ json: { ok: true, data } });
      });
      await page.goto(`http://127.0.0.1:${server.address().port}/frontend/admin/`);
      await page.locator('#sideNav [data-module="records"]').click();
      const select = page.locator('#filterHazardGroup');
      await select.locator('summary').click();
      assert.equal(await select.locator('input[type="checkbox"]').count(), 13);
      await select.getByRole('checkbox', { name: '灶具无熄火保护装置' }).check();
      await select.locator('input[value="hose_tee"]').check();
      await page.locator('#filterGasGroup summary').click();
      await page.locator('#filterGasOptions input[value="虚假安检"]').check();
      await page.locator('#filterGasOptions input[value="钢瓶无二维码"]').check();
      assert.equal(await page.locator('#filterGasOptions input').count(), 6);
      await page.locator('[name="status"]').selectOption('pending');
      await page.locator('[name="dateFrom"]').fill('2026-10-01');
      const query = page.waitForResponse((r) => r.url().includes('/api/records?') && r.url().includes('stove_no_flame_failure_device'));
      await page.locator('#filtersForm button[type="submit"]').click();
      await query;
      assert.deepEqual(queries.at(-1).hazardCodes, ['hose_tee', 'stove_no_flame_failure_device']);
      assert.deepEqual(queries.at(-1).gasSourceIssues, ['钢瓶无二维码', '虚假安检']);
      assert.equal(queries.at(-1).status, 'pending');
      assert.equal(queries.at(-1).dateFrom, '2026-10-01');
      await page.screenshot({ path: path.join(output, `multi-filter-${width}.png`), fullPage: true });
      const bounds = await select.boundingBox();
      assert(bounds.width > 200 && bounds.x + bounds.width <= width, 'Hazard selector fits desktop');
      await page.locator('#exportLinksBtn').click();
      await page.locator('#exportJobsList').getByText('导出任务 #1 · 排队中', { exact: true }).waitFor();
      assert.deepEqual(Object.fromEntries(Object.entries(exports.at(-1)).filter(([key, v]) => key !== 'photoMode' && (Array.isArray(v) ? v.length : v))), Object.fromEntries(Object.entries(queries.at(-1)).filter(([key]) => !['page', 'pageSize'].includes(key))));
      assert.equal(exports.at(-1).photoMode, 'links');
      assert.match(await page.locator('#exportJobsList').textContent(), /照片链接版/);
      assert.match(await page.locator('#exportJobsList').textContent(), /6001/);
      await page.reload();
      await page.locator('#sideNav [data-module="export"]').click();
      await page.locator('#exportJobsList').getByText('导出任务 #1 · 排队中', { exact: true }).waitFor();
      jobs[0] = { ...jobs[0], status: 'processing', processed: 2300, photos: 500 };
      await page.locator('#refreshExportsBtn').click();
      await page.locator('#exportJobsList progress[value="38"]').waitFor();
      failRefresh = true;
      await page.locator('#refreshExportsBtn').click();
      await page.locator('#exportJobsError').filter({ hasText: '后台任务会继续' }).waitFor();
      assert.equal(await page.locator('#exportJobsList progress').count(), 1);
      failRefresh = false;
      jobs[0] = { ...jobs[0], status: 'finished', processed: 6001, missingPhotos: 2, expiresAt: 1793145600,
        files: [{ name: 'part-0001.xlsx', records: 3000, bytes: 15000000 }, { name: 'part-0002.xlsx', records: 3001, bytes: 9000000 }] };
      await page.locator('#refreshExportsBtn').click();
      await page.locator('[data-export-action="download"][data-part="2"]').waitFor();
      assert.equal(await page.locator('[data-export-action="download"]').count(), 3);
      for (const part of [1, 2, 0]) {
        const download = page.waitForEvent('download');
        await page.locator(`[data-export-action="download"][data-part="${part}"]`).click();
        assert.equal(await (await download).failure(), null, 'Native download completes');
      }
      assert.equal(downloadRequests, 3);
      rejectTicket = true;
      await page.locator('[data-export-action="download"][data-part="1"]').click();
      await page.locator('#toast').filter({ hasText: '账号权限已变化' }).waitFor();
      assert.equal(downloadRequests, 3);
      rejectTicket = false;
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), 'No horizontal overflow');
      await page.screenshot({ path: path.join(output, `export-jobs-${width}.png`), fullPage: true });
      jobs[0] = { ...jobs[0], files: [{ name: 'part-0001.xlsx', records: 6001, bytes: 900000 }] };
      await page.locator('#refreshExportsBtn').click();
      await page.locator('[data-export-action="download"][data-part="1"]').filter({ hasText: '下载完整表格' }).waitFor();
      assert(! (await page.locator('#exportJobsList').textContent()).includes('请下载全部分包'));
      await page.screenshot({ path: path.join(output, `single-link-${width}.png`), fullPage: true });
      jobs[0] = { ...jobs[0], status: 'queued', files: [] };
      await page.locator('#refreshExportsBtn').click();
      await page.locator('[data-export-action="cancel"]').click();
      await page.locator('#exportJobsList').getByText('导出任务 #1 · 已取消', { exact: true }).waitFor();
      jobs[0] = { ...jobs[0], status: 'failed', error: '部分记录已删除或不可访问，请重新提交导出，避免遗漏。' };
      await page.locator('#refreshExportsBtn').click();
      await page.locator('#exportJobsList').getByText('导出任务 #1 · 生成失败', { exact: true }).waitFor();
      assert.match(await page.locator('#exportJobsList').textContent(), /避免遗漏/);
      await page.locator('#sideNav [data-module="records"]').click();
      const reset = page.waitForResponse((r) => r.url().includes('/api/records') && !r.url().includes('stove_no_flame_failure_device'));
      await page.locator('#resetFiltersBtn').click();
      await reset;
      assert.equal(await page.locator('#filtersForm input[type="checkbox"]:checked').count(), 0);
      assert(!queries.at(-1).hazardCodes && !queries.at(-1).gasSourceIssues);
      await page.locator('#exportBtn').click();
      await page.locator('#exportJobsList').getByText('带照片版', { exact: true }).waitFor();
      assert.equal(exports.at(-1).photoMode, undefined, 'Existing embedded mode remains the default');
      assert.deepEqual(errors, []);
      await context.close();
      console.log(`Desktop ${width}: filtering, background submit, reload persistence, progress, network errors, all downloads, permission errors, cancellation, failure, reset and layout passed.`);
    }
  } finally {
    if (browser) await browser.close();
    await new Promise((resolve) => server.close(resolve));
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });
