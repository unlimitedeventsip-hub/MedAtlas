import test from 'node:test';
import assert from 'node:assert/strict';
import { reconcileNotes } from '../note-merge.mjs';

const note = (overrides={}) => ({
  id: 'note-123', documentId: 'medical-notebook', page:0,
  heading:'Anatomie',body:'Local',course:'General',
  paper:'BLANK',darkPaper:false,strokes:[],createdAt:100,updatedAt:200,
  ...overrides
});

test('reimporting an unchanged conflicting version does not create copies forever', async () => {
  const local=[note()];
  const remote=[note({body:'Laptop version'})];
  const first=await reconcileNotes(local,remote);
  assert.equal(first.additions.length,1);
  assert.equal(first.conflicts,1);
  assert.match(first.additions[0].id,/^conflict-[a-f0-9]{32}$/);
  const second=await reconcileNotes([...local,...first.additions],remote);
  assert.equal(second.additions.length,0);
  assert.equal(second.ignored,1);
});

test('paper style differences count as conflicting versions', async () => {
  const result=await reconcileNotes([note()],[note({paper:'GRID'})]);
  assert.equal(result.conflicts,1);
  assert.equal(result.additions[0].paper,'GRID');
});

test('explicit erasure in remote ink becomes a separate version', async () => {
  const original=[note({strokes:[{
    id:'stroke-1',tool:'PEN',width:2,color:-16777216,
    points:[[.1,.2,1],[.5,.6,.5]]
  }]})];
  const changed=[note({strokes:[]})];
  const result=await reconcileNotes(original,changed);
  assert.equal(result.conflicts,1);
  assert.deepEqual(original[0].strokes[0].points,[[.1,.2,1],[.5,.6,.5]]);
  assert.deepEqual(result.additions[0].strokes,[]);
});

test('legacy note without ink field preserves existing pen strokes', async () => {
  const withInk=note({strokes:[{
    id:'s1',tool:'PENCIL',width:3,color:-1,points:[[.2,.2,.4]]
  }]});
  const legacy=note();
  delete legacy.strokes;
  const result=await reconcileNotes([withInk],[legacy]);
  assert.equal(result.ignored,1);
  assert.equal(result.additions.length,0);
});

test('a malformed late entry rejects the entire merge before any writes', async () => {
  const local=[note()];
  const malformed=[note({id:'new-valid'}),{id:'unsafe',heading:'Invalid',body:42}];
  await assert.rejects(reconcileNotes(local,malformed),/invalidă/);
  assert.equal(local.length,1);
});

test('null and string coordinates are rejected rather than silently altered', async () => {
  for (const point of [[null,.2,1],['.1',.2,1]]) {
    const source=[note({strokes:[{
      id:'bad',tool:'PEN',width:3,color:-1,points:[point]
    }]})];
    await assert.rejects(reconcileNotes([],source),/Punct invalid/);
  }
});
