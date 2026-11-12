/** MedAtlas AI backend for Cloudflare Workers. Never expose OPENAI_API_KEY to Android or GitHub. */
const MODES = new Set(['explain', 'summary', 'quiz', 'free', 'web', 'highlights', 'sticker', 'patient', 'case_feedback']);
const JSON_HEADERS = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' };
const reply = (data, code = 200) => new Response(JSON.stringify(data), { status: code, headers: JSON_HEADERS });
const extractAnswer = (r) => (r.output ?? []).flatMap(item => item.content ?? []).filter(p => p.type === 'output_text').map(p => p.text).join('\n').trim();
const extractSources = (r) => {
  const sources = (r.output ?? []).flatMap(item => item.content ?? []).flatMap(c => c.annotations ?? [])
    .filter(a => a.type === 'url_citation' && typeof a.url === 'string')
    .map(a => ({title:String(a.title ?? 'Sursă online'), url:a.url}));
  return [...new Map(sources.map(s => [s.url,s])).values()].slice(0, 8);
};

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/health' && request.method === 'GET') return reply({ok:true, aiConfigured:Boolean(env.OPENAI_API_KEY)});
    if (url.pathname !== '/ai' || request.method !== 'POST') return reply({error:'Not found'}, 404);
    if (!env.OPENAI_API_KEY || !env.MEDATLAS_APP_TOKEN) return reply({error:'Secretelor backend nu sunt configurate.'}, 503);
    const authorization = request.headers.get('Authorization') ?? '';
    if (authorization !== `Bearer ${env.MEDATLAS_APP_TOKEN}`) return reply({error:'Acces neautorizat.'}, 401);
    if (Number(request.headers.get('content-length') || 0) > 5_500_000) return reply({error:'Selecția este prea mare.'}, 413);
    let body;
    try { body = await request.json(); } catch { return reply({error:'JSON invalid.'}, 400); }
    const mode = String(body.mode ?? '');
    if (!MODES.has(mode)) return reply({error:'Operație necunoscută.'}, 400);
    const text = String(body.text ?? '').slice(0, 60000);
    const document = String(body.document ?? '').slice(0, 160);
    const page = Number.isSafeInteger(body.page) ? body.page + 1 : 'necunoscută';
    const image = body.image_b64 && /^[A-Za-z0-9+/=]+$/.test(body.image_b64) && body.image_b64.length < 4_500_000 ? body.image_b64 : null;
    if (!text.trim() && !image) return reply({error:'Lipsește textul sau imaginea selectată.'}, 400);

    const coreRules = `Ești Nadia AI, tutor medical pentru studiu universitar, nu pentru decizii clinice. Răspunde în limba română.\n` +
      `Document-sursă: ${document}; pagina: ${page}. Separă informația DIN FRAGMENT de cunoștințele suplimentare.\n` +
      `Nu inventa citări, pagini, surse sau date medicale; indică incertitudinile și limitele OCR.\n` +
      `Explică structurat folosind scheme logice, exemple și întrebări de examen, fără a face recomandări pentru un pacient real.`;
    const tasks = {
      patient: "Joacă rolul pacientului fictiv. Răspunde în română la persoana întâi, pe scurt și numai pe baza anamnezei date. Dacă o informație lipsește, spune că nu știi. Nu dezvălui diagnosticul sau tratamentul.",
      case_feedback: "Evaluează fișa studentului față de baremul fictiv. Identifică un argument susținut, o lacună și o întrebare de reevaluare. Nu prescrie pentru persoane reale.",
      explain: 'Explică logic selecția, mecanismul, semnificația, o mnemonică și 3 idei pentru examen.',
      summary: 'Fă un rezumat fidel, cu definiții, clasificări, praguri, diagnostice, tratamente și idei-cheie. Nu completa lacunele din fragment cu presupuneri.',
      quiz: 'Generează 8 grile medicale cu patru variante A–D, un singur răspuns corect, explicații, capcane și precizarea fragmentului-sursă.',
      free: 'Generează 5 întrebări cu răspuns liber și un barem de evaluare separat. Include, unde se potrivește, cazuri clinice.',
      highlights: 'Selectează cel mult 8 rânduri ESENȚIALE pentru examen DIN TEXTUL OCR, cu fraze EXACTE copiate din fragment, fără reformulare. Scrie FIECARE rând pe linie proprie cu prefixul MARK: . Nu include comentarii sau alt text.',
      web: 'Explică tema și compară fragmentul cu recomandări externe, citând explicit sursele și datele lor. Diferențiază clar cursul de completările online.'
    };
    try {
      if (mode === 'sticker') {
        let contextFromImage = '';
        if (image) {
          const vision = await fetch('https://api.openai.com/v1/responses', {
            method: 'POST', headers: {'Authorization': `Bearer ${env.OPENAI_API_KEY}`, 'Content-Type':'application/json'},
            body: JSON.stringify({ model: env.TEXT_MODEL || 'gpt-4.1-mini', store: false, max_output_tokens: 250,
              instructions: 'Descrie concis în limba română structurile vizibile în imaginea de curs. Nu ghici etichete ilizibile sau identificări incerte.',
              input: [{role:'user', content:[{type:'input_text', text:'Descrie conceptele și anatomia pentru a construi un desen explicativ, fără informații inventate.'}, {type:'input_image', image_url:`data:image/jpeg;base64,${image}`}]}]
            })
          });
          if (vision.ok) contextFromImage = extractAnswer(await vision.json()).slice(0, 900);
        }
        const prompt = `Creează o diagramă medicală educațională ORIGINALĂ, precisă, stil atlas medical minimalist, fundal alb simplu, elemente anatomice clare, etichete foarte puține; nu inventa anatomie. Tema: ${text.slice(0, 900)}. ${contextFromImage ? 'Conținut al selecției vizuale: '+contextFromImage : 'Nu există o descriere verificată a imaginii.'}`;
        const result = await fetch('https://api.openai.com/v1/images/generations', { method:'POST', headers:{'Authorization': `Bearer ${env.OPENAI_API_KEY}`, 'Content-Type':'application/json'}, body:JSON.stringify({model:env.IMAGE_MODEL || 'gpt-image-2.5-flare', prompt, size:'1024x1024', n:1}) });
        const parsed = await result.json();
        if (!result.ok) return reply({error:parsed.error?.message || 'Generarea imaginii a eșuat.'}, 502);
        const b64 = parsed.data?.[0]?.b64_json;
        return b64 ? reply({image_b64:b64, warning:'Verifică etichetele și anatomia imaginii generate AI înainte de utilizare.'}) : reply({error:'Nu s-a primit imaginea în format compatibil.'}, 502);
      }
      const content = [{type:'input_text',text:`${tasks[mode]}\n\nFragmentul din document:\n${text}`}];
      if (image) content.push({type:'input_image',image_url:`data:image/jpeg;base64,${image}`, detail:'auto'});
      const payload = {
        model: env.TEXT_MODEL || 'gpt-4.1-mini',
        instructions: coreRules,
        input:[{role:'user',content}],
        max_output_tokens: mode === 'quiz' ? 2200 : 1700,
        store:false
      };
      if (mode === 'web') payload.tools = [{type:'web_search'}];
      const upstream = await fetch('https://api.openai.com/v1/responses',{method:'POST',headers:{'Authorization':`Bearer ${env.OPENAI_API_KEY}`,'Content-Type':'application/json'},body:JSON.stringify(payload)});
      const parsed = await upstream.json();
      if (!upstream.ok) return reply({error:parsed.error?.message || `OpenAI HTTP ${upstream.status}`}, 502);
      const answer = extractAnswer(parsed);
      const sources = mode === 'web' ? extractSources(parsed) : [];
      const withSources = sources.length ? `${answer}\n\nSurse consultate:\n${sources.map(s => `• ${s.title}: ${s.url}`).join('\n')}` : answer;
      return answer ? reply({answer:withSources, page, document, sources}) : reply({error:'Răspuns AI gol.'}, 502);
    } catch (error) { return reply({error:'Conexiune AI indisponibilă: ' + String(error.message || error)}, 502); }
  }
};
