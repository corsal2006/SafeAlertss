package com.siddesh.smsalerts.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.siddesh.smsalerts.mesh.MeshSecurity

/** All 256 bits remain visible and copyable. A short device identifier is not a verification value. */
@Composable
fun FingerprintCard(fingerprint: String, label: String = "Verification fingerprint") {
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { clipboard.setText(AnnotatedString(fingerprint)) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.ContentCopy, "Copy full fingerprint", Modifier.size(18.dp))
            }
        }
        SelectionContainer {
            Text(MeshSecurity.formatFingerprint(fingerprint), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        Text("Compare every group of characters with the other person's phone. A matching name or short device ID is not enough.",
            style = MaterialTheme.typography.bodySmall)
    }
}
