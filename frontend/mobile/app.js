import {
  RESULT_CLEARED,
  RESULT_NOT_CLEARED,
  customerTypes,
  gasSourceIssues,
  hazardItems
} from '../../shared/catalog.js?v=20261108-stove-hazard';

const apiBase = localStorage.getItem('apiBase') || '/api';
const tokenKey = 'xinyuan_h5_token';
const cloudDraftKey = 'xinyuan_h5_cloud_draft_uuid';
const singlePageForm = true;
const LOCATION_TARGET_ACCURACY_METERS = 30;
const LOCATION_MAX_ACCURACY_METERS = 100;
const LOCATION_FIX_TIMEOUT_MS = 15000;
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
  recordsRequest: 0,
  recordsVisible: 50,
  currentRecord: null,
  photoSupplement: null,
  organizationOptions: { regions: [], stores: [], loaded: false },
  recordOrganization: { regionId: 0, regionName: '', storeId: 0, storeName: '', remembered: false },
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
    const accuracyText = state.location.accuracy
      ? `；GPS 精度约 ${state.location.accuracy} 米`
      : '';
    setLocationSurface(
      'success',
      '已获取用户地址',
      `${address || '用户地址坐标已记录'}；定位坐标：${state.location.lat}, ${state.location.lng}${accuracyText}；后续照片统一复用`
    );
    return;
  }
  setLocationSurface('pending', '等待获取用户地址', '拍摄门头照片时获取一次高精度定位，后续照片统一复用。');
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
  if (!validInspectionOrganization(state.recordOrganization)) {
    showToast('请选择本次检查的片区和门店后再提交。');
    openOrganizationSheet();
    return false;
  }
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
    ['检查归属', `${state.recordOrganization.regionName || '-'}${state.recordOrganization.storeName ? ` / ${state.recordOrganization.storeName}` : ''}`],
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
    const clear = $('#clearFormBtn');
    $('#formStepper')?.classList.add('hidden');
    if (clear) clear.classList.remove('hidden');
    if (prev) prev.classList.add('hidden');
    if (next) next.classList.add('hidden');
    if (submit) submit.classList.remove('hidden');
    $('#cloudDraftBanner')?.classList.toggle('hidden', !state.cloudDraftSaved);
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
    灶具: '灶',
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
  const requestToken = state.token;
  if (!(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }
  if (requestToken) {
    headers.Authorization = `Bearer ${requestToken}`;
  }

  const response = await fetch(`${apiBase}${path}`, { ...options, headers });
  const result = await response.json().catch(() => ({}));
  if (response.status === 401 && requestToken && requestToken === state.token) {
    const message = result.message || '当前账号已在另一台手机或 H5 登录，请重新登录。';
    logout(false);
    showToast(message);
    throw new Error(message);
  }
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

function organizationChoiceKey() {
  return state.user ? `xinyuan_inspection_organization:${apiBase}:${state.user.companyId}:${state.user.id}` : '';
}

function validInspectionOrganization(selection) {
  const region = state.organizationOptions.regions.find((item) => Number(item.id) === Number(selection.regionId));
  const store = state.organizationOptions.stores.find((item) => (
    Number(item.id) === Number(selection.storeId) && Number(item.regionId) === Number(selection.regionId)
  ));
  return region && store ? { regionId: Number(region.id), regionName: region.name, storeId: Number(store.id), storeName: store.name } : null;
}

function rememberInspectionOrganization() {
  const key = organizationChoiceKey();
  if (key) localStorage.setItem(key, JSON.stringify(state.recordOrganization));
}

function resetRecordOrganization() {
  let previous = {};
  try {
    previous = JSON.parse(localStorage.getItem(organizationChoiceKey()) || '{}') || {};
  } catch { /* Ignore obsolete preferences. */ }
  const selection = state.organizationOptions.loaded ? (validInspectionOrganization(previous) || {}) : previous;
  state.recordOrganization = {
    regionId: Number(selection.regionId || 0),
    regionName: String(selection.regionName || ''),
    storeId: Number(selection.storeId || 0),
    storeName: String(selection.storeName || ''),
    remembered: Boolean(selection.regionId && selection.storeId)
  };
  renderInspectionOrganization();
}

function renderInspectionOrganization() {
  const summary = $('#inspectionOrganizationSummary');
  const hint = $('#inspectionOrganizationHint');
  if (!summary || !hint) return;
  const organization = state.recordOrganization;
  summary.textContent = organization.regionName
    ? `${organization.regionName}${organization.storeName ? ` · ${organization.storeName}` : ''}`
    : '请选择检查片区和门店';
  const complete = Boolean(organization.regionId && organization.storeId);
  hint.textContent = !complete ? '必选' : (organization.remembered ? '沿用上次选择' : '本次检查');
  hint.classList.toggle('changed', !complete);
  $('#organizationChangeBtn').textContent = complete ? '更换' : '选择';
}

async function loadOrganizationOptions(force = false) {
  if (state.organizationOptions.loaded && !force) return state.organizationOptions;
  const sessionToken = state.token;
  const data = await api('/organization-options');
  if (sessionToken !== state.token) return state.organizationOptions;
  state.organizationOptions = {
    regions: Array.isArray(data.regions) ? data.regions : [],
    stores: Array.isArray(data.stores) ? data.stores : [],
    loaded: true
  };
  const selection = validInspectionOrganization(state.recordOrganization);
  if (selection) {
    state.recordOrganization = { ...state.recordOrganization, ...selection };
    renderInspectionOrganization();
  } else {
    resetRecordOrganization();
  }
  renderReviewSummary();
  return state.organizationOptions;
}

function renderOrganizationStoreOptions(resetStore = false) {
  const regionId = Number($('#inspectionRegionSelect').value || 0);
  const currentStoreId = resetStore ? 0 : Number(state.recordOrganization.storeId || 0);
  const stores = state.organizationOptions.stores.filter((store) => Number(store.regionId) === regionId);
  $('#inspectionStoreSelect').innerHTML = [
    `<option value="">${!regionId ? '请先选择片区' : (stores.length ? '请选择门店' : '该片区暂无可用门店')}</option>`,
    ...stores.map((store) => `<option value="${Number(store.id)}">${escapeHtml(store.name)}</option>`)
  ].join('');
  $('#inspectionStoreSelect').disabled = !regionId || stores.length === 0;
  if (stores.some((store) => Number(store.id) === currentStoreId)) {
    $('#inspectionStoreSelect').value = String(currentStoreId);
  }
}

async function openOrganizationSheet() {
  try {
    await loadOrganizationOptions(true);
  } catch (error) {
    showToast(error.message);
    return;
  }
  if (state.organizationOptions.regions.length === 0 || state.organizationOptions.stores.length === 0) {
    showToast('暂无可选片区或门店，请联系管理员在后台录入后再检查。');
    return;
  }
  $('#inspectionRegionSelect').innerHTML = [
    '<option value="">请选择片区</option>',
    ...state.organizationOptions.regions.map((region) => `<option value="${Number(region.id)}">${escapeHtml(region.name)}</option>`)
  ].join('');
  $('#inspectionRegionSelect').value = state.recordOrganization.regionId
    ? String(state.recordOrganization.regionId)
    : '';
  renderOrganizationStoreOptions();
  $('#inspectionStoreSelect').value = state.recordOrganization.storeId
    ? String(state.recordOrganization.storeId)
    : '';
  $('#organizationSheet').classList.remove('hidden');
  $('#organizationSheet').setAttribute('aria-hidden', 'false');
}

function closeOrganizationSheet() {
  $('#organizationSheet').classList.add('hidden');
  $('#organizationSheet').setAttribute('aria-hidden', 'true');
}

function applyOrganizationSelection() {
  const regionId = Number($('#inspectionRegionSelect').value || 0);
  if (!regionId) {
    showToast('请选择本次检查所属片区。');
    return;
  }
  const storeId = Number($('#inspectionStoreSelect').value || 0);
  const selection = validInspectionOrganization({ regionId, storeId });
  if (!selection) {
    showToast('请选择该片区下的检查门店。');
    return;
  }
  state.recordOrganization = {
    ...selection,
    remembered: false
  };
  rememberInspectionOrganization();
  renderInspectionOrganization();
  renderReviewSummary();
  closeOrganizationSheet();
  showToast('已变更本次检查归属。');
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
  $('#profileScope').textContent = state.user.role === 'hq_admin'
    ? '全部公司数据'
    : (state.user.role === 'company_admin' || state.user.canViewCompanyRecords ? '本公司数据' : '仅查看本人录入');
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
  if (!state.recordOrganization.regionId) resetRecordOrganization();
  loadOrganizationOptions(true).catch((error) => showToast(error.message));
  if (!formStepKeys.includes(state.formStep)) {
    state.formStep = 'user';
  }
  updateStepSurface();
  window.scrollTo({ top: 0, behavior: 'instant' });
}

function mobileRecordFilters() {
  const form = new FormData($('#mobileRecordFilters'));
  return {
    keyword: String(form.get('keyword') || '').trim().toLowerCase(),
    status: form.get('status') || '',
    deliveryPerson: String(form.get('recordDeliveryPerson') || '').trim().toLowerCase(),
    inspectorName: String(form.get('recordInspectorName') || '').trim().toLowerCase(),
    regionId: form.get('recordRegionId') || '',
    storeId: form.get('recordStoreId') || '',
    ownOnly: form.has('recordOwnOnly')
  };
}

function fillMobileRecordOrganizationFilter(name, records, keepSelection = true) {
  const select = $('#mobileRecordFilters').elements.namedItem(name);
  const previous = keepSelection && select.value ? select.selectedOptions[0] : null;
  const isRegion = name === 'recordRegionId';
  const options = new Map();
  for (const record of records) {
    const id = Number(isRegion ? record.region_id : record.store_id);
    const label = isRegion ? record.region_name : record.store_name;
    if (!Number.isSafeInteger(id) || id <= 0 || !label || options.has(String(id))) continue;
    const company = state.user?.role === 'hq_admin' ? record.company_name : '';
    const parts = isRegion ? [company, label] : [label, record.region_name, company];
    options.set(String(id), parts.filter(Boolean).join(' / '));
  }
  if (previous && !options.has(previous.value)) options.set(previous.value, previous.text);
  select.replaceChildren(
    new Option(isRegion ? '全部片区' : '全部门店', ''),
    ...[...options].sort((a, b) => a[1].localeCompare(b[1], 'zh-CN')).map(([id, label]) => new Option(label, id))
  );
  if (previous) select.value = previous.value;
}

function updateMobileRecordOrganizationFilters(resetStore = false) {
  fillMobileRecordOrganizationFilter('recordRegionId', state.records);
  const regionId = mobileRecordFilters().regionId;
  const stores = regionId
    ? state.records.filter((record) => String(record.region_id) === regionId)
    : state.records;
  fillMobileRecordOrganizationFilter('recordStoreId', stores, !resetStore);
}

function setCloudStatus(connected, text) {
  $('#cloudStatusCard').classList.toggle('offline', !connected);
  $('#cloudStatusText').textContent = text;
}

function renderHomeRecords(records, summary, todayTotal) {
  $('#homeTodayCount').textContent = todayTotal;
  $('#homePendingCount').textContent = summary.pending;
  $('#homeRecordCount').textContent = summary.total;

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
  const sessionToken = state.token;
  await runWithButton(button, '刷新中...', async () => {
    setCloudStatus(true, '正在同步云端数据');
    const todayKey = today();
    const [overview, todayRecords] = await Promise.all([
      api('/records?page=1&pageSize=3'),
      api(`/records?page=1&pageSize=1&dateFrom=${todayKey}&dateTo=${todayKey}`)
    ]);
    if (sessionToken !== state.token) return;
    renderHomeRecords(overview.records, overview.summary, todayRecords.pagination.total);
    setCloudStatus(true, '云端连接正常 · 数据已同步');
  });
}

async function showRecordsPanel() {
  setView('records');
  await loadMobileRecords();
}

async function loadMobileRecords(button = null) {
  const sessionToken = state.token;
  const request = ++state.recordsRequest;
  await runWithButton(button, '刷新中...', async () => {
    const records = [];
    const ids = new Set();
    const pageSize = 100;
    let total = 0;
    for (let page = 1; ; page++) {
      const data = await api(`/records?page=${page}&pageSize=${pageSize}`);
      if (sessionToken !== state.token || request !== state.recordsRequest) return;
      const pagination = data.pagination;
      if (page === 1) total = pagination?.total;
      const pageCount = Math.max(1, Math.ceil(total / pageSize));
      if (!Number.isSafeInteger(total) || total < 0 || pagination?.page !== page
          || pagination.total !== total || pagination.pageCount !== pageCount
          || !Array.isArray(data.records)
          || data.records.length !== Math.min(pageSize, total - records.length)) {
        throw new Error('记录加载不完整，请刷新后重试。');
      }
      for (const record of data.records) {
        const id = String(record.id);
        if (record.id == null || ids.has(id)) throw new Error('记录加载不完整，请刷新后重试。');
        ids.add(id);
        records.push(record);
      }
      if (page === pageCount) break;
    }
    state.records = records;
    updateMobileRecordOrganizationFilters();
    renderFilteredMobileRecords(true);
  });
}

function renderFilteredMobileRecords(reset = false) {
  if (reset) state.recordsVisible = 50;
  const filters = mobileRecordFilters();
  const accountId = Number(state.user?.id);
  const accountRecords = filters.ownOnly
    ? state.records.filter((record) => Number.isSafeInteger(accountId) && accountId > 0 && Number(record.creator_id) === accountId)
    : state.records;
  const pending = accountRecords.filter((record) => record.status === 'pending').length;
  $('#allStatusCount').textContent = accountRecords.length;
  $('#pendingStatusCount').textContent = pending;
  $('#doneStatusCount').textContent = accountRecords.length - pending;
  let scope = '仅显示本账号录入的记录';
  if (!filters.ownOnly) {
    if (state.user?.role === 'hq_admin') scope = '显示当前账号可查看的全部公司记录';
    else if (state.user?.role === 'company_admin' || state.user?.canViewCompanyRecords) scope = '显示当前账号可查看的本公司记录';
  }
  $('#recordsScope').textContent = scope;
  const filtered = accountRecords.filter((record) => {
    const includes = (value, query) => !query || String(value ?? '').toLowerCase().includes(query);
    const matchesKeyword = !filters.keyword || [
      record.customer_name, record.customer_phone, record.customer_address, record.inspector_name
    ].some((value) => includes(value, filters.keyword));
    const matchesStatus = filters.status === 'pending'
      ? record.status === 'pending'
      : filters.status === 'done'
        ? record.status !== 'pending'
        : true;
    return matchesKeyword && matchesStatus
      && includes(record.delivery_person, filters.deliveryPerson)
      && includes(record.inspector_name, filters.inspectorName)
      && (!filters.regionId || String(record.region_id) === filters.regionId)
      && (!filters.storeId || String(record.store_id) === filters.storeId);
  });
  const active = [filters.deliveryPerson, filters.inspectorName, filters.regionId, filters.storeId, filters.ownOnly]
    .filter(Boolean).length;
  $('#recordFilterSummary').textContent = active ? `筛选（${active}）` : '筛选';
  $('#recordFilterResult').textContent = `共 ${filtered.length} 条记录`;
  $('#recordsMoreBtn').classList.toggle('hidden', filtered.length <= state.recordsVisible);
  renderMobileRecords(filtered.slice(0, state.recordsVisible));
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
  if (state.photoSupplement?.saving) return;
  resetPhotoSupplement();
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
    ['检查归属', [record.region_name, record.store_name].filter(Boolean).join(' / ') || '公司级'],
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
  resetPhotoSupplement();
  const supplementForm = $('#mobilePhotoSupplementForm');
  supplementForm.classList.toggle('hidden', record.status !== 'rectified' || record.canSupplementRectificationPhoto !== true);
  const originals = (record.photos || []).filter((photo) => photo.photo_type === 'rectification' && Number(photo.id) > 0);
  supplementForm.elements.replacePhotoId.innerHTML = '<option value="">追加照片</option>' + originals.map((photo, index) =>
    `<option value="${Number(photo.id)}">替换第 ${index + 1} 张整改照片</option>`).join('');
}

function resetPhotoSupplement() {
  if (state.photoSupplement?.previewUrl) URL.revokeObjectURL(state.photoSupplement.previewUrl);
  state.photoSupplement = null;
  $('#mobilePhotoSupplementForm').reset();
  $('#supplementPhotoPreview').removeAttribute('src');
  $('#supplementPhotoPreview').classList.add('hidden');
}

function updatePhotoSupplement(event) {
  const form = $('#mobilePhotoSupplementForm');
  const previous = state.photoSupplement;
  if (previous?.saving) return;
  let previewUrl = previous?.previewUrl;
  if (event.target.name === 'file') {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    const file = form.elements.file.files[0];
    previewUrl = file ? URL.createObjectURL(file) : '';
    const preview = $('#supplementPhotoPreview');
    preview.classList.toggle('hidden', !previewUrl);
    if (previewUrl) preview.src = previewUrl; else preview.removeAttribute('src');
  }
  const operationId = globalThis.crypto?.randomUUID?.() || 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const n = Math.floor(Math.random() * 16);
    return (c === 'x' ? n : (n & 3) | 8).toString(16);
  });
  state.photoSupplement = { operationId, previewUrl, saving: false };
  form.querySelector('[type="submit"]').textContent = form.elements.replacePhotoId.value ? '保存替换' : '保存补录';
}

