import {
  RESULT_CLEARED,
  RESULT_NOT_CLEARED,
  customerTypes,
  gasSourceIssues,
  hazardItems
} from '../../shared/catalog.js';

const apiBase = localStorage.getItem('apiBase') || '/api';
const tokenKey = 'xinyuan_h5_token';
const cloudDraftKey = 'xinyuan_h5_cloud_draft_uuid';
const singlePageForm = false;
const formSteps = [
  { key: 'user', label: '用户与现场' },
  { key: 'hazard', label: '核查与照片' },
  { key: 'review', label: '确认提交' }
];
const formStepKeys = formSteps.map((step) => step.key);
const fieldStepMap = {
  customerName: 'user',
  customerPhone: 'user',
  customerType: 'user',
  customerAddress: 'user',
  inspectorName: 'user',
  inspectionDate: 'user',
  deliveryPerson: 'user',
  storefront: 'user',
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
  photos: {},
  clientUuid: localStorage.getItem(cloudDraftKey) || '',
  cloudDraftSaved: false
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
      '已获取用户地址',
      `${address || '用户地址坐标已记录'}；定位坐标：${state.location.lat}, ${state.location.lng}`
    );
    return;
  }
  setLocationSurface('pending', '等待获取用户地址', '拍摄门头照片后自动记录定位地址。');
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
    if (!form.inspectorName) return failStep('请填写检查人。', 'inspectorName', step);
    if (!form.inspectionDate) return failStep('请选择检查日期。', 'inspectionDate', step);
    if (!form.deliveryPerson) return failStep('请填写配送员。', 'deliveryPerson', step);
    if (!hasPhoto('storefront')) return failStep('请先拍摄门头照片。', 'storefront', step);
    if (!form.customerAddress) return failStep('未获取到用户地址，请确认定位权限后重新拍摄门头照片。', 'storefront', step);
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
  const clear = $('#clearFormBtn');
  if (clear) clear.classList.toggle('hidden', index !== 0);
  if (prev) {
    prev.classList.toggle('hidden', index === 0);
    prev.textContent = index === formSteps.length - 1 ? '返回修改' : '上一步';
  }
  if (next) next.classList.toggle('hidden', index === formSteps.length - 1);
  if (submit) submit.classList.toggle('hidden', index !== formSteps.length - 1);
  $('#cloudDraftBanner')?.classList.toggle('hidden', !(state.cloudDraftSaved && state.formStep === 'hazard'));
  renderAddressSurface();
  renderReviewSummary();
}

async function setFormStep(step) {
  const targetIndex = formStepIndex(step);
  const currentIndex = formStepIndex();
  if (targetIndex > currentIndex) {
    for (let index = currentIndex; index < targetIndex; index += 1) {
      if (!validateStep(formStepKeys[index])) return;
    }
    if (currentIndex === 0) {
      await saveCloudDraft();
    }
  }
  goToFormStep(step);
  window.scrollTo({ top: 0, behavior: 'instant' });
}

async function nextFormStep() {
  if (!validateCurrentStep()) return;
  const next = formSteps[formStepIndex() + 1];
  if (next) await setFormStep(next.key);
}

function previousFormStep() {
  const previous = formSteps[formStepIndex() - 1];
  if (previous) {
    goToFormStep(previous.key);
    window.scrollTo({ top: 0, behavior: 'instant' });
  }
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
  $('#profilePanel').classList.toggle('hidden', view !== 'profile');
  $('#inspectionForm').classList.toggle('hidden', view !== 'form');
  $('#bottomNav').classList.toggle('hidden', !state.user);
  document.body.classList.remove('keyboard-open');
  updateAccountSurface();
  updateBottomNav();
}

