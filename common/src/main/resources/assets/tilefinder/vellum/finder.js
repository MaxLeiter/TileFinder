// TileFinder's finder page. vellum.data.tf is the scan from Java (FinderPage.java): groups of spots, nearest first.
// Filtering, sorting and grouping happen here, so typing never waits on the game; Java rescans when the radius or
// the settings change, and acts on what the player picks (vellum.send).
//
// Rhino notes: loops use `let` (a `const` in a loop body is initialised once), and there is no object rest syntax.

const HOLD_ICONS = { items: '▣', fluids: '◍', energy: '⚡' };
const COLOR = /^#[0-9a-fA-F]{6}$/;

// Templates see `ui`, `form` and `colorFields` as names; scripts reach them through `page`.
const page = vellum.state({
  ui: {
    tab: 'list',
    query: '',
    chip: 'all',
    sort: 'near',
    radius: 32,
    open: {},       // group key -> expanded
    openSpots: {},  // "key x,y,z" -> members expanded
    saved: false,
    lastScan: null
  },
  form: {},
  colorFields: [
    { key: 'beamColor', label: 'tilefinder.settings.beam_color' },
    { key: 'beamColor2', label: 'tilefinder.settings.beam_color2' },
    { key: 'boxColor', label: 'tilefinder.settings.box_color' }
  ]
});
const state = page.ui;

// ---------------------------------------------------------------- data from Java

function onData() {
  const tf = vellum.data.tf;
  if (!tf || tf.scan === state.lastScan) return;
  const first = state.lastScan === null;
  state.lastScan = tf.scan;
  state.radius = tf.radius;
  if (first) {
    if (tf.query) state.query = tf.query;
    if (tf.tab) state.tab = tf.tab;
    if (!tf.inWorld) state.tab = 'settings';
    resetForm();
    // A search for one item opens its group straight away.
    if (state.query.indexOf('item:') === 0) {
      const g = tf.groups.find(g => matchesItem(g, state.query.slice(5)));
      if (g) state.open[g.key] = true;
    }
    // Focus the search a moment later: the key that opened the finder must not type into it.
    if (state.tab === 'list') setTimeout(() => { const s = document.getElementById('search'); if (s) s.focus(); }, 80);
  }
}
vellum.on('data', onData);
onData();

// ---------------------------------------------------------------- filtering

function matchesItem(g, id) {
  return g.key === id || g.item === id || g.block === id;
}

function queryParts() {
  const words = state.query.trim().toLowerCase().split(/\s+/).filter(w => w.length > 0);
  const parts = { mods: [], items: [], words: [] };
  for (let w of words) {
    if (w.indexOf('item:') === 0 && w.length > 5) parts.items.push(w.slice(5));
    else if (w.charAt(0) === '@' && w.length > 1) parts.mods.push(w.slice(1));
    else parts.words.push(w);
  }
  return parts;
}

function spotText(s) {
  return s.names.join(' ').toLowerCase();
}

// One group as shown: its spots narrowed to the ones that match, or null when none do.
function filterGroup(g, parts, chip) {
  if (chip === 'all' && g.decorative && !vellum.data.tf.settings.showDecorative) return null;
  if (chip === 'decor' && !g.decorative) return null;
  if ((chip === 'items' || chip === 'fluids' || chip === 'energy') && g.holds.indexOf(chip) < 0) return null;
  for (let id of parts.items) if (!matchesItem(g, id)) return null;
  const mod = (g.mod + ' ' + g.modId).toLowerCase();
  for (let m of parts.mods) if (mod.indexOf(m) < 0) return null;

  const groupText = (g.name + ' ' + g.block + ' ' + g.item).toLowerCase();
  let spots = g.spots;
  if (chip === 'fav') spots = spots.filter(s => s.fav || (s.members && s.members.some(m => m.fav)));
  // Every word must be in the group's name or ids, or in a spot's custom names (a chest called "Diamonds").
  const loose = parts.words.filter(w => groupText.indexOf(w) < 0);
  if (loose.length > 0) spots = spots.filter(s => loose.every(w => spotText(s).indexOf(w) >= 0));
  if (spots.length === 0) return null;
  const shownGroup = Object.assign({}, g);
  shownGroup.spotsShown = spots;
  // Found by a spot's name or narrowed to stars: open, so the matching places show.
  shownGroup.narrowed = loose.length > 0 || chip === 'fav';
  let members = 0;
  let blocks = 0;
  for (let s of spots) {
    members += s.count;
    blocks += s.blocks;
  }
  shownGroup.shownCount = members;
  shownGroup.shownBlocks = blocks;
  return shownGroup;
}

