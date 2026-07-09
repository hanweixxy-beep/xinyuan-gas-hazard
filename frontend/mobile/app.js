import {
  RESULT_CLEARED,
  RESULT_NOT_CLEARED,
  customerTypes,
  gasSourceIssues,
  hazardItems
} from '../../shared/catalog.js';

const apiBase = localStorage.getItem('apiBase') || '/api';
const tokenKey = 'xinyuan_h5_token';
const draftKey = 'xinyuan_inspection_draft';
const singlePageForm = true;
const formSteps = [
  { key: 'user', label: '用户信息' },
  { key: 'inspect', label: '检查信息' },
  { key: 'photo', label: '拍照与结果' },
  { key: 'hazard', label: '隐患与备注' },
  { key: 'review', label: '提交确认' }
];
const formStepKeys = formSteps.map((step) => step.key);
const fieldStepMap = {
  customerName: 'user',
  customerPhone: 'user',
  customerType: 'user',
  customerAddress: 'user',
  inspectorName: 'inspect',
  inspectionDate: 'inspect',
  deliveryPerson: 'inspect',
  storefront: 'photo',
  cylinderQr: 'hazard',
  cylinder: 'hazard'
};
const state = {
  token: localStorage.getItem(tokenKey) || '',
  user: null,
  location: null,
  locating: false,
  view: 'login',
  formStep: 'user',
  records: [],
  currentRecord: null,
  photos: {}
};

const $ = (selector) => document.querySelector(selector);
let toastTimer = null;

function showToast(message) {
  const toast = $('#toast');
  if (toastTimer) window.clearTimeout(toastTimer);
  toast.textContent = message;
  toast.classList.remove('hidden');
  toastTimer = window.setTimeout(() => toast.classList.add('hidden'), 2600);
}

function setButtonBusy(button, busy, busyText = '') {
  if (!button) return;
  if (busy) {
    if (!button.dataset.defaultText) button.dataset.defaultText = button.textContent;
    if (!button.dataset.defaultHtml) button.dataset.defaultHtml = button.innerHTML;
    button.textContent = busyText || button.dataset.defaultText;
    button.disabled = true;
    button.classList.add('is-loading');
    return;
  }
  button.disabled = false;
  button.classList.remove('is-loading');
  if (button.dataset.defaultHtml) {
    button.innerHTML = button.dataset.defaultHtml;
    delete button.dataset.defaultHtml;
  } else if (button.dataset.defaultText) {
    button.textContent = button.dataset.defaultText;
  }
  if (button.dataset.defaultText) {
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

function uniqueId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function buildQuery(params) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (String(value || '').trim() !== '') query.set(key, String(value).trim());
  });
  const text = query.toString();
  return text ? `?${text}` : '';
}

function setLocationSurface(status, title, detail) {
  $('#locationCard').className = `location-card ${status}`;
  $('#locationTitle').textContent = title;
  $('#locationStatus').textContent = detail;
}

function currentAddress() {
  return $('[name="customerAddress"]').value.trim();
}

function renderAddressSurface() {
  const address = currentAddress();
  if (!$('#locationCard')) return;
  if (state.location) {
    setLocationSurface(
      'success',
      '用户地址',
      `${address || '未填写用户地址'}；定位坐标：${state.location.lat}, ${state.location.lng}`
    );
    return;
  }
  if (address) {
    setLocationSurface('pending', '用户地址', `${address}；拍摄门头照片时自动尝试定位。`);
    return;
  }
  setLocationSurface('pending', '用户地址', '填写用户地址后用于照片水印；拍摄门头照片时自动尝试定位。');
}

function photoItems(field) {
  state.photos[field] ||= [];
  return state.photos[field];
}

function photoInput(field) {
  return document.querySelector(`[name="${field}"]`);
}

function hasPhoto(field) {
  return (state.photos[field] || []).length > 0;
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[char]));
}

function formStepIndex(step = state.formStep) {
  return Math.max(0, formStepKeys.indexOf(step));
}

function formStepLabel(step = state.formStep) {
  return formSteps.find((item) => item.key === step)?.label || '登记';
}

function focusFormField(name) {
  const field = photoInput(name) || document.querySelector(`[name="${name}"]`);
  const target = field?.closest('.photo-tile') || field;
  target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  if (field && field.type !== 'file') {
    window.setTimeout(() => field.focus(), 180);
  }
}

function goToFormStep(step, focusName = '') {
  if (!formStepKeys.includes(step)) return;
  state.formStep = step;
  updateStepSurface();
  if (focusName) focusFormField(focusName);
}

function failStep(message, fieldName = '', step = state.formStep) {
  showToast(message);
  goToFormStep(fieldStepMap[fieldName] || step, fieldName);
  return false;
}

