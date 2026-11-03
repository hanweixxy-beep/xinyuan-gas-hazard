import { downloadUserTemplate, downloadUsers, readUserFile } from './userWorkbook.js?v=20260915';
import { hazardItems, gasSourceIssues } from '../../shared/catalog.js?v=20261020-field-update';
import { configureExportJobs, createExportJob, refreshExportJobs, setExportVisible } from './exportJobs.js?v=20261026-single-link';

const apiBase = localStorage.getItem('apiBase') || '/api';
const tokenKey = 'xinyuan_admin_token';
const state = {
  token: localStorage.getItem(tokenKey) || '',
  user: null,
  records: [],
  recordTotal: 0,
  recordRequest: 0,
  appliedFilters: null,
  currentRecord: null,
  companies: [],
  regions: [],
  stores: [],
  users: [],
  organizationFiltersLoaded: false,
  accessUserId: 0,
  organizationEdit: null,
  organizationSaving: false,
  userImport: null,
  currentModule: 'home',
  recordPage: 1,
  recordPageSize: 50
};

const $ = (selector) => document.querySelector(selector);
let toastTimer = null;

function setText(selector, value) {
  const element = $(selector);
  if (element) element.textContent = value;
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function showToast(message) {
  const toast = $('#toast');
  if (toastTimer) window.clearTimeout(toastTimer);
  toast.textContent = message;
  toast.classList.remove('hidden');
  toastTimer = window.setTimeout(() => toast.classList.add('hidden'), 2800);
}

function setButtonBusy(button, busy, busyText = '') {
  if (!button) return;
  if (busy) {
    if (!button.dataset.defaultText) button.dataset.defaultText = button.textContent;
    button.textContent = busyText || button.dataset.defaultText;
    button.disabled = true;
    button.classList.add('is-loading');
    return;
  }

  button.disabled = false;
  button.classList.remove('is-loading');
  if (button.dataset.defaultText) {
    button.textContent = button.dataset.defaultText;
    delete button.dataset.defaultText;
  }
}

async function runWithButton(button, busyText, task) {
  setButtonBusy(button, true, busyText);
  try {
    return await task();
  } finally {
    setButtonBusy(button, false);
  }
}

async function exportUsers(button) {
  const token = state.token;
  await runWithButton(button, '正在导出...', async () => {
    const data = await api('/admin/users');
    if (state.token !== token) return;
    await downloadUsers(data.users || []);
    showToast('用户信息已导出，不包含密码。');
  });
}

async function downloadUserTemplateFile(button) {
  const token = state.token;
  await runWithButton(button, '正在生成...', async () => {
    const options = await api('/admin/users/import-options');
    if (state.token !== token) return;
    await downloadUserTemplate(options);
  });
}

function currentUserImport(batch) {
  return state.userImport === batch && state.token === batch.token;
}

function updateUserImportControls() {
  const batch = state.userImport;
  if (!batch) return;
  $('#userImportFile').disabled = batch.busy || !batch.options;
  $('#previewUserImportBtn').disabled = batch.busy || !batch.options || !$('#userImportFile').files.length;
  $('#previewUserImportBtn').textContent = batch.busy && !batch.committing ? '正在校验...' : '校验文件';
  $('#commitUserImportBtn').disabled = batch.busy || !batch.preview || batch.preview.errorCount > 0;
  $('#commitUserImportBtn').textContent = batch.committing ? '正在创建...' : batch.preview?.errorCount === 0 ? `确认创建 ${batch.preview.total} 个账号` : '确认创建';
  $('#closeUserImportBtn').disabled = batch.committing;
  $('#cancelUserImportBtn').disabled = batch.committing;
}

function clearUserImportRows(batch) {
  batch.rows.forEach((row) => { row.password = ''; });
  batch.rows = [];
  batch.preview = null;
  $('#userImportRows').replaceChildren();
  $('#userImportPreview').classList.add('hidden');
}

async function openUserImport() {
  if (state.userImport) return;
  const batch = { token: state.token, options: null, rows: [], preview: null, busy: true, committing: false, abort: new AbortController() };
  state.userImport = batch;
  $('#userImportFile').value = '';
  $('#userImportError').textContent = '';
  $('#userImportSummary').textContent = '正在加载公司和权限...';
  $('#userImportModal').classList.remove('hidden');
  $('#userImportModal').setAttribute('aria-hidden', 'false');
  updateUserImportControls();
  $('#closeUserImportBtn').focus();
  try {
    const options = await api('/admin/users/import-options', { signal: batch.abort.signal });
    if (!currentUserImport(batch)) return;
    batch.options = options;
    $('#userImportSummary').textContent = '尚未校验文件';
  } catch (error) {
    if (currentUserImport(batch)) $('#userImportError').textContent = error.message;
  } finally {
    if (currentUserImport(batch)) { batch.busy = false; updateUserImportControls(); }
  }
}

function closeUserImport(force = false) {
  const batch = state.userImport;
  if (batch?.committing && !force) return;
  if (batch) {
    batch.abort.abort();
    clearUserImportRows(batch);
  }
  state.userImport = null;
  $('#userImportFile').value = '';
  $('#userImportModal').classList.add('hidden');
  $('#userImportModal').setAttribute('aria-hidden', 'true');
  if (batch && !force) $('#openUserImportBtn').focus();
}

async function previewUserImport() {
  const batch = state.userImport;
  const file = $('#userImportFile').files[0];
  if (!batch?.options || batch.busy || !file) return;
  clearUserImportRows(batch);
  batch.busy = true;
  $('#userImportError').textContent = '';
  $('#userImportSummary').textContent = '正在读取和校验文件...';
  updateUserImportControls();
  try {
    const rows = await readUserFile(file, batch.options, batch.abort.signal);
    if (!currentUserImport(batch)) return;
    batch.rows = rows;
    const preview = await api('/admin/users/import-preview', { method: 'POST', body: JSON.stringify({ rows }), signal: batch.abort.signal });
    if (!currentUserImport(batch)) return;
    batch.preview = preview;
    $('#userImportSummary').textContent = `共 ${preview.total} 人，校验通过 ${preview.validCount} 人，有错误 ${preview.errorCount} 人。`;
    $('#userImportRows').innerHTML = preview.rows.map((row) => `<tr class="${row.errors.length ? 'user-import-invalid' : ''}">
      <td>${row.rowNumber}</td><td>${escapeHtml(row.companyName || row.companyCode)}</td><td>${escapeHtml(row.name)}</td><td>${escapeHtml(row.phone)}</td>
      <td>${escapeHtml(row.role)}</td><td>${escapeHtml(row.loginScope)}</td><td>${escapeHtml(row.canViewCompanyRecords)}</td>
      <td class="user-import-result">${row.errors.length ? row.errors.map(escapeHtml).join('<br>') : '通过'}</td></tr>`).join('');
    $('#userImportPreview').classList.remove('hidden');
    if (preview.errorCount) $('#userImportError').textContent = '请修正表格中的错误行后重新校验，目前未创建任何账号。';
  } catch (error) {
    if (currentUserImport(batch)) {
      clearUserImportRows(batch);
      $('#userImportError').textContent = error.message;
      $('#userImportSummary').textContent = '校验未完成，未创建任何账号。';
    }
  } finally {
    if (currentUserImport(batch)) { batch.busy = false; updateUserImportControls(); }
  }
}

async function commitUserImport() {
  const batch = state.userImport;
  if (!batch?.preview || batch.busy || batch.preview.errorCount) return;
  batch.busy = true;
  batch.committing = true;
  $('#userImportError').textContent = '';
  updateUserImportControls();
  try {
    const result = await api('/admin/users/import', { method: 'POST', body: JSON.stringify({ rows: batch.rows }) });
    if (!currentUserImport(batch)) return;
    batch.committing = false;
    closeUserImport();
    try {
      await loadAdminData();
      showToast(`已创建 ${result.createdCount} 个账号。`);
    } catch {
      showToast(`已创建 ${result.createdCount} 个账号，请刷新用户列表。`);
    }
  } catch (error) {
    if (currentUserImport(batch)) {
      clearUserImportRows(batch);
      $('#userImportError').textContent = error.message;
      $('#userImportSummary').textContent = '请重新校验文件。若网络中断，请先刷新用户列表核对创建结果。';
    }
  } finally {
    if (currentUserImport(batch)) { batch.busy = false; batch.committing = false; updateUserImportControls(); }
  }
}

function today() {
  return new Date().toISOString().slice(0, 10);
}

function buildQuery(params) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (Array.isArray(value)) {
      value.forEach((item) => query.append(`${key}[]`, item));
      return;
    }
    if (String(value || '').trim() !== '') query.set(key, String(value).trim());
  });
  const text = query.toString();
  return text ? `?${text}` : '';
}