function shown() {
  const tf = vellum.data.tf;
  if (!tf) return [];
  const parts = queryParts();
  const out = [];
  for (let g of tf.groups) {
    const f = filterGroup(g, parts, state.chip);
    if (f) out.push(f);
  }
  const byName = (a, b) => a.name.localeCompare(b.name);
  if (state.sort === 'name') out.sort(byName);
  else if (state.sort === 'mod') out.sort((a, b) => a.mod.localeCompare(b.mod) || byName(a, b));
  else if (state.sort === 'count') out.sort((a, b) => b.blocks - a.blocks || byName(a, b));
  else out.sort((a, b) => a.spotsShown[0].d - b.spotsShown[0].d);
  return out;
}

function chips() {
  const tf = vellum.data.tf;
  if (!tf) return [];
  const parts = queryParts();
  const defs = [
    ['all', 'tilefinder.chip.all'], ['items', 'tilefinder.chip.items'], ['fluids', 'tilefinder.chip.fluids'],
    ['energy', 'tilefinder.chip.energy'], ['fav', 'tilefinder.chip.fav'], ['decor', 'tilefinder.chip.decor']
  ];
  const out = [];
  for (let d of defs) {
    let count = 0;
    for (let g of tf.groups) {
      const f = filterGroup(g, parts, d[0]);
      if (f) count += f.spotsShown.length;
    }
    // Empty categories stay out of the way, except "All" and the one selected.
    if (count > 0 || d[0] === 'all' || d[0] === state.chip) out.push({ id: d[0], label: vellum.t(d[1]), count: count });
  }
  return out;
}

// ---------------------------------------------------------------- text

function meters(d) {
  return d < 10 ? d.toFixed(1) + 'm' : Math.round(d) + 'm';
}

function dyText(dy) {
  if (dy >= 2) return '▲' + dy;
  if (dy <= -2) return '▼' + (-dy);
  return '';
}

// "×12", or "×214 in 2" when touching blocks were merged into fewer places.
function countText(g) {
  if (g.spotsShown.length < g.shownCount) return vellum.t('tilefinder.gui.blocks_in', g.shownCount, g.spotsShown.length);
  return '×' + g.shownCount;
}

function spotLabel(s) {
  if (s.count > 1) return vellum.t('tilefinder.gui.connected', s.count);
  if (s.names.length > 0) return '“' + s.names[0] + '”';
  if (s.parts > 1) return vellum.t('tilefinder.gui.double');
  return '';
}

function holdIcon(h) {
  return HOLD_ICONS[h] || '';
}

function tooltip(g) {
  return g.mod + '\n' + g.block;
}

function summary() {
  const tf = vellum.data.tf;
  if (!tf) return '';
  let spots = 0;
  for (let g of tf.groups) spots += g.spots.length;
  return vellum.t('tilefinder.gui.summary', spots, tf.groups.length, tf.radius);
}

function canWiden() {
  const tf = vellum.data.tf;
  return !!tf && tf.groups.length === 0 && state.radius < tf.maxRadius;
}

function emptyText() {
  const tf = vellum.data.tf;
  if (!tf || tf.groups.length === 0) return vellum.t('tilefinder.gui.none', state.radius);
  return vellum.t('tilefinder.gui.no_match');
}

