'use strict';
const $=id=>document.getElementById(id),names={ring:'响铃',vibrate:'振动',silent:'静音'};
const localDate=d=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
const localTime=d=>`${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
const today=()=>localDate(new Date());
let plans=[],state=null,tab='ai',filter='all',timer,editing=null,busy=false,importRevision=null;
const pending=new Map();let seq=0;
function native(action,data={}){return new Promise((resolve,reject)=>{if(!window.Silora){reject(Error('系统 WebView 不支持安全原生通信，请更新 WebView'));return;}const requestId=String(++seq),timeout=setTimeout(()=>{pending.delete(requestId);reject(Error('操作超时，请刷新确认结果，勿重复提交'));},action==='ai_chat'?270000:90000);pending.set(requestId,{resolve,reject,timeout,action});window.Silora.postMessage(JSON.stringify({requestId,action,...data}));});}
if(window.Silora)window.Silora.onmessage=e=>{const r=JSON.parse(e.data),p=pending.get(r.requestId);if(!p)return;if(r.progress){if(p.action==='ai_chat')window.aiProgress?.(r.progress);return;}clearTimeout(p.timeout);pending.delete(r.requestId);if(r.ok)p.resolve(r.data);else p.reject(Error(r.error));};
function toast(text){$('toast').textContent=text;$('toast').hidden=false;clearTimeout(timer);timer=setTimeout(()=>$('toast').hidden=true,4000);}
window.nativeUpdate=s=>{state=s;plans=s.plans;render();renderStatus();};
async function refresh(){try{nativeUpdate(await native('status'));}catch(e){$('native-status').textContent=e.message;}}
async function mutate(action,data){if(busy)return false;busy=true;try{nativeUpdate(await native(action,data));return true;}catch(e){toast(e.message);return false;}finally{busy=false;}}
function switchTab(next){tab=next;document.querySelectorAll('.page').forEach(p=>p.classList.toggle('active',p.id===next));document.querySelectorAll('[data-tab]').forEach(b=>{b.classList.toggle('active',b.dataset.tab===next);if(b.dataset.tab===next)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current');});document.querySelector('main').scrollTop=0;document.body.classList.toggle('viewing-week',next==='plans');if(next!=='ai')refresh();}
document.querySelectorAll('[data-tab]').forEach(b=>b.onclick=()=>switchTab(b.dataset.tab));
window.goBack=()=>{for(const d of document.querySelectorAll('dialog'))if(d.open){d.close();return true;}if(tab!=='ai'){switchTab('ai');return true;}return false;};
const media=matchMedia('(prefers-color-scheme: dark)');let theme=localStorage.getItem('silora.theme')||'system';
function applyTheme(){const resolved=theme==='system'?(media.matches?'dark':'light'):theme;document.documentElement.dataset.theme=resolved;document.querySelector('.orb').textContent=resolved==='light'?'❀':'☾';document.querySelectorAll('[data-theme]').forEach(b=>{b.classList.toggle('selected',b.dataset.theme===theme);b.setAttribute('aria-pressed',String(b.dataset.theme===theme));});}
document.querySelectorAll('[data-theme]').forEach(b=>b.onclick=()=>{theme=b.dataset.theme;localStorage.setItem('silora.theme',theme);applyTheme();});media.addEventListener('change',applyTheme);applyTheme();
document.querySelectorAll('[data-prompt]').forEach(b=>b.onclick=()=>{$('prompt').value=b.dataset.prompt;$('prompt').focus();});
function message(text,kind){const p=document.createElement('div');p.className='message '+kind;p.textContent=text;$('messages').append(p);while($('messages').children.length>20)$('messages').firstChild.remove();}
$('send').onclick=()=>{const text=$('prompt').value.trim();if(!text)return;message(text,'user');message('AI 尚未接入。请前往「计划表」手动创建或导入 JSON 计划；确认保存后，原生调度器会真实切换手机铃声。','assistant');$('prompt').value='';$('messages').lastChild.scrollIntoView({block:'center'});};
$('today').textContent=new Intl.DateTimeFormat('zh-CN',{year:'numeric',month:'long',day:'numeric',weekday:'long'}).format(new Date());
function el(tag,text,cls){const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(cls)e.className=cls;return e;}
function renderStatus(){if(!state)return;$('native-status').textContent=`当前：${names[state.mode]} · 计划版本 ${state.revision}\n${state.issue||(state.suppressed?'已尊重外部模式修改':state.activeId?'计划正在生效':'当前没有生效中的计划')}\n下一节点：${state.next?new Date(Number(state.next)).toLocaleString():'无'}${state.next&&!state.scheduled?'（未调度）':''}`;$('permission-state').textContent=`精确闹钟：${state.exact?'已授权':'不可用'} · 通知策略访问：${state.policy?'已授权':'未授权'}`;$('restore').value=state.restore;$('manual').value=state.manual;$('event-list').replaceChildren();state.events.slice(0,20).forEach(e=>$('event-list').append(el('p',`${new Date(e.at).toLocaleString()} · ${e.result}\n${e.detail} · 实际${['静音','振动','响铃'][e.actual]}`,'subtitle')));}
function render(){window.weekView?.render();}
function openEditor(p=null){if(!state){toast('请先等待原生状态加载');return;}editing=p;$('plan-form').reset();$('editor-title').textContent=p?'编辑计划':'新建计划';const start=p?new Date(p.start):new Date(Date.now()+60000),end=p?new Date(p.end):new Date(start.getTime()+3600000);$('label').value=p?.label||'';$('date').value=localDate(start);$('end-date').value=localDate(end);$('start').value=localTime(start);$('end').value=localTime(end);$('mode').value=p?.mode||'vibrate';$('error').textContent='';$('editor').showModal();}
$('add').onclick=()=>openEditor();$('cancel').onclick=()=>$('editor').close();
$('plan-form').onsubmit = async e => {
    e.preventDefault(); if (busy) return;
    try {
        const beginText = `${$('date').value}T${$('start').value}`, endText = `${$('end-date').value}T${$('end').value}`;
        const start = new Date(beginText), end = new Date(endText);
        if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) ||
            `${localDate(start)}T${localTime(start)}` !== beginText || `${localDate(end)}T${localTime(end)}` !== endText)
            throw Error('日期或时间无效，请重新选择。');
        if (start >= end) throw Error('结束时间必须晚于开始时间；跨天安排请选择第二天。');
        const revision = state.revision, edit = !!editing;
        const plan = { id: editing?.id || crypto.randomUUID(), label: $('label').value.trim(), start: start.toISOString(), end: end.toISOString(), mode: $('mode').value, enabled: editing?.enabled ?? true };
        const format = d => d.toLocaleString('zh-CN', { hour12: false });
        if (!await askConfirm(plan.enabled ? '保存后按以下安排切换铃声；当前时间已在区间内时，会立即生效。' : '这条计划目前已停用，保存后仍保持停用。', {
            title: edit ? '确认修改安排' : '确认这段安排', confirmLabel: '确认保存', cancelLabel: '返回修改',
            rows: [['名称', plan.label], ['开始', format(start)], ['结束', format(end)], ['模式', names[plan.mode]], ['时区', state.zone]]
        })) return;
        if (await mutate('save', { plan, edit, revision })) { $('editor').close(); toast('计划已保存到原生数据库'); }
    } catch (e) { $('error').textContent = e.message; }
};
$('refresh').onclick=refresh;document.querySelectorAll('[data-permission]').forEach(b=>b.onclick=()=>native('permission',{permission:b.dataset.permission}).catch(e=>toast(e.message)));
$('save-settings').onclick=async()=>{const settings={restore:$('restore').value,manual:$('manual').value};if(await askConfirm('保存执行策略？当前计划可能立即重新计算铃声模式。'))await mutate('settings',settings);};
$('json-open').onclick=()=>{$('json-title').textContent='导入 JSON';$('json-source').hidden=false;importRevision=null;$('json-text').readOnly=false;$('json-text').value='';$('json-preview').textContent='';$('json-apply').disabled=true;$('json-check').hidden=false;$('json-dialog').showModal();};
$('json-export').onclick=()=>{$('json-title').textContent='导出 JSON';$('json-source').hidden=false;if(!state)return;const starts=plans.map(p=>Date.parse(p.start)),ends=plans.map(p=>Date.parse(p.end)),start=starts.length?Math.min(...starts):Date.now(),end=ends.length?Math.max(...ends):start+86400000;$('json-text').value=JSON.stringify({schema_version:1,status:'plan_ready',operation:'replace',time_zone:state.zone,scope:{start:new Date(start).toISOString(),end:new Date(end).toISOString()},intervals:plans},null,2);$('json-text').readOnly=true;$('json-check').hidden=true;$('json-apply').disabled=true;$('json-preview').textContent='长按文本复制。导入会按 scope 替换范围内计划。';$('json-dialog').showModal();};
$('json-text').oninput=()=>{importRevision=null;$('json-apply').disabled=true;};
$('json-check').onclick=async()=>{try{const r=await native('preview',{document:JSON.parse($('json-text').value)});importRevision=r.revision;$('json-preview').textContent=`校验通过：当前 ${plans.length} 条 → 确认后 ${r.candidate.length} 条\n时区：${state.zone}\n`+plans.filter(p=>!r.candidate.some(n=>n.id===p.id)).map(p=>`将移除：${p.label} · ${new Date(p.start).toLocaleString()}`).join('\n')+'\n确认后的计划：\n'+r.candidate.map(p=>`${p.label}\n${new Date(p.start).toLocaleString()} — ${new Date(p.end).toLocaleString()} · ${names[p.mode]}${p.enabled?'':'（停用）'}`).join('\n\n');$('json-apply').disabled=false;}catch(e){$('json-preview').textContent=e.message;$('json-apply').disabled=true;}};
$('json-apply').onclick=async()=>{if(importRevision===null)return;if(await askConfirm('确认应用以上候选计划？会真实执行铃声切换。'))if(await mutate('import',{revision:importRevision,document:JSON.parse($('json-text').value)}))$('json-dialog').close();};$('json-close').onclick=()=>$('json-dialog').close();
document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh();});refresh();
