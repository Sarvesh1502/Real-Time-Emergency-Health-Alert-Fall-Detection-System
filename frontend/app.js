let API_BASE = localStorage.getItem('apiBase') || 'http://localhost:8081/api';
let streaming = false;
let watchId = null;
let lastLat = null, lastLng = null;

const statusText = document.getElementById('statusText');
const startBtn = document.getElementById('startBtn');
const stopBtn = document.getElementById('stopBtn');
const simulateFall = document.getElementById('simulateFall');
const alertsEl = document.getElementById('alerts');
const eventsEl = document.getElementById('events');
const backendUrlInput = document.getElementById('backendUrl');
const saveBackendBtn = document.getElementById('saveBackend');
const useDeviceSensors = document.getElementById('useDeviceSensors');
const permNote = document.getElementById('permNote');
const netStatus = document.getElementById('netStatus');
const overrideLoc = document.getElementById('overrideLoc');
const ovLat = document.getElementById('ovLat');
const ovLng = document.getElementById('ovLng');

if (backendUrlInput) backendUrlInput.value = API_BASE;
if (saveBackendBtn) {
  saveBackendBtn.onclick = () => {
    const v = backendUrlInput.value.trim();
    if (v) {
      API_BASE = v;
      localStorage.setItem('apiBase', v);
      if (netStatus) netStatus.textContent = `API set to ${API_BASE}`;
      refreshUI();
    }
  };
}

startBtn.onclick = async () => {
  streaming = true;
  startBtn.disabled = true;
  stopBtn.disabled = false;
  statusText.textContent = 'Streaming...';
  startGeo();
  startSensors();
};

stopBtn.onclick = () => {
  streaming = false;
  startBtn.disabled = false;
  stopBtn.disabled = true;
  statusText.textContent = 'Idle';
  if (watchId) navigator.geolocation.clearWatch(watchId);
};

function startGeo() {
  if (!('geolocation' in navigator)) return;
  watchId = navigator.geolocation.watchPosition(pos => {
    lastLat = pos.coords.latitude;
    lastLng = pos.coords.longitude;
    document.getElementById('lat').textContent = lastLat.toFixed(5);
    document.getElementById('lng').textContent = lastLng.toFixed(5);
  }, err => {
    console.warn('geo error', err);
    if (permNote) permNote.textContent = `Location error: ${err.message}. If on iOS, you may need HTTPS or to allow location.`;
  }, { enableHighAccuracy: true, maximumAge: 2000, timeout: 5000 });
}

function startSensors() {
  let latest = { ax:0, ay:0, az:9.81, gx:0, gy:0, gz:0, ok:false };

  async function requestPermissionsIfNeeded() {
    try {
      // iOS 13+
      if (typeof DeviceMotionEvent !== 'undefined' && typeof DeviceMotionEvent.requestPermission === 'function') {
        const s = await DeviceMotionEvent.requestPermission();
        if (s !== 'granted') permNote.textContent = 'Motion permission not granted.';
      }
    } catch (_) {}
  }

  if (useDeviceSensors && useDeviceSensors.checked) {
    requestPermissionsIfNeeded();
    window.addEventListener('devicemotion', (e) => {
      const acc = e.accelerationIncludingGravity || e.acceleration || {x:0,y:0,z:9.81};
      const rot = e.rotationRate || {alpha:0,beta:0,gamma:0};
      latest.ax = acc.x || 0; latest.ay = acc.y || 0; latest.az = acc.z || 9.81;
      // Approximate gyro using rotationRate degrees/sec -> keep as-is
      latest.gx = rot.alpha || 0; latest.gy = rot.beta || 0; latest.gz = rot.gamma || 0;
      latest.ok = true;
    });
  }

  const intervalMs = 500; // send 2 Hz
  let timer = setInterval(() => {
    if (!streaming) { clearInterval(timer); return; }

    // Start with device values if available, else use light random walk
    let ax=latest.ok ? latest.ax : (Math.random()*0.6-0.3);
    let ay=latest.ok ? latest.ay : (Math.random()*0.6-0.3);
    let az=latest.ok ? latest.az : (9.81 + (Math.random()*0.6-0.3));
    let gx=latest.ok ? latest.gx : (Math.random()*6-3);
    let gy=latest.ok ? latest.gy : (Math.random()*6-3);
    let gz=latest.ok ? latest.gz : (Math.random()*6-3);

    // Always allow simulateFall to inject a spike (even when device sensors are on)
    if (simulateFall.checked) {
      ax = (Math.random()*10+15) * (Math.random()>0.5?1:-1);
      ay = (Math.random()*10+15) * (Math.random()>0.5?1:-1);
      az = (Math.random()*10+15);
      gx = (Math.random()*200-100);
      gy = (Math.random()*200-100);
      gz = (Math.random()*200-100);
    }

    const payload = {
      timestamp: Date.now(),
      accel: { x: ax, y: ay, z: az },
      gyro: { x: gx, y: gy, z: gz },
      lat: (()=>{ if (overrideLoc && overrideLoc.checked) { const v=parseFloat(ovLat?.value); return isNaN(v)? null : v; } return lastLat; })(),
      lng: (()=>{ if (overrideLoc && overrideLoc.checked) { const v=parseFloat(ovLng?.value); return isNaN(v)? null : v; } return lastLng; })()
    };

    fetch(`${API_BASE}/events`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    .then(r => { if (netStatus) netStatus.textContent = `POST /events -> ${r.status}`; })
    .catch(e => { console.warn('post error', e); if (netStatus) netStatus.textContent = `POST /events failed: ${e}`; });
  }, intervalMs);
}

async function refreshUI() {
  try {
    const alertsResp = await fetch(`${API_BASE}/alerts`);
    const alerts = await alertsResp.json();
    const eventsResp = await fetch(`${API_BASE}/events/recent`);
    const events = await eventsResp.json();
    if (netStatus) netStatus.textContent = `GET alerts:${alertsResp.status} events:${eventsResp.status} • API ${API_BASE}`;

    alertsEl.innerHTML = '';
    alerts.forEach(a => {
      const li = document.createElement('li');
      li.innerHTML = `<div><strong class="bad">ALERT</strong> • ${new Date(a.timestamp).toLocaleTimeString()} • ${a.reason}</div>
      <div class="small">Lat ${a.lat?.toFixed?.(5) ?? '-'}, Lng ${a.lng?.toFixed?.(5) ?? '-'}</div>`;
      alertsEl.appendChild(li);
    });

    eventsEl.innerHTML = '';
    events.forEach(e => {
      const mag = Math.sqrt(e.ax*e.ax + e.ay*e.ay + e.az*e.az).toFixed(2);
      const li = document.createElement('li');
      li.innerHTML = `<div>${new Date(e.timestamp).toLocaleTimeString()} • Accel|G: ${mag} | ${e.gx.toFixed(1)},${e.gy.toFixed(1)},${e.gz.toFixed(1)}</div>`;
      eventsEl.appendChild(li);
    });
  } catch (e) {
    if (netStatus) netStatus.textContent = `GET failed: ${e} • API ${API_BASE}`;
  }
}

setInterval(refreshUI, 1500);
refreshUI();
