package com.siddesh.smsalerts.ui.screens.contacts

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    contactsViewModel: ContactsViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val isLoading by contactsViewModel.isLoading.collectAsState()
    val errorMessage by contactsViewModel.errorMessage.collectAsState()
    val successMessage by contactsViewModel.successMessage.collectAsState()

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("Parent") }
    var isEnabledForAlerts by remember { mutableStateOf(true) }

    var localError by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    // Multi-phone selection dialog state
    val multiplePhones = remember { mutableStateListOf<String>() }
    var showMultiplePhonesDialog by remember { mutableStateOf(false) }

    // Contact picker launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        if (contactUri == null) {
            // User cancelled picker
            return@rememberLauncherForActivityResult
        }

        try {
            var pickedDisplayName = ""
            val retrievedNumbers = mutableListOf<String>()

            // 1. Query display name
            context.contentResolver.query(
                contactUri,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        pickedDisplayName = cursor.getString(nameIdx).orEmpty()
                    }
                }
            }

            // 2. Query phone numbers using data directory
            val dataUri = Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Data.CONTENT_DIRECTORY)
            context.contentResolver.query(
                dataUri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
                null
            )?.use { phoneCursor ->
                val numIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                while (phoneCursor.moveToNext()) {
                    if (numIdx != -1) {
                        val num = phoneCursor.getString(numIdx)?.trim()
                        if (!num.isNullOrBlank() && !retrievedNumbers.contains(num)) {
                            retrievedNumbers.add(num)
                        }
                    }
                    if (pickedDisplayName.isBlank() && nameIdx != -1) {
                        pickedDisplayName = phoneCursor.getString(nameIdx).orEmpty()
                    }
                }
            }

            if (pickedDisplayName.isNotBlank()) {
                name = pickedDisplayName
            }

            when {
                retrievedNumbers.isEmpty() -> {
                    infoMessage = "No phone numbers were found for '$pickedDisplayName'. Please enter the phone number manually."
                }
                retrievedNumbers.size == 1 -> {
                    phone = retrievedNumbers.first()
                    infoMessage = "Imported '$pickedDisplayName' successfully."
                    localError = null
                }
                else -> {
                    multiplePhones.clear()
                    multiplePhones.addAll(retrievedNumbers)
                    showMultiplePhonesDialog = true
                }
            }
        } catch (e: SecurityException) {
            localError = "Contacts permission is required to import from your phone."
        } catch (e: Exception) {
            localError = "Could not import contact: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    // Permission launcher for READ_CONTACTS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        } else {
            localError = "Contacts permission was denied. You can still enter details manually below."
        }
    }

    fun launchContactImport() {
        localError = null
        infoMessage = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            contactPickerLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add Emergency Contact",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Option B: Import Shortcut
            OutlinedButton(
                onClick = { launchContactImport() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceCard)
            ) {
                Icon(
                    imageVector = Icons.Default.Contacts,
                    contentDescription = null,
                    tint = EmergencyRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "IMPORT FROM PHONE CONTACTS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(CardBorder)
                )
                Text(
                    text = "OR ENTER MANUALLY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(CardBorder)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error & Info Banners
            val displayError = localError ?: errorMessage
            if (!displayError.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusError.copy(alpha = 0.12f))
                        .border(1.dp, StatusError.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(text = displayError, color = StatusError, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!infoMessage.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusSuccess.copy(alpha = 0.12f))
                        .border(1.dp, StatusSuccess.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(text = infoMessage.orEmpty(), color = StatusSuccess, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Contact Name
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    localError = null
                    contactsViewModel.clearMessages()
                },
                label = { Text("Contact Full Name") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null, tint = TextMuted)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmergencyRed,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Phone Number
            OutlinedTextField(
                value = phone,
                onValueChange = {
                    phone = it
                    localError = null
                    contactsViewModel.clearMessages()
                },
                label = { Text("Phone Number") },
                leadingIcon = {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = TextMuted)
                },
                placeholder = { Text("+1 (555) 000-0000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmergencyRed,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Relationship
            OutlinedTextField(
                value = relationship,
                onValueChange = { relationship = it },
                label = { Text("Relationship (e.g. Parent, Spouse, Colleague)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmergencyRed,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Enabled for emergency alerts switch
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable for Emergency Alerts",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "This contact will be selected by default when sending SOS alerts",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Switch(
                        checked = isEnabledForAlerts,
                        onCheckedChange = { isEnabledForAlerts = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmergencyRed
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Save Button
            Button(
                onClick = {
                    contactsViewModel.addContact(
                        name = name,
                        phoneNumber = phone,
                        relationship = relationship,
                        enabled = isEnabledForAlerts,
                        onSuccess = {
                            onBackClick()
                        }
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmergencyRed,
                    contentColor = Color.White
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("SAVING CONTACT...", color = Color.White)
                } else {
                    Text(
                        text = "SAVE TRUSTED CONTACT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Multiple Phone Numbers Picker Dialog
    if (showMultiplePhonesDialog) {
        AlertDialog(
            onDismissRequest = { showMultiplePhonesDialog = false },
            title = {
                Text(
                    text = "Select Phone Number",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Multiple phone numbers found for $name. Choose which number to register:",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
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
                            Text(
                                text = num,
                                fontWeight = FontWeight.SemiBold,
                                color = EmergencyRed,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
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
