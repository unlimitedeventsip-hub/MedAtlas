import test from 'node:test';
import assert from 'node:assert/strict';
import {scanImage, normalizeCodes, normalizeText} from '../image-scan.mjs';

class FakeBarcode {
  static async getSupportedFormats() { return ['qr_code','ean_13']; }
  constructor({formats}) { assert.deepEqual(formats,['qr_code','ean_13']); }
  async detect() { return [
    {format:'qr_code',rawValue:'https://example.org/curs'},
    {format:'ean_13',rawValue:'5901234123457'}
  ]; }
}
class FakeText {
  async detect() { return [{rawValue:'Plămâni'}, {rawValue:'VEMS 65%'}]; }
}

test('browser-native image scanner extracts local text and QR without following links',async()=>{
  const image={width:300,height:400};
  const result=await scanImage(image,FakeBarcode,FakeText);
  assert.equal(result.codes.length,2);
  assert.equal(result.codes[0].value,'https://example.org/curs');
  assert.deepEqual(result.lines,['Plămâni','VEMS 65%']);
  assert.deepEqual(result.warnings,[]);
});

test('unsupported native APIs return explicit, safe limitations',async()=>{
  const result=await scanImage({},null,null);
  assert.deepEqual(result.codes,[]);
  assert.deepEqual(result.lines,[]);
  assert.equal(result.barcodeAvailable,false);
  assert.equal(result.textAvailable,false);
  assert.match(result.warnings.join(' '),/browser/);
});

test('malformed and excessively long raw values are normalized',()=>{
  assert.deepEqual(normalizeCodes([{format:'qr_code',rawValue:'  abc  '},{rawValue:''}]),
    [{format:'qr_code',value:'abc'}]);
  assert.equal(normalizeCodes([{rawValue:'X'.repeat(9000)}])[0].value.length,2000);
  assert.deepEqual(normalizeText([{rawValue:'  text  '},{text:' 123 '},null]),
    ['text','123']);
});

test('detector errors do not prevent other supported local detections',async()=>{
  class BrokenBarcode {static async getSupportedFormats(){return ['qr_code'];}
    async detect(){throw Error('unavailable');}}
  const result=await scanImage({},BrokenBarcode,FakeText);
  assert.deepEqual(result.lines,['Plămâni','VEMS 65%']);
  assert.equal(result.codes.length,0);
  assert.ok(result.warnings.length>0);
});
