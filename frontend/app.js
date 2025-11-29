let API_BASE = localStorage.getItem('apiBase') || 'http://localhost:8081/api';
let streaming = false;
let watchId = null;
let lastLat = null, lastLng = null;
let pending = null; // { id, expiryAt, timer }
const scheduledModals = new Map(); // alertId -> timeoutId
const dismissedAlerts = new Set(); // alertIds already handled by user
let confirming = false; // guard against double actions
// Map state
let map, userMarker, alertsLayer;
let lastAlertCount = 0;
let didInitialFit = false;
// Orientation history for fallback motion inference
const oriHist = [];
let prevCtx = 'unknown';
let motionStreak = 0;
let stillStreak = 0;

// Helpers for context inference
function average(arr) {
  if (!arr || arr.length === 0) return 0;
  return arr.reduce((a, b) => a + b, 0) / arr.length;
}
function variance(arr) {
  if (!arr || arr.length < 2) return 0;
  const m = average(arr);
  return arr.reduce((s, x) => s + (x - m) * (x - m), 0) / arr.length;
}

// Extra sensors state
const extra = {
  alpha: null, beta: null, gamma: null, // orientation
  light: null,                          // lux
  proxNear: null                        // boolean
};
const cal = { g:{x:0,y:0,z:9.81}, alpha:0.1, ready:false };

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
// Modal elements
const confirmModal = document.getElementById('confirmModal');
const btnYesOk = document.getElementById('btnYesOk');
const btnNoHelp = document.getElementById('btnNoHelp');
const confirmCountdown = document.getElementById('confirmCountdown');
const contextLine = document.getElementById('contextLine');

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

// Init Leaflet map if available
function initMap() {
  if (typeof L === 'undefined') return;
  if (map) return;
  map = L.map('map', { zoomControl: true });
  const tiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap'
  });
  tiles.addTo(map);
  alertsLayer = L.layerGroup().addTo(map);
}

startBtn.onclick = async () => {
  streaming = true;
  startBtn.disabled = true;
  stopBtn.disabled = false;
  statusText.textContent = 'Streaming...';
  startGeo();
  startSensors();
  initMap();
  startOrientation();
  startAmbientLight();
  startProximity();
  // Also request orientation permission on iOS 13+
  try {
    if (typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function') {
      const s = await DeviceOrientationEvent.requestPermission();
      if (s !== 'granted' && permNote) permNote.textContent = 'Orientation permission not granted.';
    }
  } catch(_) {}
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
    // Update map
    if (!map) initMap();
    if (map && lastLat != null && lastLng != null) {
      const ll = [lastLat, lastLng];
      if (!userMarker) {
        userMarker = L.marker(ll, { title: 'You' }).addTo(map);
        map.setView(ll, 16);
      } else {
        userMarker.setLatLng(ll);
      }
    }
  }, err => {
    console.warn('geo error', err);
    if (permNote) permNote.textContent = `Location error: ${err.message}. If on iOS, you may need HTTPS or to allow location.`;
  }, { enableHighAccuracy: true, maximumAge: 2000, timeout: 5000 });
}

async function requestPermission() {
  // Check if we're on HTTPS or localhost
  const isSecure = window.location.protocol === 'https:' || 
                  window.location.hostname === 'localhost' || 
                  window.location.hostname === '127.0.0.1';

  if (!isSecure) {
    console.warn('[sensors] Motion/orientation requires HTTPS or localhost');
    alert('For motion detection to work, please use HTTPS or localhost');
    return;
  }

  // iOS 13+ devices
  if (typeof DeviceMotionEvent !== 'undefined' && 
      typeof DeviceMotionEvent.requestPermission === 'function') {
    
    DeviceMotionEvent.requestPermission()
      .then(response => {
        if (response === 'granted') {
          startSensors();
          console.log('[sensors] iOS permission granted');
        } else {
          console.warn('[sensors] iOS permission denied');
          alert('Motion permission is required for fall detection');
        }
      })
      .catch(error => {
        console.error('[sensors] Permission error:', error);
        alert('Error accessing motion sensors: ' + error.message);
      });
  } 
  // Android/Desktop browsers
  else if (window.DeviceMotionEvent) {
    startSensors();
    console.log('[sensors] Motion sensors active');
  } 
  else {
    console.warn('[sensors] Device motion not supported');
    alert('Your device/browser does not support motion detection');
  }
}

