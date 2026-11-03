import { hazardItems } from '../../shared/catalog.js';

let options;
let visible = false;
let timer = null;
let generation = 0;
let busy = false;
const $ = (selector) => document.querySelector(selector);
const escape = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const bytes = (value) => `${(Number(value) / 1024 / 1024).toFixed(1)} MB`;
const statusText = { queued: '排队中', processing: '正在生成', finished: '已完成', failed: '生成失败', cancelled: '已取消' };

export function configureExportJobs(config) {
  options = config;
  $('#refreshExportsBtn').addEventListener('click', () => refreshExportJobs());
  $('#exportJobsList').addEventListener('click', async (event) => {
    const button = event.target.closest('button[data-export-action]');
    if (!button || button.disabled) return;
    button.disabled = true;
    try {
      const id = Number(button.dataset.id);
      if (button.dataset.exportAction === 'cancel') {
        await options.api(`/export/jobs/${id}/cancel`, { method: 'POST', body: '{}' });
        await refreshExportJobs();
      } else {
        const part = Number(button.dataset.part);
        const { ticket } = await options.api(`/export/jobs/${id}/files/${part}/ticket`, { method: 'POST', body: '{}' });
        // Native download streams directly to disk instead of buffering a large Blob.
        const link = document.createElement('a');
        link.href = `${options.apiBase}/export/jobs/${id}/files/${part}?ticket=${encodeURIComponent(ticket)}`;
        link.download = '';
        link.referrerPolicy = 'no-referrer';
        document.body.appendChild(link);
        link.click();
        link.remove();
        options.notify('下载已交给浏览器，请查看浏览器下载列表。');
      }
    } catch (error) {
      options.notify(error.message);
    } finally {
      button.disabled = false;
    }
  });
}

export function setExportVisible(next) {
  visible = next;
  generation++;
  clearTimeout(timer);
  timer = null;
  if (!next) {
    $('#exportJobsList').innerHTML = '';
    $('#exportJobsError').textContent = '';
  }
}

export async function createExportJob(photoMode = 'embedded') {
  const { job } = await options.api('/export/jobs', { method: 'POST', body: JSON.stringify({ ...options.filters(), ...(photoMode === 'links' ? { photoMode: 'links' } : {}) }) });
  options.showModule('export');
  options.notify(`已提交 ${job.total} 条记录，可离开页面，稍后到“数据导出”下载。`);
  await refreshExportJobs();
}

function filterSummary(filters) {
  const labels = { keyword: '关键词', companyId: '公司编号', regionId: '片区编号', storeId: '门店编号', status: '状态',
    checkResult: '检查结果', customerType: '用户类型', inspectorName: '检查人', deliveryPerson: '配送员',
    dateFrom: '开始', dateTo: '结束', hazardCode: '隐患项目', hazardCodes: '隐患项目', gasSourceIssues: '气源排查', customerName: '用户', customerPhone: '电话', customerAddress: '地址' };
  return Object.entries(filters || {}).filter(([, value]) => Array.isArray(value) ? value.length > 0 : value).map(([key, value]) => {
    if (key === 'hazardCodes') value = value.map((code) => hazardItems.find((h) => h.code === code)?.name || code).join('、');
    if (key === 'gasSourceIssues') value = value.join('、');
    if (key === 'hazardCode') value = hazardItems.find((h) => h.code === value)?.name || value;
    if (key === 'status') value = ({ pending: '待整改', done: '已完成', rectified: '已整改' })[value] || value;
    return `${labels[key] || key}：${value}`;
  }).join('；') || '全部有权查看的检查记录';
}

export async function refreshExportJobs() {
  if (!visible || busy || !options) return;
  busy = true;
  const current = generation;
  clearTimeout(timer);
  try {
    const { jobs } = await options.api('/export/jobs');
    if (!visible || current !== generation) return;
    $('#exportJobsError').textContent = '';
    $('#exportJobsList').innerHTML = jobs.length ? jobs.map((job) => {
      const active = ['queued', 'processing'].includes(job.status);
      const percent = job.total ? Math.min(job.status === 'finished' ? 100 : 99, Math.floor(job.processed * 100 / job.total)) : 0;
      const downloads = (job.files || []).map((file, index) => `<button type="button" data-export-action="download" data-id="${job.id}" data-part="${index + 1}">${job.files.length === 1 ? '下载完整表格' : `下载第 ${index + 1} 包`} · ${file.records} 条 · ${bytes(file.bytes)}</button>`).join('');
      const warning = job.missingPhotos > 0 ? `<p class="export-warning">有 ${job.missingPhotos} 张照片缺失或无法处理，请核对。Excel 对应位置有提示。${job.status === 'finished' ? `<button type="button" data-export-action="download" data-id="${job.id}" data-part="0">下载照片异常清单</button>` : ''}</p>` : '';
      return `<article class="export-job">
        <div class="export-job-heading"><strong>导出任务 #${job.id} · ${escape(statusText[job.status] || job.status)}</strong><span>${escape(job.createdAt)}</span></div>
        <p><strong>${job.photoMode === 'links' ? '照片链接版' : '带照片版'}</strong></p>
        <p class="muted">${escape(filterSummary(job.filters))}</p>
        <p>已处理 ${job.processed} / ${job.total} 条 · ${job.photoMode === 'links' ? '已整理照片链接' : '已处理照片'} ${job.photos} 张${job.status === 'finished' ? ` · 共 ${job.files.length} 个文件` : ''}</p>
        ${active ? `<progress max="100" value="${percent}" aria-label="导出任务 ${job.id} 进度"></progress><span> ${percent}%${job.processed === job.total ? ' · 正在保存文件' : ''}</span>` : ''}
        ${warning}${job.error ? `<p class="export-warning">${escape(job.error)}</p>` : ''}
        <div class="export-job-actions">${downloads}${active ? `<button type="button" data-export-action="cancel" data-id="${job.id}">取消任务</button>` : ''}</div>
        ${job.status === 'finished' ? `<p class="muted">${job.files.length === 1 ? '完整表格已生成。' : '请下载全部分包。'}文件保留至 ${escape(new Date(job.expiresAt * 1000).toLocaleString('zh-CN'))}。</p>` : ''}
        ${['failed', 'cancelled'].includes(job.status) ? '<p class="muted">如需重新导出，请核对当前筛选条件后选择导出方式重新提交。</p>' : ''}
      </article>`;
    }).join('') : '<p class="muted">暂无导出任务。可按“检查记录”中的筛选条件选择带图或照片链接方式导出。</p>';
  } catch (error) {
    if (visible && current === generation) $('#exportJobsError').textContent = `进度暂时无法刷新：${error.message}。已提交的后台任务会继续处理。`;
  } finally {
    busy = false;
    if (visible) timer = setTimeout(refreshExportJobs, 5000);
  }
}