async function submitPhotoSupplement(event) {
  event.preventDefault();
  const record = state.currentRecord;
  const form = event.currentTarget;
  if (!record || record.status !== 'rectified' || state.photoSupplement?.saving) return;
  const file = form.elements.file.files[0];
  if (!file || file.size > 10 * 1024 * 1024) throw new Error('请选择不超过 10MB 的 JPG、PNG 或 WebP 照片。');
  if (!state.photoSupplement) updatePhotoSupplement({ target: form.elements.file });
  const operationId = state.photoSupplement.operationId;
  const body = new FormData(form);
  body.set('reason', String(body.get('reason') || '').trim());
  if (!body.get('reason')) throw new Error('请填写补录原因。');
  if (!body.get('replacePhotoId')) body.delete('replacePhotoId');
  body.set('operationId', operationId);
  const controls = [...form.querySelectorAll('input, select, textarea, button')];
  state.photoSupplement.saving = true;
  controls.forEach((control) => { control.disabled = true; });
  try {
    await api(`/records/${record.id}/rectification-photo`, { method: 'POST', body });
    // Refresh separately so a failed read cannot turn a saved upload into a retry.
    state.photoSupplement.saving = false;
    resetPhotoSupplement();
    showToast('整改照片已补录');
    $('#mobilePhotoSupplementForm').classList.add('hidden');
    try { await openMobileRecord(record.id); } catch { showToast('照片已保存，请重新打开详情查看。'); }
  } finally {
    if (state.photoSupplement) state.photoSupplement.saving = false;
    controls.forEach((control) => { control.disabled = false; });
  }
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
  state.organizationOptions = { regions: [], stores: [], loaded: false };
  resetRecordOrganization();
  localStorage.setItem(tokenKey, state.token);
  showMenu();
  showToast('登录成功');
}

