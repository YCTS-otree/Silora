 'use strict';
(()=>{const box=$('support-dialog'),pay=$('sponsor-dialog');
$('support-close').onclick=()=>box.close();$('sponsor-close').onclick=()=>pay.close();
$('support-pay').onclick=()=>{box.close();pay.showModal();};
$('support-video').onclick=async()=>{try{await native('support_bilibili');box.close();}catch{toast('无法打开 B 站');}};
function show(){if(document.hidden||state?.tutorial?.active||document.querySelector('dialog[open]')||document.body.classList.contains('ai-busy')){setTimeout(show,1500);return;}box.showModal();}
async function start(){if(!state){setTimeout(start,1000);return;}try{if((await native('support_start')).due)show();}catch{}}
start();})();
