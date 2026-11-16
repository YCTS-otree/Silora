'use strict';
(() => {
    let checking = false, pendingRelease = null, retry;
    async function showPending() {
        clearTimeout(retry);
        if (!pendingRelease) return;
        if (document.hidden || !state || state.tutorial?.active || document.querySelector('dialog[open]') || DeepSeekBusy()) {
            retry = setTimeout(showPending, 1500); return;
        }
        const release = pendingRelease; pendingRelease = null;
        const accepted = await askConfirm(release.notes.join('\n') || '此版本未提供更新说明。', {
            title: `发现新版本 ${release.latest}`, confirmLabel: '前往下载', cancelLabel: '稍后再说',
            rows: [['当前', release.current], ['最新', release.latest], ['日期', release.published || '未提供']]
        });
        if (accepted) try { await native('update_open', {url: release.pages[0]}); }
        catch { toast('无法打开下载页面，请检查浏览器后重试。'); }
    }
    function DeepSeekBusy() { return document.body.classList.contains('ai-busy') || $('send')?.disabled; }
    async function check(manual) {
        if (checking) return;
        checking = true; $('update-check').disabled = true; $('update-status').textContent = '正在检查更新…';
        try {
            const result = await native('update_check', {manual});
            $('update-status').textContent = result.status === 'available' ? `发现新版本 ${result.latest} · 当前 ${result.current}` :
                result.status === 'current' ? `当前已是最新版本（${result.current}）` : '暂时无法获取更新信息，请检查网络后重试。';
            if (result.status === 'available' && (manual || result.prompt)) { pendingRelease = result; showPending(); }
        } catch { $('update-status').textContent = '检查失败，请稍后重试。'; }
        finally { checking = false; $('update-check').disabled = false; }
    }
    $('update-check').onclick = () => check(true);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) showPending(); });
    check(false);
})();
