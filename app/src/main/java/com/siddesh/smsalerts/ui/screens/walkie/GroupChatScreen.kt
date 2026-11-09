package com.siddesh.smsalerts.ui.screens.walkie

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.siddesh.smsalerts.data.repository.*
import com.siddesh.smsalerts.mesh.*
import com.siddesh.smsalerts.service.GroupSyncWorker
import com.siddesh.smsalerts.ui.components.SafeAlertTopBar
import com.siddesh.smsalerts.walkie.WalkieTalkieManager
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

@Composable
fun GroupChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val repo = remember { GroupRepository(context) }
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val walkie = remember { WalkieTalkieManager.getInstance(context) }
    val nearbyMessages by walkie.messages.collectAsState()
    var groups by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var selected by rememberSaveable { mutableStateOf(MeshSecurity.cloudGroupId(context)) }
    var group by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var members by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var recent by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var older by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    var local by remember { mutableStateOf<List<QueuedGroupMessage>>(emptyList()) }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var invitation by remember { mutableStateOf<DocumentSnapshot?>(null) }
    var inviter by remember { mutableStateOf("") }
    var issued by remember { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { withTimeout(20_000) { block() } }
            catch (e: TimeoutCancellationException) { status = "Server confirmation timed out. Reconnect and refresh before retrying." }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = if (e is FirebaseFirestoreException) "Group operation failed (${e.code}). Check connectivity, membership and deployed rules." else e.message ?: "Group operation failed." }
            finally { busy = false }
        }
    }
    DisposableEffect(uid) {
        val listener = uid.takeIf(String::isNotBlank)?.let {
            repo.db.collection("groups").whereArrayContains("memberUids", uid).limit(32).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) { status = "Group list unavailable (${error.code})."; groups = emptyList() }
                else { groups = snapshot?.documents.orEmpty(); offline = snapshot?.metadata?.isFromCache == true }
            }
        }
        onDispose { listener?.remove() }
    }
    DisposableEffect(selected, uid) {
        group = null; members = emptyList(); recent = emptyList(); older = emptyList(); local = emptyList()
        val listeners = mutableListOf<ListenerRegistration>()
        if (selected.isNotBlank()) {
            val id = selected
            listeners += repo.db.document("groups/$id").addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
                if (error != null || doc?.exists() != true) { group = null; status = "This group is unavailable or access was removed." }
                else { group = doc; offline = doc.metadata.isFromCache }
            }
            listeners += repo.db.collection("groups/$id/members").addSnapshotListener { snapshot, error ->
                members = if (error == null) snapshot?.documents.orEmpty() else emptyList()
            }
            listeners += repo.db.collection("groups/$id/messages").orderBy("createdAt", Query.Direction.DESCENDING).limit(40)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) status = "Message history unavailable (${error.code})."
                    else recent = snapshot?.documents.orEmpty()
                }
        }
        onDispose { listeners.forEach { it.remove() } }
    }
    LaunchedEffect(selected, uid) {
        if (selected.isBlank() || uid.isBlank()) return@LaunchedEffect
        GroupSyncWorker.enqueue(context, uid, selected)
        while (isActive) {
            try { local = withContext(Dispatchers.IO) { GroupMessageOutbox(context, uid, selected).read() } }
            catch (e: Exception) { status = "Saved outgoing messages could not be read. Check device storage." }
            delay(1500)
        }
    }
    Scaffold(topBar = { SafeAlertTopBar("Groups & chat", true, onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Group membership and cloud history use your signed-in account. Nearby transfers use the same group's private channel.")
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (offline) "Offline/cached: no new server confirmation" else "Online changes require server confirmation", style = MaterialTheme.typography.bodySmall)
            }
            items(groups, key = { "group-${it.id}" }) { doc ->
                OutlinedButton(onClick = { selected = doc.id; issued = "" }, Modifier.fillMaxWidth()) { Text(doc.getString("name").orEmpty()) }
            }
            if (selected.isBlank() || group == null) {
                item {
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text("New group name") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { action { selected = repo.create(name); status = "Group created." } }, enabled = !busy && name.isNotBlank()) { Text("Create group") }
                    OutlinedTextField(code, { code = it.filterNot(Char::isWhitespace).take(32) }, label = { Text("32-character group invitation") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { action {
                        val preview = repo.preview(code)
                        inviter = repo.db.document("public_profiles/${preview.getString("ownerUid")}").get(Source.SERVER).await().getString("name").orEmpty().ifBlank { "Name not provided" }
                        invitation = preview
                    } }, enabled = !busy && code.length == 32) { Text("Check invitation") }
                    invitation?.let { preview ->
                        Text("${preview.getString("groupName")} · Invited by $inviter. Accept to join; this does not enable location sharing.")
                        Button(onClick = { action { selected = repo.accept(preview); invitation = null; status = "Membership confirmed." } }, enabled = !busy) { Text("Accept invitation") }
                        TextButton(onClick = { invitation = null; code = "" }) { Text("Decline") }
                    }
                }
            } else {
                item {
                    Text(group?.getString("name").orEmpty(), style = MaterialTheme.typography.titleLarge)
                    val active = MeshSecurity.cloudGroupId(context) == selected
                    Button(onClick = { action { repo.activate(group!!); status = "Group selected. Start Nearby for Bluetooth discovery." } }, enabled = !busy && !active) { Text(if (active) "Current nearby group" else "Use for nearby chat") }
                    Text("${(group?.get("memberUids") as? List<*>)?.size ?: 0} accepted members. Membership does not mean a radio connection.")
                    if (group?.getString("ownerUid") == uid) {
                        Button(onClick = { action { issued = repo.invite(selected); status = "Single-use invitation expires in 15 minutes." } }, enabled = !busy) { Text("Create invitation") }
                        if (issued.isNotBlank()) {
                            Text(issued)
                            TextButton(onClick = { clipboard.setText(AnnotatedString(issued)) }) { Text("Copy invitation") }
                            TextButton(onClick = { action { repo.revokeInvitation(selected); issued = ""; status = "Invitation revoked." } }) { Text("Revoke invitation") }
                        }
                    } else OutlinedButton(onClick = { action { repo.remove(selected, uid); selected = ""; status = "You left the group." } }, enabled = !busy) { Text("Leave group") }
                    TextButton(onClick = { selected = ""; issued = "" }) { Text("Create or join another group") }
                }
                items(members.filter { it.id in (group?.get("memberUids") as? List<*>).orEmpty() }, key = { "member-${it.id}" }) { member ->
                    Row {
                        Text(member.getString("name").orEmpty().ifBlank { "Name not provided" }, Modifier.weight(1f))
                        if (group?.getString("ownerUid") == uid && member.id != uid) TextButton(onClick = { action { repo.remove(selected, member.id); status = "Member removed and nearby key rotated. Offline copies cannot be recalled." } }, enabled = !busy) { Text("Remove") }
                    }
                }
                item {
                    OutlinedTextField(message, { if (it.toByteArray().size <= 1000) message = it }, label = { Text("Group message (up to 1000 bytes)") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { action {
                        if (MeshSecurity.cloudGroupId(context) != selected) repo.activate(group!!)
                        walkie.selectPeer(null)
                        withContext(Dispatchers.IO) { walkie.sendTextMessage(message) }
                        message = ""; status = "Message saved. Cloud sync and nearby receipt are separate outcomes."
                    } }, enabled = !busy && message.isNotBlank() && group?.getBoolean("needsRotation") == false) { Text("Send to group") }
                    TextButton(onClick = { GroupSyncWorker.enqueue(context, uid, selected); status = "Cloud retry queued." }) { Text("Retry cloud synchronization") }
                    OutlinedButton(onClick = { action {
                        val cursor = (recent + older).minByOrNull { it.getTimestamp("createdAt")?.seconds ?: Long.MAX_VALUE }
                        if (cursor != null) older = (older + repo.db.collection("groups/$selected/messages").orderBy("createdAt", Query.Direction.DESCENDING).startAfter(cursor).limit(40).get().await().documents).distinctBy { it.id }
                    } }, enabled = !busy && recent.isNotEmpty()) { Text("Load older messages") }
                }
                val remote = (recent + older).distinctBy { it.id }.sortedBy { it.getTimestamp("createdAt") }
                if (remote.isEmpty() && local.isEmpty() && nearbyMessages.isEmpty()) item { Text("No messages yet. Queued messages will appear here with their actual status.") }
                items(remote, key = { "message-${it.id}" }) { doc ->
                    val sender = members.firstOrNull { it.id == doc.getString("senderUid") }?.getString("name").orEmpty().ifBlank { "Name not available" }
                    Text("$sender: ${doc.getString("text").orEmpty()}\n${if (doc.metadata.isFromCache) "Cached cloud message" else "Cloud confirmed"}", style = MaterialTheme.typography.bodyMedium)
                }
                items(local.filter { queued -> remote.none { it.id == queued.id } }, key = { "queued-${it.id}" }) { queued ->
                    val peerStatus = nearbyMessages.firstOrNull { it.id == queued.id }?.deliveryStatus ?: "No peer acknowledgement"
                    Text("You: ${queued.text}\n${queued.status} · Nearby: $peerStatus")
                }
                if (MeshSecurity.cloudGroupId(context) == selected) items(nearbyMessages.filter { m -> !m.isOutgoing && remote.none { it.id == m.id } }, key = { "nearby-${it.id}" }) { m ->
                    Text("${m.senderName}: ${m.text}\nReceived over nearby transport · cloud sync unconfirmed")
                }
                item { Text("Leaving/removal blocks future cloud access. Key rotation blocks future nearby exchanges with updated members; disconnected phones and saved copies cannot be remotely erased.", style = MaterialTheme.typography.bodySmall) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