function validateStep(step = state.formStep) {
  const form = getFormData();
  if (step === 'user') {
    if (!form.customerName) return failStep('请填写用户名。', 'customerName', step);
    if (!form.customerPhone) return failStep('请填写联系电话。', 'customerPhone', step);
    if (!form.customerType) return failStep('请选择用户类型。', 'customerType', step);
    if (!form.customerAddress) return failStep('请填写详细地址。', 'customerAddress', step);
  }
  if (step === 'inspect') {
    if (!form.inspectorName) return failStep('请填写检查人。', 'inspectorName', step);
    if (!form.inspectionDate) return failStep('请选择检查日期。', 'inspectionDate', step);
    if (!form.deliveryPerson) return failStep('请填写配送员。', 'deliveryPerson', step);
  }
  if (step === 'photo') {
    if (!hasPhoto('storefront')) return failStep('请先拍摄门头照片。', 'storefront', step);
  }
  if (step === 'hazard') {
    const hazardCodes = selectedValues('hazardItems');
    const gasSourceSelected = selectedValues('gasSourceIssues');
    if (form.checkResult === RESULT_NOT_CLEARED && hazardCodes.length === 0 && gasSourceSelected.length === 0) {
      return failStep('隐患未清零时，请至少选择一个隐患项目或气源排查异常。', '', step);
    }
    const missingHazardPhoto = hazardCodes.find((code) => !hasPhoto(`hazardPhoto_${code}`));
    if (missingHazardPhoto) {
      const hazard = hazardItems.find((item) => item.code === missingHazardPhoto);
      return failStep(`请为“${hazard?.name || missingHazardPhoto}”拍摄隐患照片。`, `hazardPhoto_${missingHazardPhoto}`, step);
    }
    if (gasSourceSelected.length > 0 && !hasPhoto('cylinderQr') && !hasPhoto('cylinder')) {
      return failStep('气源排查异常时，请至少拍摄钢瓶二维码或钢瓶照片。', 'cylinderQr', step);
    }
  }
  return true;
}

function validateCurrentStep() {
  return validateStep(state.formStep);
}

function validateAllBeforeSubmit() {
  return formStepKeys
    .filter((step) => step !== 'review')
    .every((step) => validateStep(step));
}

function renderReviewSummary() {
  const container = $('#reviewSummary');
  if (!container) return;
  const form = getFormData();
  const hazardCodes = form.checkResult === RESULT_NOT_CLEARED ? selectedValues('hazardItems') : [];
  const gasSourceSelected = selectedValues('gasSourceIssues');
  const hazardNames = hazardCodes
    .map((code) => hazardItems.find((item) => item.code === code)?.name || code)
    .join('、');
  const photoTotal = Object.values(state.photos).reduce((total, items) => total + items.length, 0);
  const locationText = state.location ? `${state.location.lat}, ${state.location.lng}` : '未获取定位';

  container.innerHTML = [
    ['用户', `${form.customerName || '-'} / ${form.customerPhone || '-'}`],
    ['地址', form.customerAddress || '-'],
    ['检查', `${form.inspectionDate || '-'} / ${form.inspectorName || '-'} / ${form.deliveryPerson || '-'}`],
    ['结果', form.checkResult || '-'],
    ['隐患', form.checkResult === RESULT_NOT_CLEARED ? (hazardNames || '未选择') : '隐患已清零'],
    ['气源异常', gasSourceSelected.length ? gasSourceSelected.join('、') : '无'],
    ['照片', `共 ${photoTotal} 张，门头 ${hasPhoto('storefront') ? '已拍' : '未拍'}`],
    ['定位', locationText],
    ['备注', form.remark || '无']
  ].map(([label, value]) => `
    <div class="review-row">
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(value)}</strong>
    </div>
  `).join('');
}

function updateStepSurface() {
  const index = formStepIndex();
  if (singlePageForm) {
    document.querySelectorAll('.form-step').forEach((section) => {
      section.classList.remove('step-hidden');
    });
    const prev = $('#stepPrevBtn');
    const next = $('#stepNextBtn');
    const submit = $('#submitRecordBtn');
    if (prev) prev.classList.add('hidden');
    if (next) next.classList.add('hidden');
    if (submit) submit.classList.remove('hidden');
    renderAddressSurface();
    renderReviewSummary();
    return;
  }
  document.querySelectorAll('.form-step').forEach((section) => {
    section.classList.toggle('step-hidden', section.dataset.step !== state.formStep);
  });
  document.querySelectorAll('#formStepper [data-step-target]').forEach((button) => {
    const buttonIndex = formStepIndex(button.dataset.stepTarget);
    button.classList.toggle('active', button.dataset.stepTarget === state.formStep);
    button.classList.toggle('done', buttonIndex < index);
  });
  const status = $('#stepStatus');
  if (status) status.textContent = `${index + 1}/${formSteps.length} ${formStepLabel()}`;
  const prev = $('#stepPrevBtn');
  const next = $('#stepNextBtn');
  const submit = $('#submitRecordBtn');
  if (prev) prev.disabled = index === 0;
  if (next) next.classList.toggle('hidden', index === formSteps.length - 1);
  if (submit) submit.classList.toggle('hidden', index !== formSteps.length - 1);
  renderReviewSummary();
}

