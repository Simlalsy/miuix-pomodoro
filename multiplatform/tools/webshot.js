/**
 * 网页版截图（headless Edge + CDP），用于与原生版截图逐像素比对。
 *
 * 关键点：
 *  - 字体改用 android/ 里内置的那份（与安卓版 WebView 实际渲染完全一致，不依赖 Google Fonts 能否访问）
 *  - 视口/DPR 与原生截图对齐：默认 1280×800 @2 → 2560×1600 PNG
 *  - 通过 localStorage 预设主题，通过点击 .rail-item 切页
 *
 * 用法: node tools/webshot.js <page> <theme> <out.png> [w] [h] [dpr]
 *   page: timer|stats|todos|settings   theme: light|dark|aurora
 */
const fs = require('fs');
const path = require('path');
const os = require('os');
const cp = require('child_process');
const http = require('http');

const REPO = 'e:/ke/cook';
const SHOT_DIR = path.join(os.tmpdir(), 'shot');
const FONTS_DIR = path.join(REPO, 'android/app/src/main/assets/fonts');
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

const page = process.argv[2] || 'timer';
const theme = process.argv[3] || 'dark';
const out = process.argv[4] || path.join(SHOT_DIR, `web-${page}-${theme}.png`);
const W = parseInt(process.argv[5] || '1280', 10);
const H = parseInt(process.argv[6] || '800', 10);
const DPR = parseFloat(process.argv[7] || '2');

