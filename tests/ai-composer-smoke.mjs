import { connectSilora } from './webview-client.mjs';
const client = await connectSilora();
try {
    const checks = await client.evaluate(`(async()=>{
        if (aiBusy || attachedImages.length) throw Error('Requires idle composer without pending attachments');
        const checks = [], originalNative = native, originalState = state;
        const originalPrompt = $('prompt').value, children = [...$('messages').childNodes];
        const originalEffort = (await native('status')).reasoningEffort;
        const assert = (value, name) => { if (!value) throw Error(name); checks.push(name); };
        try {
            assert(['low','high','max'].includes(originalEffort), 'saved effort is valid');
            for (const effort of ['low','max','high']) {
                await originalNative('ai_settings', {effort});
                assert((await originalNative('status')).reasoningEffort === effort, 'native saves ' + effort);
            }
            let rejected = false;
            try { await originalNative('ai_settings', {effort:'none'}); } catch { rejected = true; }
            assert(rejected && (await originalNative('status')).reasoningEffort === 'high', 'cannot disable thinking');
            const fake = [{id:'smoke',preview:'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7'}];
            state = {...state,apiConfigured:true};
            for (const text of ['', '带图消息']) {
                let resolveSend;
                native = (action,data) => action === 'ai_chat' ? new Promise(resolve => resolveSend=resolve) : originalNative(action,data);
                renderAttachments(fake); $('prompt').value = text;
                const done = $('send').onclick();
                assert(attachedImages.length === 0 && $('attachment').hidden, 'immediate clear: ' + (text || 'image only'));
                assert($('image-pick').disabled, 'no attachment changes during request');
                resolveSend({document:{status:'clarification_required',question:'测试追问'},images:[]});
                await done;
                assert(attachedImages.length === 0 && !$('image-pick').disabled, 'success leaves empty composer');
            }
            let rejectSend;
            native = (action,data) => action === 'ai_chat' ? new Promise((_,reject) => rejectSend=reject) : originalNative(action,data);
            renderAttachments(fake); $('prompt').value = '';
            const done = $('send').onclick(); rejectSend(Error('模拟失败 / 取消')); await done;
            assert(attachedImages.length === 1 && !$('attachment').hidden, 'failure restores pending image');
        } finally {
            native = originalNative; state = originalState; renderAttachments([]);
            $('prompt').value = originalPrompt; $('messages').replaceChildren(...children);
            await originalNative('ai_settings', {effort:originalEffort}); await refresh();
        }
        return checks;
    })()`);
    checks.forEach(check => console.log('PASS ' + check));
} finally { client.close(); }