function setFormStep(step) {
  const targetIndex = formStepIndex(step);
  const currentIndex = formStepIndex();
  if (targetIndex > currentIndex) {
    for (let index = currentIndex; index < targetIndex; index += 1) {
      if (!validateStep(formStepKeys[index])) return;
    }
  }
  goToFormStep(step);
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function nextFormStep() {
  if (!validateCurrentStep()) return;
  const next = formSteps[formStepIndex() + 1];
  if (next) setFormStep(next.key);
}

function previousFormStep() {
  const previous = formSteps[formStepIndex() - 1];
  if (previous) setFormStep(previous.key);
}

function getOrCreatePreview(input) {
  let preview = document.querySelector(`[data-preview-for="${input.name}"]`);
  if (preview) return preview;

  preview = document.createElement('div');
  preview.className = 'photo-preview';
  preview.dataset.previewFor = input.name;
  input.closest('.photo-tile').after(preview);
  return preview;
}

function clearPhotoField(field, resetInput = true) {
  (state.photos[field] || []).forEach((item) => URL.revokeObjectURL(item.previewUrl));
  state.photos[field] = [];
  const input = photoInput(field);
  if (input && resetInput) input.value = '';
  if (input) renderPhotoPreview(field);
  renderReviewSummary();
}

function clearAllPhotos() {
  Object.keys(state.photos).forEach((field) => clearPhotoField(field, true));
}

function groupHazards() {
  return hazardItems.reduce((groups, item) => {
    groups[item.category] ||= [];
    groups[item.category].push(item);
    return groups;
  }, {});
}

function categoryIcon(category) {
  const icons = {
    可燃气体报警器: '警',
    软管: '管',
    用气环境: '境',
    减压阀: '阀',
    安全用气指南: '安'
  };
  return icons[category] || '隐';
}

function initOptions() {
  $('#customerType').innerHTML = [
    '<option value="">请选择用户类型</option>',
    ...customerTypes.map((item) => `<option value="${item}">${item}</option>`)
  ].join('');

  $('#gasSourceList').innerHTML = gasSourceIssues.map((item) => (
    `<label><input type="checkbox" name="gasSourceIssues" value="${item}"><span>${item}</span></label>`
  )).join('');

  $('#hazardCategoryList').innerHTML = Object.entries(groupHazards()).map(([category, items]) => `
    <section class="form-card hazard-category">
      <div class="card-heading">
        <span class="card-icon">${categoryIcon(category)}</span>
        <h3>${category}</h3>
      </div>
      ${items.map((item) => `
        <div class="hazard-item" data-hazard="${item.code}">
          <label>
            <input type="checkbox" name="hazardItems" value="${item.code}">
            <span>${item.name}</span>
          </label>
          <label class="photo-tile hazard-photo hidden" data-photo-label="${category}隐患照片">
            <input name="hazardPhoto_${item.code}" type="file" accept="image/*" capture="environment" multiple>
            <span>${category}隐患拍照</span>
            <em>拍照后显示水印预览</em>
          </label>
        </div>
      `).join('')}
    </section>
  `).join('');

  $('[name="inspectionDate"]').value = today();
}

async function api(path, options = {}) {
  const headers = options.headers || {};
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }

  const response = await fetch(`${apiBase}${path}`, { ...options, headers });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || result.ok === false) {
    throw new Error(result.message || '请求失败，请稍后重试。');
  }
  return result.data;
}

function roleLabel(role) {
  const labels = {
    hq_admin: '总部管理员',
    company_admin: '公司管理员',
    inspector: '检查员'
  };
  return labels[role] || role || '-';
}

function statusText(record) {
  if (record.status === 'pending') return '待整改';
  if (record.status === 'rectified') return '已整改';
  return '已完成';
}

function statusClass(record) {
  return record.status === 'pending' ? 'record-status pending' : 'record-status';
}

function photoLabel(photo) {
  const labels = {
    storefront: '门头照片',
    hazard: '隐患照片',
    cylinder_qr: '钢瓶二维码照片',
    cylinder: '钢瓶照片',
    rectification: '整改照片'
  };
  return labels[photo.photo_type] || '照片';
}

