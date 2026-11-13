import { safeStrokes, cssInkColor, inkArgb } from './stroke-format.mjs';
import { reconcileNotes } from './note-merge.mjs';
/* MedAtlas Companion · no external APIs and no telemetry.
 * Text notes follow StudyNote JSON fields from the Android app.
 * The desktop cache is independent until explicit file transfer.
 */
const DB_NAME = 'medatlas-companion-v1';
const DB_VERSION = 1;
const byId = id => document.getElementById(id);
const state = {
  view: 'all', selected: null, blobUrl: null, saveTimer: null,
  inkStrokes: [], undone: [], activeStroke: null, activePointer: null,
  inkTool: 'PEN', inkColor: '#234d83', inkWidth: 3
};
let database;

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains('documents'))
        db.createObjectStore('documents', { keyPath: 'id' });
      if (!db.objectStoreNames.contains('notes'))
        db.createObjectStore('notes', { keyPath: 'id' });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}
function collection(name, method, record) {
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(name, method === 'getAll' || method === 'get' ? 'readonly' : 'readwrite');
    const store = transaction.objectStore(name);
    const request = method === 'getAll' ? store.getAll() : method === 'get' ? store.get(record)
      : method === 'put' ? store.put(record) : store.delete(record);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
    transaction.onerror = () => reject(transaction.error);
  });
}
const all = name => collection(name, 'getAll');
const get = (name, id) => collection(name, 'get', id);
const put = (name, entry) => collection(name, 'put', entry);
const remove = (name, id) => collection(name, 'delete', id);