function filenameFromDisposition(header) {
  const value = String(header || '');
  const match = value.match(/filename="?([^";]+)"?/i);
  return match ? match[1] : `xinyuan-gas-records-${today()}.xlsx`;
}

function isManager() {
  return ['hq_admin', 'company_admin'].includes(state.user?.role);
}

function isHqAdmin() {
  return state.user?.role === 'hq_admin';
}

function renderFilterControls() {
  const companyField = $('#filterCompanyField');
  const companySelect = $('#filterCompanySelect');
  const regionField = $('#filterRegionField');
  const regionSelect = $('#filterRegionSelect');
  const storeField = $('#filterStoreField');
  const storeSelect = $('#filterStoreSelect');
  if (!companyField || !companySelect || !regionField || !regionSelect || !storeField || !storeSelect) return;

  companyField.classList.toggle('hidden', !isHqAdmin());
  companySelect.disabled = !isHqAdmin();

  const selected = companySelect.value;
  companySelect.innerHTML = [
    '<option value="">全部公司</option>',
    ...state.companies
      .filter((company) => company.status === 'active')
      .map((company) => `<option value="${company.id}">${escapeHtml(company.name)}</option>`)
  ].join('');

  if ([...companySelect.options].some((option) => option.value === selected)) {
    companySelect.value = selected;
  }

  const companyId = isHqAdmin() ? Number(companySelect.value || 0) : Number(state.user?.companyId || 0);
  const regionSelected = regionSelect.value;
  const availableRegions = state.regions.filter((region) => (
    region.status === 'active' && (!companyId || Number(region.company_id) === companyId)
  ));
  regionSelect.innerHTML = [
    '<option value="">全部片区</option>',
    ...availableRegions.map((region) => `<option value="${region.id}">${escapeHtml(region.name)}</option>`)
  ].join('');
  if ([...regionSelect.options].some((option) => option.value === regionSelected)) {
    regionSelect.value = regionSelected;
  }

  const storeSelected = storeSelect.value;
  const regionId = Number(regionSelect.value || 0);
  const availableStores = state.stores.filter((store) => (
    store.status === 'active' &&
    (!companyId || Number(store.company_id) === companyId) &&
    (!regionId || Number(store.region_id) === regionId)
  ));
  storeSelect.innerHTML = [
    '<option value="">全部门店</option>',
    ...availableStores.map((store) => `<option value="${store.id}">${escapeHtml(store.name)}</option>`)
  ].join('');
  if ([...storeSelect.options].some((option) => option.value === storeSelected)) {
    storeSelect.value = storeSelected;
  }

  regionField.classList.remove('hidden');
  storeField.classList.remove('hidden');
}

async function ensureFilterLookups() {
  renderFilterControls();
  if (state.organizationFiltersLoaded) return;

  if (isManager()) {
    const requests = [api('/admin/organization')];
    if (isHqAdmin() && state.companies.length === 0) requests.push(api('/admin/companies'));
    const [organization, companies] = await Promise.all(requests);
    state.regions = organization.regions || [];
    state.stores = organization.stores || [];
    if (companies) state.companies = companies.companies || [];
  } else {
    const organization = await api('/organization-options');
    const companyId = Number(organization.companyId || state.user?.companyId || 0);
    state.regions = (organization.regions || []).map((region) => ({
      ...region,
      company_id: companyId,
      status: 'active'
    }));
    state.stores = (organization.stores || []).map((store) => ({
      ...store,
      company_id: companyId,
      region_id: Number(store.regionId || 0),
      status: 'active'
    }));
  }
  state.organizationFiltersLoaded = true;
  renderFilterControls();
}

function roleLabel(role) {
  const labels = {
    hq_admin: '总部管理员',
    company_admin: '公司管理员',
    inspector: '检查员'
  };
  return labels[role] || role || '-';
}

function loginScopeLabel(scope) {
  return {
    app: '仅 APP',
    admin: '仅后台',
    both: 'APP + 后台'
  }[scope] || 'APP + 后台';
}

function userStatusText(status) {
  return status === 'active' ? '启用' : '停用';
}

function dataScopeLabel(user) {
  if (user.role === 'hq_admin') return '全部公司';
  if (user.role === 'company_admin') return '本公司';
  return Number(user.can_view_company_records) === 1 ? '本公司' : '仅本人录入';
}

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  const response = await fetch(`${apiBase}${path}`, { ...options, headers });
  const result = await response.json().catch(() => ({}));
  if (response.status === 401 && state.token) {
    const message = result.message || '当前账号已在另一台电脑登录，请重新登录。';
    logout();
    showToast(message);
    throw new Error(message);
  }
  if (!response.ok || result.ok === false) {
    throw new Error(result.message || '请求失败，请稍后重试。');
  }
  return result.data;
}

async function login(button = null) {
  await runWithButton(button, '正在登录...', async () => {
    const data = await api('/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        phone: $('#loginPhone').value.trim(),
        password: $('#loginPassword').value,
        clientType: 'admin'
      })
    });
    state.token = data.token;
    state.user = data.user;
    localStorage.setItem(tokenKey, state.token);
    await bootstrapDashboard();
    showToast('登录成功');
  });
}

function logout() {
  setExportVisible(false);
  state.token = '';
  state.user = null;
  state.records = [];
  state.currentRecord = null;
  state.companies = [];
  state.regions = [];
  state.stores = [];
  state.users = [];
  state.organizationFiltersLoaded = false;
  state.currentModule = 'home';
  localStorage.removeItem(tokenKey);
  $('#dashboard').classList.add('hidden');
  document.querySelectorAll('.module-panel').forEach((panel) => panel.classList.add('hidden'));
  $('#userBadge').classList.add('hidden');
  $('#loginPanel').classList.remove('hidden');
  closeModal();
  closePasswordModal();
  closeAccessModal();
  closeOrganizationModal(true);
  closeUserImport(true);
}

function restore() {
  if (!state.token) return;
  loadSession().then(bootstrapDashboard).catch((error) => {
    showToast(error.message);
    logout();
  });
}

async function loadSession() {
  const data = await api('/auth/me');
  state.user = data.user;
}

async function bootstrapDashboard() {
  $('#loginPanel').classList.add('hidden');
  $('#dashboard').classList.remove('hidden');
  updateUserSurface();
  showModule('home');
  await ensureFilterLookups();
  await loadRecords();
}

function updateUserSurface() {
  const label = `${state.user?.companyName || '未绑定公司'} · ${state.user?.name || ''} · ${roleLabel(state.user?.role)}`;
  $('#userBadge').textContent = label;
  $('#userBadge').classList.toggle('hidden', !state.user);
  setText('#sidebarUserName', state.user?.name || '管理员');
  setText('#sidebarCompanyName', state.user?.companyName || '淮北鑫远燃气');
  updateDashboardGreeting();
  $('#homeBtn').disabled = !state.user;
  $('#changePasswordBtn').disabled = !state.user;
  $('#logoutBtn').disabled = !state.user;
  $('#refreshBtn').disabled = !state.user;
  renderFilterControls();
  updateModuleAccess();
}

function updateModuleAccess() {
  const adminCard = document.querySelector('[data-module="admin"]');
  if (!adminCard) return;

  adminCard.disabled = !isManager();
  adminCard.classList.toggle('disabled', !isManager());
  const hint = adminCard.querySelector('em');
  if (hint) {
    hint.textContent = isManager() ? '' : '无权限';
  }
}

