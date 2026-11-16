/* Silora release website v1.0.0. No build step or backend required. */
(() => {
  'use strict';
  const HOUR = 60 * 60 * 1000;
  const KEY = 'silora.release.v1';
  const SOURCES = [
    { name: 'Gitee', url: 'https://gitee.com/otreee/Silora/raw/main/update.json' },
    { name: 'GitHub', url: 'https://raw.githubusercontent.com/YCTS-otree/Silora/main/update.json' }
  ];
  const read = key => { try { return JSON.parse(localStorage.getItem(key)); } catch { return null; } };
  const write = (key, value) => { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* Session cache still works. */ } };
  let theme = read('silora.site.theme') === 'light' ? 'light' : 'dark';
  function applyTheme() {
    document.documentElement.dataset.theme = theme;
    const light = theme === 'light';
    const toggle = document.getElementById('theme-toggle');
    toggle.innerHTML = light ? '❀ <span>绿色自然</span>' : '☾ <span>蓝色夜空</span>';
    toggle.setAttribute('aria-label', light ? '切换至蓝色夜空主题' : '切换至绿色自然主题');
    toggle.setAttribute('aria-pressed', String(light));
    const screen = document.getElementById('app-screen');
    screen.src = `assets/app-${theme}.webp`;
    screen.alt = `静律${light ? '绿色自然' : '蓝色夜空'}主题：AI 安排首页实机截图`;
    document.getElementById('plans-screen').src = `assets/plans-${theme}.webp`;
  }
  document.getElementById('theme-toggle').addEventListener('click', () => {
    theme = theme === 'dark' ? 'light' : 'dark';
    write('silora.site.theme', theme); applyTheme();
  });
  applyTheme();
  function validate(data) {
    if (!data || data.app !== 'Silora' || typeof data.latest !== 'string' || !/^\d{1,6}\.\d{1,6}\.\d{1,6}$/.test(data.latest)) throw new Error('Invalid release manifest');
    return { latest: data.latest, published: typeof data.published === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(data.published) ? data.published : '', notes: Array.isArray(data.notes) ? data.notes.filter(n => typeof n === 'string').slice(0, 20).map(n => n.slice(0, 1000)) : [] };
  }
  let cache = read(KEY);
  if (!cache || !Number.isFinite(cache.checkedAt) || cache.checkedAt > Date.now() || !SOURCES.some(s => s.name === cache.source)) cache = null;
  if (cache?.data) { try { cache.data = validate({ ...cache.data, app: 'Silora' }); } catch { cache = null; } }
  let running = false;
  let timer;
  function render() {
    const data = cache?.data;
    const fresh = cache && !cache.failed && Date.now() - cache.checkedAt < HOUR;
    document.getElementById('release-version').textContent = data ? `${fresh ? '最新版本' : '上次获取'} v${data.latest}` : '随页版本 v1.8.1';
    document.getElementById('release-status').textContent = data ? `${data.published ? data.published + ' · ' : ''}${cache.source}${fresh ? ' · 每小时自动检查' : ' · 暂未连接更新源'}` : '暂未连接更新源，可直接前往发布页';
    const list = document.getElementById('release-notes');
    list.replaceChildren();
    for (const note of data?.notes || []) { const li = document.createElement('li'); li.textContent = note; list.append(li); }
    document.getElementById('release-details').hidden = !data?.notes.length;
  }
  function schedule() {
    clearTimeout(timer);
    timer = setTimeout(check, Math.max(1000, HOUR - (Date.now() - (cache?.checkedAt || 0))));
  }
  async function check() {
    if (running) return;
    // Shared localStorage also avoids repeated requests after reloads and in other tabs.
    const shared = read(KEY);
    if (shared && Number.isFinite(shared.checkedAt) && shared.checkedAt <= Date.now() && shared.checkedAt > (cache?.checkedAt || 0) && SOURCES.some(s => s.name === shared.source)) {
      try { if (shared.data) shared.data = validate({ ...shared.data, app: 'Silora' }); cache = shared; } catch { /* Ignore malformed cache. */ }
    }
    if (cache && Date.now() - cache.checkedAt < HOUR) { render(); schedule(); return; }
    running = true;
    document.getElementById('release-status').textContent = '正在检查最新版本…';
    let result = null;
    // Prefer the last successful source; initially try the domestic mirror first.
    const ordered = [...SOURCES].sort((a, b) => Number(b.name === cache?.source) - Number(a.name === cache?.source));
    for (const source of ordered) {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 5000);
      try {
        const response = await fetch(source.url, { signal: controller.signal, cache: 'no-store', credentials: 'omit', referrerPolicy: 'no-referrer' });
        if (!response.ok) throw new Error('Unavailable');
        const body = await response.text();
        if (body.length > 65536) throw new Error('Oversize manifest');
        result = { data: validate(JSON.parse(body)), source: source.name, checkedAt: Date.now(), failed: false };
        break;
      } catch { /* Try the other source on network, CORS, timeout or invalid data. */ }
      finally { clearTimeout(timeout); }
    }
    cache = result || { data: cache?.data || null, source: cache?.source || 'Gitee', checkedAt: Date.now(), failed: true };
    write(KEY, cache);
    running = false; render(); schedule();
  }
  document.addEventListener('visibilitychange', () => { if (!document.hidden) check(); });
  check();
})();