function updateAccountSurface() {
  if (!state.user) return;
  const name = state.user.name || '检查员';
  const company = state.user.companyName || '未绑定公司';
  const initial = name.trim().slice(0, 1) || '鑫';
  const hour = new Date().getHours();
  const greeting = hour < 11 ? '上午好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好';
  const dateText = new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(new Date()).replaceAll('/', '-');

  $('#homeDate').textContent = dateText;
  $('#homeGreeting').textContent = `${greeting}，${name}`;
  $('#homeAvatar').textContent = initial;
  $('#profileAvatar').textContent = initial;
  $('#profileName').textContent = name;
  $('#profileCompany').textContent = company;
  $('#profilePhone').textContent = state.user.phone || '-';
  $('#profileRole').textContent = roleLabel(state.user.role);
  $('#profileScope').textContent = state.user.role === 'inspector' ? '仅查看本人录入' : '按账号权限查看';
}

function updateBottomNav() {
  document.querySelectorAll('#bottomNav [data-nav]').forEach((button) => {
    const active = ['form', 'records', 'profile'].includes(state.view) ? state.view : 'menu';
    button.classList.toggle('active', button.dataset.nav === active);
  });
}

function showMenu() {
  setView('menu');
  loadHomeRecords().catch(() => {
    setCloudStatus(false, '云端连接异常 · 点击重试');
  });
}

function showProfile() {
  setView('profile');
}

