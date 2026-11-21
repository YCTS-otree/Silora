'use strict';
const photo=$('chat-photo'), stage=$('photo-stage'), full=$('photo-full');
const photos=new Map();let scale=1,dx=0,dy=0,gesture=null,activeImage=null;
function positionPhoto(){
    const maxX=Math.max(0,(full.offsetWidth*scale-stage.clientWidth)/2);
    const maxY=Math.max(0,(full.offsetHeight*scale-stage.clientHeight)/2);
    dx=Math.max(-maxX,Math.min(maxX,dx));dy=Math.max(-maxY,Math.min(maxY,dy));
    full.style.transform='translate('+dx+'px,'+dy+'px) scale('+scale+')';
}
function resetPhoto(){photos.clear();gesture=null;scale=1;dx=dy=0;positionPhoto()}
function photoUrl(kind,id){return '/chat-images/'+kind+'/'+encodeURIComponent(id)}
function openPhoto(ids,i){
    activeImage=ids[i];$('photo-note').textContent='';$('photo-number').textContent=(i+1)+' / '+ids.length;
    $('photo-save').disabled=true;photo.showModal();resetPhoto();full.src=photoUrl('full',activeImage);
}
full.onload=()=>{$('photo-save').disabled=false;positionPhoto()};
full.onerror=()=>{if(photo.open){$('photo-save').disabled=true;$('photo-note').textContent='这张旧图片已不在本机。'}};
$('photo-back').onclick=()=>photo.close();
photo.onclose=()=>{activeImage=null;full.removeAttribute('src');resetPhoto()};
$('photo-save').onclick=async()=>{
    const id=activeImage;if(!id)return;
    $('photo-save').disabled=true;
    try{await native('gallery_save',{imageId:id});if(activeImage===id)$('photo-note').textContent='已保存到相册 · Pictures/Silora'}
    catch(e){if(activeImage===id)$('photo-note').textContent=e.message}
    finally{if(activeImage===id)$('photo-save').disabled=false}
};
let moved=false;
function metrics(){
    const [a,b]=photos.values();
    return b?{x:(a.x+b.x)/2,y:(a.y+b.y)/2,d:Math.hypot(a.x-b.x,a.y-b.y)}:a;
}
function baseline(){
    if(!photos.size){gesture=null;return}
    gesture={...metrics(),s:scale,dx,dy};
}
stage.onpointerdown=e=>{
    if(!photos.size)moved=false;
    photos.set(e.pointerId,{x:e.clientX,y:e.clientY});if(photos.size>1)moved=true;
    stage.setPointerCapture(e.pointerId);baseline();
};
stage.onpointermove=e=>{
    if(!photos.has(e.pointerId)||!gesture)return;
    photos.set(e.pointerId,{x:e.clientX,y:e.clientY});
    const m=metrics(),g=gesture;
    if(Math.hypot(m.x-g.x,m.y-g.y)>4)moved=true;
    scale=Math.max(1,Math.min(6,g.d?g.s*m.d/g.d:g.s));
    const box=stage.getBoundingClientRect(),cx=box.left+box.width/2,cy=box.top+box.height/2;
    dx=m.x-cx-(g.x-cx-g.dx)*scale/g.s;dy=m.y-cy-(g.y-cy-g.dy)*scale/g.s;positionPhoto();
};
stage.onpointerup=e=>{
    if(!photos.has(e.pointerId))return;
    photos.delete(e.pointerId);baseline();
    const r=full.getBoundingClientRect();
    if(!moved&&!photos.size&&(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom))photo.close();
};
stage.onpointercancel=e=>{photos.delete(e.pointerId);moved=true;baseline()};
photo.onclick=e=>{if(e.target===photo)photo.close()};
function attachGallery(card,ids){
    if(!ids?.length)return;
    const toggle=el('button',card.textContent,'gallery-toggle');toggle.type='button';
    const hint=el('small','查看图片（'+ids.length+'）');toggle.append(hint);
    const grid=el('div',undefined,'gallery-grid');grid.hidden=true;toggle.setAttribute('aria-expanded','false');
    card.replaceChildren(toggle,grid);
    card.onclick=e=>{
        if(e.target.closest('.gallery-thumb'))return;
        grid.hidden=!grid.hidden;toggle.setAttribute('aria-expanded',String(!grid.hidden));
        hint.textContent=grid.hidden?'查看图片（'+ids.length+'）':'收起图片';
        if(grid.hidden||grid.childNodes.length)return;
        ids.forEach((id,i)=>{
            const b=el('button',undefined,'gallery-thumb');b.type='button';b.setAttribute('aria-label','全屏查看第 '+(i+1)+' 张图片');
            const img=el('img');img.alt='图片 '+(i+1);img.onerror=()=>{b.textContent='图片已不可用';b.disabled=true};img.src=photoUrl('thumb',id);
            b.append(img);b.onclick=()=>openPhoto(ids,i);grid.append(b);
        });
    };
}
