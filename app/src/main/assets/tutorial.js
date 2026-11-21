'use strict';
(() => {
    let progress = null, saving = false, checkingKey = false, target = null, frame = 0, resizeTimer = 0;
    let weekLesson = 0, lastStep = null;
    const main = document.querySelector('main');
    const coach = $('tour-coach'), spotlight = $('tour-spotlight');
    const active = () => progress?.active;
    const steps = [null,
        ['先说说你的安排', '像聊天一样输入即可，例如“明天上午开会，帮我静音”。日期、模式不完整时，静律会提出合理方案让你确认。'],
        ['有课表？直接选图片', '点“＋ 图片”选择作息表和课程表，可一次选多张。文字在图片下方；发送后附件自动清空。没有图片也可以继续。'],
        ['发送 → 核对 → 确认', '准备好后，点真实界面的“发送”。会显示发送与思考状态；生成方案后核对日期、模式，再回复“是”或点确认。此步骤会使用你的 API 额度，也可以稍后再试。'],
        ['安排都在计划表', '点击底部“计划表”，查看已保存的安排。'],
        ['让系统允许按时执行', '点击底部“设置”，查看执行权限。'],
        ['决定结束后怎么恢复', '在这里选择区间结束后的铃声模式，以及手动修改时如何处理。需要调整时再点击“保存策略”。'],
        ['准备好了，开始使用吧', '之后只需：告诉静律 → 核对方案 → 确认执行。课时表保存后可重复使用，单双周规则也能交给 AI 安排。随时可在设置中重温教学。']
    ];
    function positionSpotlight() {
        cancelAnimationFrame(frame);
        frame = requestAnimationFrame(() => {
            if (!active() || !progress.step || !target || document.querySelector('dialog[open]')) { spotlight.hidden = true; return; }
            const r = target.getBoundingClientRect();
            const clip = target.closest('nav') ? document.querySelector('nav').getBoundingClientRect() : main.getBoundingClientRect();
            const top = Math.max(r.top, clip.top), bottom = Math.min(r.bottom, clip.bottom);
            const left = Math.max(r.left, clip.left), right = Math.min(r.right, clip.right);
            spotlight.hidden = bottom <= top || right <= left;
            Object.assign(spotlight.style, { top: `${top}px`, left: `${left}px`, width: `${right-left}px`, height: `${bottom-top}px` });
        });
    }
    function highlight(next) {
        const group=next?.closest("details.setting-group"); if(group)group.open=true;
        const changed = target !== next; if (changed) target?.classList.remove('tour-target'); target = next; target?.classList.add('tour-target');
        if (changed && target && !target.closest('nav') && !target.closest('#plans')) {
            const rect = target.getBoundingClientRect(), viewport = main.getBoundingClientRect();
            const destination = rect.top - viewport.top + main.scrollTop - Math.max(0, (main.clientHeight - rect.height) / 2);
            const smooth = target.closest('#settings') && !matchMedia('(prefers-reduced-motion: reduce)').matches;
            main.scrollTo({ top: Math.max(0, Math.min(destination, main.scrollHeight - main.clientHeight)), behavior: smooth ? 'smooth' : 'instant' });
        }
        positionSpotlight();
    }
    function keepFocusedVisible() {
        // Android resizes the WebView for the keyboard; keep the focused field
        // inside main rather than underneath the teaching panel.
        if (active() && !target?.closest('#plans') && target?.contains(document.activeElement)) {
            const r = target.getBoundingClientRect(), area = main.getBoundingClientRect();
            if (r.height <= area.height && (r.top < area.top || r.bottom > area.bottom))
                target.scrollIntoView({ block: 'center', behavior: 'instant' });
        }
        positionSpotlight();
    }
    function resized() {
        keepFocusedVisible();
        // WebView can scroll the caret again after the IME resize animation.
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(keepFocusedVisible, 350);
    }
    function paint() {
        if (!progress) return;
        if (lastStep !== progress.step) { weekLesson = 0; lastStep = progress.step; }
        window.weekView.setDemo(active() && progress.step === 4 && tab === 'plans');
        const welcome = active() && progress.step === 0, running = active() && progress.step > 0;
        document.body.classList.toggle('tour-welcoming', welcome);
        document.body.classList.toggle('tour-running', running);
        $('tour-welcome').hidden = !welcome; coach.hidden = !running;
        $('tutorial-resume').hidden = progress.completed || active();
        if (welcome) {
            $('tour-key-entry').hidden = !!state?.apiConfigured;
            $('tour-key-ready').hidden = !state?.apiConfigured;
            $('tour-key-next').disabled = checkingKey || saving || (!state?.apiConfigured && !$('tour-key').value.trim());
            $('tour-key-later').hidden = checkingKey || saving || !!state?.apiConfigured;
            $('tour-key-later').textContent = '下一步，先了解本地功能';
            $('tour-key-next').textContent = checkingKey ? '正在验证…' : state?.apiConfigured ? '开始认识静律' : '验证并继续';
        }
        if (!running) { target?.classList.remove('tour-target'); target = null; spotlight.hidden = true; return; }
        const step = progress.step;
        $('tour-counter').textContent = `认识静律 · ${step} / 7`;
        $('tour-steps').replaceChildren(...steps.slice(1).map((_, i) => el('span', undefined, i < step ? 'done' : '')));
        $('tour-title').textContent = steps[step][0]; $('tour-copy').textContent = steps[step][1];
        $('tour-next').textContent = step === 7 ? '完成，开始使用' : step === 3 ? '已了解，继续' : '下一步';
        $('tour-next').disabled = saving || (step === 4 && tab !== 'plans') || (step === 5 && tab !== 'settings');
        $('tour-prev').disabled = saving;
        $('tour-action').hidden = true;
        let next;
        if (step <= 3 && tab !== 'ai') {
            $('tour-copy').textContent = '先点击底部 AI，回到输入安排的地方。';
            $('tour-next').disabled = true; $('tour-next').textContent = '↓ 点击「AI」'; next = document.querySelector('[data-tab="ai"]');
        } else if (step === 1) {
            next = $('prompt'); $('tour-action').hidden = false; $('tour-action').textContent = '填入示例';
            $('tour-action').disabled = !!$('prompt').value.trim() || aiBusy;
        } else if (step === 2) {
            next = attachedImages.length ? $('attachment') : $('image-pick');
            $('tour-next').textContent = attachedImages.length ? '图片已选好，继续' : '没有图片，继续';
        } else if (step === 3) {
            next = aiBusy ? $('ai-progress') : !$('proposal-card').hidden ? $('proposal-card') : $('send');
            if (aiBusy) $('tour-copy').textContent = '消息正在处理，真实进度显示在上方。可以继续了解其他功能，回复仍会正常接收；也可以点击“取消请求”。';
            else if (!$('proposal-card').hidden) $('tour-copy').textContent = '上方是当前真实的待确认方案。请核对日期、模式及替换范围；只有你确认才会启用。可以先继续教学，之后再决定。';
            const review = ['period-dialog','automation-dialog','json-dialog'].map($).find(d=>d.open);
            if (review) { next = review; $('tour-copy').textContent = '这里是 AI 的真实结果，核对后再确认；也可取消，继续教学。'; }
        } else if (step === 4) {
            next = document.querySelector('[data-tab="plans"]');
            $('tour-next').textContent = '↓ 点击「计划表」';
            if (tab === 'plans') {
                $('tour-title').textContent = '一屏看懂这一周';
                $('tour-next').disabled = saving || weekLesson < 2;
                if ($('plan-detail').open) {
                    next = $('plan-detail-card');
                    $('tour-copy').textContent = '这里有完整时间、模式和状态。点卡片或上方模糊空白处收起，即可下一步。';
                    $('tour-next').textContent = '收起卡片后继续';
                } else if (weekLesson < 2) {
                    next = window.weekView.firstCard();
                    $('tour-copy').textContent = '示例不会执行。横轴从今天起显示 7 天，纵轴按时间缩放。点开并收起发光卡片，才能下一步。';
                    $('tour-next').textContent = '↑ 点开示例卡片';
                } else {
                    next = $('plan-list');
                    $('tour-copy').textContent = '卡片操作已完成，可以下一步了。周日会从 Sun 开始；跨月、跨年分组显示，点日期可看当天全部安排。';
                    $('tour-next').textContent = '下一步';
                }
            }
        } else if (step === 5) {
            next = tab === 'settings' ? $('permission-panel') : document.querySelector('[data-tab="settings"]');
            if (tab !== 'settings') $('tour-next').textContent = '↓ 点击「设置」';
            if (tab === 'settings') $('tour-copy').textContent = '精确闹钟用于按时调度，通知策略访问用于铃声切换；通知权限用于到期提醒。缺少时点对应按钮前往系统授权，返回后会重新检查。';
        } else if (step === 6) {
            next = tab === 'settings' ? $('execution-settings') : document.querySelector('[data-tab="settings"]');
            $('tour-next').disabled = saving || tab !== 'settings';
            if (tab !== 'settings') $('tour-next').textContent = '↓ 点击「设置」';
        } else if (step === 7) next = tab === 'settings' ? $('tutorial-help-card') : document.querySelector('[data-tab="settings"]');
        $('tour-next').classList.toggle('guide-action', $('tour-next').disabled && !saving);
        if (saving) $('tour-next').textContent = '正在保存…';
        highlight(next);
    }
    async function save(step, enabled, completed = false) {
        if (saving || checkingKey) return false;
        saving = true; $('tour-feedback').textContent = ''; paint();
        try {
            const result = await native('tutorial_save', { step, active: enabled, completed });
            nativeUpdate(result);
            if (!enabled || step > 0) {
                $('tour-key').value = ''; $('tour-key').type = 'password';
                $('tour-key-toggle').textContent = '显示';
                $('tour-key-toggle').setAttribute('aria-label', '显示 API Key');
                $('tour-key-toggle').setAttribute('aria-pressed', 'false');
            }
            return true;
        } catch (e) { toast(e.message); $('tour-feedback').textContent = e.message; return false; }
        finally { saving = false; paint(); }
    }
    async function advance() {
        if (!active() || (progress.step > 0 && $('tour-next').disabled)) return;
        const step = progress.step;
        if (step === 7) { if (await save(7, false, true)) { switchTab('ai'); toast('教学已完成，随时可以在设置中重温'); } }
        else await save(step + 1, true);
    }
    const update = window.nativeUpdate;
    window.nativeUpdate = s => {
        update(s);
        if (s.tutorial) {
            const entering = !progress || (!progress.active && s.tutorial.active);
            progress = s.tutorial;
            if (entering && active() && progress.step > 0) {
                // Restore the appropriate page after process recreation, without changing app data.
                const destination = progress.step >= 6 ? 'settings' : progress.step === 4 ? 'ai' : progress.step === 5 ? 'plans' : 'ai';
                if (tab !== destination) switchTab(destination);
            }
        }
        paint();
    };
    const oldSwitch = switchTab;
    switchTab = next => { target?.classList.remove('tour-target'); target = null; oldSwitch(next); paint(); };
    const oldAttachments = renderAttachments;
    renderAttachments = images => { oldAttachments(images); paint(); };
    const oldBack = window.goBack;
    window.goBack = () => {
        if (active()) return true;
        if (document.querySelector('dialog[open]')) return oldBack();
        return oldBack();
    };
    $('tour-pause').onclick = $('tour-welcome-skip').onclick = () => progress && save(progress.step, false);
    $('tutorial-replay').onclick = () => { if (!aiBusy) { $('tour-key-feedback').textContent = ''; $('tour-key-later').hidden = true; return save(0, true); } else toast('请等待当前 AI 请求结束后再重温教学'); };
    $('tutorial-resume').onclick = () => progress && save(progress.step, true);
    $('tour-next').onclick = advance;
    $('tour-prev').onclick = () => save(Math.max(0, progress.step - 1), true);
    $('tour-action').onclick = () => {
        if (!$('prompt').value.trim() && !aiBusy) { $('prompt').value = '明天上午 9 点到 10 点开会，设为振动。'; $('prompt').focus(); paint(); }
    };
    $('prompt').addEventListener('input', paint);
    $('tour-help').onclick = () => native('open_api_help').catch(e => toast(e.message));
    $('tour-key-toggle').onclick = () => {
        const visible = $('tour-key').type === 'password'; $('tour-key').type = visible ? 'text' : 'password';
        $('tour-key-toggle').textContent = visible ? '隐藏' : '显示';
        $('tour-key-toggle').setAttribute('aria-label', visible ? '隐藏 API Key' : '显示 API Key');
        $('tour-key-toggle').setAttribute('aria-pressed', String(visible));
    };
    $('tour-key').oninput = () => { $('tour-key-feedback').textContent = ''; paint(); };
    $('tour-key-next').onclick = async () => {
        if (checkingKey || saving) return;
        if (state?.apiConfigured) { await advance(); return; }
        const key = $('tour-key').value.trim(); if (!key) return;
        checkingKey = true; $('tour-key-feedback').textContent = '正在验证连接，成功后会加密保存在本机…';
        $('tour-key').disabled = true; $('tour-welcome-skip').disabled = true; paint();
        try {
            const result = await native('ai_key', { key });
            $('tour-key').value = ''; $('tour-key').type = 'password';
            await refresh();
            if (result.available === false) {
                $('tour-key-feedback').textContent = 'Key 已验证并加密保存，但余额不足。充值后即可调用 AI，也可以先了解本地功能。';
                $('tour-key-later').hidden = false;
            } else {
                $('tour-key-feedback').textContent = '连接成功，Key 已加密保存。'; checkingKey = false; await advance();
            }
        } catch (e) { $('tour-key-feedback').textContent = e.message; }
        finally { checkingKey = false; $('tour-key').disabled = false; $('tour-welcome-skip').disabled = false; paint(); }
    };
    $('tour-key-later').onclick = advance;
    // Capture before every application handler; unrelated clicks, touch and keyboard actions are inert.
    function permitted(node) {
        if (!active()) return true;
        if (!(node instanceof Element)) return false;
        if (progress.step === 0) return ['tour-key','tour-key-toggle','tour-help','tour-key-next','tour-key-later'].some(id => $(id).contains(node));
        if (progress.step === 3 && ['period-dialog','automation-dialog','json-dialog'].some(id=>$(id).open)) {
            const top = $('picker-sheet').open ? $('picker-sheet') : $('confirm-sheet').open ? $('confirm-sheet') : target;
            return !!top?.contains(node);
        }
        if ($('confirm-sheet').open && progress.step === 6) return $('confirm-sheet').contains(node);
        if ($('plan-detail').open && progress.step === 4) return $('plan-detail-card').contains(node) || $('plan-detail-close').contains(node);
        return $('tour-next').contains(node) || (!$('tour-action').hidden && $('tour-action').contains(node)) || !!target?.contains(node);
    }
    const block = event => {
        if (active() && progress.step === 4 && ['click','pointerdown','mousedown','touchstart'].includes(event.type) && window.weekView.isDetailBackdrop(event)) return;
        if (!active() || permitted(event.target)) return;
        event.preventDefault(); event.stopImmediatePropagation();
    };
    for (const type of ['click','dblclick','pointerdown','mousedown','touchstart','contextmenu']) window.addEventListener(type, block, {capture:true,passive:false});
    window.addEventListener('keydown', event => {
        if (!active()) return;
        if (event.key === 'Escape') { event.preventDefault(); event.stopImmediatePropagation(); return; }
        if (event.key === 'Tab') {
            const choices = [...document.querySelectorAll('button,input,textarea,select,[tabindex="0"]')].filter(e => permitted(e) && !e.disabled && e.getClientRects().length && !e.closest('[hidden]'));
            if (choices.length) { const i = choices.indexOf(document.activeElement); choices[(i + (event.shiftKey ? -1 : 1) + choices.length) % choices.length].focus(); }
            event.preventDefault(); event.stopImmediatePropagation(); return;
        }
        block(event);
    },true);
    window.addEventListener('focusin',event=>{if(active()&&!permitted(event.target))event.target.blur();},true);
    document.addEventListener('week-card-open',event=>{if(active()&&progress.step===4&&event.detail.demo){weekLesson=1;paint();}});
    document.addEventListener('week-card-close',event=>{if(active()&&progress.step===4&&event.detail.demo&&weekLesson===1){weekLesson=2;paint();}});
    document.addEventListener('week-layout',()=>{if(active()&&progress.step===4)paint();});
    main.addEventListener('scroll', positionSpotlight, { passive: true });
    window.addEventListener('resize', resized);
    window.visualViewport?.addEventListener('resize', resized);
    new ResizeObserver(resized).observe(main);
    // Observe actual result/progress changes; never synthesize a response or confirm a plan.
    new MutationObserver(() => { if (active() && progress.step === 3) paint(); }).observe($('ai-progress'), { childList: true, attributes: true, characterData: true, subtree: true });
    new MutationObserver(() => { if (active() && progress.step === 3) paint(); }).observe($('proposal-card'), { childList: true, attributes: true });
    document.querySelectorAll('dialog').forEach(d => new MutationObserver(() => { if (active() && progress.step === 3) paint(); else positionSpotlight(); }).observe(d, { attributes: true, attributeFilter: ['open'] }));
    if (state) window.nativeUpdate(state);
})();
