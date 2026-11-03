#!/usr/bin/env node
'use strict';
// Read-only: no fetch, pull, add, commit, config writes, push, deployment or secret values.
const cp = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const args = process.argv.slice(2);
const repoIndex = args.indexOf('--repo');
if (repoIndex >= 0 && !args[repoIndex + 1]) { console.error('--repo requires a path'); process.exit(1); }
const repo = path.resolve(repoIndex >= 0 ? args[repoIndex + 1] : path.join(__dirname, '..'));
const offline = args.includes('--offline');
const expectedRemote = 'https://github.com/hanweixxy-beep/xinyuan-gas-hazard-APP-H5-PHP-MySQL-';
function git(...cmd) {
  const result = cp.spawnSync('git', ['-c', 'credential.interactive=false', ...cmd], {
    cwd: repo, encoding: 'utf8', timeout: 20000, maxBuffer: 16 * 1024 * 1024,
    env: { ...process.env, GIT_TERMINAL_PROMPT: '0', GIT_OPTIONAL_LOCKS: '0' }, windowsHide: true
  });
  if (result.status !== 0) throw new Error('Git check failed: ' + cmd[0]);
  return result.stdout.trimEnd();
}
function samePath(a, b) {
  return process.platform === 'win32' ? path.resolve(a).toLowerCase() === path.resolve(b).toLowerCase() : path.resolve(a) === path.resolve(b);
}
try {
  if (!samePath(repo, git('rev-parse', '--show-toplevel'))) throw new Error('Use the actual repository root');
  const head = git('rev-parse', 'HEAD');
  const branch = git('symbolic-ref', '--short', '-q', 'HEAD');
  const rows = git('status', '--porcelain=v1', '-z', '-uall').split('\0').filter(Boolean);
  const tracked = git('ls-files', '-z').split('\0').filter(Boolean);
  const forbidden = tracked.filter(p => /(^|\/)(?:signing\/|local\.properties$|\.env$)|\.(?:jks|keystore|pem|p12|pfx|key|apk|aab|xlsx|csv|bak)$/i.test(p));
  const seen = new Map(), collisions = [];
  for (const p of tracked) {
    const folded = p.normalize('NFC').toLowerCase();
    if (seen.has(folded) && seen.get(folded) !== p) collisions.push([seen.get(folded), p]);
    seen.set(folded, p);
  }
  const remote = git('remote', 'get-url', 'origin').replace(/\.git$/, '');
  const remoteMatches = remote === expectedRemote || remote === 'git@github.com:hanweixxy-beep/xinyuan-gas-hazard-APP-H5-PHP-MySQL-';
  let remoteHead = null, remoteError = null;
  if (!offline && remoteMatches) {
    try { remoteHead = git('ls-remote', '--heads', 'origin', 'main').split(/\s/)[0] || null; }
    catch { remoteError = 'Cannot verify origin/main; check network and GitHub access'; }
  }
  let developmentEnvironment = 'not configured (optional for source-only handoff)';
  const envFile = path.join(repo, 'backend', '.env');
  if (fs.existsSync(envFile)) {
    const values = {};
    for (const line of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
      const m = line.match(/^\s*([A-Z_]+)\s*=\s*(.*?)\s*$/);
      if (m) values[m[1]] = m[2].replace(/^(['"])(.*)\1$/, '$2');
    }
    developmentEnvironment = ['local', 'development', 'test'].includes(values.APP_ENV) &&
      ['127.0.0.1', 'localhost', '::1'].includes(values.DB_HOST) && /^xinyuan_test_[a-zA-Z0-9_]+$/.test(values.DB_DATABASE || '')
      ? 'local test configuration (connectivity not checked)'
      : 'not confirmed as isolated development; do not run database tests automatically';
  }
  const blockers = [];
  if (branch !== 'main') blockers.push('Not on main; review the intended task branch');
  if (rows.length) blockers.push('Uncommitted files: ' + rows.filter(r => /^.{2} /.test(r)).length);
  if (forbidden.length) blockers.push('Sensitive/data artifact paths are tracked');
  if (collisions.length) blockers.push('Paths collide on case-insensitive filesystems');
  if (!remoteMatches) blockers.push('Origin differs from the expected project repository');
  if (!remoteHead) blockers.push(offline ? 'Remote not checked (offline)' : remoteError || 'No main branch verified');
  if (remoteHead && remoteHead !== head) blockers.push('Local HEAD and remote main differ; inspect before handoff');
  const report = { repo, branch, head, remoteMatches, remoteHead, workingTreeClean: rows.length === 0,
    forbiddenTrackedPaths: forbidden, caseCollisions: collisions, developmentEnvironment,
    readyForCodeHandoff: blockers.length === 0, blockers,
    boundary: 'Checks Git handoff only; does not certify builds, phone behavior, database, deployment or repository visibility.' };
  console.log(JSON.stringify(report, null, 2));
  process.exitCode = blockers.length ? 2 : 0;
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