function startSensors() {
  let latest = { ax:0, ay:0, az:9.81, gx:0, gy:0, gz:0, ok:false };
  // Keep a short history for context inference
  const hist = [];
  const HIST_MAX = 20; // ~10s at 500ms

  // Add with options for better performance
  const options = { frequency: 100 };
  
  if (window.DeviceMotionEvent) {
    window.addEventListener('devicemotion', (e) => {
      const acc = e.accelerationIncludingGravity || e.acceleration || {x:0,y:0,z:9.81};
      const rot = e.rotationRate || {alpha:0,beta:0,gamma:0};
      latest.ax = acc.x || 0; latest.ay = acc.y || 0; latest.az = acc.z || 9.81;
      // Approximate gyro using rotationRate degrees/sec -> keep as-is
      latest.gx = rot.alpha || 0; latest.gy = rot.beta || 0; latest.gz = rot.gamma || 0;
      latest.ok = true;
      if (window.__dm_once !== true) { window.__dm_once = true; console.log('[sensors] devicemotion active'); }
    });
  }

  const intervalMs = 250; // send 4 Hz for better responsiveness
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

    // Context inference (simple rules)
    // Update gravity estimate (low-pass) and compute linear acceleration
    if (!isNaN(ax) && !isNaN(ay) && !isNaN(az)) {
      const a = cal.alpha; const ig = 1-a;
      cal.g.x = a*ax + ig*cal.g.x;
      cal.g.y = a*ay + ig*cal.g.y;
      cal.g.z = a*az + ig*cal.g.z;
      cal.ready = true;
    }
    const lax = ax - cal.g.x, lay = ay - cal.g.y, laz = az - cal.g.z;
    hist.push({ax,ay,az,gx,gy,gz,lax,lay,laz});
    if (hist.length > HIST_MAX) hist.shift();
    const accelMag = Math.sqrt(ax*ax+ay*ay+az*az);
    const rotMag = Math.sqrt(gx*gx+gy*gy+gz*gz);
    let ctx = prevCtx === 'unknown' ? 'moving' : prevCtx;
    if (hist.length >= 6) {
      const varA = variance(hist.map(h=>Math.sqrt(h.lax*h.lax+h.lay*h.lay+h.laz*h.laz)));
      const varG = variance(hist.map(h=>Math.sqrt(h.gx*h.gx+h.gy*h.gy+h.gz*h.gz)));
      const meanAz = cal.ready ? cal.g.z : average(hist.map(h=>h.az));
      const still = varA < 0.25 && varG < 18;

      // Orientation hints (beta: front-back tilt, gamma: left-right tilt)
      const beta = extra.beta; // -180..180
      const gamma = extra.gamma; // -90..90
      const faceUp = (meanAz > 8.8) || (beta !== null && Math.abs(beta) < 25 && meanAz > 7.8);
      const faceDown = (meanAz < 1.2) || (beta !== null && Math.abs(beta) < 25 && meanAz < 2.0);
      const sideish = !faceUp && !faceDown;

      // Fallback: if device sensors not yet active, but orientation changes a lot over ~2.5s, treat as moving
      let oriSuggestsMoving = false;
      if (!latest.ok && (beta != null || gamma != null)) {
        oriHist.push({b: beta ?? 0, g: gamma ?? 0});
        if (oriHist.length > 10) oriHist.shift();
        if (oriHist.length >= 3) {
          const db = Math.abs(oriHist[oriHist.length-1].b - oriHist[0].b);
          const dg = Math.abs(oriHist[oriHist.length-1].g - oriHist[0].g);
          if (db > 25 || dg > 25) { oriSuggestsMoving = true; }
        }
      }

      // Hysteresis: require a short streak before flipping states
      const looksMoving = (!still) || oriSuggestsMoving || (varA > 0.6) || (varG > 22);
      if (looksMoving) { motionStreak++; stillStreak = 0; } else { stillStreak++; motionStreak = 0; }

      if (stillStreak >= 3) {
        if (extra.proxNear === true && (extra.light !== null && extra.light < 5)) {
          ctx = 'in_pocket';
        } else if (faceUp) {
          ctx = 'still_face_up';
        } else if (faceDown) {
          ctx = 'still_face_down';
        } else {
          ctx = 'still_side';
        }
      } else if (motionStreak >= 3) {
        ctx = (extra.proxNear === true) ? 'in_hand' : 'moving';
      } else {
        // uncertain: keep previous
        ctx = prevCtx;
      }
    }
    if (contextLine) contextLine.textContent = `Context: ${ctx}`;
    prevCtx = ctx;
    // If using device sensors but nothing has come through, hint HTTPS/permission once
    if (useDeviceSensors && useDeviceSensors.checked && !latest.ok) {
      if (permNote && !window.__dm_warned) {
        window.__dm_warned = true;
        permNote.textContent = 'No sensor data yet. If on iOS, open over HTTPS (ngrok) and allow Motion & Orientation.';
        console.warn('[sensors] No devicemotion yet. Ensure HTTPS + permissions.');
      }
    }

    const payload = {
      timestamp: Date.now(),
      accel: { x: ax, y: ay, z: az },
      gyro: { x: gx, y: gy, z: gz },
      lat: (()=>{ if (overrideLoc && overrideLoc.checked) { const v=parseFloat(ovLat?.value); return isNaN(v)? null : v; } return lastLat; })(),
      lng: (()=>{ if (overrideLoc && overrideLoc.checked) { const v=parseFloat(ovLng?.value); return isNaN(v)? null : v; } return lastLng; })(),
      context: ctx,
      extra: {
        orientation: { alpha: extra.alpha, beta: extra.beta, gamma: extra.gamma },
        light: extra.light,
        proximity: extra.proxNear
      }
    };

    fetch(`${API_BASE}/events`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    .then(async r => {
      if (netStatus) netStatus.textContent = `POST /events -> ${r.status}`;
      if (!r.ok) return;
      const data = await r.json().catch(()=>null);
      if (data && data.alert && data.alertId && data.expiryAt) {
        const id = data.alertId;
        if (dismissedAlerts.has(id)) return; // user already handled
        const startAt = data.confirmStartsAt || Date.now();
        const delay = Math.max(0, startAt - Date.now());
        if (scheduledModals.has(id)) return; // already scheduled
        const tid = setTimeout(() => {
          scheduledModals.delete(id);
          showConfirm(id, data.expiryAt);
        }, delay);
        scheduledModals.set(id, tid);
      }
    })
    .catch(e => { console.warn('post error', e); if (netStatus) netStatus.textContent = `POST /events failed: ${e}`; });
  }, intervalMs);
}

