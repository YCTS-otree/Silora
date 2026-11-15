// Opt-in destructive-to-test-plans integration test on an otherwise empty Silora database.
// Uses actual ringer modes; restores the original mode by finishing the interval sequence.
import {execFileSync} from 'node:child_process';
const adb=(...args)=>execFileSync('adb',args,{encoding:'utf8'}).trim();
const delay=ms=>new Promise(r=>setTimeout(r,ms));
const pid=adb('shell','pidof','com.silora.app');adb('forward','tcp:9222',`localabstract:webview_devtools_remote_${pid}`);
const pages=await(await fetch('http://127.0.0.1:9222/json')).json(),page=pages.find(p=>p.url.includes('/assets/index.html')&&p.title.includes('Silora'));
if(!page)throw Error('Open Silora first');
const ws=new WebSocket(page.webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);let seq=0;const callbacks=new Map();ws.onmessage=e=>{const r=JSON.parse(e.data);if(r.id){callbacks.get(r.id)?.(r);callbacks.delete(r.id);}};
const evaluate=expression=>new Promise((resolve,reject)=>{const id=++seq;callbacks.set(id,r=>r.result.exceptionDetails?reject(Error(JSON.stringify(r.result.exceptionDetails))):resolve(r.result.result.value));ws.send(JSON.stringify({id,method:'Runtime.evaluate',params:{expression,awaitPromise:true,returnByValue:true}}));});
const initial=await evaluate("native('status')");if(initial.plans.length)throw Error('Use an empty test installation; will not replace user plans');if(!initial.exact||!initial.policy)throw Error('Grant exact alarms and notification policy access first');
const begin=Date.now(),a=begin+15000,b=begin+90000,end=begin+165000;
const doc={schema_version:1,status:'plan_ready',operation:'add',time_zone:'Asia/Shanghai',intervals:[{id:'device-test-vibrate',start:new Date(a).toISOString(),end:new Date(b).toISOString(),mode:'vibrate',label:'真机振动测试'},{id:'device-test-silent',start:new Date(b).toISOString(),end:new Date(end).toISOString(),mode:'silent',label:'真机静音测试'}]};
const result=await evaluate(`native('import',${JSON.stringify({revision:initial.revision,document:doc})})`);if(!result.scheduled)throw Error('Alarm not scheduled');
console.log('Scheduled real vibrate -> silent -> restore sequence; original mode='+initial.mode);
ws.close();adb('shell','input','keyevent','KEYCODE_HOME');adb('shell','am','kill','com.silora.app');adb('shell','input','keyevent','KEYCODE_SLEEP');
const checks=[[a+4000,'1','vibrate after process kill / screen off'],[b+4000,'0','silent at adjacent interval'],[end+4000,String({ring:2,vibrate:1,silent:0}[initial.mode]),'original ringer mode restored']];
let failed=false;
for(const [when,expected,label]of checks){await delay(Math.max(0,when-Date.now()));const actual=adb('shell','settings','get','global','mode_ringer');console.log(`${actual===expected?'PASS':'FAIL'} ${label}: actual=${actual}`);if(actual!==expected)failed=true;}
adb('shell','am','start','-n','com.silora.app/.MainActivity');
if(failed)process.exitCode=1;else console.log('ALL NATIVE ALARM CHECKS PASSED');
