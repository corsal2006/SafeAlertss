const {test, before, beforeEach, after} = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const {initializeTestEnvironment, assertSucceeds, assertFails} = require('@firebase/rules-unit-testing');
const {doc, setDoc, getDoc, updateDoc, deleteDoc, serverTimestamp, Timestamp} = require('firebase/firestore');
let env;
before(async () => {
  if (!/^(127\.0\.0\.1|localhost):8080$/.test(process.env.FIRESTORE_EMULATOR_HOST || '')) throw Error('Local emulator required');
  env = await initializeTestEnvironment({projectId:'demo-safealerts', firestore:{host:'127.0.0.1',port:8080,rules:fs.readFileSync(path.join(__dirname,'../../firestore.rules'),'utf8')}});
});
beforeEach(async () => env.clearFirestore());
after(async () => { if (env) await env.cleanup(); });
const user = uid => env.authenticatedContext(uid).firestore();
async function invite() {
  await env.withSecurityRulesDisabled(async ctx => setDoc(doc(ctx.firestore(),'circle_relationships','ab'), {senderUid:'a',recipientUid:'b',participants:['a','b'],status:'PENDING',createdAt:serverTimestamp()}));
}
async function accepted() {
  await invite();
  await env.withSecurityRulesDisabled(async ctx => updateDoc(doc(ctx.firestore(),'circle_relationships','ab'), {status:'ACCEPTED'}));
}
async function grant(owner='a',peer='b',expiry=Date.now()+3600000) {
  await setDoc(doc(user(owner),`users/${owner}/circle_members/${peer}`),{relationId:'ab',relationshipCreatedAt:(await getDoc(doc(user(owner),'circle_relationships/ab'))).data().createdAt,shareEnabled:true,expiresAt:Timestamp.fromMillis(expiry)});
}
async function point(owner='a') {
  await setDoc(doc(user(owner),`users/${owner}/live_location/current`),{latitude:12,longitude:77,accuracy:10,timestamp:Date.now(),incidentId:'test',receivedAt:serverTimestamp()});
}
test('location metadata requires owner identity, valid coordinates and monotonic capture time', async () => {
  const db = user('a'), ref = doc(db, 'users/a/live_location/current'), now = Date.now();
  const fix = { ownerUid:'a', incidentId:'synthetic', sessionIds:['synthetic'], latitude:12, longitude:77,
    accuracy:8, timestamp:now, source:'fused', cached:false, receivedAt:serverTimestamp() };
  await assertSucceeds(setDoc(ref,fix));
  const {incidentId, ...withoutSession} = fix;
  await assertFails(setDoc(ref, withoutSession));
  for (const patch of [{ownerUid:'b'},{latitude:91},{longitude:-181},{accuracy:-1},{timestamp:now-1},
    {timestamp:now+300000},{cached:'false'},{sessionIds:Array(21).fill('synthetic')}]) {
    await assertFails(setDoc(ref,{...fix,...patch}));
  }
  await assertFails(setDoc(doc(user('b'),'users/a/live_location/current'),{...fix,timestamp:now+1}));
  await assertSucceeds(setDoc(ref,{...fix,timestamp:now+1,cached:true}));
});
test('private birth date and address remain owner-only even after acceptance and sharing',async () => {
  await accepted(); await grant();
  await setDoc(doc(user('a'),'users/a'),{dateOfBirth:'2000-01-01',homeAddress:'Synthetic address'});
  await assertSucceeds(getDoc(doc(user('a'),'users/a')));
  await assertFails(getDoc(doc(user('b'),'users/a')));
});
test('sender cannot accept its own invitation, impersonate a sender, or invite self',async () => {
  await invite();
  await assertFails(updateDoc(doc(user('a'),'circle_relationships/ab'),{status:'ACCEPTED'}));
  await assertFails(setDoc(doc(user('c'),'circle_relationships/forged'),{senderUid:'a',recipientUid:'c',participants:['a','c'],status:'PENDING',createdAt:serverTimestamp()}));
  await assertFails(setDoc(doc(user('a'),'circle_relationships/self'),{senderUid:'a',recipientUid:'a',participants:['a','a'],status:'PENDING',createdAt:serverTimestamp()}));
});
test('pending and fabricated membership do not allow access or consent grants',async () => {
  await invite(); await point();
  await assertFails(grant());
  await assertFails(getDoc(doc(user('b'),'users/a/live_location/current')));
  await assertFails(setDoc(doc(user('b'),'users/a/circle_members/b'),{relationId:'ab',shareEnabled:true,expiresAt:Timestamp.fromMillis(Date.now()+10000)}));
});
test('acceptance alone does not share; one-way and mutual sharing are independent',async () => {
  await accepted(); await point('a'); await point('b');
  await assertFails(getDoc(doc(user('b'),'users/a/live_location/current')));
  await grant('a','b');
  await assertSucceeds(getDoc(doc(user('b'),'users/a/live_location/current')));
  await assertFails(getDoc(doc(user('a'),'users/b/live_location/current')));
  await grant('b','a');
  await assertSucceeds(getDoc(doc(user('a'),'users/b/live_location/current')));
});
test('revocation, expiry and unrelated identities deny location reads',async () => {
  await accepted(); await grant(); await point();
  await assertFails(getDoc(doc(user('c'),'users/a/live_location/current')));
  await assertFails(updateDoc(doc(user('b'),'users/a/live_location/current'),{latitude:40}));
  await updateDoc(doc(user('a'),'users/a/circle_members/b'),{shareEnabled:false});
  await assertFails(getDoc(doc(user('b'),'users/a/live_location/current')));
  await grant('a','b',Date.now()-1000);
  await assertFails(getDoc(doc(user('b'),'users/a/live_location/current')));
  await grant();
  await env.withSecurityRulesDisabled(async ctx => updateDoc(doc(ctx.firestore(),'circle_relationships/ab'),{status:'REVOKED'}));
  await assertFails(getDoc(doc(user('b'),'users/a/live_location/current')));
});
test('peer cannot forge grant or change relationship identity',async () => {
  await accepted();
  await assertFails(updateDoc(doc(user('b'),'circle_relationships/ab'),{senderUid:'c',participants:['c','b']}));
  await assertFails(setDoc(doc(user('b'),'users/a/circle_members/b'),{relationId:'ab',shareEnabled:true,expiresAt:Timestamp.fromMillis(Date.now()+10000)}));
});
test('incident is readable only to current authorized peers; peer cannot resolve it',async () => {
  await accepted(); await grant();
  await setDoc(doc(user('a'),'incidents/i'),{ownerUid:'a',senderName:'Synthetic A',senderPhone:'',policeNumber:'',helplineNumber:'',category:'TEST',status:'ACTIVE',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
  await assertSucceeds(getDoc(doc(user('b'),'incidents/i')));
  await assertFails(getDoc(doc(user('c'),'incidents/i')));
  await assertFails(updateDoc(doc(user('b'),'incidents/i'),{status:'RESOLVED'}));
  await assertSucceeds(updateDoc(doc(user('a'),'incidents/i'),{status:'RESOLVED',updatedAt:serverTimestamp()}));
  await updateDoc(doc(user('a'),'users/a/circle_members/b'),{shareEnabled:false});
  await assertFails(getDoc(doc(user('b'),'incidents/i')));
});
test('public projection cannot include birth date or private contact data',async () => {
  await assertSucceeds(setDoc(doc(user('a'),'public_profiles/a'),{name:'Synthetic A',photoUrl:null}));
  await assertFails(setDoc(doc(user('a'),'public_profiles/a'),{name:'Synthetic A',dateOfBirth:'2000-01-01'}));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'public_profiles/a')));
});

