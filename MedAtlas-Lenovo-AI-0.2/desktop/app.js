/* MedAtlas Companion · no external APIs and no telemetry.
 * Text notes follow StudyNote JSON fields from the Android app.
 * The desktop cache is independent until explicit file transfer.
 */
const DB_NAME = 'medatlas-companion-v1';
const DB_VERSION = 1;
const byId = id => document.getElementById(id);
const state = { view: 'all', selected: null, blobUrl: null, saveTimer: null };
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
function closeCurrent() {
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
    paper:'BLANK', darkPaper:false, handwritingFile:'',
    createdAt:now, updatedAt:now
  };
  await put('notes', note);
  await openNote(note.id);
}
async function openNote(id) {
  if (state.selected?.kind === 'note' && state.selected.id !== id)
    await saveSelectedNote();
  if (state.blobUrl) URL.revokeObjectURL(state.blobUrl);
  state.blobUrl = null;
  const note = await get('notes', id);
  if (!note) return toast('Notița nu mai există.');
  state.selected = { id, kind:'note' };
  byId('note-title').value = note.heading || '';
  byId('note-content').value = note.body || '';
  byId('note-status').textContent = 'Salvat local';
  byId('empty').hidden = true;
  byId('reader').hidden = true;
  byId('note-editor').hidden = false;
  renderList();
}
async function saveSelectedNote() {
  if (state.selected?.kind !== 'note') return;
  const id = state.selected.id;
  // Snapshot form values before awaiting IndexedDB: closing/switching notes
  // must never accidentally write another note's text into this note.
  const heading = byId('note-title').value.trim().slice(0,180) || 'Notiță fără titlu';
  const body = byId('note-content').value.slice(0,300000);
  const note = await get('notes', id);
  if (!note) return;
  note.heading = heading;
  note.body = body;
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
    paper:item.paper || 'BLANK', darkPaper:!!item.darkPaper
  })),null,2));
  toast(notes.length + ' notițe exportate în format MedAtlas.');
}
async function importNotes(file) {
  if (!file) return;
  if (file.size > 12 * 1024 * 1024) throw Error('Fișierul JSON este prea mare.');
  const raw = JSON.parse(await file.text());
  if (!Array.isArray(raw) || raw.length > 10000) throw Error('Fișierul nu este notes.json MedAtlas.');
  const known = new Map((await all('notes')).map(note => [note.id,note]));
  let imported = 0, conflicts = 0;
  for (const source of raw) {
    if (!source || typeof source !== 'object' || typeof source.body !== 'string') continue;
    let id = typeof source.id === 'string' && /^[a-zA-Z0-9_-]{1,120}$/.test(source.id)
      ? source.id : crypto.randomUUID();
    const existing = known.get(id);
    const valid = {
      id, documentId: 'medical-notebook', page: 0,
      heading: String(source.heading || 'Notiță importată').slice(0,180),
      body: source.body.slice(0,300000),
      course: String(source.course || 'General').slice(0,80),
      paper: String(source.paper || 'BLANK').slice(0,24),
      darkPaper:!!source.darkPaper,
      handwritingFile:'', // Image/ink files need a separate Android backup import.
      createdAt: Number(source.createdAt) || Date.now(),
      updatedAt: Number(source.updatedAt) || Date.now()
    };
    if (existing) {
      if (existing.body === valid.body && existing.heading === valid.heading) continue;
      // Never silently overwrite a newer or differently edited note.
      id = crypto.randomUUID();
      valid.id = id; valid.heading += ' (copie importată)';
      conflicts++;
    }
    await put('notes',valid);
    known.set(valid.id,valid);
    imported++;
  }
  await renderList();
  toast(imported + ' notițe importate.' + (conflicts ? ' ' + conflicts + ' copii create pentru conflicte.' : ''));
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
      xmlBytes = new Uint8Array(await new Response(stream).arrayBuffer());
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
  if (state.selected?.kind==='note') await saveSelectedNote();
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
