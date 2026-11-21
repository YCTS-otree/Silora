'use strict';
let aiBusy = false;
let attachedImages = [];
let progressTimer = null, progressStart = 0, progressPhase = 'preparing';
const progressLabels = {
    preparing: '正在整理文字和图片', sending: '正在发送到 DeepSeek',
    waiting: '消息已发出，等待 AI 思考与回复', receiving: '正在接收 AI 回复', validating: '正在核对日期、课程和时间冲突'
};
function paintProgress() {
    const box = $('ai-progress');
    if(!$('stream-preview').hidden && progressPhase==='receiving'){box.hidden=true;return;}
    box.hidden = false; box.className = 'ai-progress working';
    const elapsed = Math.floor((Date.now() - progressStart) / 1000);
    box.textContent = `${progressLabels[progressPhase] || progressLabels.preparing} · ${elapsed} 秒${elapsed >= 30 ? '\n仍在等待，可取消；请勿重复发送。' : ''}`;
}
function clearStream() {
    $('stream-preview').hidden=true;$('stream-thought').textContent='';$('stream-answer').textContent='';
}
window.aiStream=({thought,summary})=>{
    if(!aiBusy || (!thought && !summary))return;
    $('stream-preview').hidden=false;
    $('ai-progress').hidden=true;
    const line=$('stream-thought'),row=$('thinking-row');row.hidden=!thought || !!summary;
    if(!row.hidden){line.textContent=thought.replace(/\s+/g,' ');line.scrollLeft=line.scrollWidth;}
    const answer=$('stream-answer');answer.hidden=!summary;answer.textContent=summary;
};
function startProgress() {
    clearStream();
    clearInterval(progressTimer); progressStart = Date.now(); progressPhase = 'preparing';
    paintProgress(); progressTimer = setInterval(paintProgress, 1000);
    $('send').textContent = '处理中…'; $('ai-progress').scrollIntoView({ block: 'center' });
}
function finishProgress(text, failed = false) {
    clearStream();
    clearInterval(progressTimer); progressTimer = null; $('ai-progress').hidden=false;
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
    const affected = new Set(proposal.affected || []);
    const incoming = proposal.affected ? proposal.candidate.filter(p=>affected.has(p.id)) : d.status === 'plan_ready' ? d.intervals : proposal.candidate.filter(p => p.id.startsWith('cycle_' + (d.recurrence || d.script).id + '_'));
    const times = incoming || [];
    const format = s => new Date(s).toLocaleString('zh-CN', { timeZone: d.time_zone || d.recurrence?.time_zone || d.script?.time_zone || state.zone, month: 'numeric', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false });
    card.append(el('p', `${times.length} 个安排 · ${d.time_zone || d.recurrence?.time_zone || d.script?.time_zone || state.zone}`));
    if (times.length && times.every(p => Date.parse(p.end) <= Date.now())) card.append(el('p', '这些日期已经过去：保存后不会补执行。如需安排下周，请直接回复“改为下周”。', 'proposal-warning'));
    if (d.operation === 'replace') card.append(el('p', `将替换 ${format(d.scope.start)} 至 ${format(d.scope.end)} 内的原计划。`));
    if (['recurrence_ready','script_ready'].includes(d.status)) card.append(el('p', '将替换原周期生成的计划，保留手动安排。'));
    if (d.period_table) card.append(el('p', `确认时同时保存作息：${d.period_table.name}（${d.period_table.periods.length} 节）。`));
    if (d.restore) card.append(el('p', '区间结束后：' + ({ ring: '响铃', vibrate: '振动', previous: '恢复原模式', keep: '保持当前模式' }[d.restore])));
    if(proposal.askNotice && times.length){
        const label=el('label','要在课程或会议开始前提醒吗？希望提前多久？');
        const input=document.createElement('input');input.type='number';input.id='proposal-notice';input.min='0';input.max='1440';input.step='1';input.placeholder='输入提前分钟数，如 10；留空不提醒';
        label.append(input);card.append(label);
    } else if(times.length){
        const values=new Set(times.map(p=>p.notice_minutes||0));
        card.append(el('p',values.size===1?(times[0].notice_minutes?'开始前 '+times[0].notice_minutes+' 分钟提醒':'开始前不提醒'):'各时段的提前量见下方明细'));
    }
    const detail = document.createElement('details');
    if(d.status==='location_ready') detail.open=true;
    detail.append(el('summary', '查看全部时间明细'));
    detail.append(el('p', times.map(p => `${format(p.start)} — ${format(p.end)} · ${p.label} · ${names[p.mode]}${p.location?" · "+p.location:""}${p.notice_minutes?" · 提前 "+p.notice_minutes+" 分钟":""}`).join('\n'), 'proposal-text'));
    card.append(detail, el('p', proposal.askNotice ? '填入分钟数后确认即可启用提醒；留空确认只保存安排。也可以回复“提前 10 分钟提醒”。' : '回复“是”或点击下方按钮即可应用；也可以直接说要改哪里。'));
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
        const input=$('proposal-notice');
        if(input && !input.checkValidity()) throw Error('提醒时间须为 0–1440 的整数分钟');
        const locationOnly=state?.proposal?.document?.status==='location_ready';
        const s = await native('proposal_apply', { proposalId: id, ...(input?.value ? {notice_minutes: Number(input.value)} : {}) });
        nativeUpdate(s); message(locationOnly?'已保存课程地点，开始前的通知将显示对应教室。':'已按确认方案保存并启用，作息及结束策略一并生效。', 'assistant');
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

const historyBox=$('messages'), moreButton=$('history-more');
let pageBusy=false, capacity=2, epoch=0;
function turnNode(){
    const node=document.createElement('div');node.className='chat-turn';return node;
}
function trimTurns(){
    while(historyBox.children.length>capacity){
        const old=historyBox.firstElementChild;
        if(old.dataset.id)moreButton.hidden=false;
        old.remove();
    }
}
async function historyPage(initial){
    if(pageBusy)return;
    const before=Array.from(historyBox.children).find(n=>n.dataset.id)?.dataset.id;
    if(!initial&&!before)return;
    pageBusy=true;moreButton.disabled=true;
    const stamp=epoch;
    try{
        const r=await native('ai_history',before?{before:Number(before)}:{});
        if(stamp!==epoch)return;
        if(initial)renderAttachments(r.images);
        const frag=document.createDocumentFragment();
        for(const item of r.turns){
            const node=turnNode();node.dataset.id=item.id;
            const userCard=el('div',item.user,'message user');attachGallery(userCard,item.imageIds||[]);
            node.append(userCard,el('div',item.assistant,'message assistant'));
            frag.append(node);
        }
        if(!initial)capacity+=r.turns.length;
        historyBox.prepend(frag);moreButton.hidden=!r.more;
    }catch(e){toast(e.message)}finally{pageBusy=false;moreButton.disabled=false}
}
moreButton.onclick=()=>{if(!aiBusy)historyPage(false)};
historyPage(true);

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
    if (!await askConfirm('删除 API Key？已确认的计划仍会继续执行。')) return;
    try { await native('ai_delete'); await refresh(); toast('已删除 Key'); }
    catch (e) { toast(e.message); }
};
$('image-pick').onclick = async () => {
    try { const r = await native('ai_image'); if (!r.cancelled) renderAttachments(r.images); }
    catch (e) { toast(e.message); }
};
$('ai-cancel').onclick = () => native('ai_cancel').catch(e => toast(e.message));
$('chat-clear').onclick = async () => {
    if (!await askConfirm('清除本地 AI 对话及图片附件？课时表、周期规则和已保存计划不受影响。')) return;
    try { await native('ai_clear'); epoch++;capacity=2;moreButton.hidden=true;$('messages').replaceChildren(); renderAttachments([]); await refresh(); $('ai-progress').hidden = true; }
    catch (e) { toast(e.message); }
};