function showConfirm(alertId, expiryAt) {
  // Avoid duplicating modal if already pending and same id
  if (pending && pending.id === alertId) return;
  // Clear previous
  if (pending && pending.timer) clearInterval(pending.timer);
  pending = { id: alertId, expiryAt, timer: null };
  let remaining = Math.max(0, Math.ceil((expiryAt - Date.now())/1000));
  if (confirmCountdown) confirmCountdown.textContent = String(remaining);
  if (confirmModal) {
    confirmModal.style.display = 'flex';
    // Ensure overlay/content receive touches/clicks on mobile
    confirmModal.style.pointerEvents = 'auto';
    confirmModal.style.touchAction = 'manipulation';
    const box = confirmModal.firstElementChild;
    if (box) {
      box.style.pointerEvents = 'auto';
      box.style.touchAction = 'manipulation';
    }
  }

  // Pause streaming to avoid generating more alerts while confirming
  streaming = false;
  if (startBtn) startBtn.disabled = false;
  if (stopBtn) stopBtn.disabled = true;
  if (statusText) statusText.textContent = 'Awaiting confirmation...';
  confirming = false;

  // Button handlers
  if (btnYesOk) {
    btnYesOk.disabled = false;
    btnYesOk.replaceWith(btnYesOk.cloneNode(true));
  }
  if (btnNoHelp) {
    btnNoHelp.disabled = false;
    btnNoHelp.replaceWith(btnNoHelp.cloneNode(true));
  }
  // Re-query after clone to attach fresh listeners
  const yesBtn = document.getElementById('btnYesOk');
  const noBtn = document.getElementById('btnNoHelp');
  const bind = (el, ok) => {
    if (!el) return;
    const handler = (ev) => {
      try { ev.preventDefault(); ev.stopPropagation(); } catch(_){}
      confirmDecision(ok);
    };
    // Bind both touchstart and click for mobile reliability
    el.addEventListener('touchstart', handler, { once: true, passive: false });
    el.addEventListener('click', handler, { once: true });
  };
  bind(yesBtn, true);
  bind(noBtn, false);

  // Countdown
  pending.timer = setInterval(() => {
    if (!pending) { clearInterval(pending?.timer); return; }
    const secs = Math.max(0, Math.ceil((pending.expiryAt - Date.now())/1000));
    if (confirmCountdown) confirmCountdown.textContent = String(secs);
    if (secs <= 0) {
      clearInterval(pending.timer);
      // Auto-send (treat as No/need help)
      confirmDecision(false);
    }
  }, 250);
}