function showInspectionForm() {
  setView('form');
  if (state.user?.name) {
    $('[name="inspectorName"]').value = state.user.name;
  }
  if (!formStepKeys.includes(state.formStep)) {
    state.formStep = 'user';
  }
  updateStepSurface();
  window.scrollTo({ top: 0, behavior: 'instant' });
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

function setCloudStatus(connected, text) {
  $('#cloudStatusCard').classList.toggle('offline', !connected);
  $('#cloudStatusText').textContent = text;
}

function renderHomeRecords(records) {
  const todayKey = today();
  $('#homeTodayCount').textContent = records.filter((record) => record.inspection_date === todayKey).length;
  $('#homePendingCount').textContent = records.filter((record) => record.status === 'pending').length;
  $('#homeRecordCount').textContent = records.length;

  const recent = records.slice(0, 3);
  $('#homeRecentList').innerHTML = recent.map((record) => `
    <article class="recent-item">
      <button type="button" data-home-record-id="${record.id}">
        <strong>${escapeHtml(record.customer_name || '未填写用户')}</strong>
        <span class="${statusClass(record)}">${statusText(record)}</span>
        <em>${escapeHtml(record.inspection_date || '-')} · ${escapeHtml(record.inspector_name || '-')}</em>
        <span class="record-address">${escapeHtml(record.customer_address || '')}</span>
      </button>
    </article>
  `).join('') || '<div class="empty-state">还没有检查记录，点击“新增检查”开始登记。</div>';
}

async function loadHomeRecords(button = null) {
  await runWithButton(button, '刷新中...', async () => {
    setCloudStatus(true, '正在同步云端数据');
    const data = await api('/records');
    state.records = data.records || [];
    renderHomeRecords(state.records);
    setCloudStatus(true, '云端连接正常 · 数据已同步');
  });
}

async function showRecordsPanel() {
  setView('records');
  await loadMobileRecords();
}

async function loadMobileRecords(button = null, busyText = '刷新中...') {
  await runWithButton(button, busyText, async () => {
    const data = await api('/records');
    state.records = data.records || [];
    const filters = mobileRecordFilters();
    const keyword = String(filters.keyword || '').trim().toLowerCase();
    const filtered = state.records.filter((record) => {
      const matchesKeyword = !keyword || [
        record.customer_name,
        record.customer_phone,
        record.customer_address,
        record.inspector_name
      ].some((value) => String(value || '').toLowerCase().includes(keyword));
      const matchesStatus = filters.status === 'pending'
        ? record.status === 'pending'
        : filters.status === 'done'
          ? record.status !== 'pending'
          : true;
      return matchesKeyword && matchesStatus;
    });
    $('#allStatusCount').textContent = state.records.length;
    $('#pendingStatusCount').textContent = state.records.filter((record) => record.status === 'pending').length;
    $('#doneStatusCount').textContent = state.records.filter((record) => record.status !== 'pending').length;
    renderMobileRecords(filtered);
  });
}

function renderMobileRecords(records) {
  const list = $('#mobileRecordList');
  list.innerHTML = records.map((record) => `
    <article class="record-card">
      <button type="button" data-record-id="${record.id}">
        <strong>${escapeHtml(record.customer_name || '未填写用户')}</strong>
        <span class="${statusClass(record)}">${statusText(record)}</span>
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
    ['用户类型', record.customer_type],
    ['地址', record.customer_address],
    ['检查日期', record.inspection_date],
    ['检查人', record.inspector_name],
    ['配送员', record.delivery_person],
    ['结果', record.check_result],
    ['备注', record.remark],
    ['定位地址', record.location_address],
    ['定位坐标', record.location_lat && record.location_lng ? `${record.location_lat}, ${record.location_lng}` : '未获取'],
    ['整改日期', record.rectification_date],
    ['整改情况', record.rectification_note],
    ['整改人', record.rectification_person]
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

  const photoGroups = (record.photos || []).reduce((groups, photo) => {
    const url = photoUrl(photo);
    if (!url) return groups;
    const title = mobilePhotoBaseTitle(photo, record);
    groups[title] ||= [];
    groups[title].push({ url, title });
    return groups;
  }, {});
  $('#mobilePhotoDetail').innerHTML = Object.entries(photoGroups).map(([title, photos]) => `
    <section class="mobile-photo-group">
      <h4>${escapeHtml(title)} · ${photos.length}张</h4>
      ${photos.map((photo, index) => {
        const numberedTitle = photos.length > 1 ? `${title} ${index + 1}` : title;
        return `
          <button class="mobile-photo-thumb" type="button"
            data-photo-url="${escapeHtml(photo.url)}"
            data-photo-title="${escapeHtml(numberedTitle)}">
            <img src="${escapeHtml(photo.url)}" alt="${escapeHtml(numberedTitle)}" loading="lazy">
            <span>${escapeHtml(numberedTitle)}</span>
          </button>
        `;
      }).join('')}
    </section>
  `).join('') || '<p class="muted">无照片</p>';

  $('#mobileRectifyForm').classList.toggle('hidden', record.status !== 'pending');
  $('#mobileRectifyForm [name="rectificationDate"]').value = today();
}

function openPhotoPreview(url, title) {
  if (!url) return;
  $('#photoPreviewImage').src = url;
  $('#photoPreviewTitle').textContent = title || '记录照片';
  $('#photoPreview').classList.remove('hidden');
  $('#photoPreview').setAttribute('aria-hidden', 'false');
}

function closePhotoPreview() {
  $('#photoPreview').classList.add('hidden');
  $('#photoPreview').setAttribute('aria-hidden', 'true');
  $('#photoPreviewImage').removeAttribute('src');
}

async function login() {
  const data = await api('/auth/login', {
    method: 'POST',
    body: JSON.stringify({
      phone: $('#loginPhone').value.trim(),
      password: $('#loginPassword').value,
      clientType: 'app'
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
  if (confirmFirst && !window.confirm('确定退出当前账号吗？')) {
    return;
  }
  state.token = '';
  state.user = null;
  state.location = null;
  state.records = [];
  state.currentRecord = null;
  state.clientUuid = '';
  state.cloudDraftSaved = false;
  localStorage.removeItem(tokenKey);
  localStorage.removeItem(cloudDraftKey);
  clearForm(false);
  setView('login');
  if (confirmFirst) showToast('已退出，可以切换账号登录。');
}

function distinctAddressParts(parts) {
  return parts
    .map((part) => String(part || '').trim())
    .filter(Boolean)
    .filter((part, index, values) => {
      if (['中国', '中华人民共和国', '中国大陆'].includes(part)) return false;
      return values.findIndex((candidate) => candidate === part) === index;
    })
    .reduce((result, part) => {
      if (!result.some((existing) => existing.includes(part) || part === existing)) {
        result.push(part);
      }
      return result;
    }, []);
}

async function reverseGeocode(latitude, longitude) {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 8000);
  const url = new URL('https://api.bigdatacloud.net/data/reverse-geocode-client');
  url.searchParams.set('latitude', String(latitude));
  url.searchParams.set('longitude', String(longitude));
  url.searchParams.set('localityLanguage', 'zh');

  try {
    const response = await fetch(url, {
      signal: controller.signal,
      referrerPolicy: 'strict-origin-when-cross-origin'
    });
    if (!response.ok) throw new Error('地址解析服务暂时不可用。');
    const data = await response.json();
    const administrative = (data.localityInfo?.administrative || [])
      .filter((item) => Number(item.adminLevel || 0) > 2)
      .map((item) => item.name);
    const fallback = [
      data.principalSubdivision,
      data.city,
      data.locality,
      data.postcode
    ];
    return distinctAddressParts(administrative.length ? administrative : fallback).join('');
  } finally {
    window.clearTimeout(timeout);
  }
}

function openPasswordDialog() {
  $('#passwordForm').reset();
  $('#passwordDialog').classList.remove('hidden');
  $('#passwordDialog').setAttribute('aria-hidden', 'false');
}

function closePasswordDialog() {
  $('#passwordDialog').classList.add('hidden');
  $('#passwordDialog').setAttribute('aria-hidden', 'true');
  $('#passwordForm').reset();
}

async function changePassword(event) {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const currentPassword = String(form.get('currentPassword') || '');
  const newPassword = String(form.get('newPassword') || '');
  const confirmPassword = String(form.get('confirmPassword') || '');
  if (newPassword.length < 8) {
    showToast('新密码至少 8 位。');
    return;
  }
  if (newPassword !== confirmPassword) {
    showToast('两次输入的新密码不一致。');
    return;
  }
  const button = event.submitter;
  await runWithButton(button, '修改中...', async () => {
    await api('/auth/change-password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword, newPassword })
    });
  });
  closePasswordDialog();
  logout(false);
  showToast('密码已修改，请使用新密码重新登录。');
}

function locate(auto = false) {
  if (!navigator.geolocation) {
    setLocationSurface('failed', '定位不可用', '当前浏览器不支持定位，无法生成用户地址，请更换手机浏览器后重新拍摄。');
    renderReviewSummary();
    if (!auto) showToast('当前浏览器不支持定位，无法生成用户地址。');
    return Promise.resolve(false);
  }

  const localHost = ['localhost', '127.0.0.1'].includes(window.location.hostname);
  if (!window.isSecureContext && !localHost) {
    setLocationSurface('failed', '定位受限', '当前连接不安全，浏览器无法自动定位。请使用 HTTPS 地址重新打开页面。');
    renderReviewSummary();
    if (!auto) showToast('请使用 HTTPS 地址打开页面后重新拍摄。');
    return Promise.resolve(false);
  }

  state.locating = true;
  setLocationSurface('pending', '正在定位', '请在浏览器弹窗中允许定位，定位成功后会写入照片水印。');
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(async (position) => {
      state.location = {
        lat: Number(position.coords.latitude.toFixed(7)),
        lng: Number(position.coords.longitude.toFixed(7)),
        accuracy: Math.round(position.coords.accuracy || 0),
        address: ''
      };
      setLocationSurface(
        'pending',
        '正在生成用户地址',
        `已获取定位坐标：${state.location.lat}, ${state.location.lng}`
      );
      let address = '';
      try {
        address = await reverseGeocode(state.location.lat, state.location.lng);
      } catch {
        address = '';
      }
      address = address || '用户地址坐标已记录';
      $('[name="customerAddress"]').value = address;
      state.location.address = address;
      state.locating = false;
      renderAddressSurface();
      renderReviewSummary();
      if (!auto) showToast('用户地址已自动获取。');
      resolve(true);
    }, (error) => {
      state.locating = false;
      if (!hasPhoto('storefront')) {
        state.location = null;
        $('[name="customerAddress"]').value = '';
      }
      const message = error.code === 1
        ? '定位权限被拒绝，请在浏览器设置中允许定位后重新拍摄门头照片。'
        : '定位失败，请检查网络、权限或信号后重新拍摄门头照片。';
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
    if (!photo.remotePath) {
      photo.remotePath = await uploadPhotoBlob(photo);
    }
    uploaded.push(photo.remotePath);
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
    ['inspectorName', form.inspectorName, '检查人'],
    ['inspectionDate', form.inspectionDate, '检查日期'],
    ['deliveryPerson', form.deliveryPerson, '配送员']
  ];
  if (field !== 'storefront') {
    required.splice(3, 0, ['customerAddress', form.customerAddress, '用户地址']);
  }
  const missing = required.find(([, value]) => !value);
  if (missing) {
    focusFormField(missing[0] === 'customerAddress' ? 'storefront' : missing[0]);
    if (missing[0] === 'customerAddress') {
      return '请先拍摄门头照片自动获取用户地址，再拍摄其他照片。';
    }
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
    if (input.name === 'storefront') {
      const located = await locate(true);
      if (!located || !currentAddress()) {
        input.value = '';
        throw new Error('未获取到用户地址，请确认定位权限后重新拍摄门头照片。');
      }
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
    if (field === 'storefront') {
      state.location = null;
      $('[name="customerAddress"]').value = '';
      renderAddressSurface();
    }
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

function ensureClientUuid() {
  if (!state.clientUuid) {
    state.clientUuid = uniqueId();
    localStorage.setItem(cloudDraftKey, state.clientUuid);
  }
  return state.clientUuid;
}

function buildRecordPayload(form, hazardCodes, gasSourceSelected, photos) {
  return {
    clientUuid: ensureClientUuid(),
    ...form,
    hazardItems: hazardCodes,
    gasSourceIssues: gasSourceSelected,
    photos,
    location: state.location
      ? { ...state.location, address: form.customerAddress }
      : { address: form.customerAddress }
  };
}

async function saveCloudDraft() {
  const form = getFormData();
  const storefront = await uploadStoredPhotos('storefront');
  const payload = buildRecordPayload(form, [], [], {
    storefront,
    hazard: {},
    cylinder_qr: [],
    cylinder: []
  });
  await api('/records/drafts', {
    method: 'POST',
    body: JSON.stringify(payload)
  });
  state.cloudDraftSaved = true;
  updateStepSurface();
  showToast('用户与现场信息已保存到云端。');
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

    await api('/records/drafts/submit', {
      method: 'POST',
      body: JSON.stringify(buildRecordPayload(form, hazardCodes, gasSourceSelected, photos))
    });
    state.clientUuid = '';
    state.cloudDraftSaved = false;
    localStorage.removeItem(cloudDraftKey);
    clearForm(false);
    await showRecordsPanel();
    showToast('记录已提交并保存到云端。');
  } catch (error) {
    showToast(error.message);
  } finally {
    submitButton.disabled = false;
    submitButton.textContent = '提交记录';
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

function clearForm(showMessage = true) {
  $('#inspectionForm').reset();
  $('[name="inspectionDate"]').value = today();
  state.location = null;
  state.clientUuid = '';
  state.cloudDraftSaved = false;
  localStorage.removeItem(cloudDraftKey);
  clearAllPhotos();
  setLocationSurface('pending', '等待获取用户地址', '拍摄门头照片后自动记录定位地址。');
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
  $('#loginForm').addEventListener('submit', (event) => {
    event.preventDefault();
    runWithButton($('#loginBtn'), '登录中...', login).catch((error) => showToast(error.message));
  });
  $('#logoutBtn').addEventListener('click', () => logout(true));
  $('#backMenuBtn').addEventListener('click', showMenu);
  $('#terminalFeatureBtn').addEventListener('click', showInspectionForm);
  $('#recordsFeatureBtn').addEventListener('click', () => showRecordsPanel().catch((error) => showToast(error.message)));
  $('#homeAvatar').addEventListener('click', showProfile);
  $('#cloudStatusCard').addEventListener('click', (event) => {
    loadHomeRecords(event.currentTarget).catch((error) => {
      setCloudStatus(false, '云端连接异常 · 点击重试');
      showToast(error.message);
    });
  });
  $('#homeRecentList').addEventListener('click', (event) => {
    const button = event.target.closest('[data-home-record-id]');
    if (!button) return;
    openMobileRecord(button.dataset.homeRecordId, button).catch((error) => showToast(error.message));
  });
  $('#recordsRefreshBtn').addEventListener('click', (event) => loadMobileRecords(event.currentTarget).then(() => showToast('记录已刷新')).catch((error) => showToast(error.message)));
  $('#mobileRecordFilters').addEventListener('submit', (event) => {
    event.preventDefault();
    loadMobileRecords(event.submitter, '查询中...').then(() => showToast('查询完成')).catch((error) => showToast(error.message));
  });
  $('#mobileRecordFilters').addEventListener('change', (event) => {
    if (!event.target.matches('[name="status"]')) return;
    loadMobileRecords().catch((error) => showToast(error.message));
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
  $('#mobilePhotoDetail').addEventListener('click', (event) => {
    const button = event.target.closest('[data-photo-url]');
    if (!button) return;
    openPhotoPreview(button.dataset.photoUrl, button.dataset.photoTitle);
  });
  $('#photoPreviewClose').addEventListener('click', closePhotoPreview);
  $('#photoPreview').addEventListener('click', (event) => {
    if (event.target.dataset.photoPreviewClose) closePhotoPreview();
  });
  $('#mobileRectifyForm').addEventListener('submit', (event) => submitMobileRectification(event).catch((error) => showToast(error.message)));
  $('#changePasswordBtn').addEventListener('click', openPasswordDialog);
  $('#passwordCloseBtn').addEventListener('click', closePasswordDialog);
  $('#passwordDialog').addEventListener('click', (event) => {
    if (event.target.dataset.passwordClose) closePasswordDialog();
  });
  $('#passwordForm').addEventListener('submit', (event) => changePassword(event).catch((error) => showToast(error.message)));
  $('#formStepper').addEventListener('click', (event) => {
    const button = event.target.closest('[data-step-target]');
    if (!button) return;
    setFormStep(button.dataset.stepTarget).catch((error) => showToast(error.message));
  });
  $('#stepPrevBtn').addEventListener('click', previousFormStep);
  $('#stepNextBtn').addEventListener('click', (event) => {
    runWithButton(event.currentTarget, '保存中...', nextFormStep).catch((error) => showToast(error.message));
  });
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
  $('#clearFormBtn').addEventListener('click', () => {
    clearForm(false);
    showMenu();
  });
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
    if (button.dataset.nav === 'profile') showProfile();
  });

  document.addEventListener('focusin', (event) => {
    if (state.view !== 'form' || !event.target.matches('input:not([type="file"]), select, textarea')) return;
    document.body.classList.add('keyboard-open');
    window.setTimeout(() => event.target.scrollIntoView({ block: 'center', behavior: 'smooth' }), 240);
  });
  document.addEventListener('focusout', () => {
    window.setTimeout(() => {
      const active = document.activeElement;
      if (!active?.matches('input:not([type="file"]), select, textarea')) {
        document.body.classList.remove('keyboard-open');
      }
    }, 180);
  });
}

initOptions();
bindEvents();
restoreLoginState();