function moduleId(moduleName) {
  return ({
    home: 'moduleHome',
    records: 'recordsPanel',
    export: 'exportPanel',
    admin: 'adminPanel',
    help: 'helpPanel'
  })[moduleName] || 'moduleHome';
}

function showModule(moduleName = 'home') {
  const nextModule = moduleName || 'home';
  if (nextModule === 'admin' && !isManager()) {
    showToast('当前账号没有账号管理权限');
    return;
  }

  state.currentModule = nextModule;
  setExportVisible(nextModule === 'export');
  document.querySelectorAll('.module-panel').forEach((panel) => {
    panel.classList.toggle('hidden', panel.id !== moduleId(nextModule));
  });
  document.querySelectorAll('#sideNav [data-module]').forEach((button) => {
    button.classList.toggle('active', button.dataset.module === nextModule);
  });
  const titles = {
    home: '工作总览',
    records: '检查记录',
    export: '数据导出',
    admin: '组织与账号',
    help: '使用说明'
  };
  setText('#adminPageTitle', titles[nextModule] || '工作总览');
  $('#refreshBtn').textContent = nextModule === 'admin' ? '刷新组织' : '刷新';
}

async function openModule(moduleName) {
  showModule(moduleName);
  if (state.currentModule === 'export') {
    await refreshExportJobs();
    return;
  }
  if (state.currentModule === 'admin') {
    await loadAdminData();
    return;
  }
  if (['home', 'records', 'export'].includes(state.currentModule)) {
    await loadRecords();
  }
}

async function refreshCurrentModule(button = null) {
  if (state.currentModule === 'export') {
    await refreshExportJobs();
    return;
  }
  if (state.currentModule === 'admin') {
    await loadAdminData(button);
    showToast('账号列表已刷新');
    return;
  }

  await loadRecords(button, '正在刷新...');
  showToast('记录已刷新');
}

function greetingForHour(hour) {
  if (hour >= 5 && hour < 10) return '早上好';
  if (hour >= 10 && hour < 12) return '上午好';
  if (hour >= 12 && hour < 18) return '下午好';
  return '晚上好';
}

