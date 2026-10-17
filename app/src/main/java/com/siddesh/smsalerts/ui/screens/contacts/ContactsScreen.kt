package com.siddesh.smsalerts.ui.screens.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.siddesh.smsalerts.data.model.EmergencyContact
import com.siddesh.smsalerts.ui.components.GlassCard
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.StatusError
import com.siddesh.smsalerts.ui.theme.StatusSuccess
import com.siddesh.smsalerts.ui.theme.SurfaceCard
import com.siddesh.smsalerts.ui.theme.SurfaceCardElevated
import com.siddesh.smsalerts.ui.theme.TextMuted
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary
import com.siddesh.smsalerts.ui.viewmodel.ContactsViewModel

@Composable
fun ContactsScreen(
    contactsViewModel: ContactsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val contacts by contactsViewModel.contacts.collectAsState()
    val isLoading by contactsViewModel.isLoading.collectAsState()
    val errorMessage by contactsViewModel.errorMessage.collectAsState()
    val successMessage by contactsViewModel.successMessage.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var contactToEdit by remember { mutableStateOf<EmergencyContact?>(null) }
    var contactToDelete by remember { mutableStateOf<EmergencyContact?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = EmergencyRed,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Trusted Contacts",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "These contacts receive your GPS position and alert via SMS.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!errorMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusError.copy(alpha = 0.12f))
                        .border(1.dp, StatusError.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(text = errorMessage.orEmpty(), fontSize = 13.sp, color = StatusError)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!successMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusSuccess.copy(alpha = 0.12f))
                        .border(1.dp, StatusSuccess.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(text = successMessage.orEmpty(), fontSize = 13.sp, color = StatusSuccess)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isLoading && contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EmergencyRed)
                }
            } else if (contacts.isEmpty()) {
                EmptyContactsView(onAddContactClick = { showAddDialog = true })
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(contacts, key = { it.id }) { contact ->
                        ContactItemCard(
                            contact = contact,
                            onToggleActive = { contactsViewModel.toggleContactActive(contact) },
                            onCall = {
                                val callIntent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${contact.phoneNumber}")
                                }
                                context.startActivity(callIntent)
                            },
                            onEdit = { contactToEdit = contact },
                            onDelete = { contactToDelete = contact }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ContactFormDialog(
            title = "Add Trusted Contact",
            confirmButtonText = "ADD CONTACT",
            initialContact = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, phone, rel, enabled ->
                contactsViewModel.addContact(
                    name = name,
                    phoneNumber = phone,
                    relationship = rel,
                    enabled = enabled,
                    onSuccess = { showAddDialog = false }
                )
            }
        )
    }

    if (contactToEdit != null) {
        ContactFormDialog(
            title = "Edit Trusted Contact",
            confirmButtonText = "SAVE CHANGES",
            initialContact = contactToEdit,
            onDismiss = { contactToEdit = null },
            onConfirm = { name, phone, rel, enabled ->
                contactToEdit?.let { existing ->
                    contactsViewModel.updateContact(
                        contact = existing.copy(
                            name = name,
                            phoneNumber = phone,
                            relationship = rel,
                            enabled = enabled,
                            isActive = enabled
                        ),
                        onSuccess = { contactToEdit = null }
                    )
                }
            }
        )
    }

    if (contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("Delete Contact", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("Remove \"${contactToDelete?.name}\" from emergency contacts? They will no longer receive emergency alerts.") },
            confirmButton = {
                Button(
                    onClick = {
                        contactToDelete?.let { contactsViewModel.deleteContact(it.id) }
                        contactToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("DELETE")
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = SurfaceCardElevated
        )
    }
}

@Composable
private fun ContactItemCard(
    contact: EmergencyContact,
    onToggleActive: () -> Unit,
    onCall: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // Top Section: Avatar + Contact Info on left + Enabled Switch on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar Circle
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (contact.enabled) EmergencyRed.copy(alpha = 0.15f) else SurfaceCardElevated)
                        .border(1.dp, if (contact.enabled) EmergencyRed.copy(alpha = 0.4f) else CardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.name.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = if (contact.enabled) EmergencyRed else TextMuted
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Name, Phone, and Relationship with proper text wrapping
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (contact.enabled) TextPrimary else TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = contact.phoneNumber,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = contact.relationship,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (contact.enabled) EmergencyRed else TextMuted
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Enabled Status Switch (Never collides with phone number)
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Switch(
                        checked = contact.enabled,
                        onCheckedChange = { onToggleActive() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmergencyRed,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = SurfaceCardElevated
                        )
                    )
                    Text(
                        text = if (contact.enabled) "Enabled" else "Disabled",
                        fontSize = 11.sp,
                        color = if (contact.enabled) StatusSuccess else TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                color = CardBorder.copy(alpha = 0.4f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Bottom Section: Accessible Action Buttons (📞 Call, ✏️ Edit, 🗑️ Delete) with >= 48dp touch targets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call Action Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCall)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call ${contact.name}",
                        tint = StatusSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Call",
                        fontSize = 12.sp,
                        color = StatusSuccess,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Edit Action Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit ${contact.name}",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Edit",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Delete Action Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete ${contact.name}",
                        tint = StatusError,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Delete",
                        fontSize = 12.sp,
                        color = StatusError,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyContactsView(onAddContactClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(SurfaceCard)
                .border(1.dp, CardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.People,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No emergency contacts configured",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "SafeAlert requires at least one verified emergency contact to broadcast SMS notifications.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddContactClick,
            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("ADD EMERGENCY CONTACT")
        }
    }
}

@Composable
private fun ContactFormDialog(
    title: String,
    confirmButtonText: String,
    initialContact: EmergencyContact?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, relationship: String, enabled: Boolean) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialContact?.name ?: "") }
    var phone by remember { mutableStateOf(initialContact?.phoneNumber ?: "") }
    var relationship by remember { mutableStateOf(initialContact?.relationship ?: "Emergency Contact") }
    var isEnabled by remember { mutableStateOf(initialContact?.enabled ?: true) }
    var formError by remember { mutableStateOf<String?>(null) }
    var formInfo by remember { mutableStateOf<String?>(null) }

    val multiplePhones = remember { mutableStateListOf<String>() }
    var showMultiplePhonesDialog by remember { mutableStateOf(false) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        if (contactUri == null) return@rememberLauncherForActivityResult

        try {
            var pickedDisplayName = ""
            var contactId: String? = null
            val retrievedNumbers = mutableListOf<String>()

            // 1. Query display name and contact ID
            context.contentResolver.query(
                contactUri,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (idIdx != -1) contactId = cursor.getString(idIdx)
                    if (nameIdx != -1) pickedDisplayName = cursor.getString(nameIdx).orEmpty()
                }
            }

            // 2. Query phone numbers directly via CommonDataKinds.Phone
            if (!contactId.isNullOrBlank()) {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(contactId),
                    null
                )?.use { phoneCursor ->
                    val numIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    while (phoneCursor.moveToNext()) {
                        if (numIdx != -1) {
                            val rawNum = phoneCursor.getString(numIdx)?.trim()
                            if (!rawNum.isNullOrBlank()) {
                                val normalized = com.siddesh.smsalerts.utils.ValidationUtils.normalizePhoneNumber(rawNum)
                                if (!retrievedNumbers.contains(normalized)) {
                                    retrievedNumbers.add(normalized)
                                }
                            }
                        }
                    }
                }
            }

            if (pickedDisplayName.isNotBlank()) {
                name = pickedDisplayName
            }

            when {
                retrievedNumbers.isEmpty() -> {
                    formInfo = "No phone numbers found for '$pickedDisplayName'. Enter number manually."
                }
                retrievedNumbers.size == 1 -> {
                    phone = retrievedNumbers.first()
                    formInfo = "Imported phone for '$pickedDisplayName'."
                    formError = null
                }
                else -> {
                    multiplePhones.clear()
                    multiplePhones.addAll(retrievedNumbers)
                    showMultiplePhonesDialog = true
                }
            }
        } catch (e: SecurityException) {
            formError = "Contacts permission required to import."
        } catch (e: Exception) {
            formError = "Could not read contact: ${e.localizedMessage}"
        }
    }

    var showPermissionRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showPermissionRationale = false
            formError = null
            contactPickerLauncher.launch(null)
        } else {
            showPermissionRationale = true
            formError = "Contacts permission is required to import a phone contact."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (initialContact == null) {
                    OutlinedButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                contactPickerLauncher.launch(null)
                            } else {
                                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp), tint = EmergencyRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("IMPORT FROM PHONE CONTACTS")
                    }

                    if (showPermissionRationale) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EmergencyRed.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                .border(1.dp, EmergencyRed.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Contacts permission is required to import a phone contact.",
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row {
                                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                                        Text("Allow Permission", color = EmergencyRed, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    TextButton(onClick = { com.siddesh.smsalerts.utils.PermissionManager.openAppSettings(context) }) {
                                        Text("Open Settings", color = TextSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (formError != null && !showPermissionRationale) {
                    Text(text = formError!!, color = StatusError, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (formInfo != null) {
                    Text(text = formInfo!!, color = StatusSuccess, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; formError = null },
                    label = { Text("Contact Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmergencyRed,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; formError = null },
                    label = { Text("Phone Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = TextMuted) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmergencyRed,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text("Relationship (e.g. Parent, Friend)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmergencyRed,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Enabled for alerts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmergencyRed
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        formError = "Name cannot be empty."
                        return@Button
                    }
                    val digits = phone.filter { it.isDigit() }
                    if (digits.length < 7) {
                        formError = "Please enter a valid phone number (at least 7 digits)."
                        return@Button
                    }
                    onConfirm(name, phone, relationship, isEnabled)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextSecondary)
            }
        },
        containerColor = SurfaceCardElevated
    )

    if (showMultiplePhonesDialog) {
        AlertDialog(
            onDismissRequest = { showMultiplePhonesDialog = false },
            title = { Text("Select Phone Number", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text("Select a phone number for $name:", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    multiplePhones.forEach { num ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceCardElevated)
                                .clickable {
                                    phone = num
                                    showMultiplePhonesDialog = false
                                }
                                .padding(12.dp)
                        ) {
                            Text(text = num, fontWeight = FontWeight.Bold, color = EmergencyRed, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMultiplePhonesDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = SurfaceCardElevated
        )
    }
}
