import { connectSilora } from './webview-client.mjs';
const client=await connectSilora();
try {
 console.log(await client.evaluate(`(async()=>{
 const checks=[],assert=(v,n)=>{if(!v)throw Error(n);checks.push(n)},tick=()=>new Promise(r=>setTimeout(r,40));
 const originalNative=native,originalState=state,originalTab=tab;
 const before=await native('status');
 if(before.tutorial.active)throw Error('Pause tutorial before UI checks');
 let saves=[];
 try {
  document.querySelectorAll('dialog[open]').forEach(d=>d.close());await tick();
  switchTab('plans');openEditor();$('label').value='UI verification';$('start').value='23:59';$('start').click();
  assert($('picker-sheet').open&&$('start').type==='text','custom time picker');
  document.querySelector('[data-target=picker-hour][data-time-step="1"]').click();
  assert($('picker-hour').value==='00','hour wraps at midnight');
  $('picker-hour').value='24';$('picker-done').click();
  assert($('picker-sheet').open&&$('picker-error').textContent,'invalid hour rejected');
  $('picker-hour').value='0';$('picker-minute').value='5';$('picker-done').click();await tick();
  assert($('start').value==='00:05','time normalized');
  $('start').click();$('picker-hour').value='22';goBack();await tick();
  assert($('editor').open&&!$('picker-sheet').open&&$('start').value==='00:05','Back cancels only top picker');
  $('date').value='2028-02-29';$('date').click();
  assert(document.querySelector('#calendar-days .selected').getAttribute('aria-label')==='2028-02-29','leap day selected');
  $('calendar-next').click();document.querySelector('#calendar-days [aria-label="2028-03-01"]').click();$('picker-done').click();await tick();
  assert($('date').value==='2028-03-01','calendar navigation and selection');
  const dynamic=document.createElement('input');dynamic.type='time';dynamic.value='08:10';$('plan-form').append(dynamic);await tick();
  assert(dynamic.dataset.picker==='time'&&dynamic.type==='text'&&dynamic.value==='08:10','dynamic academic fields upgraded without losing values');dynamic.remove();
  native=(action,data)=>{if(action==='save'){saves.push(data);return Promise.resolve(originalState);}return originalNative(action,data)};
  $('date').value='2028-03-01';$('end-date').value='2028-03-02';$('start').value='23:30';$('end').value='00:30';
  const canceled=$('plan-form').onsubmit({preventDefault(){}});await tick();
  assert($('confirm-sheet').open&&$('confirm-details').textContent.includes('23:30'),'review shows actual time');
  $('confirm-no').click();await canceled;
  assert(saves.length===0&&$('editor').open,'return to edit writes nothing');
  const revision=state.revision,approved=$('plan-form').onsubmit({preventDefault(){}});await tick();state={...state,revision:revision+10};$('confirm-yes').click();await approved;
  assert(saves.length===1&&saves[0].revision===revision&&Date.parse(saves[0].plan.end)-Date.parse(saves[0].plan.start)===3600000,'confirmed payload preserves reviewed revision and cross-day times');
  openEditor();$('date').value='2026-02-30';$('label').value='Invalid';await $('plan-form').onsubmit({preventDefault(){}});
  assert(!$('confirm-sheet').open&&$('error').textContent.includes('无效'),'invalid date cannot reach confirmation');
  $('editor').close();const answer=askConfirm('<img src=x onerror=alert(1)>');await tick();
  assert(!$('confirm-copy').querySelector('img'),'confirmation text is escaped');goBack();assert(await answer===false,'Back declines confirmation');
  assert(document.documentElement.scrollHeight<=innerHeight+1&&document.documentElement.scrollWidth<=innerWidth+1,'root viewport does not overflow');
 } finally {native=originalNative;state=originalState;document.querySelectorAll('dialog[open]').forEach(d=>d.close());switchTab(originalTab);await refresh();}
 const after=await native('status');assert(after.revision===before.revision&&JSON.stringify(after.plans)===JSON.stringify(before.plans)&&JSON.stringify(after.proposal)===JSON.stringify(before.proposal)&&after.periodVersion===before.periodVersion,'native user plans and pending proposal unchanged');
 return checks;
})()`));
} finally {client.close()}
