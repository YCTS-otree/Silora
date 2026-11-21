'use strict';

let periodEditVersion = 0;
let automationDraft = null;
let automationRevision = null;
const coreUpdateForAcademic = window.nativeUpdate;
window.nativeUpdate = s => {
    coreUpdateForAcademic(s);
    const table = s.periodTable;
    $('period-summary').textContent = table
        ? `${table.name} · ${table.periods.length} 节 · ${table.time_zone}（已保存）`
        : '尚未保存课时表。可与课程安排一起上传，让 AI 识别。';
    const rule = s.automation;
    $('automation-summary').textContent = rule
        ? `${rule.name} · ${rule.kind === 'script' ? '计划脚本' : '单双周规则'}\n第 1 周周一：${rule.anchor_monday}\n生效：${rule.valid_from} — ${rule.valid_until}`
        : '由 AI 设置每周、单双周或日期条件，确认一次后离线执行到学期结束。';
    const reminder = s.reminder;
    $('reminder-enabled').checked = reminder.enabled;
    $('reminder-hours').value = reminder.hours.join(',');
    $('reminder-summary').textContent = `${reminder.allowed ? '通知可用' : '通知未授权或渠道已关闭'}\n` +
        `当期最后结束：${reminder.end ? new Date(Number(reminder.end)).toLocaleString() : '无启用计划'}\n` +
        `下次提醒时点：${reminder.next ? new Date(Number(reminder.next)).toLocaleString() : '暂无'}`;
};

function field(label, type, value, key) {
    const wrap = el('label', label);
    const input = document.createElement('input');
    input.type = type;
    input.value = value ?? '';
    input.dataset.key = key;
    input.required = !['location','location_short','notice_minutes'].includes(key);
    if(key==='notice_minutes'){input.min='0';input.max='1440';input.step='1';}
    if(key==='location_short')input.maxLength=16;
    if(key==='location')input.maxLength=160;
    wrap.append(input);
    return wrap;
}

function choice(label, key, values, selected) {
    const wrap = el('label', label);
    const select = document.createElement('select');
    select.dataset.key = key;
    for (const [value, text] of values) {
        const option = el('option', text);
        option.value = value;
        select.append(option);
    }
    select.value = selected;
    wrap.append(select);
    return wrap;
}

function addPeriod(period = {}) {
    const row = el('div', undefined, 'period-row');
    row.append(field('第几节', 'number', period.number ?? $('period-rows').children.length + 1, 'number'),
        field('上课', 'time', period.start, 'start'), field('下课', 'time', period.end, 'end'));
    const number = row.querySelector('[data-key="number"]');
    number.min = 1; number.max = 32;
    const remove = el('button', '移除课节');
    remove.type = 'button'; remove.onclick = () => row.remove();
    row.append(remove);
    $('period-rows').append(row);
}

window.openPeriodTable = (candidate = null) => {
    if (!state) return;
    periodEditVersion = state.periodVersion;
    const table = candidate || state.periodTable;
    $('period-name').value = table?.name || '学校作息';
    $('period-zone').value = table?.time_zone || state.zone;
    $('period-rows').replaceChildren();
    for (const period of table?.periods || [{}]) addPeriod(period);
    $('period-error').textContent = candidate && state.periodTable ? '识别到了新课时表，确认后将覆盖现有表。' : '';
    $('period-dialog').showModal();
};
$('period-open').onclick = () => openPeriodTable();
$('period-add').onclick = () => addPeriod();
$('period-close').onclick = () => $('period-dialog').close();
$('period-form').onsubmit = async e => {
    e.preventDefault();
    const periods = [...$('period-rows').children].map(row => ({
        number: Number(row.querySelector('[data-key="number"]').value),
        start: row.querySelector('[data-key="start"]').value,
        end: row.querySelector('[data-key="end"]').value
    }));
    if (!await askConfirm('确认保存课时表？已有课时表会被覆盖，已确认的铃声计划不变。')) return;
    if (await mutate('period_save', { periodVersion: periodEditVersion,
        table: { name: $('period-name').value.trim(), time_zone: $('period-zone').value.trim(), periods } })) {
        $('period-dialog').close(); toast('课时表已保存，后续 AI 会自动引用');
    }
};
$('period-remove').onclick = async () => {
    const periodVersion = state?.periodVersion;
    if (state?.periodTable && await askConfirm('删除保存的课时表？已确认计划仍继续执行。'))
        await mutate('period_clear', { periodVersion });
};
$('period-ai').onclick = () => {
    switchTab('ai'); $('prompt').value = '请识别我上传的课时表，提取各节上下课时间并保存，缺少信息请追问。';
    toast('请添加课时表图片后发送，可同时选择多张');
};

function invalidateAutomation() {
    automationDraft = null;
    $('automation-apply').disabled = true;
}
function addRule(entry = {}) {
    const row = el('div', undefined, 'rule-row');
    row.append(field('名称', 'text', entry.label, 'label'));
    const days = [1, 2, 3, 4, 5, 6, 7].map((v, i) => [String(v), '周' + '一二三四五六日'[i]]);
    const line = el('div', undefined, 'time-row');
    line.append(choice('星期', 'weekday', days, String(entry.weekday || 1)),
        choice('周次', 'weeks', [['all', '每周'], ['odd', '单周'], ['even', '双周']], entry.weeks || 'all'));
    const times = el('div', undefined, 'time-row');
    times.append(field('开始', 'time', entry.start, 'start'), field('结束', 'time', entry.end, 'end'));
    row.append(line, times, choice('模式', 'mode', Object.entries(names), entry.mode || 'vibrate'));
    row.append(field('地点（选填）','text',entry.location,'location'),field('地点简称','text',entry.location_short,'location_short'),field('提前提醒（分钟，0 为关闭）','number',entry.notice_minutes,'notice_minutes'));
    const remove = el('button', '移除此规则');
    remove.type = 'button'; remove.onclick = () => { row.remove(); invalidateAutomation(); };
    row.append(remove);
    $('automation-rows').append(row);
}
function automationType() {
    const script = $('automation-kind').value === 'script';
    $('automation-script').hidden = !script;
    $('automation-cycle').hidden = script;
    $('automation-rows').querySelectorAll('input,select').forEach(e => e.disabled = script);
    invalidateAutomation();
}
window.openAutomation = (candidate = null) => {
    if (!state) return;
    const rule = candidate || state.automation;
    $('automation-name').value = rule?.name || '本学期课表';
    $('automation-kind').value = rule?.source ? 'script' : 'cycle';
    $('automation-anchor').value = rule?.anchor_monday || '';
    $('automation-from').value = rule?.valid_from || today();
    $('automation-until').value = rule?.valid_until || '';
    $('automation-zone').value = rule?.time_zone || state.zone;
    $('script-short').value=rule?.location_short||'';$('script-notice').value=rule?.notice_minutes||'';$('script-location').value=rule?.location||'';
    $('automation-source').value = rule?.source || '';
    $('automation-rows').replaceChildren();
    for (const entry of rule?.entries || [{}]) addRule(entry);
    $('automation-preview').textContent = '';
    automationType();
    $('automation-dialog').showModal();
};
async function previewAutomation() {
    invalidateAutomation();
    try {
        const rule = { name: $('automation-name').value.trim(), anchor_monday: $('automation-anchor').value,
            valid_from: $('automation-from').value, valid_until: $('automation-until').value, time_zone: $('automation-zone').value.trim() };
        if ($('automation-kind').value === 'script') { rule.source = $('automation-source').value;rule.notice_minutes=Number($('script-notice').value);rule.location=$('script-location').value;rule.location_short=$('script-short').value; }
        else rule.entries = [...$('automation-rows').children].map(row => {
            const get = key => row.querySelector(`[data-key="${key}"]`).value;
            return { weekday: Number(get('weekday')), weeks: get('weeks'), start: get('start'), end: get('end'), mode: get('mode'), label: get('label').trim(), location:get('location').trim(),location_short:get('location_short').trim(), notice_minutes:Number(get('notice_minutes')) };
        });
        const r = await native('automation_preview', { rule });
        automationDraft = r.rule; automationRevision = r.revision;
        const generated = r.candidate.filter(p => p.id.startsWith('cycle_' + r.rule.id + '_'));
        $('automation-preview').textContent = `校验通过：生成 ${generated.length} 个时间段，保留 ${r.candidate.length - generated.length} 条手动计划。\n` +
            `学期第 1 周：${r.rule.anchor_monday}\n覆盖 ${r.rule.valid_from} — ${r.rule.valid_until}，时区 ${r.rule.time_zone}\n` +
            (generated.length > 12 ? '以下展示前 12 条，确认后可在计划表查看全部：\n' : '') +
            generated.slice(0, 12).map(p => `${new Date(p.start).toLocaleString()} — ${new Date(p.end).toLocaleTimeString()} · ${p.label} · ${names[p.mode]}`).join('\n');
        $('automation-apply').disabled = false;
        $('automation-apply').scrollIntoView({ block: 'nearest' });
    } catch (e) { $('automation-preview').textContent = e.message; }
}
window.previewAutomation = previewAutomation;
$('automation-open').onclick = () => openAutomation();
$('automation-add').onclick = () => { addRule(); invalidateAutomation(); };
$('automation-close').onclick = () => $('automation-dialog').close();
$('automation-kind').onchange = automationType;
$('automation-form').oninput = invalidateAutomation;
$('automation-form').onsubmit = e => { e.preventDefault(); previewAutomation(); };
$('automation-apply').onclick = async () => {
    if (!automationDraft || !await askConfirm('确认应用周期 / 脚本计划？将替换原周期生成的所有时间段，保留手动计划，并真实执行铃声切换。')) return;
    if (await mutate('automation_save', { rule: automationDraft, revision: automationRevision })) {
        $('automation-dialog').close(); switchTab('plans'); toast('已展开并保存，本学期无需每周重新设置');
    }
};
$('automation-remove').onclick = async () => {
    const revision = state?.revision;
    if (state?.automation && await askConfirm('移除当前周期 / 脚本及其全部时间段？手动计划会保留。'))
        await mutate('automation_clear', { revision });
};
$('automation-ai').onclick = () => {
    switchTab('ai');
    $('prompt').value = '请根据课表设置每周 / 单双周计划，必要时用日期条件脚本。请先向我确认学期第1周周一、生效范围和铃声模式，不要猜测。';
};
$('reminder-save').onclick = async () => {
    const hours = $('reminder-hours').value.split(/[,，]/).map(v => Number(v.trim()));
    if (await mutate('reminder_settings', { enabled: $('reminder-enabled').checked, hours })) toast('白天提醒设置已保存');
};
$('reminder-test').onclick = async () => {
    if (await mutate('reminder_test', {})) toast('测试通知已发送，请查看通知栏');
};