function prepareHtml() {
  fs.mkdirSync(SHOT_DIR, { recursive: true });
  // 1) 字体 CSS：把 appassets 的 URL 换成 HTTP 服务的 /fonts/
  const css = fs
    .readFileSync(path.join(FONTS_DIR, 'fonts.css'), 'utf8')
    .replace(/https:\/\/appassets\.androidplatform\.net\/fonts\//g, '/fonts/');
  fs.writeFileSync(path.join(SHOT_DIR, 'fonts.css'), css, 'utf8');

  // 2) index.html：把 Google Fonts 的两条 link 换成上面的本地 CSS
  let html = fs.readFileSync(path.join(REPO, 'index.html'), 'utf8');
  html = html.replace(/<link rel="preconnect"[^>]*>\s*/g, '');
  html = html.replace(/<link[^>]*fonts\.googleapis\.com[^>]*>\s*/g, '');
  html = html.replace(/<link href="https:\/\/fonts\.googleapis\.com[^>]*>\s*/g, '');
  html = html.replace('</head>', `  <link rel="stylesheet" href="/fonts.css" />\n</head>`);
  fs.writeFileSync(path.join(SHOT_DIR, 'shot.html'), html, 'utf8');
}

/** 本地静态服务：/shot.html、/fonts.css、/fonts/<file>（与安卓 WebView 的 appassets 同构） */
function startServer() {
  const server = http.createServer((req, res) => {
    const url = req.url.split('?')[0];
    const send = (file, type) => {
      try {
        const buf = fs.readFileSync(file);
        res.writeHead(200, { 'Content-Type': type });
        res.end(buf);
      } catch {
        res.writeHead(404); res.end('not found');
      }
    };
    if (url === '/' || url === '/shot.html') return send(path.join(SHOT_DIR, 'shot.html'), 'text/html; charset=utf-8');
    if (url === '/fonts.css') return send(path.join(SHOT_DIR, 'fonts.css'), 'text/css; charset=utf-8');
    if (url.startsWith('/fonts/')) return send(path.join(FONTS_DIR, path.basename(url)), 'font/woff2');
    res.writeHead(404); res.end('not found');
  });
  return new Promise((resolve) => server.listen(8123, '127.0.0.1', () => resolve(server)));
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function fetchJson(url) {
  const res = await fetch(url);
  return res.json();
}

async function main() {
  prepareHtml();
  const server = await startServer();
  const profile = path.join(os.tmpdir(), 'edge-shot-profile');
  const edge = cp.spawn(EDGE, [
    '--headless=new',
    '--remote-debugging-port=9333',
    `--user-data-dir=${profile}`,
    '--no-first-run',
    '--disable-gpu',
    '--hide-scrollbars',
    '--allow-file-access-from-files',
    '--disable-web-security',
    'about:blank',
  ], { stdio: 'ignore' });

  // 等 CDP 端口就绪
  let version = null;
  for (let i = 0; i < 60 && !version; i++) {
    try { version = await fetchJson('http://127.0.0.1:9333/json/version'); } catch { await sleep(300); }
  }
  if (!version) { edge.kill(); throw new Error('CDP 未就绪'); }

  const targets = await fetchJson('http://127.0.0.1:9333/json/list');
  const ws = new WebSocket(targets[0].webSocketDebuggerUrl);
  await new Promise((r) => (ws.onopen = r));

  let id = 0;
  const pending = new Map();
  const events = [];
  ws.onmessage = (m) => {
    const msg = JSON.parse(m.data);
    if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
    else events.push(msg);
  };
  const send = (method, params = {}, sessionId) =>
    new Promise((resolve) => {
      const mid = ++id;
      pending.set(mid, resolve);
      ws.send(JSON.stringify({ id: mid, method, params, sessionId }));
    });

  const { result: { targetId } } = await send('Target.createTarget', { url: 'about:blank' });
  const { result: { sessionId } } = await send('Target.attachToTarget', { targetId, flatten: true });

  await send('Page.enable', {}, sessionId);
  await send('Runtime.enable', {}, sessionId);
  await send('Emulation.setDeviceMetricsOverride', {
    width: W, height: H, deviceScaleFactor: DPR, mobile: false,
    // 横屏视口要一并声明屏幕方向，否则 Chromium 的 (orientation: landscape) 仍按竖屏判定
    screenOrientation: W > H
      ? { type: 'landscapePrimary', angle: 90 }
      : { type: 'portraitPrimary', angle: 0 },
  }, sessionId);

  // 预置主题 + 清空数据（在页面脚本运行前写入）；page 支持特殊状态：
  //   running（计时运行中）、todos-items（带待办）、about（关于页）
  const basePage = page === 'running' ? 'timer' : page === 'todos-items' ? 'todos' : page === 'about' ? 'settings' : page;
  const settings = JSON.stringify({ theme, focusMinutes: 25, shortMinutes: 5, longMinutes: 15 });
  const presetTodos = page === 'todos-items'
    ? `localStorage.setItem('pomodoro.miuix.todos', ${JSON.stringify(JSON.stringify([
        { text: '读完《重构》第 3 章', done: true },
        { text: '写周报', done: false },
        { text: '晚上跑步 5km', done: false },
      ]))});`
    : '';
  await send('Page.addScriptToEvaluateOnNewDocument', {
    source: `try{localStorage.clear();localStorage.setItem('pomodoro.miuix.settings', ${JSON.stringify(settings)});${presetTodos}}catch(e){}`,
  }, sessionId);

  await send('Page.navigate', { url: 'http://127.0.0.1:8123/shot.html' }, sessionId);
  await sleep(500);

  const evalJs = async (expr) => {
    const r = await send('Runtime.evaluate', { expression: expr, awaitPromise: true, returnByValue: true }, sessionId);
    return r.result?.result?.value;
  };

  // 等加载页消失 + 字体就绪
  for (let i = 0; i < 60; i++) {
    const ok = await evalJs(`(!!document.getElementById('loading')===false || document.getElementById('loading').classList.contains('hidden')) && document.fonts.status==='loaded'`);
    if (ok) break;
    await sleep(300);
  }
  // 切页
  if (basePage !== 'timer') {
    await evalJs(`document.querySelector('.rail-item[data-page="${basePage}"]')?.click()`);
    await sleep(700);
  }
  if (page === 'about') {
    await evalJs(`document.getElementById('aboutEntry')?.click()`);
    await sleep(900);
  } else if (page === 'running') {
    // 伪造「专注进行中、剩 11:27、本周期已完成 2 轮」的界面状态（与原生 debugTimerState 对应）
    await evalJs(`(() => {
      remaining = 687; total = 1500; running = true; endAt = Date.now() + remaining * 1000;
      completedSessions = 2;
      document.getElementById('timerRing').classList.add('running');
      document.getElementById('startText').textContent = '暂停';
      document.getElementById('startIcon').textContent = 'pause';
      document.getElementById('startBtn').classList.add('running');
      updateTime(); updateRing(); updateDots();
    })()`);
    await sleep(400);
  } else {
    await sleep(1400); // 等入场动画结束
  }

  // 极光背景漂移与原生截图对齐：steps(130) 冻结在第 2 步（原生 30 帧 = 480ms 时的相位；
  // 换算见 index.html @keyframes auroraDrift 注释）
  if (theme === 'aurora') {
    await evalJs(`(() => {
      const i = document.querySelector('.aurora-bg > i');
      if (i) { i.style.animation = 'none'; i.style.transform = 'translate3d(-1.3675%, -9.7094%, 0)'; }
    })()`);
    await sleep(150);
  }

  // 环形呼吸光晕冻结到基准态（animation:none → opacity .55、无缩放），
  // 对应原生 State.debugFreezeAnim（截图时停在同一相位，帧才可比）
  await evalJs(`(() => {
    const st = document.createElement('style');
    st.textContent = '.ring-ambient{animation:none!important}.timer-center .dot{animation:none!important}';
    document.head.appendChild(st);
  })()`);
  await sleep(80);

  const fontInfo = await evalJs(`(function(){
    const el = document.querySelector('.material-symbols-rounded');
    const cs = el ? getComputedStyle(el) : null;
    const loaded = Array.from(document.fonts).map(f => f.family + ':' + f.status).join(',');
    return JSON.stringify({family: cs && cs.fontFamily, text: el && el.textContent, loaded});
  })()`);
  console.log('fonts:', fontInfo);

  const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }, sessionId);
  fs.writeFileSync(out, Buffer.from(shot.result.data, 'base64'));

  const size = fs.statSync(out).size;
  console.log(`saved ${out} (${(size / 1024).toFixed(0)} KB)`);

  ws.close();
  edge.kill();
  server.close();
  await sleep(300);
}

main().catch((e) => { console.error('FAILED', e); process.exit(1); });
