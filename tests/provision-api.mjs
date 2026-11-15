// Pass the user-owned API credential file path; never prints or persists its contents.
import fs from 'node:fs';
const key=fs.readFileSync(process.argv[2],'utf8').replace(/^\uFEFF/,'').trim();
const pages=await(await fetch('http://127.0.0.1:9222/json')).json();
const page=pages.find(p=>p.url==='https://appassets.androidplatform.net/assets/index.html'&&p.title.includes('Silora'));
if(!page)throw Error('Silora page not found');
const ws=new WebSocket(page.webSocketDebuggerUrl);await new Promise(r=>ws.onopen=r);
ws.onmessage=e=>{const r=JSON.parse(e.data);if(r.id===1){const data=r.result?.result?.value;console.log(data?.configured?'Credential validated and encrypted on device; available='+data.available:'Credential configuration failed (details intentionally omitted)');if(!data?.configured)process.exitCode=1;ws.close();}};
ws.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression:`native('ai_key',${JSON.stringify({key})})`,awaitPromise:true,returnByValue:true}}));