// Apply a prepared import atomically: a browser/storage failure rolls back all
// imported additions together, rather than exposing half an imported notebook.
function putNotesAtomically(notes) {
  return new Promise((resolve, reject) => {
    if (!notes.length) return resolve();
    const transaction = database.transaction('notes', 'readwrite');
    const store = transaction.objectStore('notes');
    for (const note of notes) store.put(note);
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error || new Error('Import anulat.'));
  });
}
let notification;
function toast(message) {
  const box = byId('toast');
  box.textContent = message;
  box.hidden = false;
  clearTimeout(notification);
  notification = setTimeout(() => box.hidden = true, 3800);
}
function escapeDate(ms) {
  const time = Number(ms) || Date.now();
  return new Date(time).toLocaleDateString('ro-RO', { day:'2-digit', month:'short', year:'numeric' });
}
function activeView(view) {
  state.view = view;
  document.querySelectorAll('[data-view]').forEach(button => {
    button.classList.toggle('active', button.dataset.view === view);
  });
  byId('view-title').textContent = view === 'all' ? 'Biblioteca mea' :
    view === 'notes' ? 'Notițele mele' : 'Documentele mele';
  renderList();
}
async function renderList() {
  const docs = await all('documents');
  const notes = await all('notes');
  const term = byId('search').value.trim().toLocaleLowerCase('ro-RO');
  const rows = [
    ...((state.view === 'notes') ? [] : docs.map(item => ({ ...item, kind:'document' }))),
    ...((state.view === 'documents') ? [] : notes.map(item => ({ ...item, kind:'note' })))
  ].filter(row => {
    const value = [row.name || row.heading, row.body || '', row.searchText || '']
      .join(' ').toLocaleLowerCase('ro-RO');
    return !term || value.includes(term);
  }).sort((a,b) => (b.updatedAt || b.importedAt || 0) - (a.updatedAt || a.importedAt || 0));
  const list = byId('list');
  list.replaceChildren();
  if (!rows.length) {
    const empty = document.createElement('p');
    empty.className = 'no-items';
    empty.textContent = term ? 'Niciun rezultat pentru această căutare.' :
      'Încă nu ai materiale. Adaugă documente sau creează o notiță.';
    list.append(empty);
    return;
  }
  for (const row of rows) {
    const button = document.createElement('button');
    button.className = 'item' + (state.selected?.id === row.id ? ' active' : '');
    const icon = document.createElement('span');
    icon.className = 'item-icon' + (row.kind === 'note' ? ' note' : '');
    icon.textContent = row.kind === 'note' ? '✎' : (row.type || 'DOC').slice(0,3).toUpperCase();
    const details = document.createElement('div');
    details.style.minWidth = '0';
    const title = document.createElement('strong');
    title.textContent = row.name || row.heading || 'Fără titlu';
    const meta = document.createElement('div');
    meta.className = 'meta';
    meta.textContent = (row.kind === 'note' ? 'NOTIȚĂ' : row.type.toUpperCase())
      + ' · ' + escapeDate(row.updatedAt || row.importedAt);
    details.append(title, meta);
    button.append(icon, details);
    button.addEventListener('click', () => {
      if (row.kind === 'note') openNote(row.id);
      else openDocument(row.id);
    });
    list.append(button);
  }
}
function finishPendingInk() {
  if (state.activeStroke?.points?.length) {
    state.inkStrokes.push(state.activeStroke);
  }
  state.activeStroke = null;
  state.activePointer = null;
}
function closeCurrent() {
  if (state.selected?.kind === 'note') finishPendingInk();
  if (state.saveTimer) {
    clearTimeout(state.saveTimer);
    state.saveTimer = null;
    saveSelectedNote();
  }
  if (state.blobUrl) URL.revokeObjectURL(state.blobUrl);
  state.blobUrl = null;
  state.selected = null;
  byId('reader').hidden = true;
  byId('note-editor').hidden = true;
  byId('empty').hidden = false;
  renderList();
}
async function createNote() {
  closeCurrent();
  const now = Date.now();
  const note = {
    id: crypto.randomUUID(), documentId:'medical-notebook', page:0,
    heading:'Notiță nouă', body:'', course:'General',
    paper:'BLANK', darkPaper:false, handwritingFile:'', strokes:[],
    createdAt:now, updatedAt:now
  };
  await put('notes', note);
  await openNote(note.id);
}
async function openNote(id) {
  if (state.selected?.kind === 'note' && state.selected.id !== id) {
    clearTimeout(state.saveTimer);
    state.saveTimer = null;
    finishPendingInk();
    await saveSelectedNote();
  }
  if (state.blobUrl) URL.revokeObjectURL(state.blobUrl);
  state.blobUrl = null;
  const note = await get('notes', id);
  if (!note) return toast('Notița nu mai există.');
  state.selected = { id, kind:'note' };
  byId('note-title').value = note.heading || '';
  byId('note-content').value = note.body || '';
  state.inkStrokes = safeStrokes(note.strokes || []);
  state.undone = [];
  state.undoStates = [];
  state.redoStates = [];
  state.activeStroke = null;
  state.activePointer = null;
  byId('note-status').textContent = 'Salvat local';
  byId('empty').hidden = true;
  byId('reader').hidden = true;
  byId('note-editor').hidden = false;
  requestAnimationFrame(renderInk);
  renderList();
}
async function saveSelectedNote() {
  if (state.selected?.kind !== 'note') return;
  const id = state.selected.id;
  // Snapshot form values before awaiting IndexedDB: closing/switching notes
  // must never accidentally write another note's text into this note.
  const heading = byId('note-title').value.trim().slice(0,180) || 'Notiță fără titlu';
  const body = byId('note-content').value.slice(0,300000);
  const strokes = state.inkStrokes.map(stroke => ({
    ...stroke, points: stroke.points.map(point => [...point])
  }));
  const note = await get('notes', id);
  if (!note) return;
  note.heading = heading;
  note.body = body;
  note.strokes = strokes;
  note.updatedAt = Date.now();
  await put('notes', note);
  byId('note-status').textContent = 'Salvat local';
  await renderList();
}
function scheduleSave() {
  if (state.selected?.kind !== 'note') return;
  byId('note-status').textContent = 'Se salvează…';
  clearTimeout(state.saveTimer);
  state.saveTimer = setTimeout(() => {
    state.saveTimer = null;
    saveSelectedNote().catch(err => {
      byId('note-status').textContent = 'Salvare eșuată';
      toast('Salvare eșuată: ' + err.message);
    });
  }, 350);
}
function inkPoint(event) {
  const rect=byId('ink-canvas').getBoundingClientRect();
  return [
    Math.max(0,Math.min(1,(event.clientX-rect.left)/Math.max(1,rect.width))),
    Math.max(0,Math.min(1,(event.clientY-rect.top)/Math.max(1,rect.height))),
    Math.max(0,Math.min(1,event.pressure || .5))
  ];
}
function cloneStrokes(source) {
  return source.map(stroke => ({...stroke,points:stroke.points.map(point=>[...point])}));
}
function pushInkHistory() {
  state.undoStates ??= [];
  state.redoStates ??= [];
  state.undoStates.push(cloneStrokes(state.inkStrokes));
  if (state.undoStates.length > 30) state.undoStates.shift();
  state.redoStates=[];
}
function undoInk() {
  if (!state.undoStates?.length) return;
  state.redoStates.push(cloneStrokes(state.inkStrokes));
  state.inkStrokes=state.undoStates.pop();
  renderInk();scheduleSave();
}
function redoInk() {
  if (!state.redoStates?.length) return;
  state.undoStates.push(cloneStrokes(state.inkStrokes));
  state.inkStrokes=state.redoStates.pop();
  renderInk();scheduleSave();
}
function eraseInk(point) {
  const width=byId('ink-canvas').getBoundingClientRect().width || 350;
  const height=byId('ink-canvas').getBoundingClientRect().height || 400;
  const threshold=16;
  const before=state.inkStrokes.length;
  state.inkStrokes=state.inkStrokes.filter(stroke =>
    !stroke.points.some(p=>
      Math.hypot((p[0]-point[0])*width,(p[1]-point[1])*height) < threshold));
  if (state.inkStrokes.length !== before) renderInk();
}
function renderInk() {
  const canvas=byId('ink-canvas');
  if (state.selected?.kind !== 'note') return;
  const bounds=canvas.getBoundingClientRect();
  if (bounds.width < 5 || bounds.height < 5) return;
  const dpr=Math.min(window.devicePixelRatio || 1, 2);
  const w=Math.max(1,Math.round(bounds.width)), h=Math.max(1,Math.round(bounds.height));
  if (canvas.width !== Math.round(w*dpr) || canvas.height !== Math.round(h*dpr)) {
    canvas.width=Math.round(w*dpr);canvas.height=Math.round(h*dpr);
  }
  const ctx=canvas.getContext('2d');
  ctx.setTransform(dpr,0,0,dpr,0,0);
  ctx.clearRect(0,0,w,h);
  ctx.fillStyle='#fffcf6';
  ctx.fillRect(0,0,w,h);
  ctx.strokeStyle='#e3ebe3';
  ctx.lineWidth=1;
  for(let y=30;y<h;y+=30) {
    ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(w,y);ctx.stroke();
  }
  for (const stroke of [...state.inkStrokes,
    ...(state.activeStroke ? [state.activeStroke] : [])]) {
    const points=stroke.points;
    if (!points.length) continue;
    ctx.strokeStyle=cssInkColor(stroke.color);
    ctx.fillStyle=cssInkColor(stroke.color);
    ctx.globalAlpha=stroke.tool==='HIGHLIGHT' ? .26 :
      stroke.tool==='PENCIL' ? .67 : stroke.tool==='BRUSH' ? .82 : 1;
    ctx.lineJoin='round';ctx.lineCap='round';
    if (points.length===1) {
      ctx.beginPath();ctx.arc(points[0][0]*w,points[0][1]*h,
        Math.max(.6,stroke.width/2),0,Math.PI*2);ctx.fill();
    } else for(let i=1;i<points.length;i++) {
      const p0=points[i-1],p1=points[i];
      ctx.lineWidth=stroke.width*(.55+Math.min(1,p1[2])* .45)*
        (stroke.tool==='BRUSH'?1.5:1);
      ctx.beginPath();ctx.moveTo(p0[0]*w,p0[1]*h);
      ctx.lineTo(p1[0]*w,p1[1]*h);ctx.stroke();
    }
  }
  ctx.globalAlpha=1;
}
function connectInk() {
  const canvas=byId('ink-canvas');
  document.querySelectorAll('[data-ink-tool]').forEach(button=>
    button.addEventListener('click',()=>{
      state.inkTool=button.dataset.inkTool;
      document.querySelectorAll('[data-ink-tool]').forEach(item=>
        item.classList.toggle('active',item===button));
    }));
  byId('ink-color').addEventListener('input',event=>{
    state.inkColor=event.target.value;
  });
  byId('ink-width').addEventListener('input',event=>{
    state.inkWidth=Number(event.target.value);
  });
  byId('ink-undo').addEventListener('click',undoInk);
  byId('ink-redo').addEventListener('click',redoInk);
  canvas.addEventListener('pointerdown',event=>{
    if (state.selected?.kind!=='note' || state.activePointer!==null) return;
    // Ignore secondary fingers and keep the active stylus as the owner.
    if (event.pointerType==='touch' && !event.isPrimary) return;
    event.preventDefault();
    canvas.setPointerCapture(event.pointerId);
    state.activePointer=event.pointerId;
    pushInkHistory();
    const point=inkPoint(event);
    if (state.inkTool==='ERASER') {
      state.activeStroke=null;eraseInk(point);
    } else {
      state.activeStroke={
        id:crypto.randomUUID(),tool:state.inkTool,
        color:inkArgb(state.inkColor),width:state.inkWidth,
        points:[point]
      };
      renderInk();
    }
  });
  canvas.addEventListener('pointermove',event=>{
    if (event.pointerId!==state.activePointer) return;
    event.preventDefault();
    const events=typeof event.getCoalescedEvents==='function'
      ? event.getCoalescedEvents():[event];
    for (const sample of events.length ? events:[event]) {
      const point=inkPoint(sample);
      if (state.inkTool==='ERASER') eraseInk(point);
      else state.activeStroke?.points.push(point);
    }
    renderInk();
  });
  function finishInk(event) {
    if (event.pointerId!==state.activePointer) return;
    if (state.activeStroke) state.inkStrokes.push(state.activeStroke);
    state.activeStroke=null;state.activePointer=null;
    renderInk();scheduleSave();
  }
  canvas.addEventListener('pointerup',finishInk);
  canvas.addEventListener('pointercancel',finishInk);
  if ('ResizeObserver' in window) {
    new ResizeObserver(()=>renderInk()).observe(canvas);
  } else window.addEventListener('resize',renderInk);
}