function photoUrl(photo) {
  const path = String(photo?.file_path || photo?.filePath || photo?.url || '').trim();
  if (!path) return '';
  if (/^(https?:)?\/\//i.test(path) || path.startsWith('data:')) return path;
  return path.startsWith('/') ? path : `/${path.replace(/^\/+/, '')}`;
}

function mobilePhotoBaseTitle(photo, record) {
  if (photo?.photo_type === 'hazard' && photo?.hazard_code) {
    const hazard = (record.hazards || []).find((item) => item.hazard_code === photo.hazard_code);
    if (hazard?.hazard_category) return `${hazard.hazard_category}隐患照片`;
  }
  return photoLabel(photo);
}

function setView(view) {
  state.view = view;
  $('#loginPanel').classList.toggle('hidden', view !== 'login');
  $('#menuPanel').classList.toggle('hidden', view !== 'menu');
  $('#recordsPanel').classList.toggle('hidden', view !== 'records');
  $('#inspectionForm').classList.toggle('hidden', view !== 'form');
  $('#accountPanel').classList.toggle('hidden', !state.user);
  $('#logoutBtn').classList.toggle('hidden', !state.user);
  $('#backMenuBtn').classList.toggle('hidden', !['form', 'records'].includes(view));
  $('#bottomNav').classList.toggle('hidden', !state.user);
  $('#pageTitle').textContent = view === 'form' ? '新增检查' : view === 'records' ? '记录查询' : '隐患排查';
  updateAccountSurface();
  updateBottomNav();
}

function updateAccountSurface() {
  if (!state.user) return;
  $('#accountName').textContent = `${state.user.companyName || '未绑定公司'} · ${state.user.name || ''} · ${roleLabel(state.user.role)}`;
}

function updateBottomNav() {
  document.querySelectorAll('#bottomNav [data-nav]').forEach((button) => {
    const active = ['form', 'records'].includes(state.view) ? state.view : 'menu';
    button.classList.toggle('active', button.dataset.nav === active);
  });
}

function showMenu() {
  setView('menu');
}

function showInspectionForm() {
  setView('form');
  if (state.user?.name && !$('[name="inspectorName"]').value) {
    $('[name="inspectorName"]').value = state.user.name;
  }
  if (!formStepKeys.includes(state.formStep)) {
    state.formStep = 'user';
  }
  updateStepSurface();
}

function mobileRecordFilters() {
  const form = new FormData($('#mobileRecordFilters'));
  return {
    keyword: form.get('keyword') || '',
    status: form.get('status') || '',
    dateFrom: form.get('dateFrom') || '',
    dateTo: form.get('dateTo') || ''
  };
}

async function showRecordsPanel() {
  setView('records');
  await loadMobileRecords();
}

async function loadMobileRecords(button = null, busyText = '刷新中...') {
  await runWithButton(button, busyText, async () => {
    const data = await api(`/records${buildQuery(mobileRecordFilters())}`);
    state.records = data.records || [];
    renderMobileRecords(state.records);
  });
}

function renderMobileRecords(records) {
  const list = $('#mobileRecordList');
  list.innerHTML = records.map((record) => `
    <article class="record-card">
      <button type="button" data-record-id="${record.id}">
        <span class="${statusClass(record)}">${statusText(record)}</span>
        <strong>${escapeHtml(record.customer_name || '未填写用户')}</strong>
        <em>${escapeHtml(record.inspection_date || '-')} · ${escapeHtml(record.inspector_name || '-')}</em>
        <span class="record-address">${escapeHtml(record.customer_address || '')}</span>
      </button>
    </article>
  `).join('') || '<div class="empty-card">暂无记录，调整筛选条件后再试。</div>';
}

async function openMobileRecord(id, button = null) {
  await runWithButton(button, '打开中...', async () => {
    const data = await api(`/records/${id}`);
    state.currentRecord = data.record;
    renderMobileRecordDetail(state.currentRecord);
    $('#mobileRecordDetailSheet').classList.remove('hidden');
    $('#mobileRecordDetailSheet').setAttribute('aria-hidden', 'false');
  });
}

function closeMobileRecordDetail() {
  $('#mobileRecordDetailSheet').classList.add('hidden');
  $('#mobileRecordDetailSheet').setAttribute('aria-hidden', 'true');
  $('#mobileRectifyForm').reset();
  state.currentRecord = null;
}

function renderMobileRecordDetail(record) {
  $('#mobileRecordCompany').textContent = record.company_name || '';
  $('#mobileRecordTitle').textContent = `${record.customer_name || '未填写用户'} · ${statusText(record)}`;
  $('#mobileRecordDetail').innerHTML = [
    ['电话', record.customer_phone],
    ['地址', record.customer_address],
    ['检查日期', record.inspection_date],
    ['检查人', record.inspector_name],
    ['配送员', record.delivery_person],
    ['结果', record.check_result],
    ['整改日期', record.rectification_date],
    ['整改情况', record.rectification_note]
  ].map(([label, value]) => `
    <div>
      <span>${escapeHtml(label)}</span>
      <strong>${escapeHtml(value || '-')}</strong>
    </div>
  `).join('');

  $('#mobileHazardDetail').innerHTML = (record.hazards || [])
    .map((item) => `<span>${escapeHtml(item.hazard_category)}：${escapeHtml(item.hazard_name)}</span>`)
    .join('') || '<p class="muted">无未清零隐患</p>';

  $('#mobileGasDetail').innerHTML = (record.gasSourceIssues || [])
    .map((item) => `<span>${escapeHtml(item.issue_text)}</span>`)
    .join('') || '<p class="muted">无气源排查异常</p>';

  const photos = record.photos || [];
  const titleTotals = photos.reduce((totals, photo) => {
    const title = mobilePhotoBaseTitle(photo, record);
    totals[title] = (totals[title] || 0) + 1;
    return totals;
  }, {});
  const titleSeen = {};
  $('#mobilePhotoDetail').innerHTML = photos
    .map((photo) => {
      const url = photoUrl(photo);
      if (!url) return '';
      const baseTitle = mobilePhotoBaseTitle(photo, record);
      titleSeen[baseTitle] = (titleSeen[baseTitle] || 0) + 1;
      const title = titleTotals[baseTitle] > 1 ? `${baseTitle} ${titleSeen[baseTitle]}` : baseTitle;
      return `
        <a class="mobile-photo-thumb" href="${escapeHtml(url)}" target="_blank" rel="noopener">
          <img src="${escapeHtml(url)}" alt="${escapeHtml(title)}" loading="lazy">
          <span>${escapeHtml(title)}</span>
        </a>
      `;
    })
    .join('') || '<p class="muted">无照片</p>';

  $('#mobileRectifyForm').classList.toggle('hidden', record.status !== 'pending');
  $('#mobileRectifyForm [name="rectificationDate"]').value = today();
}

async function login() {
  const data = await api('/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      phone: $('#loginPhone').value.trim(),
      password: $('#loginPassword').value
    })
  });
  state.token = data.token;
  state.user = data.user;
  localStorage.setItem(tokenKey, state.token);
  showMenu();
  showToast('登录成功');
}

