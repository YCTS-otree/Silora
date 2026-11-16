'use strict';
(() => {
    const confirmation = $('confirm-sheet'), picker = $('picker-sheet');
    let resolveConfirm, source = null, selectedDate, month;
    const pad = n => String(n).padStart(2, '0');
    window.askConfirm = (message, options = {}) => {
        if (confirmation.open) return Promise.resolve(false);
        $('confirm-title').textContent = options.title || '请确认';
        $('confirm-copy').textContent = message;
        $('confirm-yes').textContent = options.confirmLabel || '确认';
        $('confirm-no').textContent = options.cancelLabel || '取消';
        $('confirm-details').replaceChildren();
        for (const [label, value] of options.rows || []) {
            const row = el('div'); row.append(el('dt', label), el('dd', String(value))); $('confirm-details').append(row);
        }
        $('confirm-details').hidden = !options.rows?.length;
        confirmation.returnValue = ''; confirmation.showModal();
        return new Promise(resolve => { resolveConfirm = resolve; });
    };
    $('confirm-no').onclick = () => confirmation.close('cancel');
    $('confirm-yes').onclick = () => confirmation.close('yes');
    confirmation.addEventListener('close', () => { const resolve = resolveConfirm; resolveConfirm = null; resolve?.(confirmation.returnValue === 'yes'); });
    function convert(root) {
        const inputs = [...root.querySelectorAll('input[type=date],input[type=time]')];
        if (root.matches?.('input[type=date],input[type=time]')) inputs.push(root);
        for (const input of inputs) {
            const kind = input.type, value = input.value;
            input.dataset.picker = kind; input.type = 'text'; input.value = value;
            input.readOnly = true; input.inputMode = 'none'; input.classList.add('silora-picker');
            input.placeholder = kind === 'date' ? '选择日期' : '选择时间';
            input.setAttribute('aria-haspopup', 'dialog');
        }
    }
    function validDate(value) {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
        const date = new Date(value + 'T12:00:00');
        return Number.isFinite(date.getTime()) && localDate(date) === value ? date : null;
    }
    function paintCalendar() {
        $('calendar-month').textContent = `${month.getFullYear()} 年 ${month.getMonth() + 1} 月`;
        const grid = $('calendar-days'); grid.replaceChildren();
        const offset = (month.getDay() + 6) % 7;
        for (let i = 0; i < 42; i++) {
            const day = new Date(month.getFullYear(), month.getMonth(), i - offset + 1, 12);
            const value = localDate(day), button = el('button', String(day.getDate()));
            button.type = 'button'; button.classList.toggle('selected', value === selectedDate);
            button.classList.toggle('today', value === today()); button.classList.toggle('outside', day.getMonth() !== month.getMonth());
            button.setAttribute('aria-label', value); button.setAttribute('aria-pressed', String(value === selectedDate));
            button.onclick = () => { selectedDate = value; $('picker-selection').textContent = value; paintCalendar(); };
            grid.append(button);
        }
    }
    function openPicker(input) {
        if (input.disabled || picker.open) return;
        source = input; $('picker-error').textContent = '';
        const dateMode = input.dataset.picker === 'date';
        $('date-picker').hidden = !dateMode; $('time-picker').hidden = dateMode;
        $('picker-title').textContent = dateMode ? '选择日期' : '选择时间';
        $('picker-selection').hidden = !dateMode;
        if (dateMode) {
            const date = validDate(input.value) || new Date(); selectedDate = localDate(date);
            month = new Date(date.getFullYear(), date.getMonth(), 1, 12); $('picker-selection').textContent = selectedDate; paintCalendar();
        } else {
            const parts = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(input.value);
            $('picker-hour').value = parts?.[1] ?? '09'; $('picker-minute').value = parts?.[2] ?? '00';
        }
        picker.showModal();
    }
    document.addEventListener('click', event => { const input = event.target.closest('input[data-picker]'); if (input) { event.preventDefault(); openPicker(input); } });
    document.addEventListener('keydown', event => {
        if (event.target.matches('input[data-picker]') && ['Enter', ' '].includes(event.key)) { event.preventDefault(); openPicker(event.target); }
    });
    document.addEventListener('submit', event => {
        const missing = [...event.target.querySelectorAll('input[data-picker][required]')].find(input => !input.value);
        if (missing) { event.preventDefault(); event.stopImmediatePropagation(); openPicker(missing); }
    }, true);
    $('calendar-prev').onclick = () => { month.setMonth(month.getMonth() - 1); paintCalendar(); };
    $('calendar-next').onclick = () => { month.setMonth(month.getMonth() + 1); paintCalendar(); };
    $('calendar-today').onclick = () => { const date = new Date(); selectedDate = today(); month = new Date(date.getFullYear(), date.getMonth(), 1, 12); $('picker-selection').textContent = selectedDate; paintCalendar(); };
    document.querySelectorAll('[data-time-step]').forEach(button => button.onclick = () => {
        const input = $(button.dataset.target), limit = input.id === 'picker-hour' ? 24 : 60;
        input.value = pad(((Number(input.value) || 0) + Number(button.dataset.timeStep) + limit) % limit);
    });
    document.querySelectorAll('[data-minute]').forEach(button => button.onclick = () => { $('picker-minute').value = button.dataset.minute; });
    $('picker-cancel').onclick = () => picker.close();
    $('picker-done').onclick = () => {
        let value = selectedDate;
        if (source?.dataset.picker === 'time') {
            const hour = $('picker-hour').value.trim(), minute = $('picker-minute').value.trim();
            if (!/^\d{1,2}$/.test(hour) || !/^\d{1,2}$/.test(minute) || Number(hour) > 23 || Number(minute) > 59) {
                $('picker-error').textContent = '小时请输入 0–23，分钟请输入 0–59。'; return;
            }
            value = `${pad(Number(hour))}:${pad(Number(minute))}`;
        }
        if (source?.isConnected) { source.value = value; source.dispatchEvent(new Event('input', { bubbles: true })); source.dispatchEvent(new Event('change', { bubbles: true })); }
        picker.close();
    };
    const oldBack = window.goBack;
    window.goBack = () => {
        if (confirmation.open) { confirmation.close('cancel'); return true; }
        if (picker.open) { picker.close(); return true; }
        return oldBack();
    };
    convert(document);
    new MutationObserver(records => { for (const record of records) for (const node of record.addedNodes) if (node.nodeType === 1) convert(node); }).observe(document.body, { childList: true, subtree: true });
})();
