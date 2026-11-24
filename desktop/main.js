// M Ride (PC) — tiến trình chính: cửa sổ, nhớ kích thước, cập nhật tự động từ GitHub Releases (tag pc-v*)
const { app, BrowserWindow, ipcMain, shell, net } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const { spawn } = require('child_process');

const REPO = 'satthupc00/M-Ride';
let win = null;
let pending = null;

if (!app.requestSingleInstanceLock()) { app.quit(); }
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });

const stateFile = () => path.join(app.getPath('userData'), 'window.json');
function loadState() {
  try { return JSON.parse(fs.readFileSync(stateFile(), 'utf8')); } catch (e) { return { width: 1366, height: 820 }; }
}
function saveState() {
  if (!win) return;
  try { fs.writeFileSync(stateFile(), JSON.stringify({ ...win.getNormalBounds(), maximized: win.isMaximized() })); } catch (e) {}
}

function createWindow() {
  const s = loadState();
  win = new BrowserWindow({
    x: s.x, y: s.y, width: s.width || 1366, height: s.height || 820, minWidth: 960, minHeight: 620,
    title: 'M Ride', backgroundColor: '#1F2126', show: false,
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#1F2126', symbolColor: '#FFFFFF', height: 58 },
    autoHideMenuBar: true,
    icon: path.join(__dirname, 'renderer', 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false },
  });
  if (s.maximized) win.maximize();
  win.once('ready-to-show', () => win.show());
  win.on('close', saveState);
  win.on('closed', () => { win = null; });
  // link ngoài (ghi công bản đồ...) mở bằng trình duyệt
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: 'deny' }; });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

function newer(a, b) {
  const x = a.split('.').map(n => parseInt(n, 10) || 0), y = b.split('.').map(n => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(x.length, y.length); i++) { const p = x[i] || 0, q = y[i] || 0; if (p !== q) return p > q; }
  return false;
}

ipcMain.handle('app:version', () => app.getVersion());
ipcMain.handle('app:whatsnew', () => {
  try { return JSON.parse(fs.readFileSync(path.join(__dirname, 'renderer', 'whatsnew.json'), 'utf8')); } catch (e) { return null; }
});

ipcMain.handle('upd:check', async () => {
  try {
    const r = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=30`,
      { headers: { 'User-Agent': 'M-Ride-PC', Accept: 'application/vnd.github+json' } });
    if (!r.ok) return { status: 'error' };
    const list = await r.json();
    const rel = list.find(x => !x.draft && !x.prerelease && /^pc-v/.test(x.tag_name));
    if (!rel) return { status: 'latest' };
    const v = rel.tag_name.replace(/^pc-v/, '');
    const asset = (rel.assets || []).find(a => /\.exe$/i.test(a.name));
    if (asset && newer(v, app.getVersion())) {
      pending = { version: v, url: asset.browser_download_url, name: asset.name, size: asset.size || 0, page: rel.html_url };
      return { status: 'available', version: v, notes: rel.body || '' };
    }
    return { status: 'latest' };
  } catch (e) {
    return { status: 'error' };
  }
});

function logUpd(msg) {
  try { fs.appendFileSync(path.join(app.getPath('userData'), 'update.log'), `[${new Date().toISOString()}] ${msg}\n`); } catch (e) {}
}

ipcMain.handle('upd:install', async e => {
  if (!pending) return { ok: false, msg: 'Không có bản mới' };
  const dir = path.join(app.getPath('userData'), 'updates');
  const file = path.join(dir, pending.name);
  try {
    fs.mkdirSync(dir, { recursive: true });
    // net.fetch dùng mạng của Chromium: theo proxy/chứng chỉ của Windows, tự đi theo chuyển hướng của GitHub
    const r = await net.fetch(pending.url, { headers: { 'User-Agent': 'M-Ride-PC' } });
    if (!r.ok || !r.body) throw new Error('HTTP ' + r.status);
    const total = Number(r.headers.get('content-length')) || pending.size || 0;
    const fh = await fs.promises.open(file, 'w');
    let got = 0;
    try {
      const reader = r.body.getReader();
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        await fh.write(Buffer.from(value));
        got += value.length;
        if (total) e.sender.send('upd:progress', Math.min(1, got / total));
      }
    } finally {
      await fh.close();                      // đóng hẳn file trước khi chạy bộ cài
    }
    if (pending.size && got < pending.size) throw new Error(`Tải thiếu (${got}/${pending.size})`);
    logUpd(`downloaded ${got} bytes -> ${file}`);
    // cài im lặng rồi tự mở lại app; nếu không chạy được thì mở bộ cài cho người dùng tự bấm
    const child = spawn(file, ['/S', '--force-run'], { detached: true, stdio: 'ignore' });
    child.on('error', err => { logUpd('spawn error: ' + err.message); shell.openPath(file); });
    child.unref();
    setTimeout(() => app.quit(), 800);
    return { ok: true };
  } catch (err) {
    logUpd('install error: ' + (err && err.stack || err));
    if (pending.page) shell.openExternal(pending.page);
    return { ok: false, msg: String((err && err.message) || err) };
  }
});

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