function hideConfirm() {
  if (confirmModal) confirmModal.style.display = 'none';
  if (pending && pending.timer) clearInterval(pending.timer);
  pending = null;
}

function confirmDecision(ok) {
  if (!pending) return hideConfirm();
  if (confirming) return; // prevent double click
  confirming = true;
  const id = pending.id;
  // Force-hide immediately for better UX
  if (confirmModal) confirmModal.style.display = 'none';
  if (pending && pending.timer) { clearInterval(pending.timer); }
  // Prevent any future popup for this alert id
  dismissedAlerts.add(id);
  const tid = scheduledModals.get(id);
  if (tid) { clearTimeout(tid); scheduledModals.delete(id); }
  // Visually disable buttons once action taken
  const yesBtn2 = document.getElementById('btnYesOk');
  const noBtn2 = document.getElementById('btnNoHelp');
  if (yesBtn2) yesBtn2.disabled = true;
  if (noBtn2) noBtn2.disabled = true;
  if (statusText) statusText.textContent = ok ? 'Marked OK. Resuming idle.' : 'Help requested. Sending...';
  // Clear local state
  pending = null;
  // Fire and forget
  fetch(`${API_BASE}/alerts/${id}/confirm?ok=${ok ? 'true' : 'false'}`, { method: 'POST' })
    .catch(()=>{});
  // Done
}

// Optional sensors (feature-detected)
function startOrientation() {
  try {
    if ('DeviceOrientationEvent' in window) {
      window.addEventListener('deviceorientation', (e) => {
        extra.alpha = (e.alpha ?? extra.alpha);
        extra.beta = (e.beta ?? extra.beta);
        extra.gamma = (e.gamma ?? extra.gamma);
      });
    }
  } catch (_) {}
}

function startAmbientLight() {
  try {
    if ('AmbientLightSensor' in window) {
      const sensor = new AmbientLightSensor();
      sensor.addEventListener('reading', () => { extra.light = sensor.illuminance; });
      sensor.start();
    }
  } catch (_) {}
}

function startProximity() {
  try {
    if ('ProximitySensor' in window) {
      const prox = new ProximitySensor();
      prox.addEventListener('reading', () => { extra.proxNear = !!prox.near; });
      prox.start();
    }
  } catch (_) {}
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

    // Update map markers for alerts
    if (typeof L !== 'undefined') {
      initMap();
      alertsLayer.clearLayers();
      const markers = [];
      alerts.filter(a=>a.lat!=null&&a.lng!=null).forEach(a=>{
        L.marker([a.lat, a.lng]).addTo(alertsLayer)
          .bindPopup(`${new Date(a.timestamp).toLocaleTimeString()}<br/>${a.reason}`);
        markers.push([a.lat, a.lng]);
      });
      // Fit bounds on first load or when alert count changes
      const count = markers.length;
      if (map && (count !== lastAlertCount || !didInitialFit)) {
        const b = L.latLngBounds([]);
        if (userMarker) {
          const ll = userMarker.getLatLng();
          if (ll) b.extend(ll);
        }
        markers.forEach(ll => b.extend(ll));
        if (b.isValid()) {
          map.fitBounds(b.pad(0.15), { animate: false });
          didInitialFit = true;
        }
        lastAlertCount = count;
      }
    }
  } catch (e) {
    if (netStatus) netStatus.textContent = `GET failed: ${e} • API ${API_BASE}`;
  }
}

setInterval(refreshUI, 1500);
refreshUI();