async function loadSession() {
  const data = await api('/auth/me');
  state.user = data.user;
}

async function restoreLoginState() {
  if (!state.token) {
    setView('login');
    return;
  }
  try {
    await loadSession();
    showMenu();
  } catch {
    logout(false);
  }
}

function logout(confirmFirst = true) {
  if (confirmFirst && !window.confirm('确定退出当前账号并切换用户吗？本机草稿会一并清除。')) {
    return;
  }
  state.token = '';
  state.user = null;
  state.location = null;
  state.records = [];
  state.currentRecord = null;
  localStorage.removeItem(tokenKey);
  localStorage.removeItem(draftKey);
  clearForm(false);
  setView('login');
  if (confirmFirst) showToast('已退出，可以切换账号登录。');
}

function locate(auto = false) {
  if (!navigator.geolocation) {
    setLocationSurface('failed', '定位不可用', '当前浏览器不支持定位，请手工填写地址。水印会保留手填地址和时间。');
    renderReviewSummary();
    if (!auto) showToast('当前浏览器不支持定位，请手工填写地址。');
    return Promise.resolve(false);
  }

  const localHost = ['localhost', '127.0.0.1'].includes(window.location.hostname);
  if (!window.isSecureContext && !localHost) {
    setLocationSurface('failed', '定位受限', '当前使用 HTTP/IP 访问，手机浏览器可能禁止定位。可继续拍照，水印会写入手填地址和“定位未获取”。');
    renderReviewSummary();
    if (!auto) showToast('HTTP/IP 访问可能被浏览器禁止定位，正式使用建议配置 HTTPS。');
    return Promise.resolve(false);
  }

  state.locating = true;
  setLocationSurface('pending', '正在定位', '请在浏览器弹窗中允许定位，定位成功后会写入照片水印。');
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition((position) => {
      state.location = {
        lat: Number(position.coords.latitude.toFixed(7)),
        lng: Number(position.coords.longitude.toFixed(7)),
        accuracy: Math.round(position.coords.accuracy || 0),
        address: currentAddress()
      };
      state.locating = false;
      renderAddressSurface();
      renderReviewSummary();
      if (!auto) showToast('定位成功。');
      resolve(true);
    }, (error) => {
      state.locating = false;
      const message = error.code === 1
        ? '定位权限被拒绝，请在浏览器设置中允许定位，或继续使用手填地址。'
        : '定位失败，请检查网络、权限或信号，水印会保留手填地址。';
      setLocationSurface('failed', '定位失败', message);
      renderReviewSummary();
      if (!auto) showToast(message);
      resolve(false);
    }, {
      enableHighAccuracy: true,
      timeout: 12000,
      maximumAge: 60000
    });
  });
}

function selectedValues(name) {
  return [...document.querySelectorAll(`[name="${name}"]:checked`)].map((item) => item.value);
}

function loadImage(file) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    const url = URL.createObjectURL(file);
    img.onload = () => {
      URL.revokeObjectURL(url);
      resolve(img);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('照片读取失败，请重新拍摄。'));
    };
    img.src = url;
  });
}

function wrapText(ctx, text, maxWidth) {
  const chars = String(text || '').split('');
  const lines = [];
  let line = '';
  chars.forEach((char) => {
    const next = line + char;
    if (ctx.measureText(next).width > maxWidth && line) {
      lines.push(line);
      line = char;
      return;
    }
    line = next;
  });
  if (line) lines.push(line);
  return lines.slice(0, 2);
}

function pad2(value) {
  return String(value).padStart(2, '0');
}

function formatCapturedAt(date) {
  return {
    date: `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`,
    time: `${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`
  };
}

async function watermarkImage(file, form, capturedAt = new Date()) {
  if (!file) return null;
  const image = await loadImage(file);
  const canvas = $('#watermarkCanvas');
  const maxWidth = 1600;
  const scale = Math.min(1, maxWidth / image.width);
  canvas.width = Math.round(image.width * scale);
  canvas.height = Math.round(image.height * scale);

  const ctx = canvas.getContext('2d');
  ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
  const fontSize = Math.max(22, Math.round(canvas.width / 42));
  ctx.font = `${fontSize}px sans-serif`;
  const captured = formatCapturedAt(capturedAt);
  const locationLine = state.location
    ? `定位坐标：纬度${state.location.lat}，经度${state.location.lng}，精度约${state.location.accuracy || '-'}米`
    : '定位坐标：未获取';
  const lines = [
    `日期：${captured.date}  时间：${captured.time}`,
    `用户：${form.customerName || '-'}  电话：${form.customerPhone || '-'}`,
    ...wrapText(ctx, `用户地址：${form.customerAddress || '未填写'}`, canvas.width - 36),
    `检查人：${form.inspectorName || '-'} 配送员：${form.deliveryPerson || '-'}`,
    locationLine
  ];
  const lineHeight = Math.round(fontSize * 1.38);
  const panelHeight = Math.min(canvas.height * 0.46, lines.length * lineHeight + 30);
  ctx.fillStyle = 'rgba(0,0,0,.58)';
  ctx.fillRect(0, canvas.height - panelHeight, canvas.width, panelHeight);
  ctx.fillStyle = '#fff';
  lines.forEach((line, index) => ctx.fillText(line, 18, canvas.height - panelHeight + 24 + index * lineHeight));

  return new Promise((resolve) => {
    canvas.toBlob((blob) => resolve(blob), 'image/jpeg', 0.86);
  });
}

