const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '..');
const output = process.env.FIELD_QA_OUTPUT || path.join(root, 'work/field-photo-qa');

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
      const context = await browser.newContext({ viewport: { width, height: 900 }, geolocation: { latitude: 33.97, longitude: 116.8, accuracy: 5 }, permissions: ['geolocation'] });
      await context.addInitScript(() => {
        localStorage.setItem('xinyuan_h5_token', 'mock-field-only');
        window.watermarkTexts = [];
        const original = CanvasRenderingContext2D.prototype.fillText;
        CanvasRenderingContext2D.prototype.fillText = function (text, ...rest) { window.watermarkTexts.push(text); return original.call(this, text, ...rest); };
      });
      const page = await context.newPage();
      const errors = [];
      const drafts = [];
      const submissions = [];
      const rectifications = [];
      let imageBuffer;
      let uploads = 0;
      let geocodes = 0;
      let albumPreserved = false;
      page.on('pageerror', (error) => errors.push(error.message));
      const record = { id: 1, customer_name: '测试用户', customer_phone: '19900000000', customer_type: '餐饮用户',
        customer_address: '测试地址', inspector_name: '测试检查员', status: 'pending', check_result: '隐患未清零',
        inspection_date: '2026-10-20', hazards: [], gasSourceIssues: [{ issue_text: '虚假安检' }], photos: [] };
      await page.route('**/api/**', async (route) => {
        const endpoint = new URL(route.request().url()).pathname.replace('/api', '');
        let data;
        if (endpoint === '/auth/me') data = { user: { id: 1, name: '测试检查员', companyId: 1, companyName: '测试公司', role: 'inspector', loginScope: 'both' } };
        else if (endpoint === '/records') data = { records: [record] };
        else if (endpoint === '/records/1') data = { record };
        else if (endpoint === '/organization-options') data = { companyId: 1, regions: [{ id: 1, name: '测试片区' }], stores: [{ id: 1, regionId: 1, name: '测试门店' }] };
        else if (endpoint === '/location/reverse-geocode') { geocodes++; data = { address: '测试定位地址', provider: 'mock' }; }
        else if (endpoint === '/files/upload') {
          uploads++;
          const body = route.request().postDataBuffer();
          if (body.toString('latin1').includes('album.png')) albumPreserved = body.includes(imageBuffer);
          data = { filePath: `/uploads/test-${uploads}.jpg` };
        } else if (endpoint === '/records/drafts') { drafts.push(route.request().postDataJSON()); data = { id: 10 }; }
        else if (endpoint === '/records/drafts/submit') { submissions.push(route.request().postDataJSON()); data = { id: 10 }; }
        else if (endpoint === '/records/1/rectify') { rectifications.push(route.request().postDataJSON()); data = { id: 1 }; }
        else throw new Error(`Unexpected ${endpoint}`);
        await route.fulfill({ json: { ok: true, data } });
      });
      await page.goto(`http://127.0.0.1:${server.address().port}/frontend/mobile/`);
      imageBuffer = Buffer.from(await page.evaluate(() => {
        const canvas = document.createElement('canvas'); canvas.width = 1600; canvas.height = 1200;
        const ctx = canvas.getContext('2d'); ctx.fillStyle = '#e2e8ed'; ctx.fillRect(0, 0, 1600, 1200);
        return canvas.toDataURL('image/png').split(',')[1];
      }), 'base64');
      const photo = { name: 'camera.png', mimeType: 'image/png', buffer: imageBuffer };
      await page.locator('[data-nav="form"]').click();
      await page.locator('#organizationChangeBtn').click();
      await page.locator('#inspectionRegionSelect').selectOption('1');
      await page.locator('#inspectionStoreSelect').selectOption('1');
      await page.locator('#organizationApplyBtn').click();
      await page.locator('input[name="storefront"]').setInputFiles(photo);
      await page.locator('#toast').filter({ hasText: '配送员' }).waitFor();
      assert.equal(uploads, 0, 'No photo accepted without delivery person');
      await page.locator('[name="deliveryPerson"]').fill('配送测试');
      await page.evaluate(() => window.watermarkTexts = []);
      const draftResponse = page.waitForResponse((r) => r.url().endsWith('/records/drafts'));
      await page.locator('input[name="storefront"]').setInputFiles(photo);
      await draftResponse;
      assert.equal(drafts.at(-1).customerName, '');
      assert.equal(drafts.at(-1).customerPhone, '');
      assert.equal(drafts.at(-1).customerType, '');
      let texts = await page.evaluate(() => window.watermarkTexts);
      assert(texts.some((t) => t.includes('配送员：配送测试')));
      assert(!texts.some((t) => /^用户：|^电话：|^用户类型：/.test(t)), 'Unfilled user fields omitted from actual canvas drawing');
      const source = await page.locator('[data-photo-field="storefront"] img').getAttribute('src');
      const watermarked = await page.evaluate(async (src) => {
        const blob = await (await fetch(src)).blob();
        return new Promise((resolve) => { const reader = new FileReader(); reader.onload = () => resolve(reader.result.split(',')[1]); reader.readAsDataURL(blob); });
      }, source);
      fs.writeFileSync(path.join(output, `delivery-only-watermark-${width}.jpg`), Buffer.from(watermarked, 'base64'));
      await page.locator('[name="checkResult"][value="隐患未清零"]').locator('..').click();
      await page.locator('[name="hazardItems"][value="alarm_missing"]').locator('..').click();
      await page.locator('input[name="hazardPhoto_alarm_missing"]').setInputFiles(photo);
      await page.locator('[data-photo-field="hazardPhoto_alarm_missing"] img').waitFor();
      await page.locator('input[name="cylinder"]').setInputFiles(photo);
      await page.locator('[data-photo-field="cylinder"] img').waitFor();
      assert.equal(geocodes, 1, 'Other photos reuse storefront location');
      await page.locator('#inspectionForm').dispatchEvent('submit');
      await page.locator('#toast').filter({ hasText: '用户名' }).waitFor();
      await page.locator('[name="customerName"]').fill('测试用户');
      await page.locator('#inspectionForm').dispatchEvent('submit');
      await page.locator('#toast').filter({ hasText: '电话' }).waitFor();
      await page.locator('[name="customerPhone"]').fill('19900000000');
      await page.locator('#inspectionForm').dispatchEvent('submit');
      await page.locator('#toast').filter({ hasText: '用户类型' }).waitFor();
      assert.equal(submissions.length, 0, 'Partial user data cannot be submitted');
      await page.locator('#customerType').selectOption('餐饮用户');
      await page.evaluate(() => window.watermarkTexts = []);
      await page.locator('input[name="cylinder"]').setInputFiles(photo);
      await page.waitForFunction(() => window.watermarkTexts.some((t) => t.includes('用户类型：餐饮用户')));
      texts = await page.evaluate(() => window.watermarkTexts);
      assert(texts.some((t) => t.includes('用户：测试用户')) && texts.some((t) => t.includes('电话：19900000000')));
      await page.locator('[name="gasSourceIssues"][value="虚假安检"]').locator('..').click();
      await page.screenshot({ path: path.join(output, `mobile-field-${width}.png`), fullPage: true });
      const submitted = page.waitForResponse((r) => r.url().endsWith('/records/drafts/submit'));
      await page.locator('#inspectionForm').dispatchEvent('submit');
      await submitted;
      assert.equal(submissions.length, 1);
      assert.deepEqual(submissions[0].gasSourceIssues, ['虚假安检']);
      await page.locator('[data-record-id="1"]').click();
      await page.locator('#mobileRectifyForm').waitFor({ state: 'visible' });
      assert.equal(await page.locator('[name="rectificationGallery"]').getAttribute('capture'), null);
      assert.equal(await page.locator('[name="rectificationPhotos"]').getAttribute('capture'), 'environment');
      await page.locator('[name="rectificationPhotos"]').setInputFiles(photo);
      await page.locator('[name="rectificationGallery"]').setInputFiles({ ...photo, name: 'album.png' });
      await page.locator('#mobileRectifyForm [name="rectificationNote"]').fill('测试整改');
      const rectified = page.waitForResponse((r) => r.url().endsWith('/records/1/rectify'));
      await page.locator('#mobileRectifyForm button[type="submit"]').click();
      await rectified;
      assert.equal(rectifications[0].photos.length, 2, 'Camera and album attachments both submitted');
      assert(albumPreserved, 'Album original bytes preserved without false capture watermark');
      assert.deepEqual(errors, []);
      await context.close();
      console.log(`H5 ${width}: delivery-only capture, conditional watermark, partial draft, strict final validation, fake-inspection issue and camera+album rectification passed.`);
    }
  } finally {
    if (browser) await browser.close();
    await new Promise((resolve) => server.close(resolve));
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });
