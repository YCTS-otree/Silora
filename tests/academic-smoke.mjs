import { connectSilora } from './webview-client.mjs';

const client = await connectSilora();
const call = client.native;
const assert = (condition, name) => { if (!condition) throw Error(name); console.log('PASS ' + name); };
let state = await call('status');
if (state.periodTable || state.automation) { client.close(); throw Error('Use an installation without a saved period table or automation; will not replace user data.'); }
const originalReminder = state.reminder;
const originalMode = state.mode;
const manualId = 'academic-smoke-' + Date.now();
const table = { name: 'Smoke table', time_zone: 'Asia/Shanghai', periods: [{ number: 1, start: '08:00', end: '08:45' }] };
const cycle = { name: 'Smoke cycle', anchor_monday: '2026-11-16', valid_from: '2026-11-16', valid_until: '2026-12-13', time_zone: 'Asia/Shanghai', entries: [
    { weekday: 1, weeks: 'odd', start: '08:00', end: '08:45', mode: originalMode, label: 'Smoke odd' },
    { weekday: 2, weeks: 'even', start: '08:00', end: '08:45', mode: originalMode, label: 'Smoke even' }
] };
try {
    state = await call('period_save', { table, periodVersion: state.periodVersion });
    assert(state.periodTable.periods.length === 1, 'period table saved');
    let rejected = false;
    try { await call('period_save', { table, periodVersion: state.periodVersion - 1 }); } catch { rejected = true; }
    assert(rejected, 'stale table edit rejected');
    state = await call('save', { revision: state.revision, plan: { id: manualId, start: '2030-01-01T10:00:00Z', end: '2030-01-01T11:00:00Z', mode: originalMode, label: 'Smoke manual disabled', enabled: false } });
    const preview = await call('automation_preview', { rule: cycle });
    state = await call('automation_save', { rule: preview.rule, revision: preview.revision });
    assert(state.plans.filter(p => p.id.startsWith('cycle_' + state.automation.id + '_')).length === 4, 'odd/even expands to four occurrences');
    assert(state.plans.some(p => p.id === manualId), 'manual plan retained');
    const before = JSON.stringify(state.plans);
    state = await call('period_save', { table: { ...table, periods: [{ number: 1, start: '08:05', end: '08:45' }] }, periodVersion: state.periodVersion });
    assert(JSON.stringify(state.plans) === before, 'period edit leaves confirmed plans unchanged');
    const script = { ...cycle, name: 'Smoke script', source: 'when weekday == 1 and week % 2 == 1 and date != "2026-11-30" -> ' + originalMode + '("08:00", "08:45", "Smoke script")' };
    delete script.entries;
    const prepared = await call('automation_preview', { rule: script });
    state = await call('automation_save', { rule: prepared.rule, revision: prepared.revision });
    assert(state.plans.filter(p => p.id.startsWith('cycle_' + state.automation.id + '_')).length === 1, 'script replaces old cycle and excludes date');
    const revision = state.revision;
    rejected = false;
    try { await call('automation_save', { rule: { ...script, source: 'while(true){fetch("https://example.com")}' }, revision }); } catch { rejected = true; }
    state = await call('status');
    assert(rejected && state.revision === revision, 'host code rejected without database change');
    state = await call('reminder_settings', { enabled: true, hours: [9, 14, 18] });
    assert(state.reminder.hours.join(',') === '9,14,18', 'daytime reminder hours saved');
    rejected = false;
    try { await call('reminder_settings', { enabled: true, hours: [9, 23] }); } catch { rejected = true; }
    assert(rejected, 'night reminder setting rejected');
    assert(state.mode === originalMode, 'test leaves current ringer mode unchanged');
} finally {
    state = await call('status');
    if (state.automation?.name.startsWith('Smoke')) state = await call('automation_clear', { revision: state.revision });
    if (state.periodTable?.name === 'Smoke table') state = await call('period_clear', { periodVersion: state.periodVersion });
    if (state.plans.some(p => p.id === manualId)) state = await call('delete', { planId: manualId, revision: state.revision });
    await call('reminder_settings', { enabled: originalReminder.enabled, hours: originalReminder.hours });
    client.close();
}
console.log('ALL ACADEMIC SMOKE CHECKS PASSED');