function tableFeedback(r) {
    if (r.tableResult === 'saved') message('已将识别出的课时表保存在本机，后续会自动引用。可在设置中核对、编辑或覆盖。', 'assistant');
    if (r.tableResult === 'review') {
        message('识别到了课时表候选，尚未覆盖已有数据。请核对后保存。', 'assistant');
        const button = el('button', '核对并保存课时表');
        button.onclick = () => openPeriodTable(r.periodTable);
        (historyBox.lastElementChild||historyBox).append(button);
    }
}
$('send').onclick = async () => {
    if (aiBusy) return;
    if (!state?.apiConfigured) { toast('请先到设置中配置 API Key'); switchTab('settings');$('api-key').closest('details').open=true;$('api-key').scrollIntoView({block:'center'}); return; }
    const text = $('prompt').value.trim();
    if (!text && !attachedImages.length) return;
    if (!attachedImages.length && state?.proposal && /^(是|是的|好|好的|可以|确认|就这样|按这个来|没问题|同意)[。！!\s]*$/.test(text)) {
        message(text, 'user'); $('prompt').value = '';
        await confirmProposal(state.proposal.id); return;
    }
    if(pageBusy)return toast('历史对话正在加载，请稍候');
    const liveTurn=turnNode();historyBox.append(liveTurn);
    const sentImages = attachedImages.slice();
    let delivered = false;
    aiBusy = true; $('send').disabled = true; $('ai-cancel').hidden = false;
    for (const id of ['image-pick', 'chat-clear', 'reasoning-save']) $(id).disabled = true;
    message((text || '请读取这些安排截图。') + (attachedImages.length ? `\n附 ${attachedImages.length} 张图片` : ''), 'user');
    attachGallery(liveTurn.lastElementChild,sentImages.map(image=>image.id));
    $('prompt').value = '';
    renderAttachments([]);
    renderProposal(null);
    startProgress();
    try {
        const r = await native('ai_chat', { text });
        delivered = true;liveTurn.dataset.id=r.turnId;trimTurns();
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
        if (!delivered && !liveTurn.dataset.id && historyBox.children.length>capacity){
            // A failed request has no transcript cursor. Keep its error in the status box.
            liveTurn.remove();
        }
        if (!delivered) renderAttachments(sentImages);
        if (!delivered && !$('prompt').value) $('prompt').value = text;
        finishProgress((e.message.includes('取消') ? '请求已取消' : '发送或处理失败：' + e.message) + (sentImages.length && !delivered ? '；附件已恢复，可重试。' : ''), true);
        await refresh();
        if(liveTurn.isConnected)message(e.message + '；未新增或修改任何铃声计划。', 'assistant');
    }
    finally {
        aiBusy = false; $('send').disabled = false; $('ai-cancel').hidden = true;
        for (const id of ['image-pick', 'chat-clear', 'reasoning-save']) $(id).disabled = false;
    }
};
