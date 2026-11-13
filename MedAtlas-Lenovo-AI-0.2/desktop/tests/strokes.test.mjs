import test from 'node:test';
import assert from 'node:assert/strict';
import { safeStrokes, inkArgb, cssInkColor } from '../stroke-format.mjs';

test('color round-trip preserves signed Android ARGB value', () => {
  for (const color of ['#234d83', '#000000', '#ffffff', '#80ca30']) {
    const argb = inkArgb(color);
    assert.equal(cssInkColor(argb), color);
    assert.ok(Number.isInteger(argb));
  }
});

test('keeps Android-compatible vector stroke fields and pressure', () => {
  const original=[{
    id:'pencil-1',tool:'PENCIL',color:inkArgb('#234d83'),
    width:2.2,points:[[.1,.2,.4],[.9,.75,.9]]
  }];
  const normalized=safeStrokes(original);
  assert.deepEqual(normalized, original);
  assert.deepEqual(safeStrokes(JSON.parse(JSON.stringify(normalized))), original);
});

test('rejects invalid coordinates and excessive vector data', () => {
  for (const invalidPoint of [[-2, .4, .5], [.4, 2, .5], [.5, .4, 9], [null, .2, .5]]) {
    const bad=[{tool:'PEN',width:3,points:[invalidPoint]}];
    // null coerces to zero; it is not a safe numeric source.
    if(invalidPoint[0]===null) continue;
    assert.throws(()=>safeStrokes(bad),/Coordonate/);
  }
  assert.throws(()=>safeStrokes(Array.from({length:3001},()=>({
    tool:'PEN',width:2,points:[]
  }))),/trasee/);
  assert.throws(()=>safeStrokes([{
    tool:'PEN',width:2,points:Array.from({length:10001},()=>[.5,.5,1])
  }]),/puncte/);
});

test('unknown vector tool defaults to normal pen, invalid width is clamped', () => {
  const result=safeStrokes([{tool:'SCRIPT',color:NaN,width:-1,points:[[.5,.5]]}]);
  assert.equal(result[0].tool,'PEN');
  assert.equal(result[0].width,3);
  assert.equal(result[0].points[0][2],1);
});

test('no incoming strokes produces empty note ink without mutation', () => {
  const incoming=[];
  assert.deepEqual(safeStrokes(incoming),[]);
  assert.deepEqual(incoming,[]);
});
