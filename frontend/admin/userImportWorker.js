importScripts('./vendor/xlsx-0.20.3.min.js');

self.onmessage = (event) => {
  try {
    const { bytes, options } = event.data;
    const workbook = XLSX.read(bytes, { type: 'array', sheets: options.sheetName, sheetRows: options.maxRows + 2, cellFormula: true, cellHTML: false, cellText: false, bookVBA: true });
    if (workbook.vbaraw) throw new Error('不接受带宏的文件，请使用 .xlsx 模板。');
    const sheet = workbook.Sheets[options.sheetName];
    if (!sheet || !sheet['!ref']) throw new Error(`找不到“${options.sheetName}”工作表，请使用下载的模板。`);
    const range = XLSX.utils.decode_range(sheet['!fullref'] || sheet['!ref']);
    if (range.e.r > options.maxRows) throw new Error(`仅支持第2-${options.maxRows + 1}行，每批最多${options.maxRows}人。`);
    if (range.e.c >= options.fields.length) throw new Error('表格列数不正确，请勿在固定模板右侧增加内容。');
    if (sheet['!merges']?.length) throw new Error('模板数据不能使用合并单元格。');
    const value = (r, c) => {
      const cell = sheet[XLSX.utils.encode_cell({ r, c })];
      if (!cell) return '';
      if (cell.f || cell.F) throw new Error(`第${r + 1}行包含公式，请改为直接填写的文本。`);
      if (cell.t === 'e' || cell.t === 'd' || cell.t === 'b') throw new Error(`第${r + 1}行单元格类型不正确。`);
      if (typeof cell.v === 'number' && (!Number.isSafeInteger(cell.v) || Math.abs(cell.v) >= 1e15)) throw new Error(`第${r + 1}行数字可能失真，请改为文本格式重新填写。`);
      return String(cell.v ?? '');
    };
    options.fields.forEach((field, c) => {
      if (value(0, c).trim() !== field.label) throw new Error(`第${c + 1}列表头应为“${field.label}”，请使用固定模板。`);
    });
    const rows = [];
    for (let r = 1; r <= range.e.r; r++) {
      const values = options.fields.map((field, c) => value(r, c));
      if (values.every((item) => item.trim() === '')) continue;
      const row = { rowNumber: r + 1 };
      options.fields.forEach((field, c) => { row[field.key] = values[c]; });
      rows.push(row);
    }
    if (!rows.length) throw new Error('表格中没有填写用户。');
    self.postMessage({ rows });
  } catch (error) {
    self.postMessage({ error: error.message || '文件格式不正确。' });
  }
};
