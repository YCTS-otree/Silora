import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const source=readFileSync('website/site.js','utf8');
const manifest={app:'Silora',latest:'1.9.2',published:'2026-11-16',notes:['<script>literal text</script>']};
async function run(saved,respond){
 const nodes=new Map();const node=id=>nodes.get(id)||nodes.set(id,{textContent:'',hidden:false,children:[],setAttribute(){},addEventListener(){},replaceChildren(){this.children=[]},append(n){this.children.push(n)}}).get(id);
 const storage=new Map(saved?[['silora.release.v1',JSON.stringify(saved)]]:[]);const calls=[];let timers=[];
 const sandbox={document:{documentElement:{dataset:{}},getElementById:node,createElement:()=>({}),addEventListener(){}},localStorage:{getItem:k=>storage.get(k)||null,setItem:(k,v)=>storage.set(k,v)},setTimeout:(fn,ms)=>{timers.push(ms);return timers.length},clearTimeout(){},AbortController,Date,fetch:async url=>{calls.push(url);return respond(url)}};
 vm.runInNewContext(source,sandbox);for(let i=0;i<20;i++)await new Promise(r=>setImmediate(r));
 return {nodes,storage,calls,timers};
}
let r=await run(null,async url=>{if(url.includes('gitee'))throw Error('CORS');return {ok:true,text:async()=>JSON.stringify(manifest)}});
assert.equal(r.calls.length,2);assert.match(r.nodes.get('release-status').textContent,/GitHub/);assert.equal(r.nodes.get('release-notes').children[0].textContent,manifest.notes[0]);console.log('PASS source failover and safe text rendering');
const cached=JSON.parse(r.storage.get('silora.release.v1'));
r=await run(cached,()=>{throw Error('Unexpected request')});assert.equal(r.calls.length,0);assert.match(r.nodes.get('release-version').textContent,/1.9.2/);console.log('PASS hourly cache suppresses reload requests');
r=await run({...cached,checkedAt:Date.now()-3600001},async()=>({ok:true,text:async()=>JSON.stringify(manifest)}));assert.equal(r.calls.length,1);assert.match(r.calls[0],/githubusercontent/);console.log('PASS expired cache refresh and preferred successful source');
r=await run({...cached,checkedAt:Date.now()-3600001},async()=>{throw Error('offline')});assert.equal(r.calls.length,2);assert.match(r.nodes.get('release-version').textContent,/上次获取/);assert.equal(JSON.parse(r.storage.get('silora.release.v1')).failed,true);console.log('PASS offline stale version retained without claiming latest');
r=await run(null,async()=>({ok:true,text:async()=>JSON.stringify({...manifest,app:'wrong'})}));assert.match(r.nodes.get('release-version').textContent,/随页版本/);assert.equal(r.nodes.get('release-details').hidden,true);console.log('PASS invalid manifests rejected');
r=await run({...cached,failed:true},()=>{throw Error('Unexpected request')});assert.equal(r.calls.length,0);console.log('PASS failure cache prevents repeated requests');
