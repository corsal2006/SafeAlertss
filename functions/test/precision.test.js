const {test,before,after}=require('node:test');
const fs=require('node:fs');
const path=require('node:path');
const {createHash}=require('node:crypto');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,runTransaction,serverTimestamp,Timestamp}=require('firebase/firestore');
let env;
before(async()=>{
  if (!/^(127\.0\.0\.1|localhost):8080$/.test(process.env.FIRESTORE_EMULATOR_HOST||'')) throw Error('Local emulator required');
  env=await initializeTestEnvironment({projectId:'demo-safealerts-precision',firestore:{host:'127.0.0.1',port:8080,rules:fs.readFileSync(path.join(__dirname,'../../firestore.rules'),'utf8')}});
  await env.clearFirestore();
});
after(async()=>env?.cleanup());
const hash=x=>createHash('sha256').update(x).digest('hex');
test('production microsecond invitation expiry survives Android timestamp arithmetic',async()=>{
  const h=hash('ABCD2345'), id=hash('alice:bob');
  const created=new Timestamp(Math.floor(Date.now()/1000)-10,123456000);
  await env.withSecurityRulesDisabled(async ctx=>{
    await setDoc(doc(ctx.firestore(),`spark_circle_codes/${h}`),{ownerUid:'alice',createdAt:created,revoked:false,usedBy:''});
    await setDoc(doc(ctx.firestore(),'spark_circle_code_owners/alice'),{codeHash:h});
  });
  const db=env.authenticatedContext('bob',{firebase:{sign_in_provider:'password'}}).firestore();
  await setDoc(doc(db,'spark_circle_lookups/bob'),{codeHash:h,requestedAt:serverTimestamp(),windowStart:serverTimestamp(),count:1});
  const redeem=expiry=>runTransaction(db,async tx=>{
    await tx.get(doc(db,`spark_circle_codes/${h}`)); await tx.get(doc(db,`circle_relationships/${id}`));
    tx.set(doc(db,`circle_relationships/${id}`),{senderUid:'alice',recipientUid:'bob',participants:['alice','bob'],status:'PENDING',codeHash:h,createdAt:serverTimestamp(),updatedAt:serverTimestamp(),expiresAt:expiry});
    tx.update(doc(db,`spark_circle_codes/${h}`),{usedBy:'bob'});
  });
  // This reproduces the existing Android java.util.Date conversion, which truncates microseconds.
  await assertFails(redeem(Timestamp.fromMillis(Math.floor(created.toMillis())+900000)));
  await assertSucceeds(redeem(new Timestamp(created.seconds+900,created.nanoseconds)));
});
