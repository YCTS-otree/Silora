// Run against the debug WebView with adb port 9222 forwarded.
// Uses real tutorial persistence, but never sends AI requests or applies plans.
import assert from 'node:assert/strict';
import { connectSilora } from './webview-client.mjs';
const client = await connectSilora();
try {
    const result = await client.evaluate(`(async () => {
        const original = await native('status'), oldNative = native;
        const originalTab = tab, originalDraft = $('prompt').value;
        if (aiBusy || attachedImages.length || originalDraft) throw Error('Run with an idle, empty composer');
        const stable = s => JSON.stringify([s.revision,s.plans,s.periodVersion,s.periodTable,s.automation,s.proposal,s.apiConfigured,s.restore,s.manual,s.reasoningEffort,s.reminder]);
        const check = (condition, name) => { if (!condition) throw Error(name); };
        const set = async (step, active = true, completed = false) => nativeUpdate(await oldNative('tutorial_save', {step,active,completed}));
        const settle = () => new Promise(r => setTimeout(r, 80));
        try {
            for (const bad of [{step:8,active:true,completed:false},{step:1.5,active:true,completed:false},{step:0,active:'false',completed:false},{step:2,active:true,completed:true}]) {
                let rejected = false; try { await oldNative('tutorial_save',bad); } catch { rejected = true; }
                check(rejected, 'invalid tutorial state rejected');
            }
            await set(0);
            check(!$('tour-welcome').hidden && getComputedStyle(document.querySelector('nav')).display === 'none', 'welcome hides navigation');
            nativeUpdate({...state, apiConfigured:false});
            check($('tour-key-next').disabled, 'empty key cannot submit');
            $('tour-key').value = 'tutorial-test-placeholder'; $('tour-key').dispatchEvent(new Event('input'));
            check(!$('tour-key-next').disabled, 'entered key enables validation');
            $('tour-key-toggle').click(); check($('tour-key').type === 'text', 'show key');
            $('tour-key-toggle').click(); check($('tour-key').type === 'password', 'hide key');
            for (const error of ['API Key 无效（401）','网络连接失败，请重试']) {
                native = async (action,data) => { if (action === 'ai_key') throw Error(error); return oldNative(action,data); };
                await $('tour-key-next').onclick();
                check(state.tutorial.step === 0 && $('tour-key-feedback').textContent === error && !$('tour-key-next').disabled, 'key errors remain retryable');
            }
            native = async (action,data) => action === 'ai_key' ? {configured:true,available:false} : oldNative(action,data);
            await $('tour-key-next').onclick();
            check(state.tutorial.step === 0 && !$('tour-key-later').hidden && $('tour-key-feedback').textContent.includes('余额不足'), 'insufficient balance is not an invalid key');
            native = oldNative;
            await $('tour-key-later').onclick();
            check(state.tutorial.step === 1 && !$('tour-coach').hidden, 'continue from welcome');
            $('tour-action').click(); check($('prompt').value.includes('明天'), 'example only fills draft');
            $('prompt').value = ''; $('prompt').dispatchEvent(new Event('input'));
            await $('tour-next').onclick(); check(state.tutorial.step === 2, 'image guide');
            await $('tour-next').onclick(); check(state.tutorial.step === 3, 'skip image without sending');
            await $('tour-next').onclick(); check(state.tutorial.step === 4 && $('tour-next').disabled, 'plan navigation requires actual click');
            document.querySelector('[data-tab=plans]').click(); await settle(); check(!$('tour-next').disabled, 'plan navigation unlocks next');
            await $('tour-next').onclick(); check(state.tutorial.step === 5 && $('tour-next').disabled, 'settings navigation requires actual click');
            document.querySelector('[data-tab=settings]').click(); await settle(); check(!$('tour-next').disabled, 'settings navigation unlocks next');
            await $('tour-next').onclick(); check(state.tutorial.step === 6, 'execution strategy guide');
            await $('tour-pause').onclick(); check(!state.tutorial.active && !$('tutorial-resume').hidden, 'pause retains progress');
            await $('tutorial-resume').onclick(); check(state.tutorial.step === 6 && state.tutorial.active, 'resume progress');
            await $('tour-next').onclick();
            switchTab('settings'); await settle();
            check(!$('tour-spotlight').hidden, 'reselecting current tab keeps the guide target visible');
            await $('tour-next').onclick();
            check(state.tutorial.completed && !state.tutorial.active && tab === 'ai', 'finish returns to AI');
            await refresh(); check($('tour-welcome').hidden && $('tour-coach').hidden, 'completed does not reopen');
            await $('tutorial-replay').onclick(); check(state.tutorial.active && state.tutorial.step === 0, 'replay starts welcome');
            await $('tour-welcome-skip').onclick(); check(!state.tutorial.active && $('tour-welcome').hidden, 'welcome can skip');
            const after = await oldNative('status');
            check(stable(after) === stable(original), 'tutorial preserves plans, proposal, timetable, settings and key configuration');
            check(document.documentElement.scrollHeight <= innerHeight + 1 && document.documentElement.scrollWidth <= innerWidth + 1, 'no root overflow');
            return 'PASS: persistence validation, welcome/key states, actual navigation, pause/resume, complete/replay, non-destructive flow';
        } finally {
            native = oldNative; $('prompt').value = originalDraft;
            nativeUpdate(await oldNative('tutorial_save', original.tutorial));
            switchTab(originalTab);
        }
    })()`);
    assert.match(result, /^PASS:/); console.log(result);
} finally { client.close(); }
