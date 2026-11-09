const {test,before,beforeEach,after}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {createHash}=require('node:crypto');
const {initializeTestEnvironment,assertFails,assertSucceeds}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,updateDoc,deleteDoc,runTransaction,writeBatch,serverTimestamp,Timestamp}=require('firebase/firestore');
let env;
before(async()=>{
  assert.match(process.env.FIRESTORE_EMULATOR_HOST||'',/^(127\.0\.0\.1|localhost):8080$/);
  env=await initializeTestEnvironment({projectId:'demo-safealerts-spark',firestore:{host:'127.0.0.1',port:8080,rules:fs.readFileSync(path.join(__dirname,'../../firestore.rules'),'utf8')}});
});
beforeEach(async()=>env.clearFirestore());
after(async()=>env?.cleanup());
const user=uid=>env.authenticatedContext(uid,{firebase:{sign_in_provider:'password'}}).firestore();
const hash=x=>createHash('sha256').update(x).digest('hex');
const pair=(a,b)=>hash([a,b].sort().join(':'));
async function issue(uid,code='ABCD2345') {
 const db=user(uid), h=hash(code), owner=doc(db,`spark_circle_code_owners/${uid}`);
 await runTransaction(db,async tx=>{
  const old=await tx.get(owner), count=(old.data()?.count||0)+1;
  tx.set(owner,{code,codeHash:h,issuedAt:serverTimestamp(),windowStart:old.data()?.windowStart||serverTimestamp(),count});
  tx.set(doc(db,`spark_circle_codes/${h}`),{ownerUid:uid,createdAt:serverTimestamp(),revoked:false,usedBy:''});
 });
 return h;
}
async function ticket(uid,h) {
 const db=user(uid),ref=doc(db,`spark_circle_lookups/${uid}`);
 return runTransaction(db,async tx=>{
  const old=await tx.get(ref);
  tx.set(ref,{codeHash:h,requestedAt:serverTimestamp(),windowStart:old.data()?.windowStart||serverTimestamp(),count:(old.data()?.count||0)+1});
 });
}
async function redeem(uid,h,withTicket=true){
 if(withTicket)await ticket(uid,h);
 const db=user(uid),ref=doc(db,`spark_circle_codes/${h}`);const c=(await getDoc(ref)).data();
 const id=pair(c.ownerUid,uid),rel=doc(db,`circle_relationships/${id}`);
 await runTransaction(db,async tx=>{
  const fresh=await tx.get(ref);await tx.get(rel);
  tx.set(rel,{senderUid:c.ownerUid,recipientUid:uid,participants:[c.ownerUid,uid],status:'PENDING',codeHash:h,
   createdAt:serverTimestamp(),updatedAt:serverTimestamp(),expiresAt:Timestamp.fromMillis(fresh.data().createdAt.toMillis()+900000)});
  tx.update(ref,{usedBy:uid});
 });
 return id;
}
const respond=(uid,id,status)=>updateDoc(doc(user(uid),`circle_relationships/${id}`),{status,updatedAt:serverTimestamp()});
async function grant(uid,peer,id,version){
 const r=await getDoc(doc(user(uid),`circle_relationships/${id}`));
 return setDoc(doc(user(uid),`users/${uid}/circle_members/${peer}`),{relationId:id,relationshipCreatedAt:version||r.data().createdAt,shareEnabled:true,expiresAt:Timestamp.fromMillis(Date.now()+3600000)});
}
const point=uid=>setDoc(doc(user(uid),`users/${uid}/live_location/current`),{latitude:12,longitude:77,incidentId:'circle',timestamp:Date.now(),receivedAt:serverTimestamp()});
test('Spark transaction: code -> pending -> recipient acceptance -> explicit directional grant',async()=>{
 const h=await issue('a');await assertSucceeds(getDoc(doc(user('a'),`spark_circle_codes/${h}`)));
 const id=await redeem('b',h);
 await assertFails(respond('a',id,'ACCEPTED'));
 await assertFails(getDoc(doc(user('c'),`circle_relationships/${id}`)));
 await assertSucceeds(respond('b',id,'ACCEPTED'));
 await point('a');await point('b');
 await assertFails(getDoc(doc(user('b'),'users/a/live_location/current')));
 await grant('a','b',id);
 await assertSucceeds(getDoc(doc(user('b'),'users/a/live_location/current')));
 await assertFails(getDoc(doc(user('a'),'users/b/live_location/current')));
 await assertFails(setDoc(doc(user('b'),'users/a/circle_members/b'),{shareEnabled:true}));
});
test('incident upsert transaction can read absence; existing incidents remain private',async()=>{
 const db=user('a'),ref=doc(db,'incidents/transactional');
 await assertSucceeds(runTransaction(db,async tx=>{
  assert.equal((await tx.get(ref)).exists(),false);
  tx.set(ref,{ownerUid:'a',senderName:'Synthetic A',senderPhone:'',policeNumber:'',helplineNumber:'',category:'TEST',
   status:'ACTIVE',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
 }));
 await assertFails(getDoc(doc(user('c'),'incidents/transactional')));
 await assertFails(getDocs(collection(user('c'),'incidents')));
});
test('no code listing, raw access, anonymous issuance, wrong-hash ticket, or legacy private code access',async()=>{
 const h=await issue('a');
 await assertFails(getDocs(collection(user('a'),'spark_circle_codes')));
 await assertFails(getDoc(doc(user('b'),`spark_circle_codes/${h}`)));
 await ticket('b',hash('XXXX2345'));
 await assertFails(getDoc(doc(user('b'),`spark_circle_codes/${h}`)));
 await assertFails(getDoc(doc(user('b'),'spark_circle_code_owners/a')));
 await assertFails(getDoc(doc(user('a'),'circle_codes/private')));
 const anon=env.authenticatedContext('anon',{firebase:{sign_in_provider:'anonymous'}}).firestore();
 await assertFails(setDoc(doc(anon,'spark_circle_lookups/anon'),{codeHash:h,requestedAt:serverTimestamp(),windowStart:serverTimestamp(),count:1}));
});
test('five guesses are counted; counter cannot reset, delete, roll back, or change another account',async()=>{
 for(let i=0;i<5;i++)await ticket('b',hash(String(i)));
 await assertFails(ticket('b',hash('sixth')));
 await assertFails(deleteDoc(doc(user('b'),'spark_circle_lookups/b')));
 await assertFails(updateDoc(doc(user('b'),'spark_circle_lookups/b'),{count:1,windowStart:serverTimestamp(),requestedAt:serverTimestamp()}));
 await assertFails(updateDoc(doc(user('c'),'spark_circle_lookups/b'),{count:0}));
});
test('regeneration, revocation and expiry deny redemption/acceptance, codes are single-use',async()=>{
 const first=await issue('a');await issue('a','BCDE2345');await assertFails(redeem('b',first));
 const h=hash('BCDE2345'),id=await redeem('b',h);
 await assertFails(redeem('c',h));
 await updateDoc(doc(user('a'),`spark_circle_codes/${h}`),{revoked:true});
 await assertFails(respond('b',id,'ACCEPTED'));
 await assertFails(updateDoc(doc(user('a'),`spark_circle_codes/${h}`),{revoked:false}));
 const expired=await issue('a','CDEF2345');
 await env.withSecurityRulesDisabled(ctx=>updateDoc(doc(ctx.firestore(),`spark_circle_codes/${expired}`),{createdAt:Timestamp.fromMillis(Date.now()-1000000)}));
 await assertFails(redeem('c',expired));
});
test('forged sender, non-atomic consumption, future expiry and self-join are rejected',async()=>{
 const h=await issue('a');await ticket('b',h);
 await assertFails(updateDoc(doc(user('b'),`spark_circle_codes/${h}`),{usedBy:'b'}));
 const db=user('b'),batch=writeBatch(db),c=(await getDoc(doc(db,`spark_circle_codes/${h}`))).data();
 batch.set(doc(db,`circle_relationships/${pair('c','b')}`),{senderUid:'c',recipientUid:'b',participants:['c','b'],status:'PENDING',codeHash:h,createdAt:serverTimestamp(),updatedAt:serverTimestamp(),expiresAt:Timestamp.fromMillis(c.createdAt.toMillis()+900000)});
 batch.update(doc(db,`spark_circle_codes/${h}`),{usedBy:'b'});await assertFails(batch.commit());
 await assertFails(redeem('a',h));
});
test('remove and reaccept cannot resurrect old grants; private profile remains private',async()=>{
 let h=await issue('a'),id=await redeem('b',h);await respond('b',id,'ACCEPTED');
 const original=(await getDoc(doc(user('a'),`circle_relationships/${id}`))).data().createdAt;
 await grant('a','b',id);await point('a');
 await respond('b',id,'REVOKED');await assertFails(getDoc(doc(user('b'),'users/a/live_location/current')));
 h=await issue('a','BCDE2345');await redeem('b',h);await respond('b',id,'ACCEPTED');
 await assertFails(getDoc(doc(user('b'),'users/a/live_location/current')));
 await assertFails(grant('a','b',id,original));await grant('a','b',id);
 await assertSucceeds(getDoc(doc(user('b'),'users/a/live_location/current')));
 await setDoc(doc(user('a'),'users/a'),{dateOfBirth:'2000-01-01',homeAddress:'Synthetic'});
 await assertFails(getDoc(doc(user('b'),'users/a')));
});
test('issuance budget and immutable code contents cannot be bypassed',async()=>{
 const alphabet='ABCDEFGHJKLM';
 for(let i=0;i<10;i++)await issue('a',alphabet[i]+'BCD2345');
 await assertFails(issue('a','MBCD2345'));
 await assertFails(deleteDoc(doc(user('a'),'spark_circle_code_owners/a')));
 await assertFails(updateDoc(doc(user('a'),`spark_circle_codes/${hash('ABCD2345')}`),{createdAt:serverTimestamp()}));
});
