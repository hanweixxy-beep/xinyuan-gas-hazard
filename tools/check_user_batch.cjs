const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright');
const XLSX = require('../frontend/admin/vendor/xlsx-0.20.3.min.js');

const root = path.resolve(__dirname, '..');
const output = path.join(root, 'outputs', 'user-batch');
const labels = ['公司编码', '姓名', '手机号', '角色', '允许登录端', '查看本公司全部记录', '初始密码'];
const keys = ['companyCode', 'name', 'phone', 'role', 'loginScope', 'canViewCompanyRecords', 'password'];
const options = { version: 1, sheetName: '批量创建用户', maxRows: 200, fields: keys.map((key, i) => ({ key, label: labels[i] })),
  roles: ['检查员', '公司管理员', '总部管理员'], loginScopes: ['APP+后台', '仅APP', '仅后台'], companies: [{ id: 1, code: 'TEST', name: '测试公司' }] };
const values = ['TEST', '测试检查员', '01990000001', '', '', '', 'Fixture-Secret-123'];
function workbook(rows = [labels, values], edit = () => {}) {
  const book = XLSX.utils.book_new();
  const sheet = XLSX.utils.aoa_to_sheet(rows);
  edit(sheet);
  XLSX.utils.book_append_sheet(book, sheet, options.sheetName);
  return XLSX.write(book, { type: 'buffer', bookType: 'xlsx', compression: true });
}
function file(buffer, name = 'users.xlsx') { return { name, mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from(buffer) }; }

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const server = http.createServer((req, res) => {
    const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    const filename = path.resolve(root, `.${pathname.endsWith('/') ? `${pathname}index.html` : pathname}`);
    if (!filename.startsWith(`${root}${path.sep}`) || !fs.existsSync(filename)) return res.writeHead(404).end();
    res.setHeader('Content-Type', ({ '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.png': 'image/png', '.jpg': 'image/jpeg' })[path.extname(filename)] || 'application/octet-stream');
    fs.createReadStream(filename).pipe(res);
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  let browser;
  try {
    browser = await chromium.launch({ headless: true, channel: 'chrome' });
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
    await context.addInitScript(() => localStorage.setItem('xinyuan_admin_token', 'local-batch-test'));
    const page = await context.newPage();
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    const fixture = { previews: 0, commits: 0, rejectCommit: false, users: [{ id: 1, company_id: 1, company_code: 'TEST', company_name: '测试公司',
      name: '=1+1', phone: '01990000000', role: 'hq_admin', login_scope: 'both', status: 'active', password_hash: 'MUST-NOT-EXPORT', password: 'MUST-NOT-EXPORT' }] };
    await page.route('**/api/**', async (route) => {
      const endpoint = new URL(route.request().url()).pathname.replace('/api', '');
      let data;
      if (endpoint === '/auth/me') data = { user: { id: 1, companyId: 1, name: '测试管理员', companyName: '测试公司', role: 'hq_admin', loginScope: 'both' } };
      else if (endpoint === '/records') data = { records: [], pagination: { total: 0, page: 1, pageSize: 50, pageCount: 1 },
        summary: { total: 0, pending: 0, done: 0, trend: [], pendingRecords: [] } };
      else if (endpoint === '/admin/companies') data = { companies: [{ id: 1, name: '测试公司', code: 'TEST', status: 'active' }] };
      else if (endpoint === '/admin/organization') data = { regions: [], stores: [] };
      else if (endpoint === '/admin/users') data = { users: fixture.users };
      else if (endpoint === '/admin/users/import-options') data = options;
      else if (endpoint === '/admin/users/import-preview') {
        fixture.previews++;
        const { rows } = route.request().postDataJSON();
        fixture.lastRows = rows;
        data = { total: rows.length, validCount: rows.length, errorCount: 0, rows: rows.map(({ password, ...row }) => ({ ...row,
          companyName: '测试公司', role: row.role || '检查员', loginScope: row.loginScope || 'APP+后台', canViewCompanyRecords: '是',
          errors: rows.filter((other) => other.phone === row.phone).length > 1 ? ['手机号与其他行重复'] : [] })) };
        data.errorCount = data.rows.filter((row) => row.errors.length).length;
        data.validCount -= data.errorCount;
      } else if (endpoint === '/admin/users/import') {
        fixture.commits++;
        const { rows } = route.request().postDataJSON();
        if (fixture.rejectCommit) {
          fixture.rejectCommit = false;
          await route.fulfill({ status: 409, json: { ok: false, message: '第 2 行手机号已存在，本批次未创建任何账号，请重新校验。' } });
          return;
        }
        fixture.users.push(...rows.map((row, i) => ({ id: 10 + i, company_id: 1, company_code: row.companyCode, company_name: '测试公司',
          name: row.name, phone: row.phone, role: 'inspector', login_scope: 'both', status: 'active' })));
        data = { createdCount: rows.length };
      } else if (/^\/admin\/users\/\d+\/access$/.test(endpoint)) {
        fixture.lastAccess = route.request().postDataJSON();
        data = {};
      } else throw new Error(`Unexpected API: ${endpoint}`);
      await route.fulfill({ json: { ok: true, data } });
    });
    // A live URL only tests served assets; every API request remains intercepted above.
    await page.goto(process.env.USER_BATCH_ADMIN_URL || `http://127.0.0.1:${server.address().port}/frontend/admin/`);
    await page.locator('[data-module="admin"]').click();
    await page.locator('#userRows tr').waitFor();
    assert(await page.locator('#userCompanyDataField input').isChecked());
    assert(await page.locator('#userCompanyDataField input').isDisabled());

    const templateDownload = page.waitForEvent('download');
    await page.locator('#userTemplateBtn').click();
    const templatePath = path.join(output, 'template.xlsx');
    await (await templateDownload).saveAs(templatePath);
    const template = XLSX.read(fs.readFileSync(templatePath), { type: 'buffer', cellNF: true });
    assert.deepEqual(template.SheetNames, ['批量创建用户', '填写说明', '公司编码']);
    assert.deepEqual(XLSX.utils.sheet_to_json(template.Sheets[options.sheetName], { header: 1 })[0], labels);
    assert.equal(template.Sheets[options.sheetName].C2.z, '@');
    assert.equal(template.Sheets[options.sheetName].G201.v, '');
    assert.equal(template.Sheets['公司编码'].A2.v, 'TEST');

    const exportDownload = page.waitForEvent('download');
    await page.locator('#exportUsersBtn').click();
    const exportPath = path.join(output, 'users-export.xlsx');
    await (await exportDownload).saveAs(exportPath);
    const exported = XLSX.read(fs.readFileSync(exportPath), { type: 'buffer' }).Sheets['用户信息'];
    assert.equal(exported.D2.v, '=1+1');
    assert.equal(exported.D2.t, 's');
    assert.equal(exported.D2.f, undefined);
    assert.equal(exported.E2.v, '01990000000');
    assert(!JSON.stringify(exported).includes('MUST-NOT-EXPORT'));
    assert.equal(XLSX.utils.decode_range(exported['!ref']).e.c, 10);

    await page.locator('#openUserImportBtn').click();
    await page.waitForFunction(() => !document.querySelector('#userImportFile').disabled);
    async function validate(buffer, name) {
      await page.locator('#userImportFile').setInputFiles(file(buffer, name));
      await page.locator('#previewUserImportBtn').click();
      await page.waitForFunction(() => !document.querySelector('#previewUserImportBtn').disabled);
    }
    await validate(fs.readFileSync(templatePath));
    assert.match(await page.locator('#userImportError').textContent(), /没有填写用户/);
    const badFiles = [
      [workbook([['错误表头', ...labels.slice(1)], values]), /表头/],
      [workbook(undefined, (sheet) => { sheet.B2 = { t: 'n', v: 2, f: '1+1' }; }), /公式/],
      [workbook(undefined, (sheet) => { sheet.C2 = { t: 'n', v: 1234567890123456 }; }), /数字可能失真/],
      [workbook(undefined, (sheet) => { sheet['!merges'] = [{ s: { r: 1, c: 0 }, e: { r: 1, c: 1 } }]; }), /合并/],
      [workbook([labels, ...Array.from({ length: 201 }, () => values)]), /最多200/],
      [workbook([labels, [...values, '额外内容']]), /列数/],
      [Buffer.from('invalid'), /工作表|文件|格式/],
      [Buffer.alloc(2 * 1024 * 1024 + 1), /2 MB/],
    ];
    for (const [buffer, expected] of badFiles) {
      await validate(buffer);
      assert.match(await page.locator('#userImportError').textContent(), expected);
      assert(await page.locator('#commitUserImportBtn').isDisabled());
    }
    await validate(workbook(), 'wrong.csv');
    assert.match(await page.locator('#userImportError').textContent(), /xlsx/);
    assert.equal(fixture.previews, 0);

    await validate(workbook([labels, values, values]));
    assert.equal(fixture.previews, 1);
    assert.match(await page.locator('#userImportSummary').textContent(), /有错误 2 人/);
    assert(await page.locator('#commitUserImportBtn').isDisabled());
    assert.equal(await page.locator('.user-import-invalid').count(), 2);
    await page.locator('#userImportModal .modal-panel').screenshot({ path: path.join(output, 'import-errors.png') });
    const validRows = [values, ['TEST', '跨店检查员', '01990000002', '检查员', 'APP+后台', '是', 'Fixture-Secret-456']];
    await validate(workbook([labels, ...validRows]));
    assert.equal(fixture.lastRows[0].phone, '01990000001');
    assert.equal(fixture.commits, 0);
    assert(!(await page.locator('#userImportModal').textContent()).includes('Fixture-Secret'));
    assert.match(await page.locator('#commitUserImportBtn').textContent(), /创建 2 个账号/);
    assert(!(await page.evaluate(() => JSON.stringify(localStorage))).includes('Fixture-Secret'));
    await page.locator('#userImportModal .modal-panel').screenshot({ path: path.join(output, 'import-preview-1440.png') });
    await page.setViewportSize({ width: 1080, height: 800 });
    const panel = await page.locator('#userImportModal .modal-panel').boundingBox();
    assert(panel.x >= 0 && panel.x + panel.width <= 1080 && panel.y >= 0 && panel.y + panel.height <= 800);
    await page.locator('#userImportModal .modal-panel').screenshot({ path: path.join(output, 'import-preview-1080.png') });
    await page.locator('#commitUserImportBtn').evaluate((button) => { button.click(); button.click(); });
    await page.locator('#userImportModal').waitFor({ state: 'hidden' });
    assert.equal(fixture.commits, 1);
    await page.waitForFunction(() => document.querySelectorAll('#userRows tr').length === 3);
    assert.equal(await page.locator('#userImportFile').inputValue(), '');
    assert.equal(await page.locator('#userImportRows tr').count(), 0);
    assert.equal(await page.locator('#userRows tr').nth(1).locator('td').nth(5).textContent(), '本公司');
    await page.locator('[data-action="edit-access"][data-id="10"]').click();
    assert(await page.locator('#accessCompanyDataField input').isChecked());
    assert(await page.locator('#accessCompanyDataField input').isDisabled());
    assert.match(await page.locator('#accessCompanyDataField').textContent(), /协助整改/);
    await page.locator('#accessModal .modal-panel').screenshot({ path: path.join(output, 'company-rectification-access.png') });
    await page.locator('#accessForm button[type="submit"]').click();
    await page.locator('#accessModal').waitFor({ state: 'hidden' });
    assert.equal(fixture.lastAccess.canViewCompanyRecords, true);
    const sharedExportDownload = page.waitForEvent('download');
    await page.locator('#exportUsersBtn').click();
    const sharedExportPath = path.join(output, 'users-company-scope.xlsx');
    await (await sharedExportDownload).saveAs(sharedExportPath);
    const sharedExport = XLSX.read(fs.readFileSync(sharedExportPath), { type: 'buffer' }).Sheets['用户信息'];
    assert.equal(sharedExport.H3.v, '本公司全部记录');

    await page.locator('#openUserImportBtn').click();
    await page.waitForFunction(() => !document.querySelector('#userImportFile').disabled);
    await validate(workbook());
    fixture.rejectCommit = true;
    await page.locator('#commitUserImportBtn').click();
    await page.locator('#userImportError').filter({ hasText: '手机号已存在' }).waitFor();
    assert(await page.locator('#commitUserImportBtn').isDisabled());
    assert.equal(await page.locator('#userImportRows tr').count(), 0);
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('#userImportFile').inputValue(), '');
    await page.locator('#openUserImportBtn').click();
    await page.waitForFunction(() => !document.querySelector('#userImportFile').disabled);
    assert(await page.locator('#commitUserImportBtn').isDisabled());
    await page.locator('#cancelUserImportBtn').click();
    assert.deepEqual(errors, []);
    console.log('User batch UI: company collaboration display/access/export, template safety, actual worker parsing, validation, preview, double-submit, errors, cleanup and 1080/1440px layout passed.');
    await context.close();
  } finally {
    if (browser) await browser.close();
    await new Promise((resolve) => server.close(resolve));
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });
