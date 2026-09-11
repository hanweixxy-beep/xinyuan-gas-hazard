const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { chromium } = require('playwright');

const root = path.resolve(__dirname, '..');
const output = path.join(root, 'outputs', 'organization-qa');
const regions = Array.from({ length: 5 }, (_, i) => ({ id: i + 1, name: `测试片区${i + 1}` }));
const stores = regions.flatMap((region) => Array.from({ length: 5 }, (_, i) => ({
  id: region.id * 10 + i + 1, regionId: region.id, name: `测试门店${region.id}-${i + 1}`,
})));

async function mockApi(page, fixture) {
  await page.route('**/api/**', async (route) => {
    const endpoint = new URL(route.request().url()).pathname.replace('/api', '');
    const method = route.request().method();
    const user = { id: fixture.userId, companyId: 1, name: '界面测试员', companyName: '测试公司',
      role: fixture.admin ? 'hq_admin' : 'inspector', loginScope: 'both' };
    let data;
    if (endpoint === '/auth/me') data = { user };
    else if (endpoint === '/records') data = { records: [] };
    else if (endpoint === '/organization-options') data = {
      companyId: 1, regions, stores: stores.filter((store) => store.id !== fixture.disabledStore),
      default: { regionId: 5, storeId: 55, regionName: '旧账号片区', storeName: '旧账号门店' },
    };
    else if (endpoint === '/admin/companies') data = { companies: [{ id: 1, name: '测试公司', code: 'TEST', status: 'active' }] };
    else if (endpoint === '/admin/organization') data = {
      regions: regions.map((region) => ({ ...region, ...fixture.editedRegions?.[region.id], company_id: 1, company_name: '测试公司', status: 'active' })),
      stores: stores.map((store) => ({ ...store, region_id: store.regionId, company_id: 1, status: 'active', ...fixture.editedStores?.[store.id] })),
    };
    else if (/^\/admin\/(regions|stores)\/\d+$/.test(endpoint) && method === 'POST') {
      if (fixture.rejectNextEdit) {
        fixture.rejectNextEdit = false;
        await route.fulfill({ status: 422, json: { ok: false, message: '该公司已存在同名片区。' } });
        return;
      }
      const payload = route.request().postDataJSON();
      const id = Number(endpoint.split('/').pop());
      assert(!('companyId' in payload));
      fixture.editCalls = (fixture.editCalls || 0) + 1;
      if (endpoint.includes('/regions/')) {
        fixture.editedRegions = { ...fixture.editedRegions, [id]: payload };
      } else {
        fixture.editedStores = { ...fixture.editedStores, [id]: { ...payload, region_id: payload.regionId } };
      }
      data = { id };
    }
    else if (endpoint === '/admin/users' && method === 'POST') {
      const payload = route.request().postDataJSON();
      assert(!('regionId' in payload) && !('storeId' in payload));
      fixture.createdAccount = payload;
      data = { id: 20 };
    } else if (endpoint === '/admin/users') data = { users: [
      { id: 20, company_id: 1, company_name: '测试公司', name: '测试检查员', phone: '19900000001',
        role: 'inspector', login_scope: 'both', status: 'active' },
    ] };
    else if (endpoint === '/admin/users/20/access') {
      const payload = route.request().postDataJSON();
      assert(!('regionId' in payload) && !('storeId' in payload));
      fixture.updatedAccess = payload;
      data = { id: 20 };
    } else throw new Error(`Unexpected API call: ${method} ${endpoint}`);
    await route.fulfill({ json: { ok: true, data } });
  });
}

