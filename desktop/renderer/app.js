'use strict';
/* M Ride (PC) — chỉ xem lại hành trình đã đồng bộ từ điện thoại. */
const API = window.mride || null;            // cầu nối với tiến trình chính (preload)
if (API) document.documentElement.classList.add('electron');
const QS = new URLSearchParams(location.search);
const DEMO = QS.has('demo');                 // dữ liệu mẫu để xem trước giao diện
const PLAIN = QS.has('plain');               // nền bản đồ trơn (không tải bản đồ)
const FB_CONFIG = { apiKey: 'AIzaSyADH55Tj6ERIWyvridAvg3DfQM8qjNZvLM', authDomain: 'm-ride-4e13e.firebaseapp.com', projectId: 'm-ride-4e13e' };
const STYLE = 'https://tiles.openfreemap.org/styles/liberty';
const SPEED_COLOR = ['interpolate', ['linear'], ['get', 's'], 0, '#E5383B', 10, '#F28C28', 20, '#F5C842', 30, '#2EB872', 45, '#1FB5C9', 60, '#3D7BFF'];
const RATES = [1, 10, 30, 60, 120];

const ICON = {
  side: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/></svg>',
  gear: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M20 7h-9M14 17H5"/><circle cx="17" cy="17" r="3"/><circle cx="7" cy="7" r="3"/></svg>',
  refresh: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 2v6h-6"/><path d="M3 12a9 9 0 0 1 15-6.7L21 8"/><path d="M3 22v-6h6"/><path d="M21 12a9 9 0 0 1-15 6.7L3 16"/></svg>',
  close: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>',
  play: '<svg viewBox="0 0 24 24" fill="currentColor"><path d="M7 4.5v15a1 1 0 0 0 1.5.9l12-7.5a1 1 0 0 0 0-1.7l-12-7.5A1 1 0 0 0 7 4.5z"/></svg>',
  pause: '<svg viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="5" height="16" rx="1"/><rect x="13" y="4" width="5" height="16" rx="1"/></svg>',
  nav: f => `<svg viewBox="0 0 24 24" fill="${f ? '#fff' : 'none'}" stroke="#fff" stroke-width="2" stroke-linejoin="round"><path d="M3 11 22 2 13 21 11 13z"/></svg>`,
  cube: f => `<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linejoin="round"><path d="M21 8 12 3 3 8v8l9 5 9-5z" fill="${f ? 'rgba(255,255,255,.35)' : 'none'}"/><path d="M3 8l9 5 9-5M12 13v8"/></svg>`,
  compass: f => `<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88z" fill="${f ? '#fff' : 'none'}"/></svg>`,
  north: '<svg viewBox="0 0 24 24"><path d="M12 2 16 12H8z" fill="#E5383B"/><path d="M12 22 8 12h8z" fill="#fff"/></svg>',
  eye: '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/></svg>',
  eyeOff: '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/><path d="M3 3l18 18"/></svg>',
  spark: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"/></svg>',
};

