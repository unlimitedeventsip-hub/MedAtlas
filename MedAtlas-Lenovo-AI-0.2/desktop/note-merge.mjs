import { safeStrokes } from './stroke-format.mjs';

// Explicitly keep cloud/data reconciliation separate from the UI. This file
// never deletes or overwrites an existing note, including its vector drawing.
const ID = /^[A-Za-z0-9_-]{1,120}$/;
const MAX_NOTES = 10000;
const MAX_BODY = 300000;

function cleanIncoming(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value) ||
      typeof value.body !== 'string' || value.body.length > MAX_BODY)
    throw new Error('Notiță JSON invalidă sau prea lungă.');
  const now = Date.now();
  const inkProvided = Object.hasOwn(value, 'strokes');
  if (inkProvided && !Array.isArray(value.strokes))
    throw new Error('Trasee manuscrise invalide.');
  const paper = ['BLANK','LINES','GRID','DOTS','MUSIC'].includes(value.paper)
    ? value.paper : 'BLANK';
  const note = {
    id: typeof value.id === 'string' && ID.test(value.id)
      ? value.id : crypto.randomUUID(),
    documentId: 'medical-notebook',
    page: 0,
    heading: String(value.heading || 'Notiță importată').slice(0,180),
    body: value.body,
    course: String(value.course || 'General').slice(0,80),
    paper, darkPaper: !!value.darkPaper,
    handwritingFile: '',
    createdAt: Number(value.createdAt) || now,
    updatedAt: Number(value.updatedAt) || now,
    strokes: safeStrokes(value.strokes || [])
  };
  return { note, inkProvided };
}

function sameContent(left, right, checkInk) {
  if (!left || !right) return false;
  if (left.heading !== right.heading ||
      left.body !== right.body ||
      left.course !== right.course ||
      left.paper !== right.paper ||
      !!left.darkPaper !== !!right.darkPaper) return false;
  return !checkInk || JSON.stringify(safeStrokes(left.strokes || [])) ===
    JSON.stringify(safeStrokes(right.strokes || []));
}

async function stableConflictId(note, inkProvided) {
  // Re-importing unchanged source data must produce the same ID. Use a digest
  // rather than a random ID, avoiding endless "(copie importată)" duplicates.
  const key = [
    note.id,note.heading,note.body,note.course,note.paper,
    String(note.darkPaper),String(inkProvided),
    JSON.stringify(inkProvided ? note.strokes : [])
  ].join('\u0000');
  const digest = await crypto.subtle.digest('SHA-256',new TextEncoder().encode(key));
  const signature = [...new Uint8Array(digest).slice(0,16)]
    .map(v=>v.toString(16).padStart(2,'0')).join('');
  return 'conflict-'+signature;
}

/**
 * Pure merge plan, no database writes. The caller validates the complete JSON
 * file first and then applies additions in one IndexedDB transaction.
 */
export async function reconcileNotes(current, incoming) {
  if (!Array.isArray(current) || !Array.isArray(incoming) ||
      incoming.length > MAX_NOTES) throw new Error('Fișierul nu este notes.json MedAtlas.');
  const cleaned = incoming.map(cleanIncoming);
  const existing = new Map(current.map(n=>[n.id,n]));
  const additions=[];
  let conflicts=0,ignored=0;
  for (const {note,inkProvided} of cleaned) {
    const previous=existing.get(note.id);
    if (!previous) {
      additions.push(note);
      existing.set(note.id,note);
      continue;
    }
    if (sameContent(previous,note,inkProvided)) {
      ignored++;
      continue;
    }
    const conflictHeading = note.heading.slice(0,154)+' (copie importată)';
    const stableId = await stableConflictId(note,inkProvided);
    const previousCopy = existing.get(stableId);
    const candidate = {...note,id:stableId,heading:conflictHeading};
    if (previousCopy && sameContent(previousCopy,candidate,inkProvided)) {
      ignored++;
      continue;
    }
    // Digest collision or an unrelated note already using the same ID.
    if (previousCopy) candidate.id = crypto.randomUUID();
    additions.push(candidate);
    existing.set(candidate.id,candidate);
    conflicts++;
  }
  return {additions,conflicts,ignored};
}
