'use strict';
let aiBusy=false;
const updateCore=window.nativeUpdate;
native('ai_history').then(r=>{for(const item of r.messages){if(item.role==='user')message(item.content,'user');else{try{const d=JSON.parse(item.content);message(d.status==='clarification_required'?d.question:'历史候选计划（不代表已确认或执行），请在计划表查看已保存内容。','assistant');}catch{}}}}).catch(e=>toast(e.message));
window.nativeUpdate=s=>{updateCore(s);$('api-status').textContent=s.apiConfigured?'DeepSeek Key 已加密配置':'尚未配置 DeepSeek Key';};
$('key-save').onclick=async()=>{if(aiBusy)return;const key=$('api-key').value.trim();$('api-key').value='';aiBusy=true;$('key-save').disabled=true;try{const r=await native('ai_key',{key});toast(r.available?'验证成功，密钥已加密保存':'密钥有效但余额不足，请充值');await refresh();}catch(e){toast(e.message);}finally{aiBusy=false;$('key-save').disabled=false;}};
$('key-test').onclick=async()=>{try{const r=await native('ai_test');toast(r.available?'连接成功，余额可用':'认证成功，但余额不足');}catch(e){toast(e.message);}};
$('key-delete').onclick=async()=>{if(confirm('删除 API Key？已确认的计划仍会继续执行。'))try{await native('ai_delete');await refresh();toast('已删除 Key');}catch(e){toast(e.message);}};
$('image-pick').onclick=async()=>{try{const r=await native('ai_image');if(r.cancelled)return;$('image-preview').src=r.preview;$('attachment').hidden=false;}catch(e){toast(e.message);}};
$('image-remove').onclick=async()=>{try{await native('ai_remove_image');$('attachment').hidden=true;$('image-preview').removeAttribute('src');}catch(e){toast(e.message);}};
$('ai-cancel').onclick=()=>native('ai_cancel').catch(e=>toast(e.message));
$('chat-clear').onclick=async()=>{if(confirm('清除本地 AI 对话及图片附件？已保存计划不受影响。'))try{await native('ai_clear');$('messages').replaceChildren();$('attachment').hidden=true;$('image-preview').removeAttribute('src');}catch(e){toast(e.message);}};
$('send').onclick=async()=>{
 if(aiBusy)return;if(!state?.apiConfigured){toast('请先到设置中配置 API Key');switchTab('settings');return;}
 const text=$('prompt').value.trim();if(!text&&$('attachment').hidden)return;
 aiBusy=true;$('send').disabled=true;$('ai-cancel').hidden=false;message(text||'请读取这张安排截图。','user');$('prompt').value='';
 try {
  const r=await native('ai_chat',{text}),d=r.document;
  if(d.status==='clarification_required')message(d.question,'assistant');
  else {
   message('已生成候选计划，尚未保存。请核对时间、模式和替换范围，再确认应用。','assistant');
   $('json-title').textContent='确认候选计划';$('json-source').hidden=true;$('json-text').value=JSON.stringify(d,null,2);$('json-text').readOnly=true;$('json-check').hidden=false;$('json-apply').disabled=true;$('json-preview').textContent='正在进行本地校验…';$('json-dialog').showModal();await $('json-check').onclick();
  }
  if(r.usage?.total_tokens)message(`本次用量：${r.usage.total_tokens} tokens`,'assistant');
 } catch(e){message(e.message+'；未新增或修改任何计划。','assistant');}
 finally{aiBusy=false;$('send').disabled=false;$('ai-cancel').hidden=true;}
};