async function uploadPhotoBlob(photo) {
  const fd = new FormData();
  fd.append('file', photo.blob, photo.name);
  const data = await api('/files/upload', { method: 'POST', body: fd });
  return data.filePath;
}

async function uploadStoredPhotos(field) {
  const uploaded = [];
  for (const photo of state.photos[field] || []) {
    uploaded.push(await uploadPhotoBlob(photo));
  }
  return uploaded.filter(Boolean);
}

async function uploadFiles(fileList) {
  const uploaded = [];
  for (const file of [...(fileList || [])]) {
    if (!file || !file.size) continue;
    const fd = new FormData();
    fd.append('file', file, file.name);
    const data = await api('/files/upload', { method: 'POST', body: fd });
    uploaded.push(data.filePath);
  }
  return uploaded;
}

function validatePhotoContext(field = '') {
  const form = getFormData();
  const required = [
    ['customerName', form.customerName, '用户名'],
    ['customerPhone', form.customerPhone, '电话'],
    ['customerType', form.customerType, '用户类型'],
    ['customerAddress', form.customerAddress, '用户地址'],
    ['inspectorName', form.inspectorName, '检查人'],
    ['inspectionDate', form.inspectionDate, '检查日期'],
    ['deliveryPerson', form.deliveryPerson, '配送员']
  ];
  const missing = required.find(([, value]) => !value);
  if (missing) {
    focusFormField(missing[0]);
    return field === 'storefront'
      ? `请先填写${missing[2]}，再拍摄门头照片生成完整水印。`
      : `请先填写${missing[2]}，再拍照生成完整水印。`;
  }
  return '';
}

async function buildPhotoItem(file, field) {
  const form = getFormData();
  const capturedAt = new Date();
  const blob = await watermarkImage(file, form, capturedAt);
  if (!blob) throw new Error('照片水印生成失败，请重新拍摄。');

  return {
    id: uniqueId(),
    field,
    blob,
    previewUrl: URL.createObjectURL(blob),
    name: `${field}-${Date.now()}-${Math.random().toString(16).slice(2)}.jpg`,
    capturedAt: capturedAt.toLocaleString(),
    location: state.location ? { ...state.location } : null
  };
}

function renderPhotoPreview(field) {
  const input = photoInput(field);
  if (!input) return;
  const preview = getOrCreatePreview(input);
  const items = state.photos[field] || [];
  preview.classList.toggle('hidden', items.length === 0);
  preview.innerHTML = items.map((item) => `
    <article class="photo-card">
      <img src="${item.previewUrl}" alt="已拍照片预览">
      <div>
        <strong>已加水印</strong>
        <span>${item.location ? `定位：${item.location.lat}, ${item.location.lng}` : '定位：未获取'}</span>
        <span>${item.capturedAt}</span>
      </div>
      <button type="button" data-photo-delete="${item.id}" data-photo-field="${field}">删除</button>
    </article>
  `).join('');
}

async function handlePhotoInput(event) {
  const input = event.target;
  const files = [...(input.files || [])];
  if (files.length === 0) return;

  const contextError = validatePhotoContext(input.name);
  if (contextError) {
    input.value = '';
    showToast(contextError);
    return;
  }

  const tile = input.closest('.photo-tile');
  tile?.classList.add('processing');
  try {
    if (input.name === 'storefront' && !state.location) {
      await locate(true);
    }
    if (!input.multiple) {
      clearPhotoField(input.name, false);
    }
    for (const file of files) {
      photoItems(input.name).push(await buildPhotoItem(file, input.name));
    }
    renderPhotoPreview(input.name);
    renderReviewSummary();
    showToast(state.location ? '照片已生成定位水印。' : '照片已生成水印；当前未获取经纬度。');
  } catch (error) {
    showToast(error.message);
  } finally {
    tile?.classList.remove('processing');
  }
}

function deletePhoto(field, id) {
  const items = state.photos[field] || [];
  const target = items.find((item) => item.id === id);
  if (target) URL.revokeObjectURL(target.previewUrl);
  state.photos[field] = items.filter((item) => item.id !== id);
  if (state.photos[field].length === 0) {
    const input = photoInput(field);
    if (input) input.value = '';
  }
  renderPhotoPreview(field);
  renderReviewSummary();
}

function getFormData() {
  const fd = new FormData($('#inspectionForm'));
  return {
    customerName: String(fd.get('customerName') || '').trim(),
    customerPhone: String(fd.get('customerPhone') || '').trim(),
    customerType: String(fd.get('customerType') || '').trim(),
    customerAddress: String(fd.get('customerAddress') || '').trim(),
    inspectorName: String(fd.get('inspectorName') || '').trim(),
    deliveryPerson: String(fd.get('deliveryPerson') || '').trim(),
    inspectionDate: String(fd.get('inspectionDate') || today()),
    checkResult: String(fd.get('checkResult') || RESULT_CLEARED),
    remark: String(fd.get('remark') || '').trim()
  };
}

