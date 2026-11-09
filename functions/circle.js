const {randomInt, createHash} = require('node:crypto');
const {FieldValue, Timestamp} = require('firebase-admin/firestore');
const {HttpsError} = require('firebase-functions/v2/https');

const ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
const TTL_MS = 15 * 60 * 1000;
const hash = text => createHash('sha256').update(text, 'utf8').digest('hex');
const normalizeCode = value => typeof value === 'string' ? value.replace(/\s/g, '').toUpperCase() : '';
const newCode = () => Array.from({length: 8}, () => ALPHABET[randomInt(ALPHABET.length)]).join('');
const validCode = value => value.length === 8 && [...value].every(c => ALPHABET.includes(c));

function handlers(db) {
  function uid(request) {
    if (!request.auth || request.auth.token?.firebase?.sign_in_provider === 'anonymous') {
      throw new HttpsError('unauthenticated', 'Sign in to use Trusted Circle.');
    }
    return request.auth.uid;
  }
  // Account and network budgets are independent of successful code lookup. Invalid guesses count.
  async function rateLimit(request, action, limit) {
    const user = uid(request);
    const now = Date.now();
    const refs = [db.doc(`circle_limits/${hash(`${action}:user:${user}`)}`)];
    if (request.rawRequest?.ip) refs.push(db.doc(`circle_limits/${hash(`${action}:ip:${request.rawRequest.ip}`)}`));
    await db.runTransaction(async tx => {
      const snapshots = await Promise.all(refs.map(ref => tx.get(ref)));
      const counts = snapshots.map(s => {
        const p = s.data();
        return p && now - p.start < TTL_MS ? {start: p.start, count: p.count} : {start: now, count: 0};
      });
      if (counts.some((p, i) => p.count >= (i === 0 ? limit : limit * 5))) {
        throw new HttpsError('resource-exhausted', 'Too many attempts. Wait 15 minutes before trying again.');
      }
      refs.forEach((ref, i) => tx.set(ref, {...counts[i], count: counts[i].count + 1, expiresAt: Timestamp.fromMillis(now + TTL_MS)}));
    });
  }
  async function issue(request) {
    const owner = uid(request);
    const ref = db.doc(`circle_code_owners/${owner}`);
    if (request.data?.action === 'get') {
      const current = (await ref.get()).data();
      return current && !current.revoked && current.expiresAt.toMillis() > Date.now()
        ? {code: current.code, expiresAt: current.expiresAt.toMillis()} : {code: '', expiresAt: 0};
    }
    const revoke = request.data?.action === 'revoke';
    if (!revoke && request.data?.action !== 'generate') throw new HttpsError('invalid-argument', 'Choose generate or revoke.');
    await rateLimit(request, 'issue', 10);
    for (let attempt = 0; attempt < 8; attempt++) {
      const code = newCode();
      const codeRef = db.doc(`circle_codes/${hash(code)}`);
      const result = await db.runTransaction(async tx => {
        const ownerDoc = (await tx.get(ref)).data();
        const collision = revoke ? false : (await tx.get(codeRef)).exists;
        if (collision) return null;
        if (ownerDoc?.code) tx.set(db.doc(`circle_codes/${hash(ownerDoc.code)}`), {revoked: true}, {merge: true});
        if (revoke) {
          tx.set(ref, {revoked: true}, {merge: true});
          return {code: '', expiresAt: 0};
        }
        const expiresAt = Date.now() + TTL_MS;
        tx.create(codeRef, {ownerUid: owner, expiresAt: Timestamp.fromMillis(expiresAt), revoked: false, consumedBy: null});
        tx.set(ref, {code, expiresAt: Timestamp.fromMillis(expiresAt), revoked: false});
        return {code, expiresAt};
      });
      if (result) return result;
    }
    throw new HttpsError('unavailable', 'Could not reserve a code. Try again.');
  }
  async function redeem(request) {
    const recipient = uid(request);
    await rateLimit(request, 'redeem', 5);
    const code = normalizeCode(request.data?.code);
    const unavailable = () => new HttpsError('not-found', 'Code invalid, expired, revoked or already used.');
    if (!validCode(code)) throw unavailable();
    return db.runTransaction(async tx => {
      const codeRef = db.doc(`circle_codes/${hash(code)}`);
      const invitation = (await tx.get(codeRef)).data();
      if (!invitation || invitation.revoked || invitation.expiresAt.toMillis() <= Date.now() ||
          (invitation.consumedBy && invitation.consumedBy !== recipient)) throw unavailable();
      const owner = invitation.ownerUid;
      if (owner === recipient) throw new HttpsError('failed-precondition', 'Use a code from another person.');
      const relationRef = db.doc(`circle_relationships/${hash([owner, recipient].sort().join(':'))}`);
      const existing = (await tx.get(relationRef)).data();
      const profile = (await tx.get(db.doc(`public_profiles/${owner}`))).data();
      if (existing?.status === 'ACCEPTED') throw new HttpsError('already-exists', 'You are already connected.');
      // Retrying the same redemption is idempotent. An old/rejected invite needs a regenerated code.
      if (invitation.consumedBy === recipient) {
        if (existing?.status === 'PENDING' && existing.codeHash === hash(code)) return {relationId: relationRef.id, senderName: existing.senderName};
        throw unavailable();
      }
      tx.set(relationRef, {senderUid: owner, recipientUid: recipient, participants: [owner, recipient],
        senderName: (profile?.name || '').slice(0, 100), status: 'PENDING', codeHash: hash(code),
        expiresAt: invitation.expiresAt, createdAt: FieldValue.serverTimestamp()});
      tx.update(codeRef, {consumedBy: recipient});
      tx.set(db.doc(`circle_code_owners/${owner}`), {revoked: true}, {merge: true});
      return {relationId: relationRef.id, senderName: (profile?.name || '').slice(0, 100)};
    });
  }
  async function respond(request) {
    const user = uid(request);
    const {relationId, action} = request.data || {};
    if (typeof relationId !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(relationId) ||
        !['ACCEPTED', 'REJECTED', 'REVOKED'].includes(action)) throw new HttpsError('invalid-argument', 'Invalid response.');
    return db.runTransaction(async tx => {
      const ref = db.doc(`circle_relationships/${relationId}`);
      const relation = (await tx.get(ref)).data();
      if (!relation?.participants?.includes(user)) throw new HttpsError('permission-denied', 'Invitation unavailable.');
      if (relation.status === action) return {status: action};
      if (action === 'REVOKED') {
        if (!['PENDING', 'ACCEPTED'].includes(relation.status)) throw new HttpsError('failed-precondition', 'Relationship already ended.');
      } else {
        if (relation.recipientUid !== user || relation.status !== 'PENDING') throw new HttpsError('permission-denied', 'Only the invited user can respond.');
        const code = (await tx.get(db.doc(`circle_codes/${relation.codeHash}`))).data();
        if (!code || code.revoked || relation.expiresAt.toMillis() <= Date.now()) throw new HttpsError('failed-precondition', 'Invitation expired or revoked. Ask for a new code.');
      }
      tx.update(ref, {status: action});
      // Reaccepting a regenerated relationship must never restore old directional grants.
      if (action === 'REVOKED' || action === 'ACCEPTED') {
        tx.delete(db.doc(`users/${relation.senderUid}/circle_members/${relation.recipientUid}`));
        tx.delete(db.doc(`users/${relation.recipientUid}/circle_members/${relation.senderUid}`));
      }
      return {status: action};
    });
  }
  return {issue, redeem, respond};
}

module.exports = {handlers, normalizeCode, validCode, newCode, TTL_MS};