function download(name, body, type='application/json') {
  const url = URL.createObjectURL(new Blob([body], { type }));
  const link = document.createElement('a');
  link.href = url; link.download = name;
  document.body.append(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}
async function exportNotes() {
  if (state.selected?.kind === 'note') await saveSelectedNote();
  const notes = await all('notes');
  download('MedAtlas-notes.json', JSON.stringify(notes.map(item => ({
    id: item.id, documentId: item.documentId || 'medical-notebook',
    page:item.page || 0, heading:item.heading || '', body:item.body || '',
    createdAt:item.createdAt || Date.now(), course:item.course || 'General',
    handwritingFile:item.handwritingFile || '', updatedAt:item.updatedAt || Date.now(),
    paper:item.paper || 'BLANK', darkPaper:!!item.darkPaper,
    strokes:item.strokes || []
  })),null,2));
  toast(notes.length + ' notițe exportate în format MedAtlas.');
}
async function importNotes(file) {
  if (!file) return;
  if (file.size > 12 * 1024 * 1024) throw Error('Fișierul JSON este prea mare.');
  // Commit the open note before comparing imported revisions against IndexedDB.
  if (state.selected?.kind === 'note') {
    clearTimeout(state.saveTimer);
    state.saveTimer = null;
    finishPendingInk();
    await saveSelectedNote();
  }
  const raw = JSON.parse(await file.text());
  const plan = await reconcileNotes(await all('notes'), raw);
  await putNotesAtomically(plan.additions);
  await renderList();
  toast(plan.additions.length + ' notițe importate.' +
    (plan.conflicts ? ' ' + plan.conflicts + ' copii pentru conflicte.' : '') +
    (plan.ignored ? ' ' + plan.ignored + ' deja existente.' : ''));
}

// Refuse ZIP bombs even when the archive's claimed uncompressed size lies.
// Unlike Response(arrayBuffer), this never accumulates unlimited inflated bytes.
async function readBoundedStream(stream, limit) {
  const reader=stream.getReader();
  const chunks=[];
  let length=0;
  try {
    while(true) {
      const {done,value}=await reader.read();
      if(done) break;
      length+=value.byteLength;
      if(length>limit) {
        await reader.cancel('Office XML size limit exceeded');
        throw new Error('Document Office decomprimat prea mare.');
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const output=new Uint8Array(length);
  let offset=0;
  for(const part of chunks) {
    output.set(part,offset);
    offset+=part.byteLength;
  }
  return output;
}

// Minimal local Office text extraction. PDF rendering is delegated to the native
// browser PDF viewer. Office preview is text-only, not layout fidelity.
async function officeEntries(file) {
  const buffer = await file.arrayBuffer();
  const data = new DataView(buffer);
  const uint = (offset, width=4) => width === 2
    ? data.getUint16(offset,true) : data.getUint32(offset,true);
  let eocd = -1;
  for (let pos = buffer.byteLength - 22; pos >= Math.max(0,buffer.byteLength-65558); pos--) {
    if (uint(pos) === 0x06054b50) { eocd = pos; break; }
  }
  if (eocd < 0) throw Error('Fișier Office invalid: ZIP incomplet.');
  const entries = uint(eocd+10,2);
  if (entries > 2500) throw Error('Prea multe componente Office.');
  let cursor = uint(eocd+16);
  const extracted = new Map();
  const utf8 = new TextDecoder('utf-8');
  for (let i=0;i<entries;i++) {
    if (uint(cursor) !== 0x02014b50) throw Error('Index ZIP invalid.');
    const method = uint(cursor+10,2), packed = uint(cursor+20),
      unpacked = uint(cursor+24), len = uint(cursor+28,2),
      extra = uint(cursor+30,2), comment = uint(cursor+32,2),
      local = uint(cursor+42);
    const name = utf8.decode(new Uint8Array(buffer,cursor+46,len));
    cursor += 46+len+extra+comment;
    const interested = name === 'word/document.xml' || /^ppt\/slides\/slide\d+\.xml$/.test(name);
    if (!interested) continue;
    if (unpacked > 12_000_000 || packed > 12_000_000) throw Error('Document Office prea mare.');
    if (uint(local) !== 0x04034b50) throw Error('Intrare ZIP invalidă.');
    const offset = local+30+uint(local+26,2)+uint(local+28,2);
    if (offset+packed > buffer.byteLength) throw Error('Date Office incomplete.');
    const bytes = new Uint8Array(buffer,offset,packed);
    let xmlBytes;
    if (method===0) xmlBytes=bytes;
    else if (method===8 && typeof DecompressionStream!=='undefined') {
      const stream = new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
      xmlBytes = await readBoundedStream(stream,12_000_000);
    } else throw Error('Browserul nu poate decomprima acest format Office.');
    if (xmlBytes.byteLength > 12_000_000) throw Error('Conținut Office prea mare.');
    extracted.set(name,utf8.decode(xmlBytes));
  }
  return extracted;
}
function paragraphsFromXml(xml) {
  if (/<!DOCTYPE|<!ENTITY/i.test(xml)) throw Error('XML Office nesigur.');
  const doc = new DOMParser().parseFromString(xml,'application/xml');
  if (doc.querySelector('parsererror')) throw Error('Document XML invalid.');
  const blocks = Array.from(doc.getElementsByTagName('*')).filter(n=>n.localName === 'p');
  return blocks.map(node=>Array.from(node.getElementsByTagName('*'))
    .filter(n=>n.localName === 't').map(n=>n.textContent || '').join(''))
    .filter(Boolean);
}
async function extractOffice(file, extension) {
  const entries = await officeEntries(file);
  if (extension === 'docx') return paragraphsFromXml(entries.get('word/document.xml') || '').join('\n\n');
  const slides = [...entries].filter(([name])=>name.startsWith('ppt/slides/'))
    .sort(([a],[b])=>Number(a.match(/slide(\d+)/)?.[1]||0)-Number(b.match(/slide(\d+)/)?.[1]||0));
  return slides.map(([name,xml],i)=>'SLIDE '+(i+1)+'\n'+paragraphsFromXml(xml).join('\n')).join('\n\n');
}
async function importDocuments(files) {
  let count=0;
  for (const file of files) {
    if (file.size > 150*1024*1024) { toast(file.name + ' depășește limita locală de 150 MB.'); continue; }
    const extension = file.name.toLowerCase().split('.').pop();
    if (!['pdf','txt','docx','pptx'].includes(extension)) continue;
    let searchText = '';
    if (extension === 'txt') searchText = (await file.text()).slice(0,100000);
    if (['docx','pptx'].includes(extension)) {
      try { searchText = (await extractOffice(file,extension)).slice(0,100000); }
      catch (err) { toast(file.name + ': previzualizare limitată ('+err.message+').'); }
    }
    const now=Date.now();
    const entry={id:crypto.randomUUID(),name:file.name,type:extension,blob:file,
      searchText,importedAt:now,updatedAt:now};
    await put('documents',entry);
    count++;
  }
  await renderList();
  toast(count+' documente adăugate offline.');
}
async function openDocument(id) {
  if (state.selected?.kind==='note') {
    clearTimeout(state.saveTimer);
    state.saveTimer = null;
    finishPendingInk();
    await saveSelectedNote();
  }
  const item=await get('documents',id);
  if (!item) return toast('Documentul nu mai este disponibil.');
  if (state.blobUrl) URL.revokeObjectURL(state.blobUrl);
  state.blobUrl=null;
  state.selected={id,kind:'document'};
  byId('document-title').textContent=item.name;
  byId('document-kind').textContent=(item.type||'document').toUpperCase()+' · DOCUMENT LOCAL';
  const body=byId('reader-body');
  body.replaceChildren();
  if (item.type==='pdf') {
    state.blobUrl=URL.createObjectURL(item.blob);
    const iframe=document.createElement('iframe');
    iframe.src=state.blobUrl;
    iframe.title='Cititor PDF: '+item.name;
    body.append(iframe);
  } else {
    const text=document.createElement('pre');
    text.textContent=item.searchText || 'Previzualizarea acestui document nu este disponibilă pe laptop. Originalul a fost păstrat.';
    body.append(text);
  }
  byId('empty').hidden=true;
  byId('note-editor').hidden=true;
  byId('reader').hidden=false;
  renderList();
}
function connect() {
  connectInk();
  document.querySelectorAll('[data-view]').forEach(button=>
    button.addEventListener('click',()=>activeView(button.dataset.view)));
  byId('search').addEventListener('input',renderList);
  byId('create-note').addEventListener('click',createNote);
  byId('note-title').addEventListener('input',scheduleSave);
  byId('note-content').addEventListener('input',scheduleSave);
  byId('close-note').addEventListener('click',closeCurrent);
  byId('close-reader').addEventListener('click',closeCurrent);
  byId('copy-note').addEventListener('click',async()=>{
    await navigator.clipboard.writeText(byId('note-content').value);
    toast('Text copiat.');
  });
  byId('files').addEventListener('change',async event=>{
    try { await importDocuments(Array.from(event.target.files || [])); }
    catch (error) { toast('Import nereușit: '+error.message); }
    event.target.value='';
  });
  byId('export-notes').addEventListener('click',()=>exportNotes().catch(e=>toast(e.message)));
  byId('import-notes').addEventListener('change',async event=>{
    try { await importNotes(event.target.files[0]); }
    catch (error) { toast('Import nereușit: '+error.message); }
    event.target.value='';
  });
  byId('delete-document').addEventListener('click',async()=>{
    if (!state.selected || !confirm('Ștergi documentul numai de pe acest laptop?')) return;
    await remove('documents',state.selected.id);closeCurrent();toast('Document eliminat local.');
  });
  byId('delete-note').addEventListener('click',async()=>{
    if (!state.selected || !confirm('Ștergi notița numai de pe acest laptop?')) return;
    clearTimeout(state.saveTimer);state.saveTimer=null;
    await remove('notes',state.selected.id);closeCurrent();toast('Notiță eliminată local.');
  });
  document.addEventListener('visibilitychange',()=>{
    if (document.hidden && state.selected?.kind==='note') {
      clearTimeout(state.saveTimer);state.saveTimer=null;
      finishPendingInk();
      saveSelectedNote().catch(()=>{});
    }
  });
}
async function boot() {
  database=await openDatabase();
  connect();
  await renderList();
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js').catch(()=>{});
  }
}
boot().catch(error=> {
  byId('status').textContent='Stocare indisponibilă';
  toast('Browserul nu poate porni biblioteca offline: '+error.message);
});