async function submitInspection(event) {
  event.preventDefault();
  if (!validateAllBeforeSubmit()) return;
  const form = getFormData();
  const hazardCodes = form.checkResult === RESULT_NOT_CLEARED ? selectedValues('hazardItems') : [];
  const gasSourceSelected = selectedValues('gasSourceIssues');
  if (form.checkResult === RESULT_NOT_CLEARED && hazardCodes.length === 0 && gasSourceSelected.length === 0) {
    showToast('隐患未清零时必须选择隐患项目或气源排查异常。');
    return;
  }
  if (!hasPhoto('storefront')) {
    showToast('请先拍摄门头照片。');
    return;
  }
  const missingHazardPhoto = hazardCodes.find((code) => !hasPhoto(`hazardPhoto_${code}`));
  if (missingHazardPhoto) {
    const hazard = hazardItems.find((item) => item.code === missingHazardPhoto);
    showToast(`请为“${hazard?.name || missingHazardPhoto}”拍摄隐患照片。`);
    return;
  }
  if (gasSourceSelected.length > 0 && !hasPhoto('cylinderQr') && !hasPhoto('cylinder')) {
    showToast('气源排查异常时，请至少拍摄钢瓶二维码或钢瓶照片。');
    return;
  }

  const submitButton = event.submitter || $('#submitRecordBtn');
  submitButton.disabled = true;
  submitButton.textContent = '正在保存...';
  try {
    const photos = {
      storefront: await uploadStoredPhotos('storefront'),
      hazard: {},
      cylinder_qr: await uploadStoredPhotos('cylinderQr'),
      cylinder: await uploadStoredPhotos('cylinder')
    };

    for (const code of hazardCodes) {
      photos.hazard[code] = await uploadStoredPhotos(`hazardPhoto_${code}`);
    }

    await api('/records', {
      method: 'POST',
      body: JSON.stringify({
        ...form,
        hazardItems: hazardCodes,
        gasSourceIssues: gasSourceSelected,
        photos,
        location: state.location || { address: form.customerAddress }
      })
    });
    localStorage.removeItem(draftKey);
    clearForm(false);
    showToast('保存成功，可以继续下一户。');
    showMenu();
  } catch (error) {
    showToast(error.message);
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = '保存核查记录';
  }
}

async function submitMobileRectification(event) {
  event.preventDefault();
  if (!state.currentRecord) return;
  const form = new FormData(event.currentTarget);
  const submitButton = event.submitter || event.currentTarget.querySelector('button[type="submit"]');
  await runWithButton(submitButton, '提交中...', async () => {
    const photos = await uploadFiles(form.getAll('rectificationPhotos'));
    await api(`/records/${state.currentRecord.id}/rectify`, {
      method: 'POST',
      body: JSON.stringify({
        rectificationDate: form.get('rectificationDate'),
        rectificationNote: String(form.get('rectificationNote') || '').trim(),
        photos
      })
    });
    showToast('整改已提交');
    closeMobileRecordDetail();
    await loadMobileRecords();
  });
}

function saveDraft() {
  localStorage.setItem(draftKey, JSON.stringify({
    form: getFormData(),
    hazardItems: selectedValues('hazardItems'),
    gasSourceIssues: selectedValues('gasSourceIssues'),
    location: state.location
  }));
  showToast('草稿已保存在本机。');
}

function restoreDraft() {
  const raw = localStorage.getItem(draftKey);
  if (!raw) return false;
  try {
    const draft = JSON.parse(raw);
    Object.entries(draft.form || {}).forEach(([key, value]) => {
      if (key === 'checkResult') {
        const radio = document.querySelector(`[name="checkResult"][value="${value}"]`);
        if (radio) radio.checked = true;
        return;
      }
      const field = document.querySelector(`[name="${key}"]`);
      if (field) field.value = value;
    });
    document.querySelectorAll('input[type="checkbox"]').forEach((checkbox) => {
      checkbox.checked = [...(draft.hazardItems || []), ...(draft.gasSourceIssues || [])].includes(checkbox.value);
    });
    state.location = draft.location || null;
    if (state.location) {
      renderAddressSurface();
    }
    updateHazardVisibility();
    state.formStep = 'user';
    showInspectionForm();
    showToast('已恢复本机草稿，照片需重新拍摄。');
    return true;
  } catch {
    localStorage.removeItem(draftKey);
    return false;
  }
}

function clearForm(showMessage = true) {
  $('#inspectionForm').reset();
  $('[name="inspectionDate"]').value = today();
  state.location = null;
  clearAllPhotos();
  setLocationSurface('pending', '用户地址', '填写用户地址后用于照片水印；拍摄门头照片时自动尝试定位。');
  document.querySelectorAll('.hazard-item').forEach((item) => item.classList.remove('selected'));
  document.querySelectorAll('.hazard-photo').forEach((item) => item.classList.add('hidden'));
  updateHazardVisibility();
  state.formStep = 'user';
  updateStepSurface();
  if (state.user?.name) $('[name="inspectorName"]').value = state.user.name;
  if (showMessage) showToast('表单已清空。');
}

