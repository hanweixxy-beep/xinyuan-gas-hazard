let libraryPromise;

async function excelLibrary() {
  if (!libraryPromise) {
    libraryPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = new URL('./vendor/xlsx-0.20.3.min.js', import.meta.url).href;
      script.onload = () => resolve(window.XLSX);
      script.onerror = () => { script.remove(); libraryPromise = null; reject(new Error('Excel 组件加载失败，请刷新后重试。')); };
      document.head.append(script);
    });
  }
  return libraryPromise;
}

function textSheet(XLSX, rows, widths) {
  const sheet = XLSX.utils.aoa_to_sheet(rows.map((row) => row.map((value) => String(value ?? ''))));
  sheet['!cols'] = widths.map((wch) => ({ wch }));
  for (const key of Object.keys(sheet)) if (!key.startsWith('!')) sheet[key].z = '@';
  sheet['!autofilter'] = { ref: sheet['!ref'] };
  return sheet;
}

function download(XLSX, workbook, filename) {
  const bytes = XLSX.write(workbook, { bookType: 'xlsx', type: 'array', compression: true });
  const url = URL.createObjectURL(new Blob([bytes], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export async function downloadUserTemplate(options) {
  const XLSX = await excelLibrary();
  const workbook = XLSX.utils.book_new();
  const rows = [options.fields.map((field) => field.label), ...Array.from({ length: options.maxRows }, () => options.fields.map(() => ''))];
  XLSX.utils.book_append_sheet(workbook, textSheet(XLSX, rows, [18, 16, 22, 18, 20, 26, 24]), options.sheetName);
  const notes = [
    ['字段', '填写要求'],
    ['公司编码', '必填。使用“公司编码”工作表中的编码，不填写公司名称。'],
    ['姓名', '必填，最多80个字符。'],
    ['手机号', '必填，6-20位数字。建议保持文本格式，不能与现有账号或同一批次其他行重复。'],
    ['角色', `可选：${options.roles.join('、')}。留空默认为检查员。`],
    ['允许登录端', '可选：APP+后台、仅APP、仅后台。留空默认为APP+后台，管理角色不能选择仅APP。'],
    ['查看本公司全部记录', '是或否，留空默认为否。此字段仅对检查员生效，管理员按角色权限管理。'],
    ['初始密码', '必填，至少8个字符，最多72字节，不能全为空格。登录后请及时修改，表格应妥善保管。'],
    ['导入规则', '仅填写“批量创建用户”工作表第2-201行，不改列名及顺序，不使用公式或合并单元格。'],
    ['确认创建', '全部校验通过后才能整批创建；有错误时整批不创建。导入不会覆盖或重置已有账号。'],
  ];
  XLSX.utils.book_append_sheet(workbook, textSheet(XLSX, notes, [26, 110]), '填写说明');
  XLSX.utils.book_append_sheet(workbook, textSheet(XLSX, [['公司编码', '公司名称'], ...options.companies.map((company) => [company.code, company.name])], [22, 46]), '公司编码');
  download(XLSX, workbook, '用户批量创建模板.xlsx');
}

export async function downloadUsers(users) {
  const XLSX = await excelLibrary();
  const roles = { hq_admin: '总部管理员', company_admin: '公司管理员', inspector: '检查员' };
  const scopes = { both: 'APP+后台', app: '仅APP', admin: '仅后台' };
  const rows = [['账号ID', '公司编码', '公司名称', '姓名', '手机号', '角色', '允许登录端', '数据范围', '状态', '最后登录时间', '创建时间']];
  for (const user of users) rows.push([
    user.id, user.company_code, user.company_name, user.name, user.phone, roles[user.role] || user.role,
    scopes[user.login_scope] || user.login_scope,
    user.role === 'hq_admin' ? '全部公司' : (user.role === 'company_admin' || Number(user.can_view_company_records) === 1 ? '本公司全部记录' : '本人记录'),
    user.status === 'active' ? '启用' : '停用', user.last_login_at || '', user.created_at || '',
  ]);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, textSheet(XLSX, rows, [12, 18, 32, 16, 22, 18, 18, 22, 12, 25, 25]), '用户信息');
  download(XLSX, workbook, `用户信息_${new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)}.xlsx`);
}

export function readUserFile(file, options, signal) {
  if (!/\.xlsx$/i.test(file.name)) return Promise.reject(new Error('请选择按模板填写的 .xlsx 文件。'));
  if (file.size > 2 * 1024 * 1024) return Promise.reject(new Error('文件不能超过 2 MB。'));
  return new Promise((resolve, reject) => {
    const worker = new Worker(new URL('./userImportWorker.js?v=20260915', import.meta.url));
    let finished = false;
    const finish = (error, result) => {
      if (finished) return;
      finished = true;
      clearTimeout(timer);
      worker.terminate();
      signal?.removeEventListener('abort', abort);
      if (error) reject(error); else resolve(result);
    };
    const abort = () => finish(new Error('已取消读取。'));
    const timer = setTimeout(() => finish(new Error('文件读取超时，请检查格式或减少数据量。')), 15000);
    signal?.addEventListener('abort', abort, { once: true });
    if (signal?.aborted) { abort(); return; }
    worker.onmessage = (event) => finish(event.data.error ? new Error(event.data.error) : null, event.data.rows);
    worker.onerror = () => finish(new Error('Excel 文件无法读取，请使用下载的模板重新填写。'));
    file.arrayBuffer().then((bytes) => { if (!finished) worker.postMessage({ bytes, options }, [bytes]); }).catch((error) => finish(error));
  });
}
