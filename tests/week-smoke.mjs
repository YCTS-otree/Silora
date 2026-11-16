// Real WebView layout/behavior with synthetic UI data; no native plan mutations.
import { connectSilora } from './webview-client.mjs';
const client = await connectSilora();
try {
 console.log(await client.evaluate(`(async()=>{
    const originalNative=native, before=await native('status'), oldTab=tab, oldTheme=theme;
    const checks=[], check=(v,n)=>{if(!v)throw Error(n);checks.push(n);},settle=()=>new Promise(r=>setTimeout(r,180));
    const stable=s=>JSON.stringify([s.revision,s.plans,s.periodVersion,s.periodTable,s.automation,s.proposal,s.apiConfigured,s.restore,s.manual]);
    let fake={...before,tutorial:{step:7,active:false,completed:true},plans:[]}, saves=[];
    const plan=(id,start,end,mode='vibrate',enabled=true)=>({id,label:id,start,end,mode,enabled});
    const choose=date=>{$('week-date').value=date;$('week-date').dispatchEvent(new Event('change',{bubbles:true}));};
    try {
        native=(action,data)=>{if(action==='status')return Promise.resolve(fake);if(['save','delete','import'].includes(action)){saves.push({action,data});return Promise.resolve(fake);}return originalNative(action,data);};
        nativeUpdate(fake);switchTab('plans');await settle();
        choose('2026-12-28');await settle();
        check([...$('week-years').querySelectorAll('.calendar-group')].map(e=>e.textContent).join(',')==='26,27','cross-year header groups');
        check([...$('week-months').querySelectorAll('.calendar-group')].map(e=>e.textContent).join(',')==='12 月,01 月','cross-month header groups');
        check($('week-years').children[1].style.gridColumn==='2 / span 4','year group spans actual weekdays');
        check([...document.querySelectorAll('.week-day strong')].map(e=>e.textContent).join(',')==='28,29,30,31,01,02,03','selected start date across year');
        fake.plans=[plan('跨日会议','2026-12-31T23:30:00+08:00','2027-01-01T00:30:00+08:00'),plan('到午夜结束','2026-12-28T23:00:00+08:00','2026-12-29T00:00:00+08:00')];nativeUpdate(fake);await settle();
        const split=weekView.inspect().segments;
        check(split.filter(s=>s.id==='跨日会议').length===2,'cross-midnight splits into two day segments');
        check(split.filter(s=>s.id==='到午夜结束').length===1,'midnight end stays exclusive');
        check(weekView.inspect().axis.start===0&&weekView.inspect().axis.end===1440,'full-day axis includes midnight segments');
        choose('2026-11-22');await settle();
        check([...document.querySelectorAll('.week-day small')].map(e=>e.textContent).join(',')==='Sun,Mon,Tue,Wed,Thu,Fri,Sat','Sunday leads the rolling seven-day view');
        check([...document.querySelectorAll('.week-day strong')].map(e=>e.textContent).join(',')==='22,23,24,25,26,27,28','rolling dates advance from selected Sunday');
        $('week-next').click();await settle();check(weekView.inspect().week==='2026-11-29','next advances exactly seven days');
        $('week-today').click();await settle();check(weekView.inspect().week===today(),'today restores rolling start');
        choose('2028-02-29');await settle();check([...document.querySelectorAll('.week-day strong')].some(e=>e.textContent==='29'),'leap-day week');
        choose('2026-11-16');fake.plans=[plan('早课','2026-11-16T08:00:00+08:00','2026-11-16T09:00:00+08:00'),plan('长课','2026-11-17T08:00:00+08:00','2026-11-17T10:00:00+08:00','silent'),plan('停用重叠','2026-11-16T08:30:00+08:00','2026-11-16T09:30:00+08:00','ring',false)];nativeUpdate(fake);await settle();
        const cards=[...document.querySelectorAll('.week-card')],a=cards.find(e=>e.dataset.planId==='早课'),b=cards.find(e=>e.dataset.planId==='长课'),c=cards.find(e=>e.dataset.planId==='停用重叠');
        check(Math.abs((b.offsetHeight+2)/(a.offsetHeight+2)-2)<.04,'card heights follow elapsed time');
        check(a.getBoundingClientRect().right<=c.getBoundingClientRect().left+1,'overlapping disabled plans use separate lanes');
        check(c.classList.contains('disabled'),'disabled plans visually distinguished');
        a.click();await settle();check($('plan-detail').open&&$('plan-detail-body').textContent.includes('08:00'),'card opens actual time detail');
        $('plan-detail-card').click();await settle();check(!$('plan-detail').open,'expanded card closes on tap');
        a.click();await settle();const bounds=$('plan-detail').getBoundingClientRect();$('plan-detail').dispatchEvent(new MouseEvent('click',{bubbles:true,clientX:innerWidth/2,clientY:Math.max(0,bounds.top-15)}));await settle();check(!$('plan-detail').open,'blur-area tap dismisses detail');
        a.click();await settle();$('plan-detail-actions').querySelector('button').click();await settle();check($('editor').open&&$('label').value==='早课','existing edit remains reachable');$('editor').close();
        document.querySelector('.week-day').click();await settle();check($('plan-detail-body').querySelectorAll('.day-agenda-item').length===2,'day header exposes all dense/overlapping items');$('plan-detail-close').click();
        $('plan-tools-open').click();check($('plan-tools').open,'management remains reachable');$('json-open').click();await settle();check($('json-dialog').open&&!$('plan-tools').open,'management hands off to existing import');$('json-dialog').close();
        fake.plans=[];nativeUpdate(fake);await settle();check(!$('week-empty').hidden,'empty week remains usable');
        for(const value of ['light','dark']){theme=value;applyTheme();weekView.setDemo(true);await settle();
          const area=$('week-body').getBoundingClientRect(), all=[...document.querySelectorAll('.week-card')];
          check(area.height>100&&all.length>20&&all.every(e=>{const r=e.getBoundingClientRect();return r.top>=area.top-1&&r.bottom<=area.bottom+1&&r.right<=area.right+1;}),'all sample cards fit '+value);
          check(document.querySelector('main').scrollHeight<=document.querySelector('main').clientHeight+1&&document.documentElement.scrollWidth<=innerWidth+1,'one screen without overflow '+value);
          weekView.setDemo(false);
        }
        check(saves.length===0,'viewing and cancelled editing never save a plan');
    }finally{document.querySelectorAll('dialog[open]').forEach(d=>d.close());weekView.setDemo(false);native=originalNative;theme=oldTheme;applyTheme();nativeUpdate(before);switchTab(oldTab);await refresh();}
    check(stable(await originalNative('status'))===stable(before),'native plans and reference data unchanged');
    return checks;
 })()`));
} finally { client.close(); }