/* ---------- tiện ích ---------- */
const $ = id => document.getElementById(id);
const pad = n => String(n).padStart(2, '0');
const vi1 = new Intl.NumberFormat('vi-VN', {maximumFractionDigits: 1, minimumFractionDigits: 1});
const hm = t => { const d = new Date(t); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; };
const hms = t => { const d = new Date(t); return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`; };
const WD = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
const dayLabel = t => { const d = new Date(t); return `${WD[d.getDay()]}, ${pad(d.getDate())}/${pad(d.getMonth() + 1)}`; };
const dayKey = t => { const d = new Date(t); return new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime(); };
const dur = ms => { const m = Math.round(ms / 60000); return m >= 60 ? `${Math.floor(m / 60)}:${pad(m % 60)}` : `${m} ph`; };
const elapsedStr = ms => { const x = Math.max(0, Math.floor(ms / 1000)); return `${Math.floor(x / 3600)}:${pad(Math.floor(x / 60) % 60)}:${pad(x % 60)}`; };
const store = { get: k => { try { return localStorage.getItem(k); } catch (e) { return null; } }, set: (k, v) => { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch (e) {} } };
let toastT = 0;
function toast(msg) { const el = $('toast'); el.textContent = msg; el.classList.add('show'); clearTimeout(toastT); toastT = setTimeout(() => el.classList.remove('show'), 2600); }

function decode(s) {
  const out = []; let i = 0, lat = 0, lon = 0;
  const next = () => { let r = 0, sh = 0, b; do { b = s.charCodeAt(i++) - 63; r |= (b & 31) << sh; sh += 5; } while (b >= 32 && i < s.length); return (r & 1) ? ~(r >> 1) : (r >> 1); };
  while (i < s.length) { lat += next(); if (i >= s.length) break; lon += next(); out.push([lon / 1e5, lat / 1e5]); }
  return out;
}
function parseTrip(id, d) {
  const coords = decode(d.poly || '');
  const ts = (d.ts || '').split(',').map(Number);
  const sp = (d.sp || '').split(',').map(Number);
  return { id, name: d.name || 'Chuyến đi', start: d.start || 0, end: d.end || d.start || 0, dist: d.dist || 0, max: d.max || 0,
    coords, times: coords.map((_, i) => (d.start || 0) + (ts[i] ?? i) * 1000), speeds: coords.map((_, i) => sp[i] || 0) };
}

/* ---------- trạng thái chung ---------- */
let trips = [];
let selIds = [];
const setSync = (cls, label) => { const b = $('syncDot'); b.className = 'syncdot ' + (cls || ''); b.title = label; b.setAttribute('aria-label', 'Đồng bộ: ' + label); };

/* ---------- dữ liệu mẫu ---------- */
function demoTrips() {
  const base = [[106.6655, 10.7925], [106.6702, 10.7891], [106.676, 10.786], [106.6822, 10.7838], [106.6868, 10.7811], [106.6905, 10.779], [106.695, 10.7772], [106.6988, 10.776], [106.7012, 10.7742], [106.7045, 10.7728], [106.708, 10.7716]];
  const mk = (id, name, dayOff, h, m, flip, km) => {
    const pts = flip ? [...base].reverse() : base; const coords = [];
    for (let i = 0; i < pts.length - 1; i++) for (let k = 0; k < 16; k++) { const f = k / 16; coords.push([pts[i][0] + (pts[i + 1][0] - pts[i][0]) * f + Math.sin(i + k) * 1e-4, pts[i][1] + (pts[i + 1][1] - pts[i][1]) * f]); }
    const start = new Date(); start.setDate(start.getDate() - dayOff); start.setHours(h, m, 0, 0);
    const times = coords.map((_, i) => start.getTime() + i * 9000);
    const speeds = coords.map((_, i) => Math.max(2, 26 + 18 * Math.sin(i / 11) + 8 * Math.sin(i / 3)));
    return { id, name, start: times[0], end: times.at(-1), dist: km * 1000, max: Math.max(...speeds), coords, times, speeds };
  };
  return [mk('a', 'Quận 3 → Quận 1', 0, 7, 42, false, 8.4), mk('b', 'Quận 1 → Quận 3', 0, 17, 36, true, 8.1),
    mk('c', 'Quận 3 → Quận 1', 1, 7, 50, false, 8.6), mk('d', 'Quanh Quận 1', 1, 12, 5, true, 2.0), mk('e', 'Quận 1 → Quận 3', 1, 18, 2, true, 12.7)];
}

/* ---------- danh sách hành trình ---------- */
function drawThumb(cv, coords) {
  const dpr = window.devicePixelRatio || 1, S = 48; cv.width = S * dpr; cv.height = S * dpr;
  const g = cv.getContext('2d'); g.scale(dpr, dpr);
  if (coords.length < 2) return;
  const step = Math.max(1, Math.floor(coords.length / 60)), pts = coords.filter((_, i) => i % step === 0);
  const xs = pts.map(p => p[0]), ys = pts.map(p => p[1]);
  const mnx = Math.min(...xs), mxx = Math.max(...xs), mny = Math.min(...ys), mxy = Math.max(...ys), span = Math.max(mxx - mnx, mxy - mny, 1e-6);
  const P = 7, W = S - P * 2, ox = P + (W - (mxx - mnx) / span * W) / 2, oy = P + (W - (mxy - mny) / span * W) / 2;
  const xy = p => [ox + (p[0] - mnx) / span * W, oy + (mxy - p[1]) / span * W];
  g.strokeStyle = '#E5383B'; g.lineWidth = 2.2; g.lineCap = g.lineJoin = 'round'; g.beginPath();
  pts.forEach((p, i) => { const [x, y] = xy(p); i ? g.lineTo(x, y) : g.moveTo(x, y); }); g.stroke();
  const [sx, sy] = xy(pts[0]); g.fillStyle = '#E8E9EC'; g.beginPath(); g.arc(sx, sy, 2.6, 0, 7); g.fill();
}
function renderList() {
  const list = $('list'); list.innerHTML = '';
  $('tripCount').textContent = trips.length ? `${trips.length} chuyến` : '';
  if (!trips.length) { list.innerHTML = '<div class="none">Chưa có hành trình nào.<br>Đi xe với app trên điện thoại rồi quay lại đây.</div>'; return; }
  const groups = new Map();
  [...trips].sort((a, b) => b.start - a.start).forEach(t => { const k = dayKey(t.start); if (!groups.has(k)) groups.set(k, []); groups.get(k).push(t); });
  for (const [k, ts] of groups) {
    const h = document.createElement('div'); h.className = 'day';
    const km = ts.reduce((a, t) => a + t.dist, 0) / 1000;
    h.innerHTML = `<b>${dayLabel(k)}</b> · ${vi1.format(km)} km`;
    const dp = document.createElement('button'); dp.className = 'dplay'; dp.textContent = '▶ Phát lại ngày'; dp.title = 'Phát lại cả ngày';
    dp.onclick = () => select(ts.map(t => t.id), 'Cả ngày · ' + dayLabel(k)); h.appendChild(dp); list.appendChild(h);
    for (const t of ts) {
      const b = document.createElement('button'); b.className = 'item' + (selIds.length === 1 && selIds[0] === t.id ? ' sel' : ''); b.dataset.id = t.id;
      b.innerHTML = `<canvas></canvas><div class="ti"><b></b><span>${hm(t.start)} – ${hm(t.end)} · ${dur(t.end - t.start)}</span></div><div class="km">${vi1.format(t.dist / 1000)}<small> km</small></div>`;
      b.querySelector('b').textContent = t.name; b.title = t.name;
      b.onclick = () => select([t.id], t.name);
      list.appendChild(b); drawThumb(b.querySelector('canvas'), t.coords);
    }
  }
}

/* ---------- phát lại ---------- */
// gộp các điểm liền nhau có tốc độ gần nhau (bước 3 km/h) thành một nét — nét dài không bị mất khi zoom xa
function speedRuns(coords, ss) {
  const feats = []; if (coords.length < 2) return {type: 'FeatureCollection', features: feats};
  const bk = i => Math.round(((ss[i] + ss[i + 1]) / 2) / 3);
  let run = [coords[0], coords[1]], b = bk(0);
  for (let i = 1; i < coords.length - 1; i++) {
    const nb = bk(i);
    if (nb !== b) { feats.push({type: 'Feature', properties: {s: b * 3}, geometry: {type: 'LineString', coordinates: run}}); run = [coords[i]]; b = nb; }
    run.push(coords[i + 1]);
  }
  feats.push({type: 'Feature', properties: {s: b * 3}, geometry: {type: 'LineString', coordinates: run}});
  return {type: 'FeatureCollection', features: feats};
}

let map = null, mapReady = false, R = null;    // R: dữ liệu chuyến đang phát
let t = 0, playing = true, rate = 60, follow = true, rotate = false, camB = 0, toNorth = false, followLock = 0, hidden = false;
let showElapsed = store.get('mr-elapsed') === '1';
let mode3d = store.get('mr-3d') === '1';
const store3d = v => store.set('mr-3d', v ? '1' : '0');
const paint3d = () => { $('b3dBtn').classList.toggle('on', mode3d); $('b3dBtn').innerHTML = ICON.cube(mode3d) + '3D'; };
const pointers = new Map();
let timeMarker, hoverMarker, tlEl, hoverEl, hoverOn = false;

function buildReplay(ids) {
  const sel = trips.filter(x => ids.includes(x.id)).sort((a, b) => a.start - b.start);
  const coords = [], times = [], speeds = [];
  sel.forEach(x => { coords.push(...x.coords); times.push(...x.times); speeds.push(...x.speeds); });
  const n = coords.length, vt = new Float64Array(n);
  for (let i = 1; i < n; i++) { const dt = (times[i] - times[i - 1]) / 1000; vt[i] = vt[i - 1] + (dt > 120 ? 2 : Math.max(0, dt)); }
  const ss = speeds.map((_, i) => { let a = 0, c = 0; for (let k = Math.max(0, i - 3); k <= Math.min(n - 1, i + 3); k++) { a += speeds[k]; c++; } return a / c; });
  const total = n ? vt[n - 1] : 0;
  const idx = x => { let lo = 0, hi = n - 1; while (lo < hi) { const m = (lo + hi + 1) >> 1; if (vt[m] <= x) lo = m; else hi = m - 1; } return lo; };
  const frac = (i, x) => i >= n - 1 ? 0 : Math.min(1, Math.max(0, (x - vt[i]) / ((vt[i + 1] - vt[i]) || 1)));
  const pos = x => { const i = idx(x); if (i >= n - 1) return coords[n - 1]; const f = frac(i, x); return [coords[i][0] + (coords[i + 1][0] - coords[i][0]) * f, coords[i][1] + (coords[i + 1][1] - coords[i][1]) * f]; };
  const clock = x => { const i = idx(x); if (i >= n - 1) return times[n - 1]; return times[i] + (times[i + 1] - times[i]) * frac(i, x); };
  const speed = x => { const i = idx(x); if (i >= n - 1) return speeds[n - 1] || 0; return speeds[i] + (speeds[i + 1] - speeds[i]) * frac(i, x); };
  const heading = x => { const a = pos(x), b = pos(Math.min(total, x + 10)); const dy = b[1] - a[1], dx = (b[0] - a[0]) * Math.cos(a[1] * Math.PI / 180); if (Math.hypot(dx, dy) < 3e-5) return null; return (Math.atan2(dx, dy) * 180 / Math.PI + 360) % 360; };
  const segs = speedRuns(coords, ss);
  return {sel, coords, times, n, vt, total, idx, pos, clock, speed, heading, segs,
    dist: sel.reduce((a, x) => a + x.dist, 0), max: Math.max(0, ...sel.map(x => x.max)), start: times[0], end: times[n - 1],
    moving: sel.reduce((a, x) => a + (x.end - x.start), 0)};
}

function select(ids, title) {
  selIds = ids;
  document.querySelectorAll('.item').forEach(b => b.classList.toggle('sel', ids.length === 1 && b.dataset.id === ids[0]));
  R = buildReplay(ids);
  if (R.n < 2) { toast('Chuyến này chưa đủ dữ liệu để phát lại'); return; }
  t = 0; playing = true; camB = 0; toNorth = false;
  $('empty').hidden = true; $('dock').hidden = false; $('legend').hidden = false;
  $('tripTitle').innerHTML = `<b></b> · ${dayLabel(R.start)} · ${hm(R.start)}–${hm(R.end)}`;
  $('tripTitle').querySelector('b').textContent = title;
  $('meta').textContent = title;
  const avg = R.moving > 0 ? (R.dist / 1000) / (R.moving / 3.6e6) : 0;
  $('stats').innerHTML = [[vi1.format(R.dist / 1000), 'km', 'Quãng đường'], [dur(R.moving), '', 'Thời gian'], [Math.round(avg), 'km/h', 'Trung bình'], [Math.round(R.max), 'km/h', 'Tối đa']]
    .map(([v, u, l]) => `<div class="stat"><b>${v}${u ? ` <small>${u}</small>` : ''}</b><span>${l}</span></div>`).join('');
  paint();
  ensureMap().then(() => {
    map.getSource('route').setData(R.segs);
    map.getSource('start').setData(pt(R.coords[0]));
    map.setBearing(0);
    if (follow) map.jumpTo({center: R.coords[0], zoom: 16.5, padding: camPad()}); else fitAll();
    apply3d(false); if (mode3d) map.jumpTo({pitch: 68});
  });
}

const fc = f => ({type: 'FeatureCollection', features: f});
const pt = c => ({type: 'Feature', geometry: {type: 'Point', coordinates: c}, properties: {}});
const ln = cs => ({type: 'Feature', geometry: {type: 'LineString', coordinates: cs}, properties: {}});
function camPad() {
  if (hidden) return {top: 60, bottom: 60, left: 60, right: 60};
  const dock = $('dock').getBoundingClientRect(), stage = $('stage').getBoundingClientRect();
  const wide = stage.width > 900;
  return wide ? {top: 70, bottom: 40, left: Math.min(dock.width + 40, stage.width / 2), right: 40} : {top: 80, bottom: dock.height + 30, left: 30, right: 30};
}
function fitAll() { if (!R) return; const b = new maplibregl.LngLatBounds(R.coords[0], R.coords[0]); R.coords.forEach(c => b.extend(c)); map.fitBounds(b, {padding: camPad(), duration: 500, maxZoom: 17}); }

function ensureMap() {
  if (map) return mapReady ? Promise.resolve() : new Promise(r => map.once('load', r));
  map = new maplibregl.Map({
    container: 'map', attributionControl: {compact: true},
    style: PLAIN ? {version: 8, sources: {}, layers: [{id: 'bg', type: 'background', paint: {'background-color': '#E9E6DF'}}]} : STYLE,
    center: [106.70, 10.78], zoom: 13, dragRotate: true, pitchWithRotate: false, touchPitch: false, maxPitch: 76,
  });
  map.scrollZoom.enable({around: 'center'});
  tlEl = document.createElement('div'); tlEl.className = 'tlabel';
  timeMarker = new maplibregl.Marker({element: tlEl, anchor: 'bottom', offset: [0, -16]});
  hoverEl = document.createElement('div'); hoverEl.className = 'tlabel hover';
  hoverMarker = new maplibregl.Marker({element: hoverEl, anchor: 'bottom', offset: [0, -12]});
  return new Promise(resolve => map.on('load', () => {
    const nameLayers = new Set(['place', 'transportation_name', 'water_name', 'poi', 'aerodrome_label', 'mountain_peak', 'park', 'waterway', 'aeroway']);
    for (const l of map.getStyle().layers) {
      if (l.type !== 'symbol' || !nameLayers.has(l['source-layer']) || /shield|housenumber|ref/.test(l.id)) continue;
      try { map.setLayoutProperty(l.id, 'text-field', ['coalesce', ['get', 'name:vi'], ['get', 'name'], ['get', 'name:latin']]); } catch (e) {}
    }
    ['route', 'prog', 'start', 'dot'].forEach(s => map.addSource(s, {type: 'geojson', data: fc([]), tolerance: 0}));
    const L = {'line-cap': 'round', 'line-join': 'round'};
    map.addLayer({id: 'route-casing', type: 'line', source: 'route', layout: L, paint: {'line-color': '#2A2C31', 'line-width': 7.5}});
    map.addLayer({id: 'route', type: 'line', source: 'route', layout: L, paint: {'line-color': SPEED_COLOR, 'line-width': 5}});
    map.addLayer({id: 'route-hit', type: 'line', source: 'route', layout: L, paint: {'line-color': '#000', 'line-width': 26, 'line-opacity': 0}});
    map.addLayer({id: 'prog', type: 'line', source: 'prog', layout: L, paint: {'line-color': '#A3A8B0', 'line-width': 5.5, 'line-opacity': .92}});
    map.addLayer({id: 'start', type: 'circle', source: 'start', paint: {'circle-radius': 6, 'circle-color': '#1F2126', 'circle-stroke-color': '#fff', 'circle-stroke-width': 3}});
    map.addLayer({id: 'dot-halo', type: 'circle', source: 'dot', paint: {'circle-radius': 16, 'circle-color': '#E5383B', 'circle-opacity': .25}});
    map.addLayer({id: 'dot', type: 'circle', source: 'dot', paint: {'circle-radius': 7.5, 'circle-color': '#E5383B', 'circle-stroke-color': '#fff', 'circle-stroke-width': 3}});
    // nhãn giờ vẽ ngay trong bản đồ (cùng lúc với chấm xe) để không bị lệch khi nghiêng 3D
    map.addLayer({id: 'dot-label', type: 'symbol', source: 'dot', layout: {'text-field': ['get', 'label'], 'text-font': ['Noto Sans Bold'],
      'text-size': 13, 'text-offset': [0, -1.9], 'text-allow-overlap': true, 'text-ignore-placement': true, 'text-pitch-alignment': 'viewport'},
      paint: {'text-color': '#FFFFFF', 'text-halo-color': '#1F2126', 'text-halo-width': 2.4}});
    styleOk = true;
    mapReady = true; wireMap(); requestAnimationFrame(loop); resolve();
  }));
}


// ---- Chế độ 3D: nghiêng kiểu dẫn đường + địa hình núi đồi + nhà khối 3D (tắt thì về phẳng, không tải thêm gì) ----
let styleOk = false, zoomGoal = null;
const DEM = 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png';
function ensure3dLayers() {
  if (map.getSource('dem')) return;
  map.addSource('dem', {type: 'raster-dem', tiles: [DEM], encoding: 'terrarium', tileSize: 256, maxzoom: 14});
  map.addSource('dem-hs', {type: 'raster-dem', tiles: [DEM], encoding: 'terrarium', tileSize: 256, maxzoom: 14});
  const layers = map.getStyle().layers;
  const firstSym = (layers.find(l => l.type === 'symbol') || {}).id;
  map.addLayer({id: 'hills', type: 'hillshade', source: 'dem-hs', layout: {visibility: 'none'},
    paint: {'hillshade-exaggeration': .5, 'hillshade-shadow-color': '#4A4034', 'hillshade-highlight-color': '#FFFFFF'}}, firstSym || 'route-casing');
  const b = layers.find(l => l['source-layer'] === 'building');
  if (b) map.addLayer({id: 'b3d', type: 'fill-extrusion', source: b.source, 'source-layer': 'building', minzoom: 14, layout: {visibility: 'none'},
    paint: {'fill-extrusion-color': '#D9D3C8', 'fill-extrusion-opacity': .85,
      'fill-extrusion-height': ['coalesce', ['get', 'render_height'], 8], 'fill-extrusion-base': ['coalesce', ['get', 'render_min_height'], 0]}}, 'route-casing');
}
function apply3d(animate) {
  if (!map || !styleOk) return;
  ensure3dLayers();
  const vis = mode3d ? 'visible' : 'none';
  ['hills', 'b3d'].forEach(id => map.getLayer(id) && map.setLayoutProperty(id, 'visibility', vis));
  try { map.setTerrain(mode3d ? {source: 'dem', exaggeration: 1.5} : null); } catch (e) {}
  try { if (mode3d) map.setSky({'sky-color': '#9CC9F5', 'horizon-color': '#E8F1F8', 'fog-color': '#F2F4F6', 'sky-horizon-blend': .6, 'horizon-fog-blend': .6, 'fog-ground-blend': .5}); } catch (e) {}
  if (animate) map.easeTo({pitch: mode3d ? 68 : 0, zoom: mode3d && follow ? Math.max(map.getZoom(), 16) : map.getZoom(), duration: 700});
}
function set3d(v) {
  mode3d = v; store3d(v);
  if (v && follow) { rotate = true; toNorth = false; camB = map ? map.getBearing() : 0; if (map) zoomGoal = Math.max(map.getZoom(), 16); }
  paint3d(); paint(); apply3d(true);
}

function nearestIdx(p0) {
  let best = -1, bd = 1e9;
  for (let k = 0; k < R.n; k++) { const q = map.project(R.coords[k]); const d = (q.x - p0.x) ** 2 + (q.y - p0.y) ** 2; if (d < bd) { bd = d; best = k; } }
  return bd < 30 * 30 ? best : -1;
}
function wireMap() {
  const cv = map.getCanvasContainer();
  cv.addEventListener('pointerdown', e => pointers.set(e.pointerId, {x: e.clientX, y: e.clientY, btn: e.button, ctrl: e.ctrlKey}));
  cv.addEventListener('pointermove', e => {
    const p0 = pointers.get(e.pointerId);
    if (!p0 || pointers.size !== 1 || !follow || p0.btn !== 0 || p0.ctrl) return;
    if (Math.hypot(e.clientX - p0.x, e.clientY - p0.y) > 6) { follow = false; paint(); }
  });
  ['pointerup', 'pointercancel', 'pointerleave'].forEach(ev => cv.addEventListener(ev, e => pointers.delete(e.pointerId)));
  map.on('rotatestart', e => { if (e.originalEvent) { rotate = false; toNorth = false; paint(); } });
  map.on('rotateend', () => { camB = map.getBearing(); });
  map.on('rotate', () => { const s = $('northBtn').firstChild; if (s) s.style.transform = `rotate(${-map.getBearing()}deg)`; });
  map.on('click', e => {
    if (!R) return;
    if (map.queryRenderedFeatures(e.point, {layers: ['route-hit']}).length) { const k = nearestIdx(e.point); if (k >= 0) { t = R.vt[k]; followLock = 0; return; } }
    if (hidden) setHidden(false);
  });
  map.on('mousemove', 'route-hit', e => {
    map.getCanvas().style.cursor = 'pointer'; const k = nearestIdx(e.point); if (k < 0) return;
    const ct = R.clock(R.vt[k]); hoverEl.textContent = showElapsed ? dur(ct - R.start) : hm(ct);
    hoverMarker.setLngLat(R.coords[k]); if (!hoverOn) { hoverMarker.addTo(map); hoverOn = true; }
  });
  map.on('mouseleave', 'route-hit', () => { map.getCanvas().style.cursor = ''; hoverMarker.remove(); hoverOn = false; });
}

function flyToCar() {
  if (!R) return; followLock = performance.now() + 650;
  const z = map.getZoom() < 14 ? 16.5 : map.getZoom();
  map.easeTo({center: R.pos(t), zoom: z, padding: camPad(), duration: 600});
}
function setFollow(v) { follow = v; if (follow) flyToCar(); paint(); }
function setRotate(v) { rotate = v; toNorth = false; if (rotate && !follow) { follow = true; flyToCar(); } camB = map ? map.getBearing() : 0; paint(); }
function goNorth() { rotate = false; paint(); if (!map) return; if (follow) { camB = map.getBearing(); toNorth = true; } else map.easeTo({bearing: 0, duration: 400}); }
function setHidden(v) {
  hidden = v; document.body.classList.toggle('immersive', v); $('restoreBtn').hidden = !v;
  setTimeout(() => { if (map) { map.resize(); if (follow) flyToCar(); } }, 30);
}
function seekBy(sec) { if (R) { t = Math.min(R.total, Math.max(0, t + sec)); followLock = 0; } }

function paint() {
  $('folBtn').classList.toggle('on', follow); $('folBtn').innerHTML = ICON.nav(follow) + 'Bám xe';
  $('rotBtn').classList.toggle('on', rotate); $('rotBtn').innerHTML = ICON.compass(rotate) + 'Xoay theo xe';
  $('playBtn').innerHTML = playing ? ICON.pause : ICON.play;
  $('ctog').classList.toggle('on', showElapsed); $('ctog').textContent = showElapsed ? '⇄ Đã đi' : '⇄ Giờ thực';
  [...$('rates').children].forEach(b => b.classList.toggle('on', +b.dataset.r === rate));
}

let last = 0;
function loop(now) {
  const dt = last ? (now - last) / 1000 : 0; last = now;
  if (R && R.n >= 2) {
    if (playing) { t = Math.min(R.total, t + dt * rate); if (t >= R.total) { playing = false; paint(); } }
    const p = R.pos(t), i = R.idx(t);
    map.getSource('prog').setData(ln([p].concat(R.coords.slice(i + 1))));
    if (follow && now > followLock && pointers.size === 0) {
      const o = {center: p, padding: camPad()};
      if (rotate) { const h = R.heading(t); if (h != null) { const dl = ((h - camB + 540) % 360) - 180; camB = (camB + dl * .12 + 360) % 360; } o.bearing = camB; }
      else if (toNorth) { const dl = ((0 - camB + 540) % 360) - 180; camB = Math.abs(dl) < .5 ? 0 : (camB + dl * .15 + 360) % 360; if (camB === 0) toNorth = false; o.bearing = camB; }
      // nghiêng / phóng gần dần khi bật-tắt 3D (đi chung với camera bám xe để không bị cắt ngang)
      { const tp = mode3d ? 68 : 0, cp = map.getPitch(); if (Math.abs(tp - cp) > .3) o.pitch = cp + (tp - cp) * .12; }
      if (zoomGoal != null) { const cz = map.getZoom(); if (Math.abs(zoomGoal - cz) < .02) zoomGoal = null; else o.zoom = cz + (zoomGoal - cz) * .12; }
      map.jumpTo(o);
    }
    const ct = R.clock(t), el = ct - R.start;
    { const f = pt(p); f.properties.label = showElapsed ? dur(el) : hm(ct); map.getSource('dot').setData(f); }
    $('clock').textContent = showElapsed ? elapsedStr(el) : hms(ct);
    $('spd').textContent = Math.round(R.speed(t));
    const sc = $('scrub'); if (document.activeElement !== sc) sc.value = R.total ? t / R.total * 1000 : 0;
    sc.style.setProperty('--fill', (R.total ? t / R.total * 100 : 0) + '%');
  }
  requestAnimationFrame(loop);
}

/* ---------- điều khiển ---------- */
function wireUi() {
  $('sideBtn').innerHTML = ICON.side; $('setBtn').innerHTML = ICON.gear; $('updBtn').innerHTML = ICON.refresh;
  $('northBtn').innerHTML = ICON.north; $('hideBtn').innerHTML = ICON.eyeOff; $('restoreBtn').innerHTML = ICON.eye;
  document.querySelectorAll('[data-close]').forEach(b => { if (b.classList.contains('ibtn')) b.innerHTML = ICON.close; b.onclick = () => b.closest('.modal').hidden = true; });
  document.querySelectorAll('.modal').forEach(m => m.addEventListener('mousedown', e => { if (e.target === m) m.hidden = true; }));
  $('wnBtn').innerHTML = ICON.spark + "What's new"; $('chkBtn').innerHTML = ICON.refresh + 'Kiểm tra cập nhật';
  $('rates').innerHTML = RATES.map(r => `<button class="rate" data-r="${r}" title="Tốc độ phát ${r}× (${RATES.indexOf(r) + 1})">${r}×</button>`).join('');
  $('rates').onclick = e => { const b = e.target.closest('[data-r]'); if (b) { rate = +b.dataset.r; paint(); } };
  $('playBtn').onclick = () => { if (R && t >= R.total) t = 0; playing = !playing; paint(); };
  $('folBtn').onclick = () => setFollow(!follow);
  $('rotBtn').onclick = () => setRotate(!rotate);
  $('northBtn').onclick = goNorth;
  $('b3dBtn').onclick = () => set3d(!mode3d);
  $('hideBtn').onclick = () => setHidden(true);
  $('restoreBtn').onclick = () => setHidden(false);
  const toggleClock = () => { showElapsed = !showElapsed; store.set('mr-elapsed', showElapsed ? '1' : '0'); paint(); };
  $('ctog').onclick = toggleClock; $('clock').onclick = toggleClock;
  $('scrub').oninput = e => { if (R) { t = e.target.value / 1000 * R.total; followLock = 0; } };
  const toggleSide = () => { $('side').classList.toggle('closed'); store.set('mr-side', $('side').classList.contains('closed') ? '0' : '1'); setTimeout(() => map && map.resize(), 240); };
  $('sideBtn').onclick = toggleSide;
  if (store.get('mr-side') === '0') $('side').classList.add('closed');
  $('setBtn').onclick = openSettings;
  $('syncDot').onclick = () => toast($('syncDot').title);
  $('updBtn').onclick = () => checkUpdate(true);
  $('wnBtn').onclick = openWhatsNew; $('chkBtn').onclick = () => checkUpdate(true);
  $('instBtn').onclick = () => { $('setModal').hidden = true; $('updModal').hidden = false; };
  $('updGo').onclick = installUpdate;
  $('unpairBtn').onclick = unpair;
  $('pairNew').onclick = () => startPairing();
  document.addEventListener('keydown', e => {
    if (e.target.tagName === 'INPUT' && e.target.type !== 'range') return;
    if (e.key === 'Escape') { const m = [...document.querySelectorAll('.modal')].find(x => !x.hidden); if (m) { m.hidden = true; return; } if (hidden) setHidden(false); return; }
    if (e.ctrlKey && (e.key === 'b' || e.key === 'B')) { e.preventDefault(); toggleSide(); return; }
    if (!R || e.ctrlKey || e.altKey) return;
    const k = e.key.toLowerCase();
    if (e.key === ' ') { e.preventDefault(); $('playBtn').click(); }
    else if (k === 'f') setFollow(!follow); else if (k === 'r') setRotate(!rotate); else if (k === 'n') goNorth();
    else if (k === 'h') setHidden(!hidden);
    else if (k === 'd') set3d(!mode3d);
    else if (e.key === 'ArrowRight') { e.preventDefault(); seekBy(30); } else if (e.key === 'ArrowLeft') { e.preventDefault(); seekBy(-30); }
    else if (/^[1-5]$/.test(e.key)) { rate = RATES[+e.key - 1]; paint(); }
  });
  window.addEventListener('resize', () => map && map.resize());
  paint(); paint3d();
}

/* ---------- phiên bản, What's new, cập nhật ---------- */
let version = '1.0.0', wn = null, upd = null;
async function loadMeta() {
  try { version = API ? await API.version() : version; } catch (e) {}
  try { wn = API ? await API.whatsnew() : await (await fetch('whatsnew.json')).json(); } catch (e) {}
  $('verText').textContent = 'v' + version;
}
function openSettings() {
  $('verStatus').textContent = upd ? `Có bản mới v${upd.version}` : ($('latestBadge').hidden ? 'Phiên bản đang dùng' : 'Đã là bản mới nhất');
  $('verStatus').classList.toggle('ok', !upd && !$('latestBadge').hidden);
  $('instBtn').hidden = !upd; $('instBtn').textContent = upd ? `Cập nhật lên v${upd.version}` : '';
  $('pairInfo').textContent = store.get('mr-owner') || DEMO ? 'Đã kết nối với điện thoại' : 'Chưa kết nối điện thoại';
  $('setModal').hidden = false;
}
function openWhatsNew() {
  const b = $('wnBody');
  if (!wn) { b.textContent = 'Không đọc được nội dung cập nhật.'; }
  else {
    const d = (wn.date || '').split('-'); const date = d.length === 3 ? `${d[2]}/${d[1]}/${d[0]}` : wn.date;
    const sec = (title, arr) => arr && arr.length ? `<h4>${title}</h4><ul>${arr.map(x => `<li></li>`).join('')}</ul>` : '';
    b.className = 'wn'; b.innerHTML = `<div class="muted">v${wn.version} · ${date}</div>` + sec('Mới', wn.new) + sec('Cải thiện', wn.improved) + sec('Sửa lỗi', wn.fixed)
      + (upd ? `<p style="color:#2EB872;font-weight:700">Có bản mới v${upd.version}</p>` : '');
    const items = [...(wn.new || []), ...(wn.improved || []), ...(wn.fixed || [])]; b.querySelectorAll('li').forEach((li, i) => li.textContent = items[i]);
  }
  $('wnModal').hidden = false;
}
async function checkUpdate(manual) {
  const btn = $('updBtn'); btn.className = 'ibtn upd checking'; btn.title = 'Kiểm tra cập nhật · Đang kiểm tra…';
  let r = {status: 'latest'};
  try { if (API) r = await API.checkUpdate(); } catch (e) { r = {status: 'error'}; }
  if (r.status === 'available') {
    upd = r; btn.className = 'ibtn upd avail'; btn.title = `Kiểm tra cập nhật · Có bản mới v${r.version}`; $('latestBadge').hidden = true;
    $('updTitle').textContent = `Có bản mới v${r.version}`; $('updNotes').textContent = (r.notes || '').replace(/#+\s*/g, '');
    $('updModal').hidden = false;
  } else if (r.status === 'latest') {
    upd = null; btn.className = 'ibtn upd latest-ok'; btn.title = 'Kiểm tra cập nhật · Đã là bản mới nhất'; $('latestBadge').hidden = false;
    if (manual) toast(`Đã là bản mới nhất (v${version})`);
  } else {
    btn.className = 'ibtn upd'; btn.title = 'Kiểm tra cập nhật · Không kiểm tra được'; if (manual) toast('Không kiểm tra được cập nhật, kiểm tra lại mạng');
  }
}
async function installUpdate() {
  if (!API || !upd) return;
  $('updGo').disabled = true; $('updProg').hidden = false;
  API.onProgress(p => { $('updProg').firstElementChild.style.width = Math.round(p * 100) + '%'; });
  const r = await API.installUpdate();
  if (!r || !r.ok) {
    $('updGo').disabled = false; $('updProg').hidden = true;
    toast(`Tải bản cập nhật bị lỗi${r && r.msg ? ' (' + r.msg + ')' : ''}. Đã mở trang tải về trong trình duyệt.`);
  }
}

/* ---------- kết nối điện thoại (ghép mã) ---------- */
let db = null, auth = null, unsubTrips = null, unsubPair = null;
async function initCloud() {
  if (!firebase.apps.length) firebase.initializeApp(FB_CONFIG);
  auth = firebase.auth(); db = firebase.firestore();
  if (!initCloud.persist) { initCloud.persist = true; try { await db.enablePersistence(); } catch (e) {} }
  setSync('pending', 'Đang kết nối…');
  try {
    await new Promise((res, rej) => {
      const u = auth.onAuthStateChanged(x => { if (x) { u(); res(); } });
      auth.signInAnonymously().catch(e => { u(); rej(e); });
    });
  } catch (e) {
    // không đăng nhập được máy tính (mất mạng hoặc dịch vụ đồng bộ chưa bật chế độ cho máy tính)
    setSync('error', 'Không kết nối được dịch vụ đồng bộ'); $('status').textContent = 'Không kết nối được';
    $('pair').hidden = false; $('pairCode').textContent = '------';
    $('pairStatus').textContent = 'Không kết nối được. Kiểm tra mạng rồi bấm "Tạo mã mới" để thử lại.';
    $('pairNew').onclick = () => { $('pairNew').onclick = () => startPairing(); initCloud().catch(() => {}); };
    return;
  }
  if (store.get('mr-owner')) loadTrips(); else startPairing();
}
async function startPairing() {
  if (unsubPair) unsubPair();
  $('pair').hidden = false;
  if (DEMO) { $('pairCode').textContent = '482 913'; $('pairStatus').textContent = 'Đang chờ điện thoại nhập mã…'; return; }
  const code = String(Math.floor(100000 + Math.random() * 900000));
  $('pairCode').textContent = code.slice(0, 3) + ' ' + code.slice(3); $('pairStatus').textContent = 'Đang tạo mã…';
  try {
    const ref = db.collection('pairings').doc(code);
    await ref.set({pcUid: auth.currentUser.uid, createdAt: firebase.firestore.FieldValue.serverTimestamp(),
      expiresAt: firebase.firestore.Timestamp.fromMillis(Date.now() + 10 * 60000)});
    $('pairStatus').textContent = 'Đang chờ điện thoại nhập mã… (mã có hạn 10 phút)';
    setSync('', 'Chưa kết nối điện thoại'); $('status').textContent = 'Chưa kết nối điện thoại';
    unsubPair = ref.onSnapshot(s => {
      const d = s.data(); if (!d || !d.ownerUid) return;
      store.set('mr-owner', d.ownerUid); unsubPair(); unsubPair = null; ref.delete().catch(() => {});
      $('pair').hidden = true; toast('Đã kết nối với điện thoại'); loadTrips();
    }, () => { $('pairStatus').textContent = 'Không tạo được mã, bấm "Tạo mã mới" để thử lại'; });
  } catch (e) { $('pairStatus').textContent = 'Không tạo được mã, kiểm tra kết nối mạng rồi bấm "Tạo mã mới"'; setSync('error', 'Lỗi đồng bộ'); }
}
function loadTrips() {
  const owner = store.get('mr-owner'); if (!owner) return startPairing();
  $('pair').hidden = true; setSync('pending', 'Đang đồng bộ…'); $('status').textContent = 'Đang đồng bộ…';
  if (unsubTrips) unsubTrips();
  unsubTrips = db.collection('users').doc(owner).collection('trips').onSnapshot(snap => {
    trips = snap.docs.map(d => parseTrip(d.id, d.data())).filter(x => x.coords.length >= 2);
    renderList();
    setSync('synced', 'Đã đồng bộ với điện thoại'); $('status').textContent = `Đã đồng bộ · ${trips.length} chuyến`;
  }, err => {
    if (err && err.code === 'permission-denied') { store.set('mr-owner', null); toast('Điện thoại đã ngắt kết nối máy này'); startPairing(); return; }
    setSync('error', 'Lỗi đồng bộ, sẽ thử lại'); $('status').textContent = 'Lỗi đồng bộ';
  });
}
async function unpair() {
  const owner = store.get('mr-owner');
  try { if (db && owner) await db.collection('users').doc(owner).collection('devices').doc(auth.currentUser.uid).delete(); } catch (e) {}
  if (unsubTrips) unsubTrips(); unsubTrips = null;
  store.set('mr-owner', null); trips = []; R = null; renderList();
  $('setModal').hidden = true; $('dock').hidden = true; $('legend').hidden = true; $('empty').hidden = false; $('tripTitle').textContent = '';
  startPairing();
}

/* ---------- khởi động ---------- */
(async function main() {
  wireUi(); await loadMeta();
  if (DEMO) {
    trips = demoTrips(); renderList(); setSync('synced', 'Đã đồng bộ với điện thoại'); $('status').textContent = `Đã đồng bộ · ${trips.length} chuyến`;
    $('latestBadge').hidden = false; $('updBtn').className = 'ibtn upd latest-ok';
    if (QS.has('pair')) startPairing(); else if (QS.has('select')) select([trips[0].id], trips[0].name);
    return;
  }
  checkUpdate(false);
  if (typeof firebase === 'undefined') { setSync('error', 'Lỗi đồng bộ'); return; }
  initCloud().catch(() => setSync('error', 'Lỗi đồng bộ'));
})();
