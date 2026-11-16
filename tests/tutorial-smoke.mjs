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
        const settle = () => new Promise(r => setTimeout(r, 180));
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
            check(state.tutorial.step === 0 && $('tour-key-feedback').textContent.includes('余额不足'), 'insufficient balance is not an invalid key');
            native = oldNative;
            await $('tour-key-next').onclick();
            check(state.tutorial.step === 1 && !$('tour-coach').hidden, 'continue from welcome');
            document.querySelector('[data-tab=ai]').click(); await settle();
            $('tour-action').click(); check($('prompt').value.includes('明天'), 'example only fills draft');
            document.querySelector('[data-tab=settings]').click(); check(tab === 'ai','unrelated navigation is blocked');
            $('chat-clear').click(); check(!$('confirm-sheet').open,'unrelated actions are blocked');
            $('tour-pause').click(); await settle(); check(state.tutorial.active,'hidden pause cannot bypass click restriction');
            check(window.goBack() && state.tutorial.active,'Back does not leave guided flow');
            $('prompt').value = ''; $('prompt').dispatchEvent(new Event('input'));
            await $('tour-next').onclick(); check(state.tutorial.step === 2, 'image guide');
            await $('tour-next').onclick(); check(state.tutorial.step === 3, 'skip image without sending');
            await $('tour-next').onclick(); check(state.tutorial.step === 4 && $('tour-next').disabled, 'plan navigation requires actual click');
            document.querySelector('[data-tab=plans]').click(); await settle();
            check($('tour-next').disabled && weekView.inspect().demo && document.querySelectorAll('.week-card').length > 20, 'example requires card interaction');
            check($('tour-next').textContent.includes('点开示例卡片'),'locked next is an operation instruction');
            const currentWeek = weekView.inspect().week;
            $('week-next').click(); $('add').click(); check(weekView.inspect().week===currentWeek && !$('editor').open, 'week navigation and creation blocked during lesson');
            weekView.firstCard().click(); await settle(); check($('plan-detail').open && $('plan-detail-actions').hidden,'example opens read-only detail');
            check($('plan-detail-card').classList.contains('tour-target'),'expanded card glows');
            $('plan-detail-card').click(); await settle(); check(!$('plan-detail').open && !$('tour-next').disabled,'tap expanded card to close and unlock next');
            weekView.firstCard().click(); await settle();
            const bounds=$('plan-detail').getBoundingClientRect();
            $('plan-detail').dispatchEvent(new MouseEvent('click',{bubbles:true,clientX:innerWidth/2,clientY:Math.max(0,bounds.top-15)}));
            await settle();check(!$('plan-detail').open&&!$('tour-next').disabled,'tutorial permits blur-area dismissal');
            await $('tour-next').onclick(); check(state.tutorial.step === 5 && $('tour-next').disabled, 'settings navigation requires actual click');
            document.querySelector('[data-tab=settings]').click(); await new Promise(r=>setTimeout(r,700)); check(!$('tour-next').disabled, 'settings navigation unlocks next');
            const startScroll=document.querySelector('main').scrollTop;
            await $('tour-next').onclick(); check(state.tutorial.step === 6, 'execution strategy guide');
            const positions=[];for(let i=0;i<12;i++){await new Promise(r=>setTimeout(r,50));positions.push(document.querySelector('main').scrollTop);}
            check(matchMedia('(prefers-reduced-motion: reduce)').matches || new Set(positions.map(Math.round)).size>2,'settings advances by visible smooth scrolling');
            check(document.querySelector('main').scrollTop>startScroll,'settings moves down to next controls');
            check(!weekView.inspect().demo,'sample disappears after leaving week lesson');
            $('save-settings').click(); await settle(); check($('confirm-sheet').open,'required setting confirmation is accessible');
            $('confirm-no').click(); await settle(); check(!$('confirm-sheet').open,'required confirmation can cancel');
            await $('tour-next').onclick();
            switchTab('settings'); await new Promise(r=>setTimeout(r,700));
            check(!$('tour-spotlight').hidden, 'reselecting current tab keeps the guide target visible');
            await $('tour-next').onclick();
            check(state.tutorial.completed && !state.tutorial.active && tab === 'ai', 'finish returns to AI');
            await refresh(); check($('tour-welcome').hidden && $('tour-coach').hidden, 'completed does not reopen');
            await $('tutorial-replay').onclick(); check(state.tutorial.active && state.tutorial.step === 0, 'replay starts welcome');
            nativeUpdate({...state,apiConfigured:false});
            check(!$('tour-key-later').hidden,'users without a key can learn local features');
            $('tour-key-later').click(); await settle(); check(state.tutorial.step===1,'next works without entering a key');
            const after = await oldNative('status');
            check(stable(after) === stable(original), 'tutorial preserves plans, proposal, timetable, settings and key configuration');
            check(document.documentElement.scrollHeight <= innerHeight + 1 && document.documentElement.scrollWidth <= innerWidth + 1, 'no root overflow');
            return 'PASS: persistence validation, welcome/key states, click restrictions, example week/detail, complete/replay, non-destructive flow';
        } finally {
            native = oldNative; $('prompt').value = originalDraft;
            nativeUpdate(await oldNative('tutorial_save', original.tutorial));
            switchTab(originalTab);
        }
    })()`);
    assert.match(result, /^PASS:/); console.log(result);
} finally { client.close(); }
