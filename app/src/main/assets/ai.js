'use strict';
let aiBusy = false;
let attachedImages = [];
let progressTimer = null, progressStart = 0, progressPhase = 'preparing';
const progressLabels = {
    preparing: '正在整理文字和图片', sending: '正在发送到 DeepSeek',
    waiting: '消息已发出，等待 AI 思考与回复', receiving: '已发送，等待 AI 的完整回复', validating: '正在核对日期、课程和时间冲突'
};
function paintProgress() {
    const box = $('ai-progress');
    box.hidden = false; box.className = 'ai-progress working';
    const elapsed = Math.floor((Date.now() - progressStart) / 1000);
    box.textContent = `${progressLabels[progressPhase] || progressLabels.preparing} · ${elapsed} 秒${elapsed >= 30 ? '\n仍在等待，可取消；请勿重复发送。' : ''}`;
}
function startProgress() {
    clearInterval(progressTimer); progressStart = Date.now(); progressPhase = 'preparing';
    paintProgress(); progressTimer = setInterval(paintProgress, 1000);
    $('send').textContent = '处理中…'; $('ai-progress').scrollIntoView({ block: 'center' });
}
function finishProgress(text, failed = false) {
    clearInterval(progressTimer); progressTimer = null;
    $('ai-progress').className = 'ai-progress ' + (failed ? 'failed' : 'done');
    $('ai-progress').textContent = text; $('send').textContent = '发送 ↑';
}
window.aiProgress = phase => { if (aiBusy && progressLabels[phase]) { progressPhase = phase; paintProgress(); } };

