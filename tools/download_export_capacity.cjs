const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const crypto = require('node:crypto');
const filename = process.argv[2];
if (filename !== 'single-150.xlsx') throw Error('Only the explicitly authorized 150-record workbook may be downloaded');
const output = path.join('C:/Users/tonyh/Documents/Codex/2026-09-17/xinyuan-gas-development-continuation/outputs/export-capacity-20261102', filename);
const start = Date.now();
const file = fs.createWriteStream(output, { flags: 'wx' });
const hash = crypto.createHash('sha256');
const child = cp.spawn('ssh', ['-i', 'C:/Users/tonyh/.ssh/xinyuan_gashazard_deploy', '-o', 'BatchMode=yes',
  'ubuntu@124.220.26.188', 'sudo -n cat /var/tmp/xinyuan-export-capacity-20261102/' + filename], { windowsHide: true });
let error = '';
child.stderr.on('data', data => error += data.toString());
child.stdout.on('data', data => hash.update(data));
child.stdout.pipe(file);
Promise.all([
  new Promise((resolve, reject) => { child.on('close', code => code === 0 ? resolve() : reject(Error(error))); child.on('error', reject); }),
  new Promise((resolve, reject) => { file.on('finish', resolve); file.on('error', reject); })
]).then(() => {
  const result = { filename, bytes: fs.statSync(output).size, sha256: hash.digest('hex'), downloadSeconds: (Date.now() - start) / 1000 };
  fs.writeFileSync(output + '.json', JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
}).catch(error => { console.error(error.message); process.exitCode = 1; child.kill(); });
