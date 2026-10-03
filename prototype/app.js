'use strict';
(() => {
  const STORAGE_KEY = 'car-parking-ui-v1';
  const floors = ['6F','5F','4F','3F','2F','1F','B1','B2','B3','B4','B5','B6'];
  const $ = id => document.getElementById(id);
  const defaults = () => ({version:1,side:'left',theme:'classic',floor:'B2',autoEnabled:true,parkedAt:Date.now()-7980000});
  let state = defaults();
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY));
    if (saved?.version === 1) state = {
      version:1,
      side:['left','right'].includes(saved.side) ? saved.side : 'left',
      theme:['classic','steel'].includes(saved.theme) ? saved.theme : 'classic',
      floor:floors.includes(saved.floor) ? saved.floor : 'B2',
      autoEnabled:typeof saved.autoEnabled === 'boolean' ? saved.autoEnabled : true,
      parkedAt:Number.isFinite(saved.parkedAt) && saved.parkedAt <= Date.now() ? saved.parkedAt : state.parkedAt
    };
  } catch { /* file:// storage may be unavailable; the demo still works. */ }
  const saveState = () => { try {localStorage.setItem(STORAGE_KEY,JSON.stringify(state));} catch {} };
  let draftFloor = state.floor, candidate = null, drawerOpen = false;
  let photoUrl = null, draftPhotoUrl = null, toastTimer = null, wheelUserInteraction = false;
  let focusBeforeDrawer = null, setupStep = 0;
  const panels = [$('welcomeScreen'),$('settingsScreen'),$('setupScreen')];

  function toast(message) {
    clearTimeout(toastTimer); $('toast').textContent = message; $('toast').hidden = false;
    toastTimer = setTimeout(() => {$('toast').hidden = true;},2300);
  }
  function renderElapsed() {
    const minutes = Math.max(0,Math.floor((Date.now()-state.parkedAt)/60000));
    const hours = Math.floor(minutes/60), rest = minutes%60;
    $('elapsedTime').textContent = hours ? `${hours}시간 ${rest}분` : `${rest}분`;
  }
  function renderHome() {
    $('phone').dataset.theme = state.theme;
    document.querySelectorAll('input[name="appTheme"]').forEach(input => {
      input.checked = input.value === state.theme;
      input.closest('.theme-option').classList.toggle('is-selected',input.checked);
    });
    $('homeFloor').textContent = state.floor; $('mapFloor').textContent = state.floor;
    $('bluetoothButton').setAttribute('aria-pressed',String(state.autoEnabled));
    $('bluetoothButton').setAttribute('aria-label',`자동 기록 ${state.autoEnabled?'켜짐':'꺼짐'} · 데모 설정`);
    $('autoRecordToggle').checked = state.autoEnabled;
    document.querySelectorAll('input[name="drawerSide"]').forEach(input => {input.checked=input.value===state.side;});
    $('demoState').textContent = `${state.theme==='steel'?'스틸 포인트':'클래식'} · 사이드바 ${state.side==='left'?'왼쪽':'오른쪽'} · 자동 기록 ${state.autoEnabled?'켜짐':'꺼짐'}`;
    $('drawer').classList.toggle('side-left',state.side==='left');
    $('drawer').classList.toggle('side-right',state.side==='right');
    const vehicleAsset = state.theme==='steel' ? '../assets/vehicle/car-steel.svg' : '../assets/vehicle/car-side.png';
    const vehicleAlt = state.theme==='steel' ? '실버 포인트가 있는 각진 블랙 측면 차량 아이콘' : '간결한 측면 차량 아이콘';
    $('vehicleImage').src = photoUrl || vehicleAsset;
    $('vehicleImage').alt = photoUrl ? '저장한 주차 사진' : vehicleAlt;
    $('welcomeVehicleImage').src = vehicleAsset;
    $('welcomeVehicleImage').alt = vehicleAlt;
    $('vehicleCard').classList.toggle('has-photo',Boolean(photoUrl));
    renderElapsed();
  }
  function renderWheel(centerIndex) {
    const index = centerIndex ?? Math.max(0,floors.indexOf(draftFloor || state.floor));
    [...$('floorWheel').children].forEach((button,i) => {
      button.setAttribute('aria-pressed',String(floors[i]===draftFloor));
      button.classList.toggle('near',Math.abs(i-index)===1);
    });
    $('saveButton').disabled = !draftFloor;
  }
  function chooseFloor(index,scroll=true) {
    index = Math.max(0,Math.min(floors.length-1,index)); draftFloor = floors[index];
    $('recommendation').textContent = '직접 선택한 층수예요';
    renderWheel(index);
    if(scroll) $('floorWheel').scrollTo({top:index*56,behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth'});
  }
  floors.forEach((floor,index) => {
    const button = document.createElement('button'); button.type='button'; button.className='floor-option';
    button.textContent=floor; button.setAttribute('aria-label',`${floor} 선택`);
    button.addEventListener('click',() => {wheelUserInteraction=true;chooseFloor(index);});
    button.addEventListener('keydown',event => {
      if(event.key==='ArrowDown'||event.key==='ArrowUp') {
        event.preventDefault();wheelUserInteraction=true;
        const next=Math.max(0,Math.min(floors.length-1,index+(event.key==='ArrowDown'?1:-1)));
        chooseFloor(next);$('floorWheel').children[next].focus({preventScroll:true});
      }
    });
    $('floorWheel').append(button);
  });
  $('floorWheel').addEventListener('wheel',() => {wheelUserInteraction=true;},{passive:true});
  $('floorWheel').addEventListener('pointerdown',() => {wheelUserInteraction=true;});
  $('floorWheel').addEventListener('scroll',() => {
    if(!drawerOpen || !wheelUserInteraction) return;
    const index=Math.max(0,Math.min(floors.length-1,Math.round($('floorWheel').scrollTop/56)));
    draftFloor=floors[index];$('recommendation').textContent='직접 선택한 층수예요';renderWheel(index);
  },{passive:true});

  function showPanel(panel) {
    if(drawerOpen) closeDrawer(); panels.forEach(item=>{item.hidden=item!==panel;});
    $('homeScreen').inert=Boolean(panel);
    if(panel) (panel.querySelector('button') || panel).focus({preventScroll:true});
  }
  function openDrawer(options={}) {
    showPanel(null); focusBeforeDrawer=document.activeElement;
    candidate=options.candidate || null; draftFloor=options.unavailable ? null : (options.floor || state.floor);
    draftPhotoUrl=photoUrl; wheelUserInteraction=false;drawerOpen=true;
    $('recommendation').textContent=options.unavailable?'층수를 선택해주세요':candidate?`${draftFloor}로 예상돼요`:'주차 층수를 확인하세요';
    $('drawer').hidden=false;$('drawerBackdrop').hidden=false;$('homeScreen').inert=true;
    renderHome();renderWheel();
    requestAnimationFrame(()=>{
      $('floorWheel').scrollTop=Math.max(0,floors.indexOf(draftFloor || state.floor))*56;
      $('closeDrawer').focus({preventScroll:true});
    });
  }
  function closeDrawer() {
    if(draftPhotoUrl && draftPhotoUrl!==photoUrl) URL.revokeObjectURL(draftPhotoUrl);
    draftPhotoUrl=photoUrl;candidate=null;drawerOpen=false;
    $('drawer').hidden=true;$('drawerBackdrop').hidden=true;$('homeScreen').inert=false;
    if(focusBeforeDrawer?.isConnected) focusBeforeDrawer.focus({preventScroll:true});
  }
  function setSide(side) {
    if(!['left','right'].includes(side))return;
    state.side=side;saveState();renderHome();
  }
  function setTheme(theme) {
    if(!['classic','steel'].includes(theme)) return;
    state.theme=theme;saveState();renderHome();
    toast(`${theme==='steel'?'스틸 포인트':'클래식'} 테마를 적용했어요`);
  }
  function parkingDemo(unavailable=false) {
    if(!state.autoEnabled){toast('자동 기록 데모가 꺼져 있어요');return;}
    openDrawer({floor:'B2',unavailable,candidate:{detectedAt:Date.now()}});
    toast(unavailable?'기준값이 없어 직접 선택해요':'주차 후보를 만들고 패널을 열었어요');
  }
  $('floorCard').addEventListener('click',()=>openDrawer());
  $('closeDrawer').addEventListener('click',closeDrawer);
  $('drawerBackdrop').addEventListener('click',closeDrawer);
  $('saveButton').addEventListener('click',()=>{
    if(!draftFloor)return;
    state.floor=draftFloor;if(candidate)state.parkedAt=candidate.detectedAt;
    if(photoUrl && photoUrl!==draftPhotoUrl)URL.revokeObjectURL(photoUrl);
    photoUrl=draftPhotoUrl;saveState();closeDrawer();renderHome();toast(`${state.floor}에 주차 기록을 저장했어요`);
  });
  $('cameraButton').addEventListener('click',()=>{$('photoInput').value='';$('photoInput').click();});
  $('photoInput').addEventListener('change',()=>{
    const file=$('photoInput').files[0];if(!file)return;
    if(!file.type.startsWith('image/')){toast('사진 파일을 선택해주세요');return;}
    if(draftPhotoUrl && draftPhotoUrl!==photoUrl)URL.revokeObjectURL(draftPhotoUrl);
    draftPhotoUrl=URL.createObjectURL(file);toast('사진을 선택했어요. 저장하면 기록에 반영돼요');
  });
  $('vehicleCard').addEventListener('click',()=>{
    if(photoUrl){$('photoPreview').src=photoUrl;$('photoDialog').showModal();}else openDrawer();
  });
  $('closePhoto').addEventListener('click',()=>{$('photoDialog').close();});
  $('bluetoothButton').addEventListener('click',()=>{state.autoEnabled=!state.autoEnabled;saveState();renderHome();toast(`자동 기록 ${state.autoEnabled?'켜짐':'꺼짐'} · UI 데모`);});
  $('settingsButton').addEventListener('click',()=>{renderHome();showPanel($('settingsScreen'));});
  $('closeSettings').addEventListener('click',()=>{showPanel(null);$('settingsButton').focus();});
  $('autoRecordToggle').addEventListener('change',event=>{state.autoEnabled=event.target.checked;saveState();renderHome();});
  document.querySelectorAll('input[name="drawerSide"]').forEach(input=>{input.addEventListener('change',()=>setSide(input.value));});
  document.querySelectorAll('input[name="appTheme"]').forEach(input=>{input.addEventListener('change',()=>{if(input.checked)setTheme(input.value);});});

  // Schematic UI preview only: no real coordinates, GPS or routing requests.
  const mapViewport=$('mapViewport'),mapLayer=$('mapLayer');
  let mapOffset={x:0,y:0},mapDrag=null;
  function setMapOffset(x,y) {
    const limitX=mapViewport.clientWidth*.38,limitY=mapViewport.clientHeight*.38;
    mapOffset={x:Math.max(-limitX,Math.min(limitX,x)),y:Math.max(-limitY,Math.min(limitY,y))};
    mapLayer.style.transform=`translate(${mapOffset.x}px,${mapOffset.y}px)`;
  }
  mapViewport.addEventListener('pointerdown',event=>{
    if(!event.isPrimary || event.button!==0 || event.target.closest('button'))return;
    event.preventDefault();mapViewport.focus({preventScroll:true});
    mapDrag={id:event.pointerId,x:event.clientX,y:event.clientY,startX:mapOffset.x,startY:mapOffset.y};
    mapViewport.setPointerCapture(event.pointerId);mapViewport.classList.add('is-panning');
  });
  mapViewport.addEventListener('pointermove',event=>{
    if(mapDrag?.id!==event.pointerId)return;
    setMapOffset(mapDrag.startX+event.clientX-mapDrag.x,mapDrag.startY+event.clientY-mapDrag.y);
  });
  function endMapDrag(event) {
    if(mapDrag?.id!==event.pointerId)return;
    mapDrag=null;mapViewport.classList.remove('is-panning');
    if(mapViewport.hasPointerCapture(event.pointerId))mapViewport.releasePointerCapture(event.pointerId);
  }
  mapViewport.addEventListener('pointerup',endMapDrag);
  mapViewport.addEventListener('pointercancel',endMapDrag);
  mapViewport.addEventListener('lostpointercapture',()=>{mapDrag=null;mapViewport.classList.remove('is-panning');});
  mapViewport.addEventListener('keydown',event=>{
    if(event.target.closest('button'))return;
    const shifts={ArrowLeft:[24,0],ArrowRight:[-24,0],ArrowUp:[0,24],ArrowDown:[0,-24]};
    if(!shifts[event.key])return;
    event.preventDefault();setMapOffset(mapOffset.x+shifts[event.key][0],mapOffset.y+shifts[event.key][1]);
  });
  $('recenterMap').addEventListener('click',()=>{
    const dot=mapLayer.querySelector('.map-current-position');
    const centerX=mapLayer.offsetLeft+dot.offsetLeft+dot.offsetWidth/2;
    const centerY=mapLayer.offsetTop+dot.offsetTop+dot.offsetHeight/2;
    setMapOffset(mapViewport.clientWidth/2-centerX,mapViewport.clientHeight/2-centerY);
    toast('예시 현재 위치를 중앙에 표시했어요');
  });
  window.addEventListener('resize',()=>setMapOffset(mapOffset.x,mapOffset.y));
  $('showWelcome').addEventListener('click',()=>showPanel($('welcomeScreen')));
  $('skipSetup').addEventListener('click',()=>showPanel(null));
  $('simulateParking').addEventListener('click',()=>parkingDemo());
  $('simulateNoBaseline').addEventListener('click',()=>parkingDemo(true));
  $('resetDemo').addEventListener('click',()=>{
    showPanel(null);if(photoUrl)URL.revokeObjectURL(photoUrl);photoUrl=null;draftPhotoUrl=null;
    state=defaults();saveState();renderHome();setMapOffset(0,0);toast('데모를 초기화했어요');
  });
  const setupContent=[
    {title:'내 차량을 선택하세요',body:'<div class="setup-item"><strong>내 차량 Bluetooth</strong><p>이미 연결한 차량 목록을 보여주는 자리입니다.</p></div><p>실제 앱은 휴대폰에 등록된 차량을 선택합니다.</p>',button:'이 차량 선택'},
    {title:'자동 기록을 준비해요',body:'<p>차에서 내리면 앱이 바로 나타날 수 있도록 필요한 설정을 확인합니다.</p><label class="setup-permission"><input type="checkbox" checked>주변 기기 · 데모</label><label class="setup-permission"><input type="checkbox" checked>주차 위치 · 데모</label><label class="setup-permission"><input type="checkbox" checked>다른 앱 위에 표시 · 데모</label>',button:'설정 확인'},
    {title:'주차 화면을 확인하세요',body:'<div class="setup-item"><strong>앱 자동 표시 테스트</strong><p>버튼을 누르면 저장한 쪽의 층수 패널을 엽니다.</p></div><p>실제 앱에서는 다른 앱 사용 중, 화면 꺼짐, 잠금 상태를 각각 확인합니다.</p>',button:'테스트 화면 열기'}
  ];
  function renderSetup(){const item=setupContent[setupStep];$('setupTitle').textContent=item.title;$('setupBody').innerHTML=item.body;$('setupNext').textContent=item.button;$('setupProgress').textContent=`${setupStep+1} / 3`;}
  function startSetup(){setupStep=0;renderSetup();showPanel($('setupScreen'));}
  $('startSetup').addEventListener('click',startSetup);$('settingsSetup').addEventListener('click',startSetup);
  $('closeSetup').addEventListener('click',()=>showPanel(null));
  $('setupNext').addEventListener('click',()=>{
    if(setupStep===1 && [...$('setupBody').querySelectorAll('input')].some(input=>!input.checked)){toast('데모 설정을 모두 확인해주세요');return;}
    if(setupStep<2){setupStep++;renderSetup();return;}
    state.autoEnabled=true;saveState();renderHome();parkingDemo();
  });

  let drag=null,holdTimer=null;
  const handle=$('dragHandle'),drawer=$('drawer');
  function clearDrag(){clearTimeout(holdTimer);drawer.style.transform='';drawer.classList.remove('is-dragging');$('dropGuide').hidden=true;drag=null;}
  handle.addEventListener('pointerdown',event=>{
    if(event.button!==0)return;event.preventDefault();
    drag={id:event.pointerId,startX:event.clientX,startY:event.clientY,active:false,target:null};
    handle.setPointerCapture(event.pointerId);
    holdTimer=setTimeout(()=>{if(drag){drag.active=true;drawer.classList.add('is-dragging');toast('반대쪽 가장자리로 옮겨주세요');}},350);
  });
  handle.addEventListener('pointermove',event=>{
    if(!drag || drag.id!==event.pointerId)return;
    if(!drag.active){if(Math.hypot(event.clientX-drag.startX,event.clientY-drag.startY)>9)clearDrag();return;}
    const rect=$('phone').getBoundingClientRect();
    const x=event.clientX-rect.left,threshold=Math.min(88,rect.width*.25);
    drag.target=x<threshold?'left':x>rect.width-threshold?'right':null;
    const maxShift=rect.width-drawer.offsetWidth-12;
    const offset=state.side==='left'?Math.max(0,Math.min(maxShift,event.clientX-drag.startX)):Math.min(0,Math.max(-maxShift,event.clientX-drag.startX));
    drawer.style.transform=`translateX(${offset}px)`;
    $('dropGuide').hidden=!drag.target;$('dropGuide').className=`drop-guide ${drag.target || ''}`;
  });
  handle.addEventListener('pointerup',event=>{
    if(!drag || drag.id!==event.pointerId)return;
    const target=drag.active?drag.target:null;clearDrag();
    if(handle.hasPointerCapture(event.pointerId))handle.releasePointerCapture(event.pointerId);
    if(target){setSide(target);toast(`${target==='left'?'왼쪽':'오른쪽'}에 사이드바를 고정했어요`);} 
  });
  handle.addEventListener('pointercancel',clearDrag);
  handle.addEventListener('keydown',event=>{
    if(event.key==='ArrowLeft'||event.key==='ArrowRight'){event.preventDefault();setSide(event.key==='ArrowLeft'?'left':'right');toast('사이드바 위치를 바꿨어요');}
  });
  document.addEventListener('keydown',event=>{
    if(event.key==='Escape'){if(drawerOpen)closeDrawer();else showPanel(null);}
    if(event.key==='Tab' && drawerOpen){
      const controls=[...drawer.querySelectorAll('button:not(:disabled)')];
      const first=controls[0],last=controls[controls.length-1];
      if(event.shiftKey && document.activeElement===first){event.preventDefault();last.focus({preventScroll:true});}
      else if(!event.shiftKey && document.activeElement===last){event.preventDefault();first.focus({preventScroll:true});}
    }
  });
  window.addEventListener('beforeunload',()=>{if(photoUrl)URL.revokeObjectURL(photoUrl);if(draftPhotoUrl && draftPhotoUrl!==photoUrl)URL.revokeObjectURL(draftPhotoUrl);});
  renderHome();renderWheel();setInterval(renderElapsed,60000);
})();