function renderProposal(proposal) {
    const card = $('proposal-card');
    if (!proposal) { card.hidden = true; card.replaceChildren(); delete card.dataset.proposalId; return; }
    if (card.dataset.proposalId === proposal.id) return;
    card.dataset.proposalId = proposal.id; card.hidden = false; card.replaceChildren();
    const d = proposal.document;
    card.append(el('h2', '这样安排可以吗？'));
    if (d.summary) card.append(el('p', String(d.summary).slice(0, 2000)));
    const assumptions = Array.isArray(d.assumptions) ? d.assumptions : [];
    if (assumptions.length) {
        const reasoning = document.createElement('details');
        reasoning.append(el('summary', `查看推断依据（${assumptions.length} 项）`));
        reasoning.append(el('p', assumptions.slice(0, 12).map(s => '• ' + String(s).slice(0, 400).replace(/restore=ring/g, '下课后响铃')).join('\n'), 'proposal-text'));
        card.append(reasoning);
    }
    const incoming = d.status === 'plan_ready' ? d.intervals : proposal.candidate.filter(p => p.id.startsWith('cycle_' + (d.recurrence || d.script).id + '_'));
    const times = incoming || [];
    const format = s => new Date(s).toLocaleString('zh-CN', { timeZone: d.time_zone || d.recurrence?.time_zone || d.script?.time_zone || state.zone, month: 'numeric', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false });
    card.append(el('p', `${times.length} 个上课时段 · ${d.time_zone || d.recurrence?.time_zone || d.script?.time_zone || state.zone}`));
    if (times.length && times.every(p => Date.parse(p.end) <= Date.now())) card.append(el('p', '这些日期已经过去：保存后不会补执行。如需安排下周，请直接回复“改为下周”。', 'proposal-warning'));
    if (d.operation === 'replace') card.append(el('p', `将替换 ${format(d.scope.start)} 至 ${format(d.scope.end)} 内的原计划。`));
    if (d.status !== 'plan_ready') card.append(el('p', '将替换原周期生成的计划，保留手动安排。'));
    if (d.period_table) card.append(el('p', `确认时同时保存作息：${d.period_table.name}（${d.period_table.periods.length} 节）。`));
    if (d.restore) card.append(el('p', '区间结束后：' + ({ ring: '响铃', vibrate: '振动', previous: '恢复原模式', keep: '保持当前模式' }[d.restore])));
    const detail = document.createElement('details');
    detail.append(el('summary', '查看全部时间明细'));
    detail.append(el('p', times.map(p => `${format(p.start)} — ${format(p.end)} · ${p.label} · ${names[p.mode]}`).join('\n'), 'proposal-text'));
    card.append(detail, el('p', '回复“是”或点击下方按钮即可应用；也可以直接说要改哪里。'));
    const apply = el('button', '是，按此执行', 'primary'); apply.id = 'proposal-apply';
    apply.onclick = () => confirmProposal(proposal.id);
    card.append(apply);
}
async function confirmProposal(id) {
    if (aiBusy) return;
    aiBusy = true; $('send').disabled = true;
    const button = $('proposal-apply'); if (button) button.disabled = true;
    try {
        finishProgress('正在保存已确认方案…'); $('ai-progress').hidden = false;
        const s = await native('proposal_apply', { proposalId: id });
        nativeUpdate(s); message('已按确认方案保存并启用，作息及结束策略一并生效。', 'assistant');
        finishProgress('方案已保存，可在计划表查看');
    } catch (e) { finishProgress(e.message, true); message(e.message + '；请核对最新方案后再确认。', 'assistant'); }
    finally { aiBusy = false; $('send').disabled = false; if (button) button.disabled = false; }
}
const updateCore = window.nativeUpdate;
window.nativeUpdate = s => {
    updateCore(s);
    $('api-status').textContent = s.apiConfigured ? 'DeepSeek Key 已加密配置' : '尚未配置 DeepSeek Key';
    if (document.activeElement !== $('reasoning-effort')) $('reasoning-effort').value = s.reasoningEffort;
    renderProposal(s.proposal);
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
        const remove = el('button', '×', 'attachment-remove');
        remove.type = 'button';
        remove.setAttribute('aria-label', `移除第 ${index + 1} 张图片`);
        remove.title = `移除第 ${index + 1} 张图片`;
        remove.onclick = async () => {
            remove.disabled = true;
            try { renderAttachments((await native('ai_remove_image', { imageId: image.id })).images); }
            catch (e) { toast(e.message); }
            finally { remove.disabled = false; }
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
                message(d.status === 'clarification_required' ? d.question : (d.summary ||
                    (d.status === 'period_table_ready' ? '历史课时表识别结果，请在设置中查看已保存课时表。' :
                    '历史候选计划（不代表已确认或执行），请在计划表查看已保存内容。')), 'assistant');
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
$('ai-cancel').onclick = () => native('ai_cancel').catch(e => toast(e.message));
$('chat-clear').onclick = async () => {
    if (!confirm('清除本地 AI 对话及图片附件？课时表、周期规则和已保存计划不受影响。')) return;
    try { await native('ai_clear'); $('messages').replaceChildren(); renderAttachments([]); await refresh(); $('ai-progress').hidden = true; }
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
    if (!attachedImages.length && state?.proposal && /^(是|是的|好|好的|可以|确认|就这样|按这个来|没问题|同意)[。！!\s]*$/.test(text)) {
        message(text, 'user'); $('prompt').value = '';
        await confirmProposal(state.proposal.id); return;
    }
    const sentImages = attachedImages.slice();
    let delivered = false;
    aiBusy = true; $('send').disabled = true; $('ai-cancel').hidden = false;
    for (const id of ['image-pick', 'chat-clear', 'reasoning-save']) $(id).disabled = true;
    message((text || '请读取这些安排截图。') + (attachedImages.length ? `\n附 ${attachedImages.length} 张图片` : ''), 'user');
    $('prompt').value = '';
    renderAttachments([]);
    renderProposal(null);
    startProgress();
    try {
        const r = await native('ai_chat', { text });
        delivered = true;
        renderAttachments(r.images || []);
        const d = r.document;
        await refresh();
        if (!r.proposal) tableFeedback(r);
        if (d.status === 'clarification_required') message(d.question, 'assistant');
        else if (d.status === 'review_required') message(d.summary, 'assistant');
        else if (d.status === 'period_table_ready') {
            if (r.tableResult === 'unchanged') message('已保存的课时表与本次识别一致，可直接用于后续课程安排。', 'assistant');
            openPeriodTable(r.periodTable);
        } else if (r.proposal) {
            message(d.summary || '已结合已有信息整理好方案，请核对后回复“是”，或告诉我要调整哪里。', 'assistant');
            renderProposal(r.proposal);
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
        finishProgress(r.proposal ? '方案已就绪，等待你确认' : d.status === 'review_required' ? '已整理结果，需要核对少量信息' : '已收到回复');
        (r.proposal ? $('proposal-card') : $('messages').lastChild)?.scrollIntoView({ block: 'start' });
    } catch (e) {
        if (!delivered) renderAttachments(sentImages);
        if (!delivered && !$('prompt').value) $('prompt').value = text;
        finishProgress((e.message.includes('取消') ? '请求已取消' : '发送或处理失败：' + e.message) + (sentImages.length && !delivered ? '；附件已恢复，可重试。' : ''), true);
        await refresh();
        message(e.message + '；未新增或修改任何铃声计划。', 'assistant');
    }
    finally {
        aiBusy = false; $('send').disabled = false; $('ai-cancel').hidden = true;
        for (const id of ['image-pick', 'chat-clear', 'reasoning-save']) $(id).disabled = false;
    }
};