function updateHazardVisibility() {
  const result = document.querySelector('[name="checkResult"]:checked')?.value || RESULT_CLEARED;
  const isNotCleared = result === RESULT_NOT_CLEARED;
  $('#hazardPanel').classList.toggle('hidden', !isNotCleared);
  $('#clearedHintPanel')?.classList.toggle('hidden', isNotCleared);
  document.querySelectorAll('.hazard-item').forEach((item) => {
    const checkbox = item.querySelector('[name="hazardItems"]');
    if (!isNotCleared && checkbox) checkbox.checked = false;
    const photo = item.querySelector('.hazard-photo');
    const selected = Boolean(checkbox?.checked);
    item.classList.toggle('selected', selected);
    photo?.classList.toggle('hidden', !selected);
    if (!selected && checkbox?.value) {
      clearPhotoField(`hazardPhoto_${checkbox.value}`, true);
    }
  });
  renderReviewSummary();
}

function bindEvents() {
  $('#loginBtn').addEventListener('click', () => login().catch((error) => showToast(error.message)));
  $('#logoutBtn').addEventListener('click', () => logout(true));
  $('#backMenuBtn').addEventListener('click', showMenu);
  $('#terminalFeatureBtn').addEventListener('click', showInspectionForm);
  $('#recordsFeatureBtn').addEventListener('click', () => showRecordsPanel().catch((error) => showToast(error.message)));
  $('#dataFeatureBtn').addEventListener('click', () => showToast('数据备份和完整导出请使用电脑端后台。'));
  $('#versionFeatureBtn').addEventListener('click', () => showToast('当前为 H5 测试版，按账号权限使用。'));
  $('#draftFeatureBtn').addEventListener('click', () => {
    if (!restoreDraft()) showToast('本机暂无草稿。');
  });
  $('#recordsRefreshBtn').addEventListener('click', (event) => loadMobileRecords(event.currentTarget).then(() => showToast('记录已刷新')).catch((error) => showToast(error.message)));
  $('#mobileRecordFilters').addEventListener('submit', (event) => {
    event.preventDefault();
    loadMobileRecords(event.submitter, '查询中...').then(() => showToast('查询完成')).catch((error) => showToast(error.message));
  });
  $('#mobileRecordList').addEventListener('click', (event) => {
    const button = event.target.closest('[data-record-id]');
    if (!button) return;
    openMobileRecord(button.dataset.recordId, button).catch((error) => showToast(error.message));
  });
  $('#mobileRecordCloseBtn').addEventListener('click', closeMobileRecordDetail);
  $('#mobileRecordDetailSheet').addEventListener('click', (event) => {
    if (event.target.dataset.mobileDetailClose) closeMobileRecordDetail();
  });
  $('#mobileRectifyForm').addEventListener('submit', (event) => submitMobileRectification(event).catch((error) => showToast(error.message)));
  $('#locateBtn').addEventListener('click', () => locate(false));
  $('#formStepper').addEventListener('click', (event) => {
    const button = event.target.closest('[data-step-target]');
    if (!button) return;
    setFormStep(button.dataset.stepTarget);
  });
  $('#stepPrevBtn').addEventListener('click', previousFormStep);
  $('#stepNextBtn').addEventListener('click', nextFormStep);
  $('#inspectionForm').addEventListener('submit', submitInspection);
  $('#inspectionForm').addEventListener('input', () => {
    renderAddressSurface();
    renderReviewSummary();
  });
  $('#inspectionForm').addEventListener('change', (event) => {
    if (event.target.matches('input[type="file"][accept^="image/"]')) {
      handlePhotoInput(event).catch((error) => showToast(error.message));
    }
    renderReviewSummary();
  });
  $('#inspectionForm').addEventListener('click', (event) => {
    const tile = event.target.closest('.photo-tile');
    const fileInput = tile?.querySelector('input[type="file"]');
    if (fileInput?.name === 'storefront') {
      const contextError = validatePhotoContext('storefront');
      if (contextError) {
        event.preventDefault();
        event.stopPropagation();
        showToast(contextError);
        return;
      }
    }
    const button = event.target.closest('[data-photo-delete]');
    if (!button) return;
    deletePhoto(button.dataset.photoField, button.dataset.photoDelete);
  });
  $('#saveDraftBtn').addEventListener('click', saveDraft);
  $('#clearFormBtn').addEventListener('click', () => clearForm(true));
  document.querySelectorAll('[name="checkResult"]').forEach((radio) => {
    radio.addEventListener('change', updateHazardVisibility);
  });
  $('#hazardCategoryList').addEventListener('change', (event) => {
    if (event.target.matches('[name="hazardItems"]')) updateHazardVisibility();
  });
  $('#bottomNav').addEventListener('click', (event) => {
    const button = event.target.closest('[data-nav]');
    if (!button) return;
    if (button.dataset.nav === 'menu') showMenu();
    if (button.dataset.nav === 'form') showInspectionForm();
    if (button.dataset.nav === 'records') showRecordsPanel().catch((error) => showToast(error.message));
    if (button.dataset.nav === 'data') showToast('数据统计、导出和备份请使用电脑端后台。');
    if (button.dataset.nav === 'account') showToast($('#accountName').textContent);
  });
}

initOptions();
bindEvents();
restoreLoginState();
