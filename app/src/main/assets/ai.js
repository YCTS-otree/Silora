'use strict';
let aiBusy = false;
let attachedImages = [];
const updateCore = window.nativeUpdate;
window.nativeUpdate = s => {
    updateCore(s);
    $('api-status').textContent = s.apiConfigured ? 'DeepSeek Key 已加密配置' : '尚未配置 DeepSeek Key';
    if (document.activeElement !== $('reasoning-effort')) $('reasoning-effort').value = s.reasoningEffort;
};

function renderAttachments(images) {
    attachedImages = images || [];
    $('attachment-list').replaceChildren();
    $('attachment').hidden = attachedImages.length === 0;
    for (const [index, image] of attachedImages.entries()) {
        const item = el('div', undefined, 'attachment-item');
        const preview = document.createElement('img');
        preview.src = image.preview;
        preview.alt = `附件 ${index + 1}`;
        const remove = el('button', `移除第 ${index + 1} 张`);
        remove.onclick = async () => {
            try { renderAttachments((await native('ai_remove_image', { imageId: image.id })).images); }
            catch (e) { toast(e.message); }
        };
        item.append(preview, remove);
        $('attachment-list').append(item);
    }
}

native('ai_history').then(r => {
    renderAttachments(r.images);
    for (const item of r.messages) {
        if (item.role === 'user') message(item.content, 'user');
        else {
            try {
                const d = JSON.parse(item.content);
                message(d.status === 'clarification_required' ? d.question :
                    d.status === 'period_table_ready' ? '历史课时表识别结果，请在设置中查看已保存课时表。' :
                    '历史候选计划（不代表已确认或执行），请在计划表查看已保存内容。', 'assistant');
            } catch { /* Ignore invalid historical display data. */ }
        }
    }
}).catch(e => toast(e.message));

$('key-save').onclick = async () => {
    if (aiBusy) return;
    const key = $('api-key').value.trim();
    $('api-key').value = ''; aiBusy = true; $('key-save').disabled = true;
    try {
        const r = await native('ai_key', { key });
        toast(r.available ? '验证成功，密钥已加密保存' : '密钥有效但余额不足，请充值');
        await refresh();
    } catch (e) { toast(e.message); }
    finally { aiBusy = false; $('key-save').disabled = false; }
};
$('key-test').onclick = async () => {
    try { const r = await native('ai_test'); toast(r.available ? '连接成功，余额可用' : '认证成功，但余额不足'); }
    catch (e) { toast(e.message); }
};
$('reasoning-save').onclick = async () => {
    const button = $('reasoning-save');
    button.disabled = true;
    try {
        await native('ai_settings', { effort: $('reasoning-effort').value });
        await refresh();
        toast('思考强度已保存，下次发送生效');
    } catch (e) { toast(e.message); }
    finally { button.disabled = false; }
};
$('key-delete').onclick = async () => {
    if (!confirm('删除 API Key？已确认的计划仍会继续执行。')) return;
    try { await native('ai_delete'); await refresh(); toast('已删除 Key'); }
    catch (e) { toast(e.message); }
};
$('image-pick').onclick = async () => {
    try { const r = await native('ai_image'); if (!r.cancelled) renderAttachments(r.images); }
    catch (e) { toast(e.message); }
};
$('image-remove').onclick = async () => {
    try { renderAttachments((await native('ai_remove_image')).images); }
    catch (e) { toast(e.message); }
};
$('ai-cancel').onclick = () => native('ai_cancel').catch(e => toast(e.message));
$('chat-clear').onclick = async () => {
    if (!confirm('清除本地 AI 对话及图片附件？课时表、周期规则和已保存计划不受影响。')) return;
    try { await native('ai_clear'); $('messages').replaceChildren(); renderAttachments([]); }
    catch (e) { toast(e.message); }
};

function tableFeedback(r) {
    if (r.tableResult === 'saved') message('已将识别出的课时表保存在本机，后续会自动引用。可在设置中核对、编辑或覆盖。', 'assistant');
    if (r.tableResult === 'review') {
        message('识别到了课时表候选，尚未覆盖已有数据。请核对后保存。', 'assistant');
        const button = el('button', '核对并保存课时表');
        button.onclick = () => openPeriodTable(r.periodTable);
        $('messages').append(button);
    }
}
$('send').onclick = async () => {
    if (aiBusy) return;
    if (!state?.apiConfigured) { toast('请先到设置中配置 API Key'); switchTab('settings'); return; }
    const text = $('prompt').value.trim();
    if (!text && !attachedImages.length) return;
    const sentImages = attachedImages.slice();
    let delivered = false;
    aiBusy = true; $('send').disabled = true; $('ai-cancel').hidden = false;
    for (const id of ['image-pick', 'image-remove', 'chat-clear', 'reasoning-save']) $(id).disabled = true;
    message((text || '请读取这些安排截图。') + (attachedImages.length ? `\n附 ${attachedImages.length} 张图片` : ''), 'user');
    $('prompt').value = '';
    renderAttachments([]);
    try {
        const r = await native('ai_chat', { text });
        delivered = true;
        renderAttachments(r.images || []);
        const d = r.document;
        await refresh();
        tableFeedback(r);
        if (d.status === 'clarification_required') message(d.question, 'assistant');
        else if (d.status === 'period_table_ready') {
            if (r.tableResult === 'unchanged') message('已保存的课时表与本次识别一致，可直接用于后续课程安排。', 'assistant');
            openPeriodTable(r.periodTable);
        } else if (d.status === 'recurrence_ready' || d.status === 'script_ready') {
            message('已生成周期 / 计划脚本，尚未生效。请核对学期起点、结束日期及展开结果，再确认应用。', 'assistant');
            openAutomation(d.recurrence || d.script);
            await previewAutomation();
        } else {
            message('已生成候选计划，尚未保存。请核对时间、模式和替换范围，再确认应用。', 'assistant');
            $('json-title').textContent = '确认候选计划'; $('json-source').hidden = true;
            $('json-text').value = JSON.stringify(d, null, 2); $('json-text').readOnly = true;
            $('json-check').hidden = false; $('json-apply').disabled = true;
            $('json-preview').textContent = '正在进行本地校验…'; $('json-dialog').showModal();
            await $('json-check').onclick();
        }
        if (r.usage?.total_tokens) message(`本次用量：${r.usage.total_tokens} tokens`, 'assistant');
        $('messages').lastChild?.scrollIntoView({ block: 'center' });
    } catch (e) {
        if (!delivered) renderAttachments(sentImages);
        message(e.message + '；未新增或修改任何铃声计划。', 'assistant');
    }
    finally {
        aiBusy = false; $('send').disabled = false; $('ai-cancel').hidden = true;
        for (const id of ['image-pick', 'image-remove', 'chat-clear', 'reasoning-save']) $(id).disabled = false;
    }
};