// ---------------------------------------------------------------- actions

function isOpen(g) {
  return g.narrowed ? state.open[g.key] !== false : !!state.open[g.key];
}

function toggle(g) {
  state.open[g.key] = !isOpen(g);
}

function spotKey(g, s) {
  return g.key + ' ' + s.x + ',' + s.y + ',' + s.z;
}

function isSpotOpen(g, s) {
  return !!state.openSpots[spotKey(g, s)];
}

function toggleSpot(g, s) {
  const k = spotKey(g, s);
  state.openSpots[k] = !state.openSpots[k];
}

// Every place of a group, or only the ones the search and filter left.
function trackGroup(g) {
  if (g.spotsShown.length === g.spots.length) {
    vellum.send('track', { group: g.key });
  } else {
    vellum.send('track', { group: g.key, spots: g.spotsShown.map(s => [s.x, s.y, s.z]) });
  }
}

function track(g, s) {
  vellum.send('track', { group: g.key, x: s.x, y: s.y, z: s.z });
}

// Enter in the search box: track the nearest match.
function pickFirst() {
  const list = shown();
  if (list.length > 0) track(list[0], list[0].spotsShown[0]);
}

// The icon under the pointer goes to Java, so a recipe viewer's own keys (R, U) work on it. Moving across a list
// changes it every few frames: only the latest value is sent, at most every 50 ms. While the search box has focus
// nothing is reported, or typing an "r" over an icon would open the viewer.
let hoverRaw = null;
let hoverSent = null;
let hoverTimer = 0;

function hoverIcon(g, event) {
  hoverRaw = null;
  if (g && event) {
    const r = event.currentTarget.getBoundingClientRect();
    hoverRaw = { group: g.key, x: r.x, y: r.y, w: r.width, h: r.height };
  }
  syncHover();
}

function syncHover() {
  if (hoverTimer) return;
  hoverTimer = setTimeout(() => {
    hoverTimer = 0;
    const typing = document.activeElement && document.activeElement.id === 'search';
    const now = JSON.stringify(typing ? null : hoverRaw);
    if (now === hoverSent) return;
    hoverSent = now;
    vellum.send('hover', typing ? null : hoverRaw);
  }, 50);
}

function sendRadius() {
  vellum.send('radius', state.radius);
}

function widen() {
  state.radius = Math.min(vellum.data.tf.maxRadius, state.radius * 2);
  sendRadius();
}

function openSettings() {
  state.tab = 'settings';
  resetForm();
}

// ---------------------------------------------------------------- settings

function resetForm() {
  const s = vellum.data.tf.settings;
  const f = Object.assign({}, s);
  f.hiddenText = s.hidden.join('\n');
  f.decorativeText = s.decorative.join('\n');
  page.form = f;
  state.saved = false;
}

function validColor(c) {
  return typeof c === 'string' && COLOR.test(c);
}

function formValid() {
  const f = page.form;
  return validColor(f.beamColor) && validColor(f.beamColor2) && validColor(f.boxColor);
}

function lines(text) {
  return text.split(/[\s,]+/).map(t => t.trim()).filter(t => t.length > 0);
}

function save() {
  if (!formValid()) return;
  const f = page.form;
  vellum.send('settings', {
    radius: f.radius, mergeConnected: f.mergeConnected, showDecorative: f.showDecorative, beam: f.beam,
    beamColor: f.beamColor, beamColor2: f.beamColor2, boxColor: f.boxColor, helixRadius: f.helixRadius,
    helixSpeed: f.helixSpeed, arc: f.arc, arriveDistance: f.arriveDistance, hud: f.hud,
    hidden: lines(f.hiddenText), decorative: lines(f.decorativeText)
  });
  state.saved = true;
  setTimeout(() => { state.saved = false; }, 2000);
}
