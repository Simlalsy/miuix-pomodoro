/**
 * 走 GitHub Git Data API 推送本地领先的提交（当 github.com:443 被阻断、但 api.github.com 可用时）。
 *
 * 原理：对每个待推提交，逐个上传变更文件的 blob → 组装 tree → 创建 commit（保留原 message/author/日期）
 *      → 更新 refs/heads/<branch>。远端历史与本地提交 SHA 会不同（父链一致、内容一致），
 *      推完后可用 `git reset --soft` 对齐，或直接以远端为准。
 *
 * 用法: node tools/gh-push.js [branch] [--dry-run]
 */
const cp = require('child_process');
const fs = require('fs');

const REPO = 'Simlalsy/miuix-pomodoro';
const BRANCH = process.argv[2] && !process.argv[2].startsWith('--') ? process.argv[2] : 'main';
const DRY = process.argv.includes('--dry-run');
const ROOT = 'e:/ke/cook';

const git = (args) => cp.execSync(`git ${args}`, { cwd: ROOT, maxBuffer: 64 * 1024 * 1024 }).toString().trim();
const gitRaw = (args) => cp.execSync(`git ${args}`, { cwd: ROOT, maxBuffer: 64 * 1024 * 1024 });

/**
 * 在本地写出「与远端等价」的提交对象（同一 tree、同一父链、同一 message + 推送标记），
 * 返回其 sha —— 这样 update-ref 才有对象可指（API 提交的 sha 本地并不存在）。
 */
function materialize(localSha, remoteTree, remoteParent) {
  const raw = gitRaw(`cat-file commit ${localSha}`).toString('utf8');
  const lines = raw.split('\n');
  const out = [lines[0].startsWith('tree ') ? `tree ${remoteTree}` : lines[0]];
  let skippedParents = false;
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i];
    if (line.startsWith('parent ')) {
      if (!skippedParents) {
        out.push(`parent ${remoteParent}`);
        skippedParents = true;
      }
      continue;
    }
    out.push(line);
  }
  const content = Buffer.from(
    out.join('\n').replace(/\n+$/, '') + '\n\n(pushed via GitHub Git Data API)',
    'utf8',
  );
  return gitRaw2(['hash-object', '-t', 'commit', '-w', '--stdin'], content).trim();
}

function gitRaw2(args, input) {
  return cp
    .execSync(`git ${args.join(' ')}`, { cwd: ROOT, input, maxBuffer: 64 * 1024 * 1024 })
    .toString();
}
const token = cp.execSync('gh auth token').toString().trim();
const API = `https://api.github.com/repos/${REPO}`;

async function api(method, path, body) {
  const res = await fetch(API + path, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'User-Agent': 'gh-push-script',
      'Content-Type': 'application/json',
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${text.slice(0, 300)}`);
  return text ? JSON.parse(text) : null;
}

async function main() {
  // 1) 远端当前 head
  const ref = await api('GET', `/git/ref/heads/${BRANCH}`);
  let parentSha = ref.object.sha;
  let lastLocalSha = null;
  console.log('remote head:', parentSha.slice(0, 7));

  // 2) 本地领先的提交（旧 → 新）
  const remoteHead = git(`rev-parse ${BRANCH}`); // 本地分支头（=origin/main 之后的 HEAD）
  const remoteLocal = git(`rev-parse origin/main`);
  const shas = git(`rev-list --reverse ${remoteLocal}..HEAD`).split('\n').filter(Boolean);
  console.log('待推提交:', shas.map((s) => s.slice(0, 7)).join(' '));

  for (const sha of shas) {
    const meta = git(`show -s --format=%an%n%ae%n%aI%n%cn%n%ce%n%cI%n%B ${sha}`).split('\n');    const [an, ae, ad, cn, ce, cd, ...msgLines] = meta;
    const message = msgLines.join('\n').trim();

    const changes = git(`diff-tree --no-commit-id --name-status -r ${sha}`).split('\n').filter(Boolean);
    const tree = [];
    for (const line of changes) {
      const [status, ...rest] = line.split('\t');
      const path = rest.join('\t');
      if (status === 'D') {
        tree.push({ path, mode: '100644', type: 'blob', sha: null });
        continue;
      }
      // 必须从提交对象取内容（而不是工作区文件）：工作区可能是 CRLF，提交里存的是规范化后的字节
      const content = cp.execSync(`git cat-file blob ${sha}:${JSON.stringify(path)}`, {
        cwd: ROOT,
        maxBuffer: 64 * 1024 * 1024,
      });
      const mode = git(`ls-tree ${sha} -- ${JSON.stringify(path)}`).split(/\s+/)[0] || '100644';
      let blobSha;
      if (DRY) {
        blobSha = '0'.repeat(40);
      } else {
        const blob = await api('POST', '/git/blobs', { content: content.toString('base64'), encoding: 'base64' });
        blobSha = blob.sha;
      }
      tree.push({ path, mode, type: 'blob', sha: blobSha });
    }

    const commitMsg = `${message}\n\n(pushed via GitHub Git Data API)`;
    console.log(`  ${sha.slice(0, 7)} ${status(changes.length)} tree=${tree.length} "${message.split('\n')[0].slice(0, 50)}"`);
    if (DRY) continue;

    const newTree = await api('POST', '/git/trees', { base_tree: parentSha ? (await api('GET', `/git/commits/${parentSha}`)).tree.sha : undefined, tree });
    const newCommit = await api('POST', '/git/commits', {
      message: commitMsg,
      tree: newTree.sha,
      parents: [parentSha],
      author: { name: an, email: ae, date: ad },
      committer: { name: cn, email: ce, date: cd },
    });
    // 在本地写出等价提交，供最后 update-ref 使用（父链用远端 sha）
    lastLocalSha = materialize(sha, newTree.sha, parentSha);
    parentSha = newCommit.sha;
  }

  if (DRY) { console.log('dry-run 结束'); return; }

  await api('PATCH', `/git/refs/heads/${BRANCH}`, { sha: parentSha, force: false });
  console.log('远端已更新 ->', parentSha.slice(0, 7));
  // 3) 本地分支与 origin/<branch> 对齐到等价提交（tree/内容一致，sha 与远端一致）
  git(`update-ref refs/heads/${BRANCH} ${lastLocalSha}`);
  git(`update-ref refs/remotes/origin/${BRANCH} ${lastLocalSha}`);
  console.log('本地 ' + BRANCH + ' 与 origin/' + BRANCH + ' 已同步 ->', lastLocalSha.slice(0, 7));
}

function status(n) { return `files=${n}`; }

main().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
