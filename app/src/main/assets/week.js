'use strict';
(() => {
    const weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    const startOfDay = value => { const d = new Date(value); d.setHours(0, 0, 0, 0); return d; };
    const weekday = date => weekdays[(date.getDay() + 6) % 7];
    const dayAfter = (d, count) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + count);
    const minutes = d => d.getHours() * 60 + d.getMinutes() + d.getSeconds() / 60;
    const clock = m => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(Math.round(m % 60)).padStart(2, '0')}`;
    let week = startOfDay(new Date()), followsToday = true, demo = false, savedWeek, selected = null, renderFrame = 0;
    let segments = [], axis = { start: 480, end: 1080 }, samples = [];
    const board = $('plan-list'), body = $('week-body'), grid = $('week-grid'), detail = $('plan-detail');
    function samplePlans() {
        const slots = [[480,525],[535,580],[600,645],[655,700],[840,885],[895,940],[960,1005],[1015,1060]];
        const labels = ['语文','数学','英语','物理','化学','阅读','自习','社团'];
        const list = [];
        for (let day = 0; day < 6; day++) slots.forEach(([start,end], i) => {
            if ((day + i) % 4 === 3 || (day === 5 && i > 3)) return;
            const date = dayAfter(week, day), a = new Date(date), b = new Date(date);
            a.setMinutes(start); b.setMinutes(end);
            list.push({ id: `example-${day}-${i}`, label: labels[(day + i) % labels.length], start: a.toISOString(), end: b.toISOString(), mode: i === 7 ? 'ring' : i === 4 ? 'silent' : 'vibrate', enabled: true });
        });
        return list;
    }
    const source = () => demo ? samples : plans;
    function groupedHeader(id, dates, key, label, cls) {
        const root = $(id); root.replaceChildren(el('span'));
        for (let a = 0; a < dates.length;) {
            let b = a + 1; while (b < dates.length && key(dates[b]) === key(dates[a])) b++;
            const cell = el('div', label(dates[a]), `calendar-group ${cls}`);
            cell.setAttribute('aria-label', cls === 'year' ? `${dates[a].getFullYear()} 年` : `${dates[a].getFullYear()} 年 ${dates[a].getMonth()+1} 月`);
            cell.style.gridColumn = `${a + 2} / span ${b - a}`; root.append(cell); a = b;
        }
    }
    function split(items, start) {
        const result = [];
        for (let day = 0; day < 7; day++) {
            const date = dayAfter(start, day), tomorrow = dayAfter(start, day + 1);
            for (const plan of items) {
                const from = new Date(plan.start), until = new Date(plan.end);
                if (!(from < tomorrow && until > date)) continue;
                result.push({ plan, day, start: from <= date ? 0 : minutes(from), end: until >= tomorrow ? 1440 : minutes(until), before: from < date, after: until > tomorrow });
            }
        }
        return result;
    }
    // Pack overlapping (including disabled) intervals into separate lanes; never hide one behind another.
    function lanes(items) {
        const sorted = [...items].sort((a,b) => a.start - b.start || b.end - a.end);
        let group = [], end = -1;
        function flush() {
            const ends = [];
            for (const item of group) {
                let lane = ends.findIndex(value => value <= item.start);
                if (lane < 0) lane = ends.length;
                ends[lane] = item.end; item.lane = lane;
            }
            group.forEach(item => item.lanes = ends.length); group = [];
        }
        for (const item of sorted) {
            if (item.start >= end) { flush(); end = -1; }
            group.push(item); end = Math.max(end, item.end);
        }
        flush(); return sorted;
    }
    function status(plan) {
        if (!plan.enabled) return '已停用';
        if (demo) return '教学示例 · 不会执行';
        const event = state?.events.find(e => e.plan === plan.id && e.revision === state.revision);
        if (event) return ({applied: state.activeId === plan.id ? '正在执行' : '曾成功执行', ended:'执行区间已结束', failed:'执行失败', manual:'尊重手动修改'})[event.result] || event.result;
        return Date.parse(plan.end) < Date.now() ? '已过期 · 无成功记录' : '待执行';
    }
    function openDetail(plan) {
        selected = plan; $('plan-detail-title').textContent = plan.label;
        $('plan-detail-mode').textContent = names[plan.mode];
        const content = $('plan-detail-body'); content.replaceChildren();
        const format = value => new Date(value).toLocaleString('zh-CN', { hour12:false, month:'long', day:'numeric', weekday:'short', hour:'2-digit', minute:'2-digit', year:'numeric' });
        content.append(el('p', `开始 · ${format(plan.start)}`), el('p', `结束 · ${format(plan.end)}`), el('p', `状态 · ${status(plan)}`), el('p', `时区 · ${state?.zone || Intl.DateTimeFormat().resolvedOptions().timeZone}`));
        if(plan.location) content.append(el('p','地点 · '+plan.location));
        if(!demo) content.append(el('p',plan.notice_minutes?'提醒 · 提前 '+plan.notice_minutes+' 分钟':'提醒 · 未开启'));
        $('plan-detail-hint').textContent = demo ? '示例不会执行。点卡片或上方模糊空白处收起，即可下一步。' : '点卡片或上方模糊空白处收起';
        const actions = $('plan-detail-actions'); actions.replaceChildren(); actions.hidden = demo;
        if (!demo) {
            const revision = state.revision;
            const edit = el('button','编辑'), toggle = el('button',plan.enabled ? '停用' : '启用'), remove = el('button','删除');
            edit.onclick = () => { detail.close(); openEditor(plan); };
            toggle.onclick = async () => {
                if (await askConfirm(`${plan.enabled ? '停用' : '启用'}“${plan.label}”？会重新计算当前铃声模式。`) && await mutate('save',{revision,edit:true,plan:{...plan,enabled:!plan.enabled}})) detail.close();
            };
            remove.onclick = async () => { if (await askConfirm(`删除“${plan.label}”并取消对应定时任务？`) && await mutate('delete',{revision,planId:plan.id})) detail.close(); };
            actions.append(edit,toggle,remove);
        }
        if (!detail.open) detail.showModal();
        grid.querySelectorAll('.week-card').forEach(card=>card.setAttribute('aria-expanded',String(card.dataset.planId===plan.id)));
        document.dispatchEvent(new CustomEvent('week-card-open',{detail:{demo}}));
    }
    function openDay(day) {
        const rows = segments.filter(s => s.day === day).sort((a,b)=>a.start-b.start);
        if (!rows.length) { toast('这一天没有安排'); return; }
        selected = null; $('plan-detail-title').textContent = `${localDate(dayAfter(week,day))} · ${weekday(dayAfter(week,day))}`; $('plan-detail-mode').textContent = `${rows.length} 项`;
        $('plan-detail-body').replaceChildren(...rows.map(s => {
            const button = el('button',`${clock(s.start)}–${clock(s.end)} · ${s.plan.label}${s.plan.location?" · "+s.plan.location:""} · ${names[s.plan.mode]}`,'day-agenda-item');
            button.onclick = event => { event.stopPropagation(); openDetail(s.plan); }; return button;
        }));
        $('plan-detail-actions').replaceChildren(); $('plan-detail-actions').hidden = true;
        $('plan-detail-hint').textContent = '点击条目查看详情；点击卡片空白处收起'; detail.showModal();
    }
    function layout() {
        const height = body.clientHeight; grid.replaceChildren();
        const duration = axis.end - axis.start;
        const step = [30,60,120,180,240,360,720,1440].find(n => height * n / duration >= 38) || 1440;
        for (let day = 0; day < 7; day++) {
            const column = el('div',undefined,'week-day-column');
            column.classList.toggle('is-today',localDate(dayAfter(week,day)) === today());
            column.style.left = `calc(var(--gutter) + (100% - var(--gutter)) * ${day} / 7)`;
            column.style.width = 'calc((100% - var(--gutter)) / 7)'; grid.append(column);
        }
        const ticks = [axis.start];
        for (let time = Math.ceil(axis.start/step)*step; time < axis.end; time += step)
            if (time > axis.start && height*(time-axis.start)/duration >= 20 && height*(axis.end-time)/duration >= 20) ticks.push(time);
        ticks.push(axis.end);
        for (const time of ticks) { const tick = el('div',undefined,'week-tick'); tick.style.top = `${100*(time-axis.start)/duration}%`; tick.append(el('span',clock(time))); grid.append(tick); }
        for (let day = 0; day < 7; day++) for (const item of lanes(segments.filter(s=>s.day===day))) {
            const plan = item.plan, card = el('button',undefined,`week-card mode-${plan.mode}`);
            card.dataset.planId = plan.id; card.dataset.day = day;
            card.style.top = `${100*(item.start-axis.start)/duration}%`;
            card.style.height = `max(2px,calc(${100*(item.end-item.start)/duration}% - 2px))`;
            card.style.left = `calc(var(--gutter) + (100% - var(--gutter)) * ${day + item.lane/item.lanes} / 7 + 2px)`;
            card.style.width = `calc((100% - var(--gutter)) / ${7*item.lanes} - 4px)`;
            const cardHeight = height*(item.end-item.start)/duration;
            card.classList.toggle('tiny',cardHeight < 26);
            card.classList.toggle('compact',cardHeight < 44);
            card.classList.toggle('disabled',!plan.enabled); card.classList.toggle('active-plan',!demo && state?.activeId===plan.id);
            card.setAttribute('aria-label',`${localDate(dayAfter(week,day))} ${plan.label}，${clock(item.start)}至${clock(item.end)}，${names[plan.mode]}${plan.location?"，地点 "+plan.location:""}，${status(plan)}，点击展开`);
            card.setAttribute('aria-expanded',String(detail.open && selected?.id===plan.id));
            card.append(el('strong',(item.before?'↳ ':'')+plan.label+(item.after?' ↴':'')),el('small',`${clock(item.start)}`));
            if(plan.location){
                const full=plan.location.trim();
                const code=full.match(/(?:[东南西北]|[A-Za-z])\s*[-－–]\s*\d+[A-Za-z]?|\d+[号室房]/g)?.pop();
                const short=plan.location_short || code || (Array.from(full).length>8?Array.from(full).slice(0,7).join('')+'…':full);
                const room=el('small',short,'week-room');room.title=plan.location;
                card.append(room);card.classList.add('has-room');
            }
            card.onclick = () => openDetail(plan); grid.append(card);
        }
        document.dispatchEvent(new Event('week-layout'));
    }
    function renderWeek() {
        if (followsToday && !demo) week = startOfDay(new Date());
        const dates = Array.from({length:7},(_,i)=>dayAfter(week,i));
        const crossesYear = dates[0].getFullYear() !== dates[6].getFullYear();
        groupedHeader('week-years',dates,d=>d.getFullYear(),d=>crossesYear?String(d.getFullYear()).slice(-2):String(d.getFullYear()),'year');
        groupedHeader('week-months',dates,d=>`${d.getFullYear()}-${d.getMonth()}`,d=>`${String(d.getMonth()+1).padStart(2,'0')} 月`,'month');
        $('week-days').replaceChildren(el('span','时间'));
        dates.forEach((d,i)=>{const button=el('button',undefined,'week-day');button.classList.toggle('is-today',localDate(d)===today());button.classList.toggle('weekend',[0,6].includes(d.getDay()));button.setAttribute('aria-label',`${localDate(d)} ${weekday(d)}，查看当天全部安排`);button.append(el('strong',String(d.getDate()).padStart(2,'0')),el('small',weekday(d)));button.onclick=()=>openDay(i);$('week-days').append(button);});
        segments = split(source(),week);
        const min = segments.length ? Math.min(...segments.map(s=>s.start)) : 480, max = segments.length ? Math.max(...segments.map(s=>s.end)) : 1080;
        axis = {start:Math.floor(min/60)*60,end:Math.ceil(max/60)*60};
        if (axis.end-axis.start < 120) { axis.end=Math.min(1440,axis.start+120);axis.start=Math.max(0,axis.end-120); }
        $('week-date').value = localDate(week); $('week-empty').hidden = !!segments.length;
        $('week-summary').textContent = `${demo?'示例周表':localDate(week)===localDate(startOfDay(new Date()))?'今天起 7 天':'这 7 天'} · ${new Set(segments.map(s=>s.plan.id)).size} 项 · 点击卡片查看`;
        $('week-demo-label').hidden = !demo;
        cancelAnimationFrame(renderFrame); layout();
    }
    function changeWeek(date, follow = false) { if (!Number.isFinite(date.getTime())) return; followsToday=follow;week=startOfDay(date);renderWeek(); }
    $('week-prev').onclick=()=>changeWeek(dayAfter(week,-7));$('week-next').onclick=()=>changeWeek(dayAfter(week,7));$('week-today').onclick=()=>changeWeek(new Date(),true);
    $('week-date').onchange=()=>changeWeek(new Date($('week-date').value+'T12:00:00'));
    $('plan-tools-open').onclick=()=>$('plan-tools').showModal();$('plan-tools-close').onclick=()=>$('plan-tools').close();
    // Management's existing dialogs stay independent; avoid stacking them under the tools sheet.
    for (const id of ['json-open','json-export','automation-open','automation-ai']) $(id).addEventListener('click',()=>$('plan-tools').close());
    $('plan-detail-card').onclick=event=>{if(!event.target.closest('button'))detail.close();};
    $('plan-detail-card').onkeydown=event=>{if(event.target===$('plan-detail-card')&&['Enter',' '].includes(event.key)){event.preventDefault();detail.close();}};
    $('plan-detail-close').onclick=()=>detail.close();
    function isDetailBackdrop(event) {
        if (!detail.open || event.target !== detail) return false;
        const point = event.touches?.[0] || event, rect = detail.getBoundingClientRect();
        return point.clientX < rect.left || point.clientX > rect.right || point.clientY < rect.top || point.clientY > rect.bottom;
    }
    detail.addEventListener('click',event=>{if(isDetailBackdrop(event))detail.close();});
    detail.addEventListener('close',()=>{selected=null;grid.querySelectorAll('.week-card').forEach(card=>card.setAttribute('aria-expanded','false'));document.dispatchEvent(new CustomEvent('week-card-close',{detail:{demo}}));});
    new ResizeObserver(()=>{cancelAnimationFrame(renderFrame);renderFrame=requestAnimationFrame(layout);}).observe(body);
    window.weekView={render:renderWeek,isDetailBackdrop,setDemo(value){if(value===demo)return;demo=value;if(demo){savedWeek=week;week=startOfDay(new Date());samples=samplePlans();}else{week=savedWeek||startOfDay(new Date());if(detail.open)detail.close();}renderWeek();},firstCard:()=>grid.querySelector('.week-card'),inspect:()=>({week:localDate(week),demo,axis,segments:segments.map(s=>({id:s.plan.id,day:s.day,start:s.start,end:s.end})),split,lanes})};
    renderWeek();
    function atNextDay() {
        const now = new Date();
        setTimeout(()=>{if(followsToday&&!demo)renderWeek();atNextDay();},Math.max(1,dayAfter(now,1).getTime()-now.getTime()+50));
    }
    atNextDay();
})();
