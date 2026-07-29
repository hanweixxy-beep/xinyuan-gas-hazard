const apiBase = localStorage.getItem('apiBase') || '/api';
const tokenKey = 'xinyuan_admin_token';
const state = {
  token: localStorage.getItem(tokenKey) || '',
  user: null,
  records: [],
  currentRecord: null,
  companies: [],
  users: [],
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

function today() {
  return new Date().toISOString().slice(0, 10);
}

function buildQuery(params) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
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
  if (!companyField || !companySelect) return;

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
}

async function ensureFilterLookups() {
  renderFilterControls();
  if (!isHqAdmin() || state.companies.length > 0) return;

  const data = await api('/admin/companies');
  state.companies = data.companies || [];
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

async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  const response = await fetch(`${apiBase}${path}`, { ...options, headers });
  const result = await response.json().catch(() => ({}));
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
  state.token = '';
  state.user = null;
  state.records = [];
  state.currentRecord = null;
  state.companies = [];
  state.users = [];
  state.currentModule = 'home';
  localStorage.removeItem(tokenKey);
  $('#dashboard').classList.add('hidden');
  document.querySelectorAll('.module-panel').forEach((panel) => panel.classList.add('hidden'));
  $('#userBadge').classList.add('hidden');
  $('#loginPanel').classList.remove('hidden');
  closeModal();
  closePasswordModal();
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
    admin: '公司与账号',
    help: '使用说明'
  };
  setText('#adminPageTitle', titles[nextModule] || '工作总览');
  $('#refreshBtn').textContent = nextModule === 'admin' ? '刷新账号' : '刷新';
}

async function openModule(moduleName) {
  showModule(moduleName);
  if (state.currentModule === 'admin') {
    await loadAdminData();
    return;
  }
  if (['home', 'records', 'export'].includes(state.currentModule)) {
    await loadRecords();
  }
}

