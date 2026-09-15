const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const root = path.resolve(__dirname, '..');
const stage = path.join(root, 'outputs', 'user-batch', 'release');
const existing = [
  'backend/app/Http/Controllers/AdminController.php', 'backend/app/Repositories/UserRepository.php',
  'backend/app/Services/AdminService.php', 'backend/public/index.php',
  'frontend/admin/app.js', 'frontend/admin/index.html', 'frontend/admin/redesign.css',
];
const added = [
  'backend/app/Services/UserBatchService.php', 'frontend/admin/userWorkbook.js', 'frontend/admin/userImportWorker.js',
  'frontend/admin/vendor/xlsx-0.20.3.min.js', 'frontend/admin/vendor/SHEETJS-LICENSE.txt', 'frontend/admin/vendor/README.md',
];
const sha = (bytes) => crypto.createHash('sha256').update(bytes).digest('hex');
const copy = (file) => { const target = path.join(stage, file); fs.mkdirSync(path.dirname(target), { recursive: true }); fs.copyFileSync(path.join(root, file), target); };
fs.mkdirSync(stage, { recursive: true });
for (const file of [...existing, ...added, 'tools/deploy_user_batch_20260915.sh']) copy(file);
const baselineRef = process.argv[2] || '28cd2d10a481e10adee986007e24a8e910eb42db';
fs.writeFileSync(path.join(stage, 'deployment-baseline.sha256'), existing.map((file) => `${sha(execFileSync('git', ['show', `${baselineRef}:${file}`], { cwd: root }))}  ${file}`).join('\n') + '\n');
fs.writeFileSync(path.join(stage, 'deployment-current.sha256'), [...existing, ...added].map((file) => `${sha(fs.readFileSync(path.join(root, file)))}  ${file}`).join('\n') + '\n');
fs.writeFileSync(path.join(stage, 'deployment-new-files.txt'), added.join('\n') + '\n');
console.log(`Prepared ${existing.length + added.length} production files in ${stage}`);
