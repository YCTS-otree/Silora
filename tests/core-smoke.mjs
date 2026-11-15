// Requires an open debug build forwarded to localhost:9222. Creates only a disabled test plan.
const pages=await(await fetch('http://127.0.0.1:9222/json')).json();
const page=pages.find(p=>p.url==='https://appassets.androidplatform.net/assets/index.html'&&p.title.includes('Silora'));
if(!page)throw Error('Silora WebView not found');
const ws=new WebSocket(page.webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);let seq=0;const waiting=new Map();
ws.onmessage=e=>{const r=JSON.parse(e.data);if(r.id){waiting.get(r.id)?.(r);waiting.delete(r.id);}};
const call=(method,params={})=>new Promise(r=>{const id=++seq;waiting.set(id,r);ws.send(JSON.stringify({id,method,params}));});
const evaluate=async expression=>{const r=await call('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(r.result.exceptionDetails)throw Error(JSON.stringify(r.result.exceptionDetails));return r.result.result.value;};
const assert=async(expression,label)=>{if(!await evaluate(expression))throw Error(label);console.log('PASS '+label);};
await assert(`document.querySelectorAll('nav button').length===3`,'three navigation tabs');
await evaluate(`document.querySelector('[data-theme="light"]').click();switchTab('settings')`);
await assert(`document.documentElement.dataset.theme==='light' && document.documentElement.scrollWidth<=innerWidth`,'light theme fits viewport');
await evaluate(`document.querySelector('[data-theme="dark"]').click()`);
await assert(`document.documentElement.dataset.theme==='dark'`,'dark theme');
const id='smoke-'+Date.now();
const s=await evaluate(`native('status')`);
const plan={id,label:'Disabled smoke test',start:new Date(Date.now()+86400000).toISOString(),end:new Date(Date.now()+90000000).toISOString(),mode:'vibrate',enabled:false};
try {
 await evaluate(`native('save',${JSON.stringify({revision:s.revision,plan})})`);
 await call('Page.reload');await new Promise(r=>setTimeout(r,700));
 await assert(`native('status').then(s=>s.plans.some(p=>p.id==='${id}'&&!p.enabled))`,'SQLite persistence across page reload');
 await assert(`native('save',${JSON.stringify({revision:s.revision,plan:{...plan,id:id+'-stale'}})}).then(()=>false,()=>true)`,'stale revision rejected');
 await assert(`(async()=>{const s=await native('status');try{await native('save',{revision:s.revision,plan:${JSON.stringify({...plan,id:id+'-bad',end:plan.start})}});return false;}catch{return true;}})()`,'invalid interval rejected');
 await evaluate(`message('<img src=x onerror=alert(1)>','user')`);
 await assert(`document.getElementById('messages').querySelectorAll('img').length===0`,'untrusted text stays text');
} finally {await evaluate(`(async()=>{const s=await native('status');if(s.plans.some(p=>p.id==='${id}'))await native('delete',{revision:s.revision,planId:'${id}'});})()`);ws.close();}
console.log('ALL CORE SMOKE CHECKS PASSED');
