import {connectSilora} from './webview-client.mjs';
const client=await connectSilora();
try { console.log(await client.evaluate(`(async()=>{
const original=native,originalState=state,oldTab=tab;const checks=[];let result={status:'current',current:'1.8.0'},opened='';
const wait=ms=>new Promise(r=>setTimeout(r,ms));const check=(ok,name)=>{if(!ok)throw Error(name);checks.push(name)};
try {
 native=async(action,data)=>action==='update_check'?result:action==='update_open'?(opened=data.url,{}):original(action,data);
 switchTab('settings');$('update-check').click();await wait(50);check($('update-status').textContent.includes('已是最新'),'current feedback');
 result={status:'failed'};$('update-check').click();await wait(50);check($('update-status').textContent.includes('重试'),'failure feedback');
 result={status:'available',current:'1.8.0',latest:'1.9.0',published:'2026-11-16',notes:['测试更新说明 <script>'],pages:['https://github.com/YCTS-otree/Silora/tree/main/Release/v1.8.0'],prompt:true};
 state={...state,tutorial:{...state.tutorial,active:true}};$('update-check').click();await wait(100);check(!$('confirm-sheet').open,'tutorial defers update');
 state=originalState;await wait(1600);check($('confirm-sheet').open,'prompt after tutorial');check($('confirm-copy').textContent.includes('<script>')&&!$('confirm-copy').querySelector('script'),'notes render as text');
 $('confirm-no').click();await wait(50);check(!opened,'later does not navigate');
 const confirmation=askConfirm('其他操作');$('update-check').click();await wait(50);check($('confirm-copy').textContent==='其他操作','existing confirmation preserved');$('confirm-no').click();await confirmation;await wait(1600);
 check($('confirm-title').textContent==='发现新版本 1.9.0','update resumes after confirmation');$('confirm-yes').click();await wait(50);check(opened===result.pages[0],'download requires click');
 return checks;
}finally{native=original;state=originalState;document.querySelectorAll('dialog[open]').forEach(d=>d.close());switchTab(oldTab);}
})()`));}finally{client.close();}
