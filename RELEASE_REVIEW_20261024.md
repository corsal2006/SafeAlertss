# SafeAlerts release verification — 24 October 2026

The user approved deploying the tested rules/index and implementing owner-confirmed terminal-incident deletion. Both are implemented; the rules/index deployment succeeded.

## Production release

Project: smselerts. Package: com.siddesh.smsalerts.

- Command: firebase deploy --only firestore:rules,firestore:indexes --project smselerts --non-interactive
- Deployment log: build/repair-deploy-20261024.log
- Released ruleset: projects/smselerts/rulesets/db4aaba0-a088-4acc-a492-3b7230533618
- Exact local/remote SHA-256 match: 014224c3ef968376c1fc6c370e7fb565fe43596f20cf167b12f84fda074d6dd8
- Read-back at 2026-10-24 07:54:18 UTC confirmed rulesMatch=true and billingEnabled=false.
- Index: incidents(ownerUid ASC, status ASC), READY at 2026-10-24 07:59:34 UTC; see build/repair-index-state.json.
- No Functions, billing upgrade, TTL, paid API or production data deletion was performed.

Read-back evidence: build/spark-deployment-verification.json and build/repair-deployed-indexes.json. The pre-release snapshot remains in build/repair-live-firestore.rules.

## Released behavior

1. Validated owner-only location metadata: ownerUid, source, cached, sessionIds, accurate timestamp constraints and monotonic latest fixes.
2. Private groups, membership profiles and immutable messages; expiring single-use invitations; key rotation on removal and protection against self-enrolment.
3. Owner-confirmed deletion of resolved/cancelled incident data, including location children and matching latest location. Immutable private opaque deletion IDs/times prevent delayed restoration; they contain no coordinates, profile or message.
4. The incident query index above.

The existing directional-consent incident-read boundary is unchanged. Membership alone does not grant location access.

## Deletion safety and compatibility

History explains that deletion is permanent, cannot recall recipient copies and retains an opaque ID/time to prevent resurrection. Active incidents and another account's incidents cannot be deleted through this operation. Child location documents are deleted in bounded batches before their parent. Cloud failures/timeouts are reported as potentially partial; the same record can be retried. Local journal/outbox cleanup follows cloud confirmation.

Automatic approval review initially blocked this extension because of permanent deletion. The user then explicitly approved it. A later proposal to accept new location writes without incidentId was rejected because it could bypass deletion protection; that exception was not applied. New writes must contain an incident/session ID. Existing clients omitting it need the updated APK.

No global purge or account-wide deletion is included. Group history, expired invitation records and opaque deletion IDs need authorized maintenance; there is no automatic retention cleanup.

## Validation and limits

- 33 Node/Firebase emulator tests pass, including deletion ownership, active-state rejection, resurrection prevention, legacy terminal cleanup, invitation replay, timestamp precision, group rotation/removal and private access.
- The phone passed the deletion flow, repeated deletion after completion, and resuming a partially marked deletion.
- APK builds and final physical results are in REPAIR_20261024.md.
- All application tests used isolated QA and local Firebase synthetic accounts. The production release is verified through rules/index/billing read-back, not a live multi-user SOS test.
- Two-phone BLE packet/ACK, three-phone relay, interrupted transfers and actual carrier SMS remain unverified. The APK is debug-signed.