async function mobileCheck(browser, origin, width) {
  const fixture = { userId: 10 };
  const context = await browser.newContext({ viewport: { width, height: 900 } });
  await context.addInitScript(() => localStorage.setItem('xinyuan_h5_token', 'local-ui-test'));
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await mockApi(page, fixture);
  await page.goto(`${origin}/frontend/mobile/`);
  await page.locator('[data-nav="form"]').waitFor({ state: 'visible' });
  await page.locator('[data-nav="form"]').click();
  await page.locator('#organizationChangeBtn').click();
  await page.locator('#organizationSheet:not(.hidden)').waitFor();
  assert.equal(await page.locator('#inspectionRegionSelect').inputValue(), '');
  assert.equal(await page.locator('#inspectionRegionSelect option').count(), 6);
  await page.selectOption('#inspectionRegionSelect', '2');
  assert.equal(await page.locator('#inspectionStoreSelect option').count(), 6);
  await page.locator('#organizationApplyBtn').click();
  assert.equal(await page.locator('#organizationSheet').getAttribute('aria-hidden'), 'false');
  assert.match(await page.locator('#toast').textContent(), /请选择.*门店/);
  await page.selectOption('#inspectionStoreSelect', '23');
  await page.locator('#organizationApplyBtn').click();
  assert.match(await page.locator('#inspectionOrganizationSummary').textContent(), /测试片区2.*测试门店2-3/);

  await page.locator('#clearFormBtn').click();
  await page.locator('[data-nav="form"]').click();
  assert.match(await page.locator('#inspectionOrganizationHint').textContent(), /沿用上次选择/);
  await page.reload();
  await page.locator('[data-nav="form"]').click();
  await page.locator('#organizationChangeBtn').click();
  await page.locator('#organizationSheet:not(.hidden)').waitFor();
  assert.equal(await page.locator('#inspectionStoreSelect').inputValue(), '23');
  await page.selectOption('#inspectionRegionSelect', '4');
  assert.equal(await page.locator('#inspectionStoreSelect').inputValue(), '');
  await page.selectOption('#inspectionStoreSelect', '41');
  await page.screenshot({ path: path.join(output, `h5-selection-${width}.png`) });
  await page.locator('#organizationApplyBtn').click();
  assert.match(await page.locator('#inspectionOrganizationSummary').textContent(), /测试片区4.*测试门店4-1/);

  fixture.disabledStore = 41;
  await page.locator('#organizationChangeBtn').click();
  await page.locator('#organizationSheet:not(.hidden)').waitFor();
  assert.equal(await page.locator('#inspectionStoreSelect').inputValue(), '');
  await page.locator('#organizationCloseBtn').click();
  await page.locator('#inspectionForm').dispatchEvent('submit');
  await page.locator('#organizationSheet:not(.hidden)').waitFor();
  assert.match(await page.locator('#toast').textContent(), /请选择.*片区和门店/);
  await page.locator('#organizationCloseBtn').click();

  fixture.userId = 11;
  await page.reload();
  await page.locator('[data-nav="form"]').click();
  await page.locator('#organizationChangeBtn').click();
  await page.locator('#organizationSheet:not(.hidden)').waitFor();
  assert.equal(await page.locator('#inspectionRegionSelect').inputValue(), '');
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
  assert.deepEqual(errors, []);
  await context.close();
  return `H5 ${width}px: mandatory selection, cascade, remembered choice, disabled store, account isolation passed`;
}

