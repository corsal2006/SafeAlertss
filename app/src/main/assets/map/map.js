/* OpenStreetMap + Leaflet integration for SafeAlerts. Real tiles, markers, route polylines. */
const map = L.map('map', { zoomControl: true, attributionControl: true }).setView([20.5937, 78.9629], 5);
const layer = L.layerGroup().addTo(map);
let tiles, firstFix = true, lastData, currentPolyline = null;
const status = document.getElementById('status');
// Recalculate after native WebView/Compose layout, including rotation and returning to a tab.
const mapSizeObserver = new ResizeObserver(() => map.invalidateSize({pan:false}));
mapSizeObserver.observe(document.getElementById('map'));

window.render = function(data) {
  lastData = data;
  if (!tiles && data.tiles) {
    tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      keepBuffer: 2,
      updateWhenIdle: true,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map);
    tiles.on('tileerror', () => {
      status.textContent = 'Tile network slow. Coordinates and markers still active.';
    });
  }
  if (!data.tiles) {
    map.attributionControl.addAttribution('&copy; OpenStreetMap contributors');
  }

  layer.clearLayers();
  currentPolyline = null;

  const positions = [];

  // Render Route Polyline if present
  if (data.route && Array.isArray(data.route) && data.route.length > 1) {
    const routeCoords = data.route.map(pt => [pt[0], pt[1]]);
    currentPolyline = L.polyline(routeCoords, {
      color: '#EC4899',
      weight: 5,
      opacity: 0.85,
      lineJoin: 'round',
      dashArray: null
    }).addTo(layer);
    routeCoords.forEach(c => positions.push(c));
  }

  // Render Markers
  if (data.markers && Array.isArray(data.markers)) {
    data.markers.forEach(p => {
      if (!Number.isFinite(p.lat) || !Number.isFinite(p.lon) || Math.abs(p.lat) > 90 || Math.abs(p.lon) > 180) return;

      const avatar = document.createElement('div');
      let extraClass = '';
      if (p.danger) extraClass = ' danger';
      else if (p.isOrigin) extraClass = ' origin';
      else if (p.isDest) extraClass = ' dest';
      avatar.className = 'person' + extraClass;

      let initials = p.label.trim().split(/\s+/).slice(0, 2).map(x => x[0] || '').join('').toUpperCase();
      if (p.isOrigin) initials = '📍';
      else if (p.isDest) initials = '🏁';
      avatar.textContent = initials || '•';

      const popup = document.createElement('div');
      popup.style.color = '#0F172A';
      const title = document.createElement('strong');
      title.textContent = p.label;
      popup.appendChild(title);

      if (p.detail) {
        const details = document.createElement('p');
        details.style.margin = '4px 0 0 0';
        details.style.fontSize = '12px';
        details.textContent = p.detail;
        popup.appendChild(details);
      }

      if (p.incident) {
        const b = document.createElement('button');
        b.style.marginTop = '6px';
        b.style.padding = '4px 8px';
        b.style.background = '#EF4444';
        b.style.color = '#FFFFFF';
        b.style.border = 'none';
        b.style.borderRadius = '4px';
        b.style.fontWeight = 'bold';
        b.style.cursor = 'pointer';
        b.textContent = '🚨 Open Emergency';
        b.onclick = () => SafeMap.incident(p.incident);
        popup.appendChild(b);
      }

      L.marker([p.lat, p.lon], {
        icon: L.divIcon({ html: avatar, className: '', iconSize: [36, 36], iconAnchor: [18, 18] }),
        title: p.label
      }).bindPopup(popup).addTo(layer);

      positions.push([p.lat, p.lon]);
    });
  }

  if (positions.length > 0) {
    if (firstFix || positions.length > 1) {
      map.fitBounds(L.latLngBounds(positions), { maxZoom: 16, padding: [35, 35] });
      firstFix = false;
    }
  }

  if (!data.tiles) {
    status.textContent = 'QA: offline mode';
  } else if (data.routeSummary) {
    status.textContent = data.routeSummary;
  } else if (positions.length > 0) {
    status.textContent = data.pick ? 'Tap/Long-press map to change destination' : 'Saved/shared coordinates · check each point’s timestamp';
  } else {
    status.textContent = 'No coordinates available. Check permission, GPS and sharing controls.';
  }

  map.invalidateSize();
};

map.on('contextmenu', e => {
  if (lastData && lastData.pick) {
    SafeMap.pick(e.latlng.lat, e.latlng.lng);
  }
});

map.on('click', e => {
  if (lastData && lastData.pick) {
    SafeMap.pick(e.latlng.lat, e.latlng.lng);
  }
});
