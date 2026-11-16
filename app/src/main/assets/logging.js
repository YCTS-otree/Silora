'use strict';
(() => {
    const labels = {
        choosing: '请选择日志 ZIP 的保存位置。', writing: '正在导出日志，请稍候…',
        done: '日志 ZIP 已保存到你选择的位置。', cancelled: '已取消导出，日志仍保留在本机。',
        failed: '导出失败，请重试；目标文件可能不完整。'
    };
    let lastExportState = 'idle', pollTimer;
    function displayExport(status) {
        $('log-export-status').textContent = labels[status] || '';
        $('log-export').disabled = status === 'writing' || status === 'choosing';
        clearTimeout(pollTimer);
        if (status === 'writing' && !document.hidden) pollTimer = setTimeout(async () => {
            try { renderLog(await native('log_status')); }
            catch { $('log-export-status').textContent = '暂时无法确认导出状态，请点击刷新状态。'; }
        }, 1000);
    }
    function renderLog(log) {
        $('log-status').textContent = `${log.writeFailed ? '日志写入遇到问题，可尝试导出已有记录' : log.ready ? '日志记录已启用' : '日志正在初始化'} · ${log.files} 份 · ${(log.bytes / 1048576).toFixed(2)} MiB`;
        if (log.exportState !== lastExportState || log.exportState === 'writing') { lastExportState = log.exportState; displayExport(log.exportState); }
    }
    const update = window.nativeUpdate;
    window.nativeUpdate = s => {
        update(s);
        if (s.logging) renderLog(s.logging);
    };
    window.logExportResult = status => {
        displayExport(status);
        if (status === 'done') toast('日志导出成功');
        if (status === 'failed') toast('日志导出失败，请重试');
        refresh();
    };
    $('log-export').onclick = async () => {
        displayExport('choosing');
        try { await native('log_export'); }
        catch (e) { displayExport('failed'); toast(e.message); }
    };
    $('log-refresh').onclick = async () => {
        try { renderLog(await native('log_status')); } catch (e) { toast(e.message); }
    };
    if (state) window.nativeUpdate(state);
})();