async function refreshCurrentModule(button = null) {
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

function updateRecordCounters(records) {
  const total = records.length;
  const pending = records.filter((item) => item.status === 'pending').length;
  const done = records.filter((item) => item.status !== 'pending').length;
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

function renderDashboard(records = []) {
  const counts = records.reduce((result, record) => {
    const date = String(record.inspection_date || '').slice(0, 10);
    if (date) result.set(date, (result.get(date) || 0) + 1);
    return result;
  }, new Map());
  const recent = [...counts.entries()].sort(([left], [right]) => left.localeCompare(right)).slice(-7);
  const maxCount = Math.max(1, ...recent.map(([, count]) => count));
  $('#dashboardTrend').innerHTML = recent.map(([date, value]) => {
    const height = Math.max(14, Math.round((value / maxCount) * 132));
    return `<div class="trend-bar"><b>${value}</b><i style="height:${height}px"></i><span>${escapeHtml(date)}</span></div>`;
  }).join('') || '<p class="muted">暂无趋势数据</p>';

  const pendingRecords = records.filter((record) => record.status === 'pending').slice(0, 5);
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
    status: form.get('status') || '',
    checkResult: form.get('checkResult') || '',
    customerType: form.get('customerType') || '',
    inspectorName: form.get('inspectorName') || '',
    deliveryPerson: form.get('deliveryPerson') || '',
    dateFrom: form.get('dateFrom') || '',
    dateTo: form.get('dateTo') || ''
  };
}

async function loadRecords(button = null, busyText = '正在刷新...') {
  await runWithButton(button, busyText, async () => {
    const data = await api(`/records${buildQuery(filters())}`);
    const records = data.records || [];
    state.records = records;
    state.recordPage = 1;
    updateRecordCounters(records);
    renderRows();
    renderDashboard(records);
  });
}

async function exportRecords(button = $('#exportBtn')) {
  await runWithButton(button, '正在导出...', async () => {
    const response = await fetch(`${apiBase}/export/xlsx${buildQuery(filters())}`, {
      headers: state.token ? { Authorization: `Bearer ${state.token}` } : {}
    });

    if (!response.ok) {
      const result = await response.json().catch(() => ({}));
      throw new Error(result.message || '导出失败，请稍后重试。');
    }

    const blob = await response.blob();
    if (!blob.size) {
      throw new Error('导出文件为空，请稍后重试。');
    }

    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.href = url;
    link.download = filenameFromDisposition(response.headers.get('Content-Disposition'));
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    showToast('导出已开始下载');
  });
}

async function loadAdminData(button = null) {
  await runWithButton(button, '正在刷新...', async () => {
    const [companiesData, usersData] = await Promise.all([
      api('/admin/companies'),
      api('/admin/users')
    ]);
    state.companies = companiesData.companies || [];
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
  renderCompanyRows();
  renderUserRows();

  if (!isHqAdmin()) {
    $('#userCompanyField').classList.add('hidden');
  } else {
    $('#userCompanyField').classList.remove('hidden');
  }
}

function renderCompanyOptions() {
  $('#userCompanySelect').innerHTML = state.companies
    .filter((company) => company.status === 'active')
    .map((company) => `<option value="${company.id}">${escapeHtml(company.name)}（${escapeHtml(company.code)}）</option>`)
    .join('');
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
    : ['admin'];
  $('#userLoginScopeSelect').innerHTML = scopes
    .map((scope) => `<option value="${scope}">${loginScopeLabel(scope)}</option>`)
    .join('');
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
        <td><span class="status ${user.status === 'active' ? '' : 'disabled'}">${userStatusText(user.status)}</span></td>
        <td>${escapeHtml(user.last_login_at || '-')}</td>
        <td>
          <button type="button" class="link-button" data-action="user-status" data-id="${user.id}" data-status="${nextStatus}" ${disabled}>
            ${user.status === 'active' ? '停用' : '启用'}
          </button>
          <button type="button" class="link-button" data-action="reset-password" data-id="${user.id}" ${disabled}>重置密码</button>
        </td>
      </tr>
    `;
  }).join('') || '<tr><td colspan="8">暂无用户</td></tr>';
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
        password: String(form.get('password') || '')
      })
    });
    formElement.reset();
    renderAdminPanel();
    showToast('用户已新增');
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

function renderRows() {
  const records = state.records || [];
  const pageCount = Math.max(1, Math.ceil(records.length / state.recordPageSize));
  state.recordPage = Math.min(Math.max(1, state.recordPage), pageCount);
  const start = (state.recordPage - 1) * state.recordPageSize;
  const pageRecords = records.slice(start, start + state.recordPageSize);

  $('#recordRows').innerHTML = pageRecords.map((record) => `
    <tr data-id="${record.id}">
      <td>${escapeHtml(record.inspection_date || '')}</td>
      <td>${escapeHtml(record.company_name || '')}</td>
      <td>${escapeHtml(record.customer_name || '')}</td>
      <td>${escapeHtml(record.customer_phone || '')}</td>
      <td>${escapeHtml(record.customer_address || '')}</td>
      <td><span class="${statusClass(record)}">${statusText(record)}</span></td>
      <td>${escapeHtml(record.inspector_name || '')}</td>
      <td><button type="button" class="link-button" data-action="detail" data-id="${record.id}">详情</button></td>
    </tr>
  `).join('') || '<tr><td colspan="8">暂无记录</td></tr>';
  renderRecordPagination(records.length, start, pageRecords.length, pageCount);
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

function changeRecordPage(direction) {
  const total = state.records.length;
  const pageCount = Math.max(1, Math.ceil(total / state.recordPageSize));
  if (direction === 'prev') {
    state.recordPage = Math.max(1, state.recordPage - 1);
  } else if (direction === 'next') {
    state.recordPage = Math.min(pageCount, state.recordPage + 1);
  }
  renderRows();
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
$('#changePasswordBtn').addEventListener('click', openPasswordModal);
$('#reloadAdminBtn').addEventListener('click', (event) => loadAdminData(event.currentTarget).then(() => showToast('账号列表已刷新')).catch((error) => showToast(error.message)));
$('#companyForm').addEventListener('submit', (event) => createCompany(event).catch((error) => showToast(error.message)));
$('#userForm').addEventListener('submit', (event) => createUser(event).catch((error) => showToast(error.message)));
$('#userRoleSelect').addEventListener('change', renderLoginScopeOptions);
$('#userRows').addEventListener('click', (event) => {
  const button = event.target.closest('[data-action]');
  if (!button || button.disabled) return;
  if (button.dataset.action === 'user-status') {
    updateUserStatus(button.dataset.id, button.dataset.status, button).catch((error) => showToast(error.message));
  }
  if (button.dataset.action === 'reset-password') {
    resetUserPassword(button.dataset.id, button).catch((error) => showToast(error.message));
  }
});
$('#filtersForm').addEventListener('submit', (event) => {
  event.preventDefault();
  const submitButton = event.submitter || event.currentTarget.querySelector('button[type="submit"]');
  loadRecords(submitButton, '查询中...').then(() => showToast('查询完成')).catch((error) => showToast(error.message));
});
$('#resetFiltersBtn').addEventListener('click', (event) => {
  runWithButton(event.currentTarget, '正在重置...', async () => {
    $('#filtersForm').reset();
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
  changeRecordPage(button.dataset.page);
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
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape') closePhotoPreview();
  if (event.key === 'Enter' && !$('#loginPanel').classList.contains('hidden') && event.target.matches('#loginPhone, #loginPassword')) {
    login($('#loginBtn')).catch((error) => showToast(error.message));
  }
});

updateUserSurface();
restore();
