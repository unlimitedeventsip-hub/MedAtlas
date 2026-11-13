/* Portable MedAtlas handwriting schema: source-compatible with Stroke.kt.
 * Pure functions shared by the UI and the Node regression tests.
 */
export function safeStrokes(source) {
  if (source === null || source === undefined) return [];
  if (!Array.isArray(source)) throw Error('Traseele manuscrise trebuie să fie o listă.');
  if (source.length > 3000) throw Error('Prea multe trasee manuscrise.');
  const tools = new Set(['PEN','PENCIL','BRUSH','HIGHLIGHT','ERASER']);
  let total = 0;
  return source.map(input => {
    if (!input || typeof input !== 'object') throw Error('Traseu invalid.');
    if (!Array.isArray(input.points) || input.points.length > 10000)
      throw Error('Traseul depășește limita de puncte.');
    const points = input.points.map(p => {
      if (!Array.isArray(p) || p.length < 2 ||
          typeof p[0] !== 'number' || typeof p[1] !== 'number' ||
          (p[2] !== undefined && typeof p[2] !== 'number'))
        throw Error('Punct invalid.');
      const x=p[0], y=p[1], pressure=p[2] ?? 1;
      if (![x,y,pressure].every(Number.isFinite) || x < -.01 || x > 1.01 ||
        y < -.01 || y > 1.01 || pressure < 0 || pressure > 4)
        throw Error('Coordonate manuscrise invalide.');
      return [x,y,pressure];
    });
    total += points.length;
    if (total > 150000) throw Error('Notița depășește limita de scriere vectorială.');
    const width=Number(input.width);
    return {
      id:typeof input.id==='string' && /^[a-zA-Z0-9_-]{1,120}$/.test(input.id)
        ? input.id : crypto.randomUUID(),
      tool:tools.has(input.tool) ? input.tool : 'PEN',
      color:Number.isFinite(Number(input.color)) ? Number(input.color)|0 : (-14332541),
      width:Number.isFinite(width) && width >= .1 && width <= 100 ? width : 3,
      points
    };
  });
}
export function cssInkColor(argb) {
  return '#'+((argb >>> 0).toString(16).padStart(8,'0').slice(2));
}
export function inkArgb(hex) {
  return (0xff000000 | parseInt(hex.slice(1), 16)) | 0;
}
