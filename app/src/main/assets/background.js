'use strict';
(() => {
    const update = window.nativeUpdate;
    window.nativeUpdate = s => {
        update(s); if (!s.guard) return;
        const g = s.guard;
        $('guard-enabled').checked = g.enabled;
        $('guard-state').textContent = g.issue || (g.running ? (g.notificationAllowed ? '后台守护运行中 · 常驻通知已建立' : '后台守护运行中 · 请允许通知显示') : g.enabled ? '守护已开启，正在等待启动' : '后台守护未开启');
        $('background-state').textContent = `系统后台限制：${g.restricted ? '受限' : '未报告限制'}\n电池优化：${g.batteryExempt ? '已豁免' : '尚未豁免'}\n守护通知：${g.notificationAllowed ? '允许显示' : '未允许显示'}`;
    };
    $('guard-enabled').onchange = async event => {
        const input = event.target; input.disabled = true;
        try {
            const enabled = input.checked;
            if (!enabled) {
                input.checked = true;
                if (!await askConfirm('关闭后，系统可能延迟或阻止后台切换铃声。是否继续？', {title:'关闭后台守护（1/2）',confirmLabel:'继续关闭',cancelLabel:'保持开启'})) return;
                if (!await askConfirm('确认关闭后台守护？计划仍会保留，但后台执行可靠性可能降低。', {title:'最后确认（2/2）',confirmLabel:'确认关闭',cancelLabel:'保持开启'})) return;
            }
            await native('guard_set', { enabled }); await refresh(); setTimeout(refresh, 600); }
        catch (e) { toast(e.message); await refresh(); }
        finally { input.disabled = false; }
    };
    $('battery-settings').onclick = () => native('background_settings', { kind: 'battery' }).catch(e => toast(e.message));
    $('app-background-settings').onclick = () => native('background_settings', { kind: 'app' }).catch(e => toast(e.message));
    if (state) window.nativeUpdate(state);
})();
