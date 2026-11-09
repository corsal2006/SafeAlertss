const {test, before, beforeEach, after} = require('node:test');
const assert = require('node:assert/strict');
const {initializeApp, deleteApp} = require('firebase-admin/app');
const {getFirestore, Timestamp} = require('firebase-admin/firestore');
const {createHash} = require('node:crypto');
const {handlers, normalizeCode, validCode, TTL_MS} = require('../circle');
let app, db, circle;
const req = (uid, data) => ({auth: uid ? {uid, token:{firebase:{sign_in_provider:'password'}}} : null, data});
const codeRef = code => db.doc(`circle_codes/${createHash('sha256').update(code).digest('hex')}`);
before(() => {
  assert.match(process.env.FIRESTORE_EMULATOR_HOST || '', /^(127\.0\.0\.1|localhost):8080$/);
  app = initializeApp({projectId:'demo-safealerts-circle'}, 'circle-tests');
  db = getFirestore(app); circle = handlers(db);
});
beforeEach(async () => {
  for (const collection of await db.listCollections()) await db.recursiveDelete(collection);
});
after(async () => { if (app) await deleteApp(app); });
test('normalization accepts spaces and case, rejects ambiguous input', () => {
  assert.equal(normalizeCode(' abcd 2345 '), 'ABCD2345');
  assert.equal(validCode('ABCD2345'), true);
  for (const bad of ['abcd2345', 'ABCD0123', 'ABCI2345', '', '123']) assert.equal(validCode(bad), false);
});
test('authenticated owner issues eight-character expiring codes, regenerated and revoked codes fail', async () => {
  await assert.rejects(circle.issue(req(null,{action:'generate'})), {code:'unauthenticated'});
  const first = await circle.issue(req('a',{action:'generate'}));
  assert.equal(validCode(first.code), true);
  assert.ok(first.expiresAt > Date.now() && first.expiresAt <= Date.now()+TTL_MS);
  assert.equal((await circle.issue(req('b',{action:'get'}))).code, '');
  const next = await circle.issue(req('a',{action:'generate'}));
  assert.notEqual(first.code, next.code);
  await assert.rejects(circle.redeem(req('b',{code:first.code})), {code:'not-found'});
  await circle.issue(req('a',{action:'revoke'}));
  await assert.rejects(circle.redeem(req('b',{code:next.code})), {code:'not-found'});
});
test('redemption is single-use and idempotent; only recipient can accept; no location grants appear', async () => {
  const code = await circle.issue(req('a',{action:'generate'}));
  const join = await circle.redeem(req('b',{code:code.code.toLowerCase().split('').join(' ')}));
  assert.equal((await circle.redeem(req('b',{code:code.code}))).relationId, join.relationId);
  await assert.rejects(circle.redeem(req('c',{code:code.code})), {code:'not-found'});
  await assert.rejects(circle.respond(req('a',{relationId:join.relationId,action:'ACCEPTED'})), {code:'permission-denied'});
  await circle.respond(req('b',{relationId:join.relationId,action:'ACCEPTED'}));
  assert.equal((await db.doc(`circle_relationships/${join.relationId}`).get()).data().status,'ACCEPTED');
  assert.equal((await db.doc('users/a/circle_members/b').get()).exists,false);
  assert.equal((await db.doc('users/b/circle_members/a').get()).exists,false);
});
test('expiry and revocation block pending acceptance and self-join is rejected', async () => {
  const code = await circle.issue(req('a',{action:'generate'}));
  await assert.rejects(circle.redeem(req('a',{code:code.code})), {code:'failed-precondition'});
  const join = await circle.redeem(req('b',{code:code.code}));
  await circle.issue(req('a',{action:'revoke'}));
  await assert.rejects(circle.respond(req('b',{relationId:join.relationId,action:'ACCEPTED'})), {code:'failed-precondition'});
  const expired = await circle.issue(req('a',{action:'generate'}));
  await codeRef(expired.code).update({expiresAt:Timestamp.fromMillis(Date.now()-1)});
  await assert.rejects(circle.redeem(req('b',{code:expired.code})), {code:'not-found'});
});
test('concurrent guesses consume a transactional budget including invalid attempts', async () => {
  const attempts = await Promise.allSettled(Array.from({length:12}, () => circle.redeem(req('guessing-account',{code:'INVALID'}))));
  assert.equal(attempts.filter(r => r.reason?.code === 'not-found').length,5);
  assert.equal(attempts.filter(r => r.reason?.code === 'resource-exhausted').length,7);
});
test('revocation and reacceptance never resurrect previous directional sharing', async () => {
  const first = await circle.issue(req('a',{action:'generate'}));
  const join = await circle.redeem(req('b',{code:first.code}));
  await circle.respond(req('b',{relationId:join.relationId,action:'ACCEPTED'}));
  await db.doc('users/a/circle_members/b').set({shareEnabled:true});
  await circle.respond(req('b',{relationId:join.relationId,action:'REVOKED'}));
  assert.equal((await db.doc('users/a/circle_members/b').get()).exists,false);
  const next = await circle.issue(req('a',{action:'generate'}));
  await circle.redeem(req('b',{code:next.code}));
  await circle.respond(req('b',{relationId:join.relationId,action:'ACCEPTED'}));
  assert.equal((await db.doc('users/a/circle_members/b').get()).exists,false);
});
