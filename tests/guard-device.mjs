import {connectSilora} from './webview-client.mjs';
import {execFileSync} from 'node:child_process';
import fs from 'node:fs';
const pkg='com.silora.app.devicecheck',adb=(...a)=>execFileSync('adb',a,{encoding:'utf8'}).trim(),phase=process.argv[2];
const file='Temp/guard-device-check.json';
if(phase==='setup'){
 const pid=adb('shell','pidof',pkg);adb('forward','tcp:9222',`localabstract:webview_devtools_remote_${pid}`);
 const c=await connectSilora();try{
  const s=await c.native('status');if(s.plans.length||!s.exact||!s.policy||!s.guard.running)throw Error('Isolated installation must be empty, authorized, and guard running');
  const now=await c.evaluate('Date.now()'),a=now+45000,b=now+120000,end=now+195000;
  const doc={schema_version:1,status:'plan_ready',operation:'add',time_zone:s.zone,intervals:[{id:'guard-vibrate',start:new Date(a).toISOString(),end:new Date(b).toISOString(),mode:'vibrate',label:'后台振动验证'},{id:'guard-silent',start:new Date(b).toISOString(),end:new Date(end).toISOString(),mode:'silent',label:'后台静音验证'}]};
  const saved=await c.native('import',{revision:s.revision,document:doc});if(!saved.scheduled)throw Error('Alarm not scheduled');
  fs.writeFileSync(file,JSON.stringify({a,b,end,mode:s.mode,pid,revision:saved.revision}));console.log({a:new Date(a).toISOString(),b:new Date(b).toISOString(),end:new Date(end).toISOString(),original:s.mode});
 }finally{c.close()}
}else if(phase==='monitor'){
 const s=JSON.parse(fs.readFileSync(file));let failed=false;
 for(const[time,expected,label]of[[s.a,1,'vibrate'],[s.b,0,'silent'],[s.end,{ring:2,vibrate:1,silent:0}[s.mode],'restore']]){
  await new Promise(r=>setTimeout(r,Math.max(0,time+3000-Date.now())));
  const actual=Number(adb('shell','settings','get','global','mode_ringer')),service=adb('shell','dumpsys','activity','services',pkg).includes('isForeground=true foregroundId=1300');
  console.log(`${actual===expected&&service?'PASS':'FAIL'} ${label}: actual=${actual}, foreground=${service}`);if(actual!==expected||!service)failed=true;
 }
 if(failed)process.exitCode=1;
}else throw Error('Use setup or monitor; this script only targets the isolated .devicecheck package');