function updateDashboardGreeting(now = new Date()) {
  const dateTime = [
    `${now.getFullYear()} 年 ${now.getMonth() + 1} 月 ${now.getDate()} 日`,
    `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
  ].join(' ');
  setText('#dashboardGreeting', greetingForHour(now.getHours()));
  setText('#dashboardGreetingName', state.user?.name || '管理员');
  setText('#dashboardUpdatedAt', `截至 ${dateTime} 的数据概况`);
}

function updateRecordCounters(summary) {
  const { total, pending, done } = summary;
  const rate = total > 0 ? Math.round((done / total) * 100) : 0;

  setText('#totalCount', total);
  setText('#pendingCount', pending);
  setText('#doneCount', done);
  setText('#recordsTotalCount', total);
  setText('#recordsPendingCount', pending);
  setText('#recordsDoneCount', done);
  setText('#closureRate', `${rate}%`);
  setText('#closureRateRing', `${rate}%`);
  setText('#homePendingLegend', pending);
  setText('#homeDoneLegend', done);
  updateDashboardGreeting();
}

function renderDashboard(summary) {
  const recent = (summary.trend || []).map((item) => [item.date, item.count]);
  const maxCount = Math.max(1, ...recent.map(([, count]) => count));
  $('#dashboardTrend').innerHTML = recent.map(([date, value]) => {
    const height = Math.max(14, Math.round((value / maxCount) * 132));
    return `<div class="trend-bar"><b>${value}</b><i style="height:${height}px"></i><span>${escapeHtml(date)}</span></div>`;
  }).join('') || '<p class="muted">暂无趋势数据</p>';

  const pendingRecords = summary.pendingRecords || [];
  $('#dashboardPendingRows').innerHTML = pendingRecords.map((record) => `
    <tr>
      <td>${escapeHtml(record.customer_name || '-')}</td>
      <td>${escapeHtml(record.customer_address || '-')}</td>
      <td>${escapeHtml(record.inspection_date || '-')}</td>
      <td>${escapeHtml(record.inspector_name || '-')}</td>
      <td><span class="status pending">待整改</span></td>
      <td><button type="button" class="detail-button" data-dashboard-record-id="${record.id}">详情</button></td>
    </tr>
  `).join('') || '<tr><td colspan="6">暂无待整改记录</td></tr>';
}

function statusClass(record) {
  return record.status === 'pending' ? 'status pending' : 'status';
}

function statusText(record) {
  if (record.status === 'pending') return '待整改';
  if (record.status === 'rectified') return '已整改';
  return '已完成';
}

function filters() {
  const form = new FormData($('#filtersForm'));
  return {
    keyword: form.get('keyword') || '',
    companyId: form.get('companyId') || '',
    regionId: form.get('regionId') || '',
    storeId: form.get('storeId') || '',
    status: form.get('status') || '',
    checkResult: form.get('checkResult') || '',
    hazardCodes: form.getAll('hazardCodes'),
    gasSourceIssues: form.getAll('gasSourceIssues'),
    customerType: form.get('customerType') || '',
    inspectorName: form.get('inspectorName') || '',
    deliveryPerson: form.get('deliveryPerson') || '',
    dateFrom: form.get('dateFrom') || '',
    dateTo: form.get('dateTo') || ''
  };
}

async function loadRecords(button = null, busyText = '正在刷新...', page = 1, query = filters()) {
  const request = ++state.recordRequest;
  const token = state.token;
  await runWithButton(button, busyText, async () => {
    const data = await api(`/records${buildQuery({ ...query, page, pageSize: state.recordPageSize })}`);
    if (request !== state.recordRequest || token !== state.token) return;
    state.records = data.records || [];
    state.recordPage = data.pagination.page;
    state.recordTotal = data.pagination.total;
    state.appliedFilters = query;
    updateRecordCounters(data.summary);
    renderRows();
    renderDashboard(data.summary);
  });
}

async function exportRecords(button = $('#exportBtn'), photoMode = 'embedded') {
  await runWithButton(button, '正在提交...', () => createExportJob(photoMode));
}

async function loadAdminData(button = null) {
  await runWithButton(button, '正在刷新...', async () => {
    const [companiesData, organizationData, usersData] = await Promise.all([
      api('/admin/companies'),
      api('/admin/organization'),
      api('/admin/users')
    ]);
    state.companies = companiesData.companies || [];
    state.regions = organizationData.regions || [];
    state.stores = organizationData.stores || [];
    state.organizationFiltersLoaded = true;
    state.users = usersData.users || [];
    renderFilterControls();
    renderAdminPanel();
  });
}

function renderAdminPanel() {
  $('#companyForm').classList.toggle('hidden', !isHqAdmin());
  $('#companyCreateBlock').classList.toggle('hidden', !isHqAdmin());
  $('#companyListBlock').classList.toggle('hidden', !isHqAdmin());
  renderCompanyOptions();
  renderRoleOptions();
  renderOrganizationOptions();
  renderCompanyRows();
  renderRegionRows();
  renderStoreRows();
  renderUserRows();

  if (!isHqAdmin()) {
    $('#userCompanyField').classList.add('hidden');
    $('#regionCompanyField').classList.add('hidden');
  } else {
    $('#userCompanyField').classList.remove('hidden');
    $('#regionCompanyField').classList.remove('hidden');
  }
}

function renderCompanyOptions() {
  const options = state.companies
    .filter((company) => company.status === 'active')
    .map((company) => `<option value="${company.id}">${escapeHtml(company.name)}（${escapeHtml(company.code)}）</option>`)
    .join('');
  const userSelected = $('#userCompanySelect').value;
  const regionSelected = $('#regionCompanySelect').value;
  $('#userCompanySelect').innerHTML = options;
  $('#regionCompanySelect').innerHTML = options;
  if ([...$('#userCompanySelect').options].some((option) => option.value === userSelected)) {
    $('#userCompanySelect').value = userSelected;
  }
  if ([...$('#regionCompanySelect').options].some((option) => option.value === regionSelected)) {
    $('#regionCompanySelect').value = regionSelected;
  }
}

function renderRoleOptions() {
  const roles = isHqAdmin()
    ? ['inspector', 'company_admin', 'hq_admin']
    : ['inspector'];
  $('#userRoleSelect').innerHTML = roles
    .map((role) => `<option value="${role}">${roleLabel(role)}</option>`)
    .join('');
  renderLoginScopeOptions();
}

function renderLoginScopeOptions() {
  const role = $('#userRoleSelect').value;
  const scopes = role === 'inspector'
    ? ['both', 'app', 'admin']
    : ['both', 'admin'];
  $('#userLoginScopeSelect').innerHTML = scopes
    .map((scope) => `<option value="${scope}">${loginScopeLabel(scope)}</option>`)
    .join('');
  $('#userCompanyDataField').classList.toggle('hidden', role !== 'inspector');
  if (role !== 'inspector') {
    $('#userCompanyDataField input').checked = false;
  }
}

function selectOptions(select, items, placeholder, label, selectedValue = '') {
  if (!select) return;
  select.innerHTML = items.length
    ? items.map((item) => `<option value="${item.id}">${escapeHtml(label(item))}</option>`).join('')
    : `<option value="">${escapeHtml(placeholder)}</option>`;
  if ([...select.options].some((option) => option.value === String(selectedValue))) {
    select.value = String(selectedValue);
  }
}

function activeRegions(companyId = 0) {
  return state.regions.filter((region) => (
    region.status === 'active' && (!companyId || Number(region.company_id) === Number(companyId))
  ));
}

function renderOrganizationOptions() {
  const regions = activeRegions();
  selectOptions(
    $('#storeRegionSelect'),
    regions,
    '请先新增片区',
    (region) => isHqAdmin() ? `${region.company_name} / ${region.name}` : region.name,
    $('#storeRegionSelect')?.value
  );
  renderFilterControls();
}

function renderCompanyRows() {
  $('#companyRows').innerHTML = state.companies.map((company) => `
    <tr>
      <td>${escapeHtml(company.name)}</td>
      <td>${escapeHtml(company.code)}</td>
      <td><span class="status ${company.status === 'active' ? '' : 'disabled'}">${company.status === 'active' ? '启用' : '停用'}</span></td>
    </tr>
  `).join('') || '<tr><td colspan="3">暂无公司</td></tr>';
}

function renderRegionRows() {
  $('#regionRows').innerHTML = state.regions.map((region) => {
    const nextStatus = region.status === 'active' ? 'disabled' : 'active';
    return `
      <tr>
        <td>${escapeHtml(region.company_name || '-')}</td>
        <td>${escapeHtml(region.name)}</td>
        <td><span class="status ${region.status === 'active' ? '' : 'disabled'}">${userStatusText(region.status)}</span></td>
        <td><div class="organization-actions"><button type="button" class="link-button" data-action="region-edit" data-id="${region.id}">编辑</button><button type="button" class="link-button" data-action="region-status" data-id="${region.id}" data-status="${nextStatus}">${region.status === 'active' ? '停用' : '启用'}</button></div></td>
      </tr>
    `;
  }).join('') || '<tr><td colspan="4">暂无片区，请先新增</td></tr>';
}

function renderStoreRows() {
  $('#storeRows').innerHTML = state.stores.map((store) => {
    const nextStatus = store.status === 'active' ? 'disabled' : 'active';
    return `
      <tr>
        <td>${escapeHtml(store.company_name || '-')}</td>
        <td>${escapeHtml(store.region_name || '-')}</td>
        <td>${escapeHtml(store.name)}</td>
        <td>${escapeHtml(store.address || '-')}</td>
        <td><span class="status ${store.status === 'active' ? '' : 'disabled'}">${userStatusText(store.status)}</span></td>
        <td><div class="organization-actions"><button type="button" class="link-button" data-action="store-edit" data-id="${store.id}">编辑</button><button type="button" class="link-button" data-action="store-status" data-id="${store.id}" data-status="${nextStatus}">${store.status === 'active' ? '停用' : '启用'}</button></div></td>
      </tr>
    `;
  }).join('') || '<tr><td colspan="6">暂无门店，请先新增</td></tr>';
}

function canOperateUser(user) {
  if (!state.user || Number(user.id) === Number(state.user.id)) return false;
  if (state.user.role === 'hq_admin') return true;
  return state.user.role === 'company_admin' && user.role === 'inspector';
}

function renderUserRows() {
  $('#userRows').innerHTML = state.users.map((user) => {
    const nextStatus = user.status === 'active' ? 'disabled' : 'active';
    const disabled = canOperateUser(user) ? '' : 'disabled';
    return `
      <tr>
        <td>${escapeHtml(user.name)}</td>
        <td>${escapeHtml(user.phone)}</td>
        <td>${escapeHtml(user.company_name || '-')}</td>
        <td>${roleLabel(user.role)}</td>
        <td>${loginScopeLabel(user.login_scope)}</td>
        <td>${dataScopeLabel(user)}</td>
        <td><span class="status ${user.status === 'active' ? '' : 'disabled'}">${userStatusText(user.status)}</span></td>
        <td>${escapeHtml(user.last_login_at || '-')}</td>
        <td>
          <button type="button" class="link-button" data-action="user-status" data-id="${user.id}" data-status="${nextStatus}" ${disabled}>
            ${user.status === 'active' ? '停用' : '启用'}
          </button>
          <button type="button" class="link-button" data-action="edit-access" data-id="${user.id}" ${disabled}>编辑权限</button>
          <button type="button" class="link-button" data-action="reset-password" data-id="${user.id}" ${disabled}>重置密码</button>
        </td>
      </tr>
    `;
  }).join('') || '<tr><td colspan="9">暂无用户</td></tr>';
}

async function createCompany(event) {
  event.preventDefault();
  const formElement = event.currentTarget;
  const form = new FormData(formElement);
  const submitButton = formElement.querySelector('button[type="submit"]');
  await runWithButton(submitButton, '正在新增...', async () => {
    await api('/admin/companies', {
      method: 'POST',
      body: JSON.stringify({
        name: String(form.get('name') || '').trim(),
        code: String(form.get('code') || '').trim()
      })
    });
    formElement.reset();
    showToast('公司已新增');
    await loadAdminData();
  });
}

async function createRegion(event) {
  event.preventDefault();
  const formElement = event.currentTarget;
  const form = new FormData(formElement);
  const submitButton = formElement.querySelector('button[type="submit"]');
  await runWithButton(submitButton, '正在新增...', async () => {
    await api('/admin/regions', {
      method: 'POST',
      body: JSON.stringify({
        companyId: form.get('companyId'),
        name: String(form.get('name') || '').trim()
      })
    });
    formElement.reset();
    showToast('片区已新增');
    await loadAdminData();
  });
}

async function createStore(event) {
  event.preventDefault();
  const formElement = event.currentTarget;
  const form = new FormData(formElement);
  const submitButton = formElement.querySelector('button[type="submit"]');
  await runWithButton(submitButton, '正在新增...', async () => {
    await api('/admin/stores', {
      method: 'POST',
      body: JSON.stringify({
        regionId: form.get('regionId'),
        name: String(form.get('name') || '').trim(),
        address: String(form.get('address') || '').trim()
      })
    });
    formElement.reset();
    showToast('门店已新增');
    await loadAdminData();
  });
}

async function createUser(event) {
  event.preventDefault();
  const formElement = event.currentTarget;
  const form = new FormData(formElement);
  const submitButton = formElement.querySelector('button[type="submit"]');
  await runWithButton(submitButton, '正在新增...', async () => {
    await api('/admin/users', {
      method: 'POST',
      body: JSON.stringify({
        companyId: form.get('companyId'),
        name: String(form.get('name') || '').trim(),
        phone: String(form.get('phone') || '').trim(),
        role: String(form.get('role') || ''),
        loginScope: String(form.get('loginScope') || 'both'),
        canViewCompanyRecords: form.get('canViewCompanyRecords') === 'on',
        password: String(form.get('password') || '')
      })
    });
    formElement.reset();
    renderAdminPanel();
    showToast('用户已新增');
    await loadAdminData();
  });
}

function openOrganizationModal(type, id) {
  if (!isManager() || state.organizationSaving) return;
  const item = state[type].find((entry) => Number(entry.id) === Number(id));
  if (!item) return;
  state.organizationEdit = { type, id: Number(id) };
  const isStore = type === 'stores';
  const form = $('#organizationEditForm');
  form.reset();
  $('#organizationEditError').textContent = '';
  $('#organizationEditTitle').textContent = isStore ? '编辑门店' : '编辑片区';
  $('#organizationEditNameLabel').textContent = isStore ? '门店名称' : '片区名称';
  form.elements.name.maxLength = isStore ? 120 : 80;
  form.elements.name.value = item.name;
  form.elements.company.value = item.company_name || state.companies.find((company) => Number(company.id) === Number(item.company_id))?.name || '';
  form.elements.address.value = item.address || '';
  $('#organizationEditRegionField').classList.toggle('hidden', !isStore);
  $('#organizationEditAddressField').classList.toggle('hidden', !isStore);
  form.elements.regionId.required = isStore;
  form.elements.regionId.disabled = !isStore;
  if (isStore) {
    const regions = state.regions.filter((region) => Number(region.company_id) === Number(item.company_id)
      && (region.status === 'active' || Number(region.id) === Number(item.region_id)));
    form.elements.regionId.innerHTML = regions.map((region) => `<option value="${Number(region.id)}">${escapeHtml(region.name)}${region.status === 'active' ? '' : '（已停用）'}</option>`).join('');
    form.elements.regionId.value = String(item.region_id);
  }
  const modal = $('#organizationEditModal');
  modal.classList.remove('hidden');
  modal.setAttribute('aria-hidden', 'false');
  form.elements.name.focus();
}

function closeOrganizationModal(force = false) {
  if (state.organizationSaving && !force) return;
  const previous = state.organizationEdit;
  $('#organizationEditModal').classList.add('hidden');
  $('#organizationEditModal').setAttribute('aria-hidden', 'true');
  $('#organizationEditForm').reset();
  $('#organizationEditError').textContent = '';
  state.organizationEdit = null;
  if (previous && !force) {
    const action = previous.type === 'stores' ? 'store-edit' : 'region-edit';
    document.querySelector(`[data-action="${action}"][data-id="${previous.id}"]`)?.focus();
  }
}

async function submitOrganizationEdit(event) {
  event.preventDefault();
  if (!state.organizationEdit || state.organizationSaving) return;
  const { type, id } = state.organizationEdit;
  const form = event.currentTarget;
  const payload = { name: form.elements.name.value.trim() };
  if (type === 'stores') {
    payload.regionId = Number(form.elements.regionId.value);
    payload.address = form.elements.address.value.trim();
  }
  const submitButton = form.querySelector('button[type="submit"]');
  $('#organizationEditError').textContent = '';
  state.organizationSaving = true;
  $('#organizationEditFields').disabled = true;
  let saved = false;
  let refreshed = false;
  try {
    await runWithButton(submitButton, '保存中...', async () => {
      await api(`/admin/${type}/${id}`, { method: 'POST', body: JSON.stringify(payload) });
      saved = true;
      try {
        await loadAdminData();
        refreshed = true;
      } catch { /* The edit succeeded even if refreshing the list failed. */ }
    });
  } catch (error) {
    $('#organizationEditError').textContent = error.message;
  } finally {
    state.organizationSaving = false;
    $('#organizationEditFields').disabled = false;
  }
  if (saved) {
    closeOrganizationModal();
    showToast(refreshed ? `${type === 'stores' ? '门店' : '片区'}信息已更新` : '信息已保存，列表刷新失败，请点击刷新重试。');
  }
}

async function updateOrganizationStatus(type, id, status, button = null) {
  const label = type === 'regions' ? '片区' : '门店';
  await runWithButton(button, status === 'active' ? '启用中...' : '停用中...', async () => {
    await api(`/admin/${type}/${id}/status`, {
      method: 'POST',
      body: JSON.stringify({ status })
    });
    showToast(`${label}已${status === 'active' ? '启用' : '停用'}`);
    await loadAdminData();
  });
}

async function updateUserStatus(id, status, button = null) {
  await runWithButton(button, status === 'active' ? '启用中...' : '停用中...', async () => {
    await api(`/admin/users/${id}/status`, {
      method: 'POST',
      body: JSON.stringify({ status })
    });
    showToast(status === 'active' ? '用户已启用' : '用户已停用');
    await loadAdminData();
  });
}

async function resetUserPassword(id, button = null) {
  const password = window.prompt('请输入新密码，至少 8 位');
  if (!password) return;
  await runWithButton(button, '重置中...', async () => {
    await api(`/admin/users/${id}/reset-password`, {
      method: 'POST',
      body: JSON.stringify({ password })
    });
    showToast('密码已重置，该用户需要重新登录');
    await loadAdminData();
  });
}

function openAccessModal(id) {
  const user = state.users.find((item) => Number(item.id) === Number(id));
  if (!user || !canOperateUser(user)) return;
  state.accessUserId = Number(user.id);
  $('#accessModalTitle').textContent = `编辑权限 · ${user.name}`;
  const scopes = user.role === 'inspector' ? ['both', 'app', 'admin'] : ['both', 'admin'];
  $('#accessLoginScopeSelect').innerHTML = scopes
    .map((scope) => `<option value="${scope}">${loginScopeLabel(scope)}</option>`)
    .join('');
  $('#accessLoginScopeSelect').value = user.login_scope || 'both';
  $('#accessCompanyDataField').classList.toggle('hidden', user.role !== 'inspector');
  $('#accessCompanyDataField input').checked = user.role === 'inspector' && Number(user.can_view_company_records) === 1;
  $('#accessModal').classList.remove('hidden');
  $('#accessModal').setAttribute('aria-hidden', 'false');
}

function closeAccessModal() {
  const modal = $('#accessModal');
  if (!modal) return;
  modal.classList.add('hidden');
  modal.setAttribute('aria-hidden', 'true');
  state.accessUserId = 0;
  $('#accessForm')?.reset();
}

async function submitUserAccess(event) {
  event.preventDefault();
  if (!state.accessUserId) return;
  const formElement = event.currentTarget;
  const form = new FormData(formElement);
  const submitButton = formElement.querySelector('button[type="submit"]');
  await runWithButton(submitButton, '保存中...', async () => {
    await api(`/admin/users/${state.accessUserId}/access`, {
      method: 'POST',
      body: JSON.stringify({
        loginScope: String(form.get('loginScope') || 'both'),
        canViewCompanyRecords: form.get('canViewCompanyRecords') === 'on'
      })
    });
    closeAccessModal();
    showToast('账号权限已更新，该用户需要重新登录');
    await loadAdminData();
  });
}

function renderRows() {
  const records = state.records || [];
  const pageCount = Math.max(1, Math.ceil(state.recordTotal / state.recordPageSize));
  state.recordPage = Math.min(Math.max(1, state.recordPage), pageCount);
  const start = (state.recordPage - 1) * state.recordPageSize;
  const pageRecords = records;

  $('#recordRows').innerHTML = pageRecords.map((record) => `
    <tr data-id="${record.id}">
      <td>${escapeHtml(record.inspection_date || '')}</td>
      <td>${escapeHtml(record.company_name || '')}</td>
      <td>${escapeHtml([record.region_name, record.store_name].filter(Boolean).join(' / ') || '未分配')}</td>
      <td>${escapeHtml(record.customer_name || '')}</td>
      <td>${escapeHtml(record.customer_phone || '')}</td>
      <td>${escapeHtml(record.customer_address || '')}</td>
      <td><span class="${statusClass(record)}">${statusText(record)}</span></td>
      <td>${escapeHtml(record.delivery_person || '未填写')}</td>
      <td>${escapeHtml(record.inspector_name || '')}</td>
      <td><button type="button" class="link-button" data-action="detail" data-id="${record.id}">详情</button></td>
    </tr>
  `).join('') || '<tr><td colspan="10">暂无记录</td></tr>';
  renderRecordPagination(state.recordTotal, start, pageRecords.length, pageCount);
}

function renderRecordPagination(total, start, pageSize, pageCount) {
  const pager = $('#recordPager');
  if (!pager) return;
  const from = total === 0 ? 0 : start + 1;
  const to = total === 0 ? 0 : start + pageSize;
  pager.innerHTML = `
    <div class="pager-summary">每页 50 条，当前 ${from}-${to} 条，共 ${total} 条</div>
    <div class="pager-actions">
      <button type="button" data-page="prev" ${state.recordPage <= 1 ? 'disabled' : ''}>上一页</button>
      <span>第 ${state.recordPage} / ${pageCount} 页</span>
      <button type="button" data-page="next" ${state.recordPage >= pageCount ? 'disabled' : ''}>下一页</button>
    </div>
  `;
}

async function changeRecordPage(direction, button) {
  const pageCount = Math.max(1, Math.ceil(state.recordTotal / state.recordPageSize));
  const page = direction === 'prev' ? Math.max(1, state.recordPage - 1) : Math.min(pageCount, state.recordPage + 1);
  await loadRecords(button, '加载中...', page, state.appliedFilters || filters());
}

async function openDetail(id, button = null) {
  await runWithButton(button, '打开中...', async () => {
    const data = await api(`/records/${id}`);
    const record = data.record;
    state.currentRecord = record;
    renderDetail(record);
    $('#recordModal').classList.remove('hidden');
    $('#recordModal').setAttribute('aria-hidden', 'false');
  });
}

function closeModal() {
  $('#recordModal').classList.add('hidden');
  $('#recordModal').setAttribute('aria-hidden', 'true');
  $('#rectifyForm').reset();
  state.currentRecord = null;
}

function openPasswordModal() {
  $('#passwordModal').classList.remove('hidden');
  $('#passwordModal').setAttribute('aria-hidden', 'false');
}

function closePasswordModal() {
  $('#passwordModal').classList.add('hidden');
  $('#passwordModal').setAttribute('aria-hidden', 'true');
  $('#passwordForm').reset();
}

async function submitPasswordChange(event) {
  event.preventDefault();
  const formElement = event.currentTarget;
  const form = new FormData(formElement);
  const newPassword = String(form.get('newPassword') || '');
  const confirmPassword = String(form.get('confirmPassword') || '');
  if (newPassword !== confirmPassword) {
    showToast('两次输入的新密码不一致');
    return;
  }

  const submitButton = formElement.querySelector('button[type="submit"]');
  await runWithButton(submitButton, '正在修改...', async () => {
    await api('/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({
        currentPassword: String(form.get('currentPassword') || ''),
        newPassword
      })
    });
    showToast('密码已修改，请重新登录');
    logout();
  });
}

function renderDetail(record) {
  const hazards = record.hazards || [];
  const gasSourceIssues = record.gasSourceIssues || [];
  $('#modalCompany').textContent = record.company_name || '';
  $('#modalTitle').textContent = `${record.customer_name || '未填写用户'} · ${statusText(record)}`;
  $('#recordDetail').innerHTML = [
    ['片区', record.region_name || '未登记'],
    ['门店', record.store_name || '未登记'],
    ['电话', record.customer_phone],
    ['用户类型', record.customer_type],
    ['地址', record.customer_address],
    ['检查日期', record.inspection_date],
    ['检查人', record.inspector_name],
    ['配送员', record.delivery_person],
    ['检查结果', record.check_result],
    ['备注', record.remark],
    ['定位地址', record.location_address],
    ['经纬度', [record.location_lat, record.location_lng].filter(Boolean).join(', ')],
    ['整改日期', record.rectification_date],
    ['整改情况', record.rectification_note],
    ['整改人', record.rectification_person]
  ].map(([label, value]) => `
    <div>
      <span>${label}</span>
      <strong>${escapeHtml(value || '-')}</strong>
    </div>
  `).join('');

  $('#hazardDetail').innerHTML = hazards
    .map((item) => `<span>${escapeHtml(item.hazard_category)}：${escapeHtml(item.hazard_name)}</span>`)
    .join('') || '<p class="muted">无未清零隐患</p>';

  $('#gasSourceDetail').innerHTML = gasSourceIssues
    .map((item) => `<span>${escapeHtml(item.issue_text)}</span>`)
    .join('') || '<p class="muted">无气源排查异常</p>';

  $('#photoDetail').innerHTML = renderPhotoDetail(record);

  $('#rectifyForm').classList.toggle('hidden', record.status !== 'pending');
  $('#rectifyForm [name="rectificationDate"]').value = today();
  $('#rectifyForm [name="rectificationPerson"]').value = record.rectification_person || state.user?.name || '';
}

function renderPhotoDetail(record) {
  const entries = normalizePhotoEntries(record.photos || []);
  if (entries.length === 0) return '<p class="muted">无照片</p>';

  const used = new Set();
  const groups = [];
  const appendTypedGroup = (title, types) => {
    const items = entries.filter((entry) => types.includes(entry.type) && !used.has(entry.index));
    if (items.length === 0) return;
    items.forEach((entry) => used.add(entry.index));
    groups.push(renderPhotoGroup(title, items));
  };

  appendTypedGroup('门头照片', ['storefront']);

  const hazardBlock = renderHazardPhotoBlock(record.hazards || [], entries, used);
  if (hazardBlock) groups.push(hazardBlock);

  appendTypedGroup('钢瓶二维码照片', ['cylinder_qr']);
  appendTypedGroup('钢瓶照片', ['cylinder']);
  appendTypedGroup('整改照片', ['rectification']);

  const others = entries.filter((entry) => !used.has(entry.index));
  if (others.length > 0) groups.push(renderPhotoGroup('其他照片', others));

  return `<div class="photo-sections">${groups.join('')}</div>`;
}

function normalizePhotoEntries(photos) {
  return [...(Array.isArray(photos) ? photos : [])]
    .map((photo, index) => {
      const normalizedPhoto = typeof photo === 'string' ? { file_path: photo } : photo;
      return {
        index,
        photo: normalizedPhoto,
        type: normalizePhotoType(normalizedPhoto),
        url: photoUrl(normalizedPhoto)
      };
    })
    .filter((entry) => entry.url !== '');
}

function renderHazardPhotoBlock(hazards, entries, used) {
  const hazardEntries = entries.filter(isHazardPhotoEntry);
  if (hazards.length === 0 && hazardEntries.length === 0) return '';

  const rows = [];
  hazards.forEach((hazard) => {
    const code = String(hazard.hazard_code || '').trim();
    const related = code
      ? hazardEntries.filter((entry) => photoHazardCode(entry.photo) === code && !used.has(entry.index))
      : [];
    related.forEach((entry) => used.add(entry.index));
    rows.push(renderHazardPhotoRow(
      hazard.hazard_category || '隐患项目',
      hazard.hazard_name || code || '未命名隐患',
      related
    ));
  });

  ['可燃气体报警器', '软管', '用气环境', '减压阀', '安全用气指南'].forEach((category) => {
    const related = hazardEntries.filter((entry) => (
      legacyHazardCategory(entry.type) === category && !photoHazardCode(entry.photo) && !used.has(entry.index)
    ));
    if (related.length === 0) return;
    related.forEach((entry) => used.add(entry.index));
    rows.push(renderHazardPhotoRow(category, '历史分类照片', related));
  });

  const unlinked = hazardEntries.filter((entry) => !used.has(entry.index));
  if (unlinked.length > 0) {
    unlinked.forEach((entry) => used.add(entry.index));
    rows.push(renderHazardPhotoRow('隐患照片', '未关联到具体隐患项目', unlinked));
  }

  if (rows.length === 0) return '';
  return `
    <div class="photo-group hazard-photo-block">
      <h4>隐患照片</h4>
      <div class="hazard-photo-list">${rows.join('')}</div>
    </div>
  `;
}

function renderHazardPhotoRow(category, title, entries) {
  return `
    <div class="hazard-photo-row">
      <div class="hazard-photo-title">
        <span>${escapeHtml(category)}</span>
        <strong>${escapeHtml(title)}</strong>
      </div>
      ${renderPhotoCards(entries, '隐患照片')}
    </div>
  `;
}

function renderPhotoGroup(title, entries) {
  return `
    <div class="photo-group">
      <h4>${escapeHtml(title)}</h4>
      ${renderPhotoCards(entries, title)}
    </div>
  `;
}

function renderPhotoCards(entries, title) {
  if (entries.length === 0) return '<p class="photo-empty">暂无对应照片</p>';
  return `
    <div class="photo-grid">
      ${entries.map((entry, index) => renderPhotoCard(entry, title, index, entries.length)).join('')}
    </div>
  `;
}

function renderPhotoCard(entry, title, index, total) {
  const cardTitle = total > 1 ? `${title} ${index + 1}` : title;
  const meta = photoMeta(entry.photo);
  return `
    <button type="button"
      class="photo-card"
      data-photo-preview="${escapeHtml(entry.url)}"
      data-photo-title="${escapeHtml(cardTitle)}"
      data-photo-meta="${escapeHtml(meta)}">
      <img src="${escapeHtml(entry.url)}" alt="${escapeHtml(cardTitle)}" loading="lazy">
      <span>${escapeHtml(cardTitle)}</span>
      ${meta ? `<small>${escapeHtml(meta)}</small>` : ''}
    </button>
  `;
}

function photoLabel(photo) {
  const labels = {
    storefront: '门头照片',
    hazard: '隐患照片',
    cylinder_qr: '钢瓶二维码照片',
    cylinder: '钢瓶照片',
    rectification: '整改照片',
    alarm: '可燃气体报警器照片',
    hose: '软管照片',
    environment: '用气环境照片',
    valve: '减压阀照片',
    guide: '安全用气指南照片'
  };
  return labels[normalizePhotoType(photo)] || '照片';
}

function normalizePhotoType(photo) {
  const raw = String(photo?.photo_type || photo?.type || '').trim();
  const aliases = {
    storefrontPhoto: 'storefront',
    storefront_photo: 'storefront',
    cylinderQrPhoto: 'cylinder_qr',
    cylinder_qr_photo: 'cylinder_qr',
    cylinderPhoto: 'cylinder',
    cylinder_photo: 'cylinder',
    rectificationPhoto: 'rectification',
    rectification_photo: 'rectification',
    alarmPhoto: 'alarm',
    alarm_photo: 'alarm',
    hosePhoto: 'hose',
    hose_photo: 'hose',
    environmentPhoto: 'environment',
    environment_photo: 'environment',
    valvePhoto: 'valve',
    valve_photo: 'valve',
    guidePhoto: 'guide',
    guide_photo: 'guide'
  };
  return aliases[raw] || raw || 'photo';
}

function photoUrl(photo) {
  const path = String(photo?.file_path || photo?.filePath || photo?.url || '').trim();
  if (path === '') return '';
  if (/^(https?:)?\/\//i.test(path) || path.startsWith('data:')) return path;
  return path.startsWith('/') ? path : `/${path.replace(/^\/+/, '')}`;
}

function photoHazardCode(photo) {
  return String(photo?.hazard_code || photo?.hazardCode || '').trim();
}

function legacyHazardCategory(type) {
  const categories = {
    alarm: '可燃气体报警器',
    hose: '软管',
    environment: '用气环境',
    valve: '减压阀',
    guide: '安全用气指南'
  };
  return categories[type] || '';
}

function isHazardPhotoEntry(entry) {
  return entry.type === 'hazard' || legacyHazardCategory(entry.type) !== '';
}

function photoMeta(photo) {
  const parts = [];
  if (photo?.captured_at) parts.push(`拍摄：${photo.captured_at}`);
  if (photo?.watermark_address) parts.push(`水印地址：${photo.watermark_address}`);
  return parts.join(' · ');
}

function ensurePhotoPreview() {
  let modal = $('#photoPreviewModal');
  if (modal) return modal;

  document.body.insertAdjacentHTML('beforeend', `
    <section id="photoPreviewModal" class="photo-preview-modal hidden" aria-hidden="true">
      <button class="photo-preview-backdrop" type="button" data-photo-close="1" aria-label="关闭照片预览"></button>
      <div class="photo-preview-panel">
        <div class="photo-preview-head">
          <div>
            <h3 id="photoPreviewTitle">照片预览</h3>
            <p id="photoPreviewMeta" class="muted"></p>
          </div>
          <button id="closePhotoPreviewBtn" type="button" data-photo-close="1">关闭</button>
        </div>
        <img id="photoPreviewImage" alt="照片预览">
        <div class="photo-preview-actions">
          <a id="photoPreviewOriginal" href="#" target="_blank" rel="noopener">打开原图</a>
        </div>
      </div>
    </section>
  `);

  modal = $('#photoPreviewModal');
  modal.addEventListener('click', (event) => {
    if (event.target.dataset.photoClose) closePhotoPreview();
  });
  return modal;
}

function openPhotoPreview(url, title, meta) {
  const modal = ensurePhotoPreview();
  $('#photoPreviewTitle').textContent = title || '照片预览';
  $('#photoPreviewMeta').textContent = meta || '';
  $('#photoPreviewImage').src = url;
  $('#photoPreviewImage').alt = title || '照片预览';
  $('#photoPreviewOriginal').href = url;
  modal.classList.remove('hidden');
  modal.setAttribute('aria-hidden', 'false');
}

function closePhotoPreview() {
  const modal = $('#photoPreviewModal');
  if (!modal) return;
  modal.classList.add('hidden');
  modal.setAttribute('aria-hidden', 'true');
  const image = $('#photoPreviewImage');
  if (image) image.removeAttribute('src');
}

async function uploadFiles(fileList) {
  const uploaded = [];
  for (const file of [...(fileList || [])]) {
    const body = new FormData();
    body.append('file', file, file.name);
    const data = await api('/files/upload', { method: 'POST', body });
    uploaded.push(data.filePath);
  }
  return uploaded;
}

async function submitRectification(event) {
  event.preventDefault();
  if (!state.currentRecord) return;
  const form = new FormData(event.currentTarget);
  const submitButton = event.currentTarget.querySelector('button[type="submit"]');
  setButtonBusy(submitButton, true, '正在提交...');
  try {
    const photos = await uploadFiles(form.getAll('rectificationPhotos').filter((file) => file && file.size > 0));
    await api(`/records/${state.currentRecord.id}/rectify`, {
      method: 'POST',
      body: JSON.stringify({
        rectificationDate: form.get('rectificationDate'),
        rectificationNote: String(form.get('rectificationNote') || '').trim(),
        rectificationPerson: String(form.get('rectificationPerson') || '').trim(),
        photos
      })
    });
    showToast('整改已提交');
    closeModal();
    await loadRecords();
  } catch (error) {
    showToast(error.message);
  } finally {
    setButtonBusy(submitButton, false);
  }
}

$('#loginBtn').addEventListener('click', (event) => login(event.currentTarget).catch((error) => showToast(error.message)));
$('#logoutBtn').addEventListener('click', logout);
$('#sideNav').addEventListener('click', (event) => {
  const button = event.target.closest('[data-module]');
  if (!button || button.disabled) return;
  openModule(button.dataset.module).catch((error) => showToast(error.message));
});
document.querySelectorAll('[data-open-module]').forEach((button) => {
  button.addEventListener('click', () => openModule(button.dataset.openModule).catch((error) => showToast(error.message)));
});
$('#dashboardPendingRows').addEventListener('click', (event) => {
  const button = event.target.closest('[data-dashboard-record-id]');
  if (!button) return;
  openDetail(button.dataset.dashboardRecordId, button).catch((error) => showToast(error.message));
});
$('#refreshBtn').addEventListener('click', (event) => refreshCurrentModule(event.currentTarget).catch((error) => showToast(error.message)));
$('#exportBtn').addEventListener('click', (event) => exportRecords(event.currentTarget).catch((error) => showToast(error.message)));
$('#exportPanelBtn').addEventListener('click', (event) => exportRecords(event.currentTarget).catch((error) => showToast(error.message)));
$('#exportLinksBtn').addEventListener('click', (event) => exportRecords(event.currentTarget, 'links').catch((error) => showToast(error.message)));
$('#exportLinksPanelBtn').addEventListener('click', (event) => exportRecords(event.currentTarget, 'links').catch((error) => showToast(error.message)));
$('#changePasswordBtn').addEventListener('click', openPasswordModal);
$('#reloadAdminBtn').addEventListener('click', (event) => loadAdminData(event.currentTarget).then(() => showToast('账号列表已刷新')).catch((error) => showToast(error.message)));
$('#companyForm').addEventListener('submit', (event) => createCompany(event).catch((error) => showToast(error.message)));
$('#regionForm').addEventListener('submit', (event) => createRegion(event).catch((error) => showToast(error.message)));
$('#storeForm').addEventListener('submit', (event) => createStore(event).catch((error) => showToast(error.message)));
$('#userForm').addEventListener('submit', (event) => createUser(event).catch((error) => showToast(error.message)));
$('#userRoleSelect').addEventListener('change', renderLoginScopeOptions);
$('#regionRows').addEventListener('click', (event) => {
  const button = event.target.closest('[data-action]');
  if (!button) return;
  if (button.dataset.action === 'region-edit') return openOrganizationModal('regions', button.dataset.id);
  updateOrganizationStatus('regions', button.dataset.id, button.dataset.status, button).catch((error) => showToast(error.message));
});
$('#storeRows').addEventListener('click', (event) => {
  const button = event.target.closest('[data-action]');
  if (!button) return;
  if (button.dataset.action === 'store-edit') return openOrganizationModal('stores', button.dataset.id);
  updateOrganizationStatus('stores', button.dataset.id, button.dataset.status, button).catch((error) => showToast(error.message));
});
$('#userRows').addEventListener('click', (event) => {
  const button = event.target.closest('[data-action]');
  if (!button || button.disabled) return;
  if (button.dataset.action === 'user-status') {
    updateUserStatus(button.dataset.id, button.dataset.status, button).catch((error) => showToast(error.message));
  }
  if (button.dataset.action === 'reset-password') {
    resetUserPassword(button.dataset.id, button).catch((error) => showToast(error.message));
  }
  if (button.dataset.action === 'edit-access') {
    openAccessModal(button.dataset.id);
  }
});
$('#filtersForm').addEventListener('submit', (event) => {
  event.preventDefault();
  const submitButton = event.submitter || event.currentTarget.querySelector('button[type="submit"]');
  loadRecords(submitButton, '查询中...').then(() => showToast('查询完成')).catch((error) => showToast(error.message));
});
$('#filterCompanySelect').addEventListener('change', renderFilterControls);
$('#filterRegionSelect').addEventListener('change', renderFilterControls);
$('#resetFiltersBtn').addEventListener('click', (event) => {
  runWithButton(event.currentTarget, '正在重置...', async () => {
    $('#filtersForm').reset();
    updateMultiFilterLabels();
    renderFilterControls();
    await loadRecords();
    showToast('筛选已重置');
  }).catch((error) => showToast(error.message));
});
$('#recordRows').addEventListener('click', (event) => {
  const button = event.target.closest('[data-action="detail"]');
  if (!button) return;
  openDetail(button.dataset.id, button).catch((error) => showToast(error.message));
});
$('#recordPager').addEventListener('click', (event) => {
  const button = event.target.closest('[data-page]');
  if (!button || button.disabled) return;
  changeRecordPage(button.dataset.page, button).catch((error) => showToast(error.message));
});
$('#closeModalBtn').addEventListener('click', closeModal);
$('#recordModal').addEventListener('click', (event) => {
  if (event.target.dataset.close) closeModal();
});
$('#photoDetail').addEventListener('click', (event) => {
  const button = event.target.closest('[data-photo-preview]');
  if (!button) return;
  openPhotoPreview(button.dataset.photoPreview, button.dataset.photoTitle, button.dataset.photoMeta);
});
$('#rectifyForm').addEventListener('submit', submitRectification);
$('#closePasswordModalBtn').addEventListener('click', closePasswordModal);
$('#passwordModal').addEventListener('click', (event) => {
  if (event.target.dataset.passwordClose) closePasswordModal();
});
$('#passwordForm').addEventListener('submit', (event) => submitPasswordChange(event).catch((error) => showToast(error.message)));
$('#closeAccessModalBtn').addEventListener('click', closeAccessModal);
$('#accessModal').addEventListener('click', (event) => {
  if (event.target.dataset.accessClose) closeAccessModal();
});
$('#accessForm').addEventListener('submit', (event) => submitUserAccess(event).catch((error) => showToast(error.message)));
$('#organizationEditForm').addEventListener('submit', submitOrganizationEdit);
$('#closeOrganizationEditBtn').addEventListener('click', () => closeOrganizationModal());
$('#cancelOrganizationEditBtn').addEventListener('click', () => closeOrganizationModal());
$('#organizationEditModal').addEventListener('click', (event) => {
  if (event.target.dataset.organizationEditClose) closeOrganizationModal();
});
$('#organizationEditModal').addEventListener('keydown', (event) => {
  if (event.key === 'Escape') {
    event.stopPropagation();
    closeOrganizationModal();
  }
  if (event.key === 'Tab') {
    const controls = [...event.currentTarget.querySelectorAll('button, input, select, textarea')]
      .filter((element) => !element.matches(':disabled') && element.getClientRects().length);
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  }
});
$('#exportUsersBtn').addEventListener('click', (event) => exportUsers(event.currentTarget).catch((error) => showToast(error.message)));
$('#userTemplateBtn').addEventListener('click', (event) => downloadUserTemplateFile(event.currentTarget).catch((error) => showToast(error.message)));
$('#openUserImportBtn').addEventListener('click', openUserImport);
$('#closeUserImportBtn').addEventListener('click', () => closeUserImport());
$('#cancelUserImportBtn').addEventListener('click', () => closeUserImport());
$('#previewUserImportBtn').addEventListener('click', previewUserImport);
$('#commitUserImportBtn').addEventListener('click', commitUserImport);
$('#userImportFile').addEventListener('change', () => {
  if (!state.userImport) return;
  clearUserImportRows(state.userImport);
  $('#userImportError').textContent = '';
  $('#userImportSummary').textContent = '尚未校验文件';
  updateUserImportControls();
});
$('#userImportModal').addEventListener('click', (event) => {
  if (event.target.dataset.userImportClose) closeUserImport();
});
$('#userImportModal').addEventListener('keydown', (event) => {
  if (event.key === 'Escape') { event.stopPropagation(); closeUserImport(); }
  if (event.key === 'Tab') {
    const controls = [...event.currentTarget.querySelectorAll('button, input')].filter((element) => !element.disabled && element.getClientRects().length);
    const first = controls[0];
    const last = controls[controls.length - 1];
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
    if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
  }
});

document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && state.userImport) {
    event.preventDefault();
    closeUserImport();
    return;
  }
  if (event.key === 'Escape') closePhotoPreview();
  if (event.key === 'Enter' && !$('#loginPanel').classList.contains('hidden') && event.target.matches('#loginPhone, #loginPassword')) {
    login($('#loginBtn')).catch((error) => showToast(error.message));
  }
});

const hazardGroups = [...new Set(hazardItems.map((item) => item.category))];
$('#filterHazardOptions').innerHTML = hazardGroups.map((category) => (
  `<div class="multi-filter-category">${escapeHtml(category)}</div>${hazardItems
    .filter((item) => item.category === category)
    .map((item) => `<label><input type="checkbox" name="hazardCodes" value="${escapeHtml(item.code)}"><span>${escapeHtml(item.name)}</span></label>`)
    .join('')}`
)).join('');
$('#filterGasOptions').innerHTML = gasSourceIssues.map((item) => `<label><input type="checkbox" name="gasSourceIssues" value="${escapeHtml(item)}"><span>${escapeHtml(item)}</span></label>`).join('');
function updateMultiFilterLabels() {
  for (const [name, id] of [['hazardCodes', 'filterHazardCount'], ['gasSourceIssues', 'filterGasCount']]) {
    const count = document.querySelectorAll(`#filtersForm input[name="${name}"]:checked`).length;
    $(`#${id}`).textContent = count ? `已选 ${count} 项` : '全部（可多选）';
  }
}
$('#filtersForm').addEventListener('change', updateMultiFilterLabels);
updateMultiFilterLabels();

configureExportJobs({ api, apiBase, filters, showModule, notify: showToast });
updateUserSurface();
restore();