test('codes, guessing budgets and relationship creation are backend only', async () => {
  for (const collection of ['circle_codes','circle_code_owners','circle_limits']) {
    await assertFails(setDoc(doc(user('a'),`${collection}/a`), {code:'23456789'}));
    await assertFails(getDoc(doc(user('a'),`${collection}/a`)));
  }
  await assertFails(setDoc(doc(user('a'),'circle_relationships/ab'), {senderUid:'a',recipientUid:'b',participants:['a','b'],status:'PENDING',createdAt:serverTimestamp()}));
  await invite();
  await assertFails(updateDoc(doc(user('b'),'circle_relationships/ab'), {status:'ACCEPTED'}));
});

test('resolved incidents cannot be reactivated and client timestamps cannot be forged', async () => {
  const ref = doc(user('a'),'incidents/terminal');
  const incident = {ownerUid:'a',senderName:'Synthetic',senderPhone:'',policeNumber:'',helplineNumber:'',category:'TEST',status:'ACTIVE',createdAt:serverTimestamp(),updatedAt:serverTimestamp()};
  await assertFails(setDoc(ref, {...incident, updatedAt:Timestamp.fromMillis(1)}));
  await assertSucceeds(setDoc(ref, incident));
  await assertSucceeds(updateDoc(ref, {status:'RESOLVED',updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(ref, {status:'ACTIVE',updatedAt:serverTimestamp()}));
});

test('only an owner can mark terminal history for deletion and delayed writes cannot resurrect it', async () => {
  const db = user('a'), id = 'delete-terminal';
  const ref = doc(db,`incidents/${id}`), marker = doc(db,`users/a/deleted_incidents/${id}`);
  const summary = {ownerUid:'a',senderName:'Synthetic',senderPhone:'',policeNumber:'',helplineNumber:'',category:'TEST',status:'ACTIVE',createdAt:serverTimestamp(),updatedAt:serverTimestamp()};
  await setDoc(ref,summary);
  await setDoc(doc(db,`users/a/alerts/${id}`),{status:'ACTIVE'});
  await setDoc(doc(db,`users/a/alerts/${id}/locations/minute_0`),{latitude:12,longitude:77});
  await assertFails(deleteDoc(ref));
  await assertFails(setDoc(marker,{deletedAt:serverTimestamp()}));
  await updateDoc(ref,{status:'RESOLVED',updatedAt:serverTimestamp()});
  await assertFails(setDoc(doc(user('b'),`users/a/deleted_incidents/${id}`),{deletedAt:serverTimestamp()}));
  await assertFails(setDoc(doc(user('b'),`users/b/deleted_incidents/${id}`),{deletedAt:serverTimestamp()}));
  await assertSucceeds(setDoc(marker,{deletedAt:serverTimestamp()}));
  await assertFails(deleteDoc(doc(user('b'),`incidents/${id}`)));
  await assertFails(updateDoc(marker,{deletedAt:serverTimestamp()}));
  await assertSucceeds(deleteDoc(doc(db,`users/a/alerts/${id}/locations/minute_0`)));
  await assertSucceeds(deleteDoc(doc(db,`users/a/alerts/${id}`)));
  await assertSucceeds(deleteDoc(ref));
  await assertFails(setDoc(ref,summary));
  await assertFails(setDoc(doc(db,`users/a/alerts/${id}`),{status:'ACTIVE'}));
  await assertFails(setDoc(doc(db,`users/a/emergencyAlerts/${id}`),{status:'ACTIVE'}));
  await assertFails(setDoc(doc(db,`users/a/alerts/${id}/locations/minute_0`),{latitude:12}));
  await assertFails(setDoc(doc(db,'users/a/live_location/current'),{latitude:12,longitude:77,timestamp:Date.now(),incidentId:id,receivedAt:serverTimestamp()}));
  await assertFails(deleteDoc(marker));
});

test('legacy history deletion requires terminal state and cannot delete active private records', async () => {
  const db = user('a'), path = 'users/a/alerts/legacy', marker = doc(db,'users/a/deleted_incidents/legacy');
  await setDoc(doc(db,path),{status:'EMERGENCY_ACTIVE'});
  await assertFails(deleteDoc(doc(db,path)));
  await assertFails(setDoc(marker,{deletedAt:serverTimestamp()}));
  await updateDoc(doc(db,path),{status:'Cancelled'});
  await assertSucceeds(setDoc(marker,{deletedAt:serverTimestamp()}));
  await assertSucceeds(deleteDoc(doc(db,path)));
});
