import assert from 'node:assert/strict';
import worker from './worker.mjs';
const env = {OPENAI_API_KEY:'test-key', MEDATLAS_APP_TOKEN:'test-token', TEXT_MODEL:'gpt-4.1-mini', IMAGE_MODEL:'gpt-image-2.5-flare'};
let upstream = [];
const previousFetch = globalThis.fetch;
globalThis.fetch = async (url, init) => {
  upstream.push({url, init});
  if (url.endsWith('/responses')) return new Response(JSON.stringify({output:[{content:[{type:'output_text', text:'Exemplu de explicație clară în română.', annotations:[{type:'url_citation', title:'Ghid', url:'https://example.org/guide'}]}]}]}), {status:200, headers:{'Content-Type':'application/json'}});
  if (url.endsWith('/images/generations')) return new Response(JSON.stringify({data:[{b64_json:'aGVsbG8='}]}), {status:200, headers:{'Content-Type':'application/json'}});
  return new Response('{}',{status:404});
};
const request = (mode, text='Astmul bronșic') => new Request('https://example.workers.dev/ai',{method:'POST',headers:{'Authorization':'Bearer test-token', 'Content-Type':'application/json'},body:JSON.stringify({mode,text,document:'Astmul bronșic.pdf',page:4})});
try {
  const health = await worker.fetch(new Request('https://example.workers.dev/health'),env);
  assert.equal((await health.json()).ok,true);
  const unauthorized = await worker.fetch(new Request('https://example.workers.dev/ai',{method:'POST',headers:{'Content-Type':'application/json'},body:'{}'}),env);
  assert.equal(unauthorized.status,401);
  const result = await worker.fetch(request('summary'),env);
  assert.equal(result.status,200);
  assert.match((await result.json()).answer,/explicație clară/);
  const web = await worker.fetch(request('web'),env);
  const webJson = await web.json();
  assert.match(webJson.answer,/https:\/\/example.org\/guide/);
  assert.equal(webJson.sources.length,1);
  const highlights = await worker.fetch(request('highlights'),env);
  assert.equal(highlights.status,200);
  const sticker = await worker.fetch(request('sticker'),env);
  assert.equal((await sticker.json()).image_b64,'aGVsbG8=');
  assert.equal(upstream.length,4);
  console.log('MedAtlas Worker: 6 scenarii de bază trecute.');
} finally {globalThis.fetch=previousFetch;}