async function adminCheck(browser, origin) {
  const fixture = { userId: 1, admin: true };
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  await context.addInitScript(() => localStorage.setItem('xinyuan_admin_token', 'local-admin-test'));
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await mockApi(page, fixture);
  await page.goto(`${origin}/frontend/admin/`);
  await page.locator('[data-module="admin"]').click();
  await page.locator('#userRows tr').waitFor();
  assert.equal(await page.locator('#userForm [name="regionId"], #userForm [name="storeId"]').count(), 0);
  await page.locator('#userForm').evaluate((form) => form.closest('details').open = true);
  await page.locator('#userForm [name="name"]').fill('新检查员');
  await page.locator('#userForm [name="phone"]').fill('19900000002');
  await page.locator('#userForm [name="password"]').fill('Temporary-123');
  await page.locator('#userForm button[type="submit"]').click();
  await page.waitForFunction(() => document.querySelector('#toast').textContent.includes('用户已新增'));
  assert.equal(fixture.createdAccount.name, '新检查员');
  await page.locator('[data-action="edit-access"]').click();
  assert.equal(await page.locator('#accessForm [name="regionId"], #accessForm [name="storeId"]').count(), 0);
  await page.locator('#accessForm button[type="submit"]').click();
  await page.waitForFunction(() => document.querySelector('#toast').textContent.includes('账号权限已更新'));
  assert.equal(fixture.updatedAccess.loginScope, 'both');
  await page.locator('#userForm').screenshot({ path: path.join(output, 'admin-account-form.png') });
  const regionEdit = page.locator('[data-action="region-edit"][data-id="1"]');
  await regionEdit.click();
  assert.equal(await page.locator('#organizationEditForm [name="name"]').inputValue(), '测试片区1');
  await page.locator('#organizationEditForm [name="name"]').fill('取消的修改');
  await page.locator('#cancelOrganizationEditBtn').click();
  assert.equal(fixture.editCalls || 0, 0);
  await regionEdit.click();
  assert.equal(await page.locator('#organizationEditForm [name="name"]').inputValue(), '测试片区1');
  await page.locator('#organizationEditForm [name="name"]').fill('修正后的片区');
  fixture.rejectNextEdit = true;
  await page.locator('#organizationEditForm button[type="submit"]').click();
  await page.locator('#organizationEditError').filter({ hasText: '同名片区' }).waitFor();
  assert.equal(await page.locator('#organizationEditModal').getAttribute('aria-hidden'), 'false');
  assert.equal(await page.locator('#organizationEditForm [name="name"]').inputValue(), '修正后的片区');
  await page.locator('#organizationEditForm button[type="submit"]').click();
  await page.locator('#organizationEditModal').waitFor({ state: 'hidden' });
  assert.match(await regionEdit.locator('xpath=ancestor::tr').textContent(), /修正后的片区/);
  assert.equal(await page.locator('#filterRegionSelect option[value="1"]').textContent(), '修正后的片区');
  await page.locator('[data-action="store-edit"][data-id="11"]').click();
  await page.locator('#organizationEditForm [name="regionId"]').selectOption('2');
  await page.locator('#organizationEditForm [name="name"]').fill('修正后的门店');
  await page.locator('#organizationEditForm [name="address"]').fill('修正后的门店地址');
  await page.locator('#organizationEditModal .modal-panel').screenshot({ path: path.join(output, 'admin-store-edit.png') });
  await page.locator('#organizationEditForm button[type="submit"]').click();
  await page.locator('#organizationEditModal').waitFor({ state: 'hidden' });
  assert.equal(fixture.editedStores[11].region_id, 2);
  const storeRow = page.locator('[data-action="store-edit"][data-id="11"]').locator('xpath=ancestor::tr');
  assert.match(await storeRow.textContent(), /修正后的门店地址/);
  assert.equal(await storeRow.locator('[data-action="store-status"]').count(), 1);
  await regionEdit.click();
  await page.keyboard.press('Escape');
  assert.equal(await page.locator('#organizationEditModal').getAttribute('aria-hidden'), 'true');
  assert.deepEqual(errors, []);
  await context.close();
  return 'Admin: accounts, region/store editing, cancellation, error recovery and filter refresh passed';
}

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const server = http.createServer((req, res) => {
    const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    const filename = path.resolve(root, `.${pathname.endsWith('/') ? `${pathname}index.html` : pathname}`);
    if (!filename.startsWith(`${root}${path.sep}`) || !fs.existsSync(filename)) {
      res.writeHead(404).end();
      return;
    }
    res.setHeader('Content-Type', ({ '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css' })[path.extname(filename)] || 'application/octet-stream');
    fs.createReadStream(filename).pipe(res);
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  let browser;
  try {
    browser = await chromium.launch({ headless: true, channel: 'chrome' });
    const origin = `http://127.0.0.1:${server.address().port}`;
    for (const width of [375, 430]) console.log(await mobileCheck(browser, origin, width));
    console.log(await adminCheck(browser, origin));
  } finally {
    if (browser) await browser.close();
    await new Promise((resolve) => server.close(resolve));
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });
