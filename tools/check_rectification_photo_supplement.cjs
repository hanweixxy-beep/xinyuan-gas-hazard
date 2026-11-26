const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright');

const origin = process.env.RECTIFICATION_QA_ORIGIN;
assert(/^http:\/\/127\.0\.0\.1:\d+$/.test(origin), 'An isolated local API is required');
const id = Number(process.env.RECTIFICATION_QA_RECORD);
assert(id > 0 && process.env.RECTIFICATION_QA_TOKEN);
const headers = { Authorization: 'Bearer ' + process.env.RECTIFICATION_QA_TOKEN };
const root = path.resolve(__dirname, '..');
const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+X3K4AAAAASUVORK5CYII=', 'base64');
const fixture = { name: 'supplement-qa.png', mimeType: 'image/png', buffer: png };
async function record() {
  const response = await fetch(`${origin}/api/records/${id}`, { headers });
  assert.equal(response.status, 200);
  return (await response.json()).data.record;
}

(async () => {
  const server = http.createServer(async (req, res) => {
    try {
      const pathname = new URL(req.url, 'http://localhost').pathname;
      if (pathname.startsWith('/api/') || pathname.startsWith('/uploads/')) {
        const chunks = [];
        for await (const chunk of req) chunks.push(chunk);
        const response = await fetch(origin + req.url, {
          method: req.method, headers: { ...(req.headers.authorization ? { Authorization: req.headers.authorization } : {}),
            ...(req.headers['content-type'] ? { 'Content-Type': req.headers['content-type'] } : {}) },
          body: ['GET', 'HEAD'].includes(req.method) ? undefined : Buffer.concat(chunks)
        });
        res.writeHead(response.status, { 'Content-Type': response.headers.get('content-type') || 'application/octet-stream' });
        return res.end(Buffer.from(await response.arrayBuffer()));
      }
      const file = path.resolve(root, '.' + (pathname.endsWith('/') ? pathname + 'index.html' : pathname));
      if (!['frontend/mobile/', 'shared/'].some(prefix => file.startsWith(path.join(root, prefix)))) return res.writeHead(404).end();
      if (!fs.existsSync(file)) return res.writeHead(404).end();
      res.setHeader('Content-Type', ({ '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css' })[path.extname(file)] || 'application/octet-stream');
      fs.createReadStream(file).pipe(res);
    } catch (error) { res.writeHead(500).end(error.message); }
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  let browser;
  try {
    browser = await chromium.launch({ channel: 'chrome', headless: true });
    for (const width of [375, 430]) {
      const context = await browser.newContext({ viewport: { width, height: 850 } });
      await context.addInitScript(token => localStorage.setItem('xinyuan_h5_token', token), process.env.RECTIFICATION_QA_TOKEN);
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(`http://127.0.0.1:${server.address().port}/frontend/mobile/`);
      await page.locator('[data-nav="records"]').click();
      await page.locator(`[data-record-id="${id}"]`).click();
      const form = page.locator('#mobilePhotoSupplementForm');
      await form.waitFor({ state: 'visible' });
      const before = await record();
      await form.locator('[name="file"]').setInputFiles(fixture);
      await form.locator('[name="reason"]').fill('隔离浏览器追加照片');
      await page.locator('#supplementPhotoPreview').waitFor({ state: 'visible' });
      await form.getByRole('button', { name: '保存补录' }).click();
      await page.locator('#toast').getByText('整改照片已补录', { exact: true }).waitFor();
      await form.locator('[name="reason"]').waitFor({ state: 'visible' });
      const appended = await record();
      assert.equal(appended.photos.length, before.photos.length + 1);

      const replacedId = appended.photos.at(-1).id;
      await form.locator('[name="replacePhotoId"]').selectOption(String(replacedId));
      await form.locator('[name="file"]').setInputFiles(fixture);
      await form.locator('[name="reason"]').fill('隔离浏览器替换错图');
      // Save on the server, then simulate losing the reply. Retrying must be idempotent.
      await page.route('**/api/records/*/rectification-photo', async route => {
        const response = await route.fetch();
        assert.equal(response.status(), 200);
        await route.fulfill({ status: 503, json: { ok: false, message: '隔离测试：回复丢失，请重试' } });
      }, { times: 1 });
      await form.getByRole('button', { name: '保存替换' }).click();
      await page.locator('#toast').getByText('隔离测试：回复丢失，请重试').waitFor();
      const committed = await record();
      assert.equal(committed.photos.length, appended.photos.length);
      await form.getByRole('button', { name: '保存替换' }).click();
      await page.locator('#toast').getByText('整改照片已补录', { exact: true }).waitFor();
      const after = await record();
      assert.deepEqual(after.photos, committed.photos);
      assert(!after.photos.some(photo => Number(photo.id) === Number(replacedId)));
      for (const key of ['status', 'rectification_date', 'rectification_person', 'rectification_note', 'creator_id']) assert.equal(after[key], before[key]);

      await form.locator('[name="file"]').setInputFiles({ name: 'wrong.png', mimeType: 'image/png', buffer: Buffer.from('not an image') });
      await form.locator('[name="reason"]').fill('隔离无效照片测试');
      await form.getByRole('button', { name: '保存补录' }).click();
      await page.locator('#toast').getByText('只支持 JPG、PNG 或 WebP 照片。', { exact: true }).waitFor();
      assert.deepEqual((await record()).photos, after.photos);
      assert.equal(await form.locator('[name="reason"]').inputValue(), '隔离无效照片测试');
      if (process.env.SUPPLEMENT_QA_OUTPUT) {
        fs.mkdirSync(process.env.SUPPLEMENT_QA_OUTPUT, { recursive: true });
        await form.screenshot({ path: path.join(process.env.SUPPLEMENT_QA_OUTPUT, `h5-supplement-${width}.png`) });
      }
      await page.locator('#mobileRecordCloseBtn').click();
      await page.route(`**/api/records/${id}`, route => route.fulfill({ json: { ok: true, data: { record: { ...after, canSupplementRectificationPhoto: false } } } }));
      await page.locator(`[data-record-id="${id}"]`).click();
      await page.locator('#mobileRecordDetailSheet').waitFor({ state: 'visible' });
      assert.equal(await form.isVisible(), false);
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth));
      assert.deepEqual(errors, []);
      await context.close();
      console.log(`H5 ${width}px: real API append, replacement, lost reply retry, invalid upload, closure preservation and old-server compatibility passed.`);
    }
  } finally {
    if (browser) await browser.close();
    await new Promise(resolve => server.close(resolve));
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