async function loadSession() {
  const data = await api('/auth/me');
  state.user = data.user;
  state.organizationOptions = { regions: [], stores: [], loaded: false };
  resetRecordOrganization();
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
  state.recordsRequest++;
  $('#mobileRecordFilters').reset();
  $('#recordExtraFilters').open = false;
  updateMobileRecordOrganizationFilters();
  renderFilteredMobileRecords(true);
  state.currentRecord = null;
  state.organizationOptions = { regions: [], stores: [], loaded: false };
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
  try {
    const result = await api('/location/reverse-geocode', {
      method: 'POST',
      body: JSON.stringify({ latitude, longitude })
    });
    const address = String(result?.address || '').trim();
    if (address) {
      return { address, provider: 'tencent' };
    }
  } catch {
    // Preserve field usability when Tencent Maps is temporarily unavailable.
  }

  return {
    address: await reverseGeocodeFallback(latitude, longitude),
    provider: 'fallback'
  };
}

async function reverseGeocodeFallback(latitude, longitude) {
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

function getBestCurrentPosition(onProgress) {
  return new Promise((resolve, reject) => {
    let bestPosition = null;
    let watchId = null;
    let finished = false;

    const accuracyOf = (position) => Number(position?.coords?.accuracy || Number.POSITIVE_INFINITY);
    const finish = (callback, value) => {
      if (finished) return;
      finished = true;
      window.clearTimeout(timeoutId);
      if (watchId !== null) navigator.geolocation.clearWatch(watchId);
      callback(value);
    };
    const finishWithBestOrError = (fallbackError) => {
      const bestAccuracy = accuracyOf(bestPosition);
      if (bestPosition && bestAccuracy <= LOCATION_MAX_ACCURACY_METERS) {
        finish(resolve, bestPosition);
        return;
      }
      const error = fallbackError instanceof Error
        ? fallbackError
        : new Error(fallbackError?.message || '未获取到足够精确的定位。');
      if (fallbackError?.code) error.code = fallbackError.code;
      error.code = error.code || 'LOW_ACCURACY';
      error.accuracy = Number.isFinite(bestAccuracy) ? Math.round(bestAccuracy) : 0;
      finish(reject, error);
    };

    const timeoutId = window.setTimeout(() => {
      finishWithBestOrError(new Error('高精度定位超时。'));
    }, LOCATION_FIX_TIMEOUT_MS);

    watchId = navigator.geolocation.watchPosition((position) => {
      const accuracy = accuracyOf(position);
      if (!bestPosition || accuracy < accuracyOf(bestPosition)) {
        bestPosition = position;
        if (Number.isFinite(accuracy)) onProgress?.(Math.round(accuracy));
      }
      if (accuracy <= LOCATION_TARGET_ACCURACY_METERS) {
        finish(resolve, position);
      }
    }, (error) => {
      finishWithBestOrError(error);
    }, {
      enableHighAccuracy: true,
      timeout: LOCATION_FIX_TIMEOUT_MS,
      maximumAge: 0
    });
  });
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

async function locate(auto = false) {
  if (!navigator.geolocation) {
    setLocationSurface('failed', '定位不可用', '当前浏览器不支持定位，无法生成用户地址，请更换手机浏览器后重新拍摄。');
    renderReviewSummary();
    if (!auto) showToast('当前浏览器不支持定位，无法生成用户地址。');
    return false;
  }

  const localHost = ['localhost', '127.0.0.1'].includes(window.location.hostname);
  if (!window.isSecureContext && !localHost) {
    setLocationSurface('failed', '定位受限', '当前连接不安全，浏览器无法自动定位。请使用 HTTPS 地址重新打开页面。');
    renderReviewSummary();
    if (!auto) showToast('请使用 HTTPS 地址打开页面后重新拍摄。');
    return false;
  }

  state.locating = true;
  setLocationSurface('pending', '正在获取高精度定位', '请允许定位并保持手机稳定，系统会选择精度最好的一次。');
  try {
    const position = await getBestCurrentPosition((accuracy) => {
      setLocationSurface(
        'pending',
        '正在提高定位精度',
        `当前精度约 ${accuracy} 米，目标 ${LOCATION_TARGET_ACCURACY_METERS} 米以内。`
      );
    });
    state.location = {
      lat: Number(position.coords.latitude.toFixed(7)),
      lng: Number(position.coords.longitude.toFixed(7)),
      accuracy: Math.round(position.coords.accuracy || 0),
      capturedAt: (() => {
        const captured = formatCapturedAt(new Date());
        return `${captured.date} ${captured.time}`;
      })(),
      address: '',
      source: 'storefront'
    };
    setLocationSurface(
      'pending',
      '正在生成用户地址',
      `已选取最佳坐标，精度约 ${state.location.accuracy} 米；地址服务仅调用一次。`
    );
    let geocoded = { address: '', provider: '' };
    try {
      geocoded = await reverseGeocode(state.location.lat, state.location.lng);
    } catch {
      geocoded = { address: '', provider: '' };
    }
    const address = geocoded.address || '用户地址坐标已记录';
    $('[name="customerAddress"]').value = address;
    state.location.address = address;
    state.location.addressProvider = geocoded.provider;
    renderAddressSurface();
    renderReviewSummary();
    if (!auto) showToast('用户地址已自动获取，后续照片将统一复用。');
    return true;
  } catch (error) {
    if (!hasPhoto('storefront')) {
      state.location = null;
      $('[name="customerAddress"]').value = '';
    }
    const message = error.code === 1
      ? '定位权限被拒绝，请在浏览器设置中允许精确定位后重新拍摄门头照片。'
      : error.code === 'LOW_ACCURACY' || Number(error.accuracy) > LOCATION_MAX_ACCURACY_METERS
        ? `当前定位精度约 ${error.accuracy || 100} 米，未达到水印要求。请到开阔处保持手机稳定后重拍。`
        : '定位失败，请检查网络、精确定位权限或 GPS 信号后重新拍摄门头照片。';
    setLocationSurface('failed', '定位失败', message);
    renderReviewSummary();
    if (!auto) showToast(message);
    return false;
  } finally {
    state.locating = false;
  }
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
    [form.customerName && `用户：${form.customerName}`, form.customerPhone && `电话：${form.customerPhone}`].filter(Boolean).join('  '),
    form.customerType && `用户类型：${form.customerType}`,
    ...(form.customerAddress ? wrapText(ctx, `用户地址：${form.customerAddress}`, canvas.width - 36) : []),
    [form.inspectorName && `检查人：${form.inspectorName}`, form.deliveryPerson && `配送员：${form.deliveryPerson}`].filter(Boolean).join('  '),
    locationLine
  ].filter(Boolean);
  const lineHeight = Math.round(fontSize * 1.38);
  const panelHeight = Math.min(canvas.height * 0.46, lines.length * lineHeight + 30);
  ctx.fillStyle = 'rgba(0,0,0,.58)';
  ctx.fillRect(0, canvas.height - panelHeight, canvas.width, panelHeight);
  ctx.fillStyle = '#fff';
  lines.forEach((line, index) => ctx.fillText(line, 18, canvas.height - panelHeight + fontSize + 12 + index * lineHeight));

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
    const location = photo.location || state.location;
    uploaded.push(location ? {
      filePath: photo.remotePath,
      watermarkAddress: location.address || currentAddress(),
      latitude: location.lat,
      longitude: location.lng,
      accuracyMeters: location.accuracy,
      capturedAt: photo.capturedAt || location.capturedAt || null
    } : photo.remotePath);
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
  if (field !== 'storefront' && (!hasPhoto('storefront') || !state.location || !form.customerAddress)) {
    focusFormField('storefront');
    return '请先拍摄门头照片获取用户地址和定位，再拍摄其他照片。';
  }
  if (!form.deliveryPerson) {
    focusFormField('deliveryPerson');
    return '请先填写配送员，再拍照。';
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
    capturedAt: `${formatCapturedAt(capturedAt).date} ${formatCapturedAt(capturedAt).time}`,
    location: state.location ? { ...state.location } : null
  };
}

function renderPhotoPreview(field) {
  const input = photoInput(field);
  if (!input) return;
  const preview = getOrCreatePreview(input);
  const items = state.photos[field] || [];
  const tile = input.closest('.photo-tile');
  const label = tile?.dataset.photoLabel || '现场照片';
  if (input.multiple) {
    const helper = tile?.querySelector('em');
    if (helper) {
      helper.dataset.emptyText ||= helper.textContent;
      helper.textContent = items.length
        ? `已拍 ${items.length} 张，点击继续拍摄`
        : helper.dataset.emptyText;
    }
    tile?.classList.toggle('has-photos', items.length > 0);
  }
  preview.classList.toggle('hidden', items.length === 0);
  preview.innerHTML = items.length ? `
    <div class="photo-preview-head">
      <strong>${escapeHtml(label)} · ${items.length} 张</strong>
      <span>点击照片查看大图</span>
    </div>
    <div class="photo-preview-strip">
      ${items.map((item, index) => `
        <article class="photo-card">
          <button class="local-photo-open" type="button"
            data-local-photo-preview="${item.id}"
            data-photo-field="${field}"
            aria-label="查看${escapeHtml(label)}第 ${index + 1} 张">
            <img src="${item.previewUrl}" alt="${escapeHtml(label)}第 ${index + 1} 张">
            <span>${index + 1}</span>
          </button>
          <button class="local-photo-delete" type="button"
            data-photo-delete="${item.id}"
            data-photo-field="${field}"
            aria-label="删除${escapeHtml(label)}第 ${index + 1} 张">×</button>
        </article>
      `).join('')}
    </div>
  ` : '';
}

function openLocalPhotoPreview(field, id) {
  const input = photoInput(field);
  const items = state.photos[field] || [];
  const index = items.findIndex((item) => item.id === id);
  if (index < 0) return;
  const label = input?.closest('.photo-tile')?.dataset.photoLabel || '现场照片';
  openPhotoPreview(items[index].previewUrl, `${label} ${index + 1}/${items.length}`);
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
    if (input.name === 'storefront') {
      await saveCloudDraft();
    }
    showToast(state.location ? '照片已生成定位水印。' : '照片已生成水印；当前未获取经纬度。');
  } catch (error) {
    showToast(error.message);
  } finally {
    if (input.multiple) input.value = '';
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
    regionId: state.recordOrganization.regionId || null,
    storeId: state.recordOrganization.storeId || null,
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
    const photos = await uploadFiles([...form.getAll('rectificationPhotos'), ...form.getAll('rectificationGallery')]);
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
  resetRecordOrganization();
  localStorage.removeItem(cloudDraftKey);
  clearAllPhotos();
  setLocationSurface('pending', '等待获取用户地址', '拍摄门头照片时获取一次高精度定位，后续照片统一复用。');
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
  $('#organizationChangeBtn').addEventListener('click', openOrganizationSheet);
  $('#organizationCloseBtn').addEventListener('click', closeOrganizationSheet);
  $('#organizationSheet').addEventListener('click', (event) => {
    if (event.target.dataset.organizationClose) closeOrganizationSheet();
  });
  $('#inspectionRegionSelect').addEventListener('change', () => renderOrganizationStoreOptions(true));
  $('#organizationApplyBtn').addEventListener('click', applyOrganizationSelection);
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
    $('#recordExtraFilters').open = false;
    renderFilteredMobileRecords(true);
    window.scrollTo({ top: 0, behavior: 'instant' });
  });
  $('#mobileRecordFilters').addEventListener('change', (event) => {
    if (event.target.matches('[name="recordRegionId"]')) updateMobileRecordOrganizationFilters(true);
    if (event.target.matches('[name="status"]')) renderFilteredMobileRecords(true);
  });
  $('#clearRecordFiltersBtn').addEventListener('click', () => {
    const fields = $('#mobileRecordFilters').elements;
    fields.namedItem('recordOwnOnly').checked = false;
    for (const name of ['recordDeliveryPerson', 'recordInspectorName', 'recordRegionId', 'recordStoreId']) {
      fields.namedItem(name).value = '';
    }
    updateMobileRecordOrganizationFilters(true);
    renderFilteredMobileRecords(true);
  });
  $('#recordsMoreBtn').addEventListener('click', () => {
    state.recordsVisible += 50;
    renderFilteredMobileRecords();
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
  $('#mobilePhotoSupplementForm').addEventListener('input', updatePhotoSupplement);
  $('#mobilePhotoSupplementForm').addEventListener('submit', (event) => submitPhotoSupplement(event).catch((error) => showToast(error.message)));
  $('#changePasswordBtn').addEventListener('click', openPasswordDialog);
  $('#passwordCloseBtn').addEventListener('click', closePasswordDialog);
  $('#passwordDialog').addEventListener('click', (event) => {
    if (event.target.dataset.passwordClose) closePasswordDialog();
  });
  $('#passwordForm').addEventListener('submit', (event) => changePassword(event).catch((error) => showToast(error.message)));
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
    const previewButton = event.target.closest('[data-local-photo-preview]');
    if (previewButton) {
      openLocalPhotoPreview(previewButton.dataset.photoField, previewButton.dataset.localPhotoPreview);
      return;
    }
    const deleteButton = event.target.closest('[data-photo-delete]');
    if (deleteButton) {
      deletePhoto(deleteButton.dataset.photoField, deleteButton.dataset.photoDelete);
      return;
    }
    const tile = event.target.closest('.photo-tile');
    const fileInput = event.target.closest('input[type="file"]') || tile?.querySelector('input[type="file"]');
    if (fileInput?.name) {
      const contextError = validatePhotoContext(fileInput.name);
      if (contextError) {
        event.preventDefault();
        event.stopPropagation();
        showToast(contextError);
        return;
      }
    }
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
