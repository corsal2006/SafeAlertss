const {initializeApp} = require('firebase-admin/app');
const {getFirestore, FieldValue, Timestamp} = require('firebase-admin/firestore');
const {getMessaging} = require('firebase-admin/messaging');
const {onDocumentCreated} = require('firebase-functions/v2/firestore');
const {onCall, HttpsError} = require('firebase-functions/v2/https');
const {defineSecret} = require('firebase-functions/params');
initializeApp();
const db = getFirestore();
const routesKey = defineSecret('ROUTES_API_KEY');
const circle = require('./circle').handlers(db);
exports.circleCode = onCall({maxInstances: 5, timeoutSeconds: 15}, circle.issue);
exports.joinCircle = onCall({maxInstances: 5, timeoutSeconds: 15}, circle.redeem);
exports.respondCircle = onCall({maxInstances: 5, timeoutSeconds: 15}, circle.respond);

// FCM acceptance is audited; it is not proof a recipient saw or heard the notification.
exports.notifyEmergency = onDocumentCreated({document: 'incidents/{incidentId}', retry: true}, async event => {
  const incident = event.data?.data();
  if (!incident || incident.status !== 'ACTIVE') return;
  const owner = incident.ownerUid;
  const grants = await db.collection(`users/${owner}/circle_members`).where('shareEnabled', '==', true).get();
  for (const grantDoc of grants.docs) {
    const grant = grantDoc.data();
    if (!grant.expiresAt || grant.expiresAt.toMillis() <= Date.now()) continue;
    const relationship = (await db.doc(`circle_relationships/${grant.relationId}`).get()).data();
    if (relationship?.status !== 'ACCEPTED' || !relationship.participants.includes(owner) || !relationship.participants.includes(grantDoc.id)) continue;
    const recipient = grantDoc.id;
    const ledger = db.doc(`notification_dispatches/${event.params.incidentId}_${recipient}`);
    // Reserve before external submission. On a process interruption, an uncertain send is not repeated.
    const reserved = await db.runTransaction(async tx => {
      if ((await tx.get(ledger)).exists) return false;
      tx.create(ledger, {state: 'SUBMISSION_PENDING', createdAt: FieldValue.serverTimestamp()});
      return true;
    });
    if (!reserved) continue;
    const tokens = await db.collection(`users/${recipient}/fcm_tokens`).limit(20).get();
    const tokenDocs = tokens.docs.filter(d => typeof d.data().token === 'string');
    if (!tokenDocs.length) { await ledger.update({state: 'NO_DEVICE_TOKEN'}); continue; }
    // Recheck revocation and resolution immediately before submission.
    const [currentGrant, currentIncident, currentRelation] = await Promise.all([grantDoc.ref.get(), event.data.ref.get(), db.doc(`circle_relationships/${grant.relationId}`).get()]);
    if (currentGrant.data()?.shareEnabled !== true || currentGrant.data().expiresAt.toMillis() <= Date.now() || currentIncident.data()?.status !== 'ACTIVE' || currentRelation.data()?.status !== 'ACCEPTED') {
      await ledger.update({state: 'REVOKED_OR_RESOLVED'}); continue;
    }
    try {
      const response = await getMessaging().sendEachForMulticast({
        tokens: tokenDocs.map(d => d.data().token),
        data: {incidentId: event.params.incidentId, recipientUid: recipient},
        android: {priority: 'high', ttl: 5 * 60 * 1000, collapseKey: event.params.incidentId}
      });
      await ledger.update({state: 'FCM_RESPONDED', accepted: response.successCount, failed: response.failureCount,
        outcomes: response.responses.map(r => r.success ? 'ACCEPTED_BY_FCM' : (r.error?.code || 'FAILED')), updatedAt: FieldValue.serverTimestamp()});
      await Promise.all(response.responses.map((r,i) => r.error?.code === 'messaging/registration-token-not-registered' ? tokenDocs[i].ref.delete() : Promise.resolve()));
    } catch (_) { await ledger.update({state: 'SUBMISSION_UNKNOWN', updatedAt: FieldValue.serverTimestamp()}); }
  }
});

exports.journeyRoute = onCall({secrets: [routesKey], timeoutSeconds: 15, maxInstances: 5}, async request => {
  if (!request.auth) throw new HttpsError('unauthenticated', 'Sign in to request a route.');
  const {origin, destination} = request.data || {};
  const valid = p => p && Number.isFinite(p.latitude) && Number.isFinite(p.longitude) && Math.abs(p.latitude) <= 90 && Math.abs(p.longitude) <= 180;
  if (!valid(origin) || !valid(destination)) throw new HttpsError('invalid-argument', 'Real origin and destination coordinates are required.');
  const budget = db.doc(`route_limits/${request.auth.uid}`);
  await db.runTransaction(async tx => {
    const previous = (await tx.get(budget)).data();
    if (previous?.at?.toMillis() > Date.now() - 10000) throw new HttpsError('resource-exhausted', 'Wait before refreshing the route.');
    tx.set(budget, {at: Timestamp.now()});
  });
  const response = await fetch('https://routes.googleapis.com/directions/v2:computeRoutes', {
    method: 'POST', signal: AbortSignal.timeout(8000),
    headers: {'Content-Type':'application/json', 'X-Goog-Api-Key':routesKey.value(), 'X-Goog-FieldMask':'routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline'},
    body: JSON.stringify({origin:{location:{latLng:origin}}, destination:{location:{latLng:destination}}, travelMode:'DRIVE', routingPreference:'TRAFFIC_AWARE'})
  });
  if (!response.ok) throw new HttpsError('unavailable', 'Route service unavailable. Your safety timer can still be set manually.');
  const route = (await response.json()).routes?.[0];
  if (!route) throw new HttpsError('not-found', 'No driving route available.');
  return {polyline:route.polyline.encodedPolyline, durationSeconds:Number.parseFloat(route.duration), distanceMeters:route.distanceMeters};
});
