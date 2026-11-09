package com.siddesh.smsalerts.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.data.sensor.ShakeDetector
import com.siddesh.smsalerts.data.voice.VoiceEmergencyDetector
import com.siddesh.smsalerts.ui.components.GlassCard
import com.siddesh.smsalerts.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyTriggersScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesDataStore = remember { UserPreferencesDataStore(context) }
    val preferences by preferencesDataStore.preferencesFlow.collectAsState(
        initial = com.siddesh.smsalerts.data.preferences.AppUserPreferences()
    )
    val realProfile by com.siddesh.smsalerts.data.repository.UserRepository.getInstance().userProfile.collectAsState()

    // Hardware checks
    val isShakeSupported = remember { ShakeDetector(context) {}.isSupported() }
    val isVoiceSupported = remember { VoiceEmergencyDetector(context, {}).isSupported() }

    // Permission launcher for RECORD_AUDIO
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch { preferencesDataStore.updateVoiceAlert(true) }
            Toast.makeText(context, "Voice trigger enabled. Say HELP ME on Home.", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch { preferencesDataStore.updateVoiceAlert(false) }
            Toast.makeText(context, "Microphone permission is required for voice commands.", Toast.LENGTH_LONG).show()
        }
    }

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (preferences.shakeToAlertEnabled) {
            com.siddesh.smsalerts.service.EmergencyProtectionService.startService(context)
        }
    }

    var showAddPhraseDialog by remember { mutableStateOf(false) }
    var newPhraseText by remember { mutableStateOf("") }
    var isTestingVoice by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf("") }
    var testMatchedPhrase by remember { mutableStateOf<String?>(null) }
    var editingServiceType by remember { mutableStateOf<String?>(null) }
    var editingServiceNumber by remember { mutableStateOf("") }

    val emergencyServicesManager = remember { com.siddesh.smsalerts.service.EmergencyServicesManager(context) }

    val voiceDetector = remember {
        VoiceEmergencyDetector(
            context = context,
            onEmergencyPhraseDetected = {},
            onError = { testResultText = it; isTestingVoice = false },
            isTestMode = true,
            onTestResult = { spoken, matched ->
                testResultText = spoken
                testMatchedPhrase = matched
            }
        )
    }
    val voiceStatus by voiceDetector.status.collectAsState()
    LaunchedEffect(voiceStatus) { isTestingVoice = voiceDetector.isListening }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                voiceDetector.stopListening()
                isTestingVoice = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            voiceDetector.stopListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Emergency Triggers",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // INFO BANNER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EmergencyRed.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .border(1.dp, EmergencyRed.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = EmergencyRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Unified Emergency Engine",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "All triggers dispatch through the same emergency pipeline, ensuring instant location attachment and recipient broadcast.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            // 1. EMERGENCY BUTTON
            TriggerSettingCard(
                title = "Emergency Button",
                description = "Enable visible Emergency Alert button on the Home dashboard.",
                icon = Icons.Default.TouchApp,
                iconTint = EmergencyRed,
                checked = preferences.emergencyButtonEnabled,
                onCheckedChange = { checked ->
                    scope.launch { preferencesDataStore.updateEmergencyButtonEnabled(checked) }
                }
            )

            // 2. SHAKE TO ALERT
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Vibration, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Shake to Alert",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isShakeSupported) {
                                        "Trigger an emergency alert after detecting deliberate shakes."
                                    } else {
                                        "Shake detection isn't supported on this device."
                                    },
                                    color = if (isShakeSupported) TextSecondary else StatusWarning,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Switch(
                            checked = preferences.shakeToAlertEnabled && isShakeSupported,
                            onCheckedChange = { checked ->
                                if (isShakeSupported) {
                                    scope.launch {
                                        preferencesDataStore.updateShakeToAlert(checked)
                                        if (checked) {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            com.siddesh.smsalerts.service.EmergencyProtectionService.startService(context)
                                        } else {
                                            com.siddesh.smsalerts.service.EmergencyProtectionService.stopService(context)
                                        }
                                    }
                                }
                            },
                            enabled = isShakeSupported,
                            colors = switchColors()
                        )
                    }

                    if (preferences.shakeToAlertEnabled && isShakeSupported) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Required Shakes Selection
                        Text(
                            text = "REQUIRED SHAKES: ${preferences.requiredShakes}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5).forEach { count ->
                                FilterChip(
                                    selected = preferences.requiredShakes == count,
                                    onClick = {
                                        scope.launch { preferencesDataStore.updateRequiredShakes(count) }
                                    },
                                    label = { Text("$count shakes") },
                                    colors = chipColors()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Shake Cooldown
                        Text(
                            text = "TRIGGER COOLDOWN: ${preferences.shakeCooldownSeconds}s",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30 to "30 sec", 60 to "1 min", 300 to "5 min").forEach { (secs, label) ->
                                FilterChip(
                                    selected = preferences.shakeCooldownSeconds == secs,
                                    onClick = {
                                        scope.launch { preferencesDataStore.updateShakeCooldown(secs) }
                                    },
                                    label = { Text(label) },
                                    colors = chipColors()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "SHAKE CALIBRATION / PRACTICE MODE",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Safely test your phone's accelerometer and practice the shake movement without sending emergency SMS or making calls.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        var isPracticeActive by remember { mutableStateOf(false) }
                        var practiceCount by remember { mutableIntStateOf(0) }
                        var practiceStatus by remember { mutableStateOf("Tap below to start practice calibration") }
                        val practiceDetector = remember {
                            com.siddesh.smsalerts.data.sensor.ShakeDetector(context) {
                                practiceCount = 5
                                com.siddesh.smsalerts.utils.HapticUtils.vibrateTick(context)
                                practiceStatus = "Five qualifying shakes detected"
                                if (practiceCount >= preferences.requiredShakes) {
                                    practiceStatus = "SUCCESS: Full ${preferences.requiredShakes}-shake sequence detected! (No alerts sent)"
                                }
                            }.apply {
                                configure(preferences.requiredShakes, 5000L)
                            }
                        }

                        DisposableEffect(isPracticeActive) {
                            if (isPracticeActive && !preferences.shakeToAlertEnabled) {
                                practiceCount = 0
                                practiceStatus = "Listening... Shake phone firmly now!"
                                practiceDetector.startListening()
                            } else {
                                practiceDetector.stopListening()
                            }
                            onDispose { practiceDetector.stopListening() }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isPracticeActive) Color(0xFFF59E0B).copy(alpha = 0.12f) else SurfaceCard)
                                .border(1.dp, if (isPracticeActive) Color(0xFFF59E0B) else CardBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = practiceStatus,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (practiceCount >= preferences.requiredShakes) StatusSuccess else if (isPracticeActive) Color(0xFFF59E0B) else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        com.siddesh.smsalerts.utils.HapticUtils.vibrateTick(context)
                                        if (preferences.shakeToAlertEnabled) practiceStatus = "Disable Shake SOS before practice to avoid a real emergency."
                                        else isPracticeActive = !isPracticeActive
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(if (isPracticeActive) "Stop Practice Mode" else "Start Practice Mode", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 3. VOICE EMERGENCY TRIGGER
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Voice Emergency Trigger",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isVoiceSupported) {
                                        "Say help me while Home is visible. Uses your microphone; if on-device recognition is unavailable, your phone's speech provider may process audio online."
                                    } else {
                                        "Speech recognition is unavailable on this device."
                                    },
                                    color = if (isVoiceSupported) TextSecondary else StatusWarning,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Switch(
                            checked = preferences.voiceAlertEnabled && isVoiceSupported,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        scope.launch { preferencesDataStore.updateVoiceAlert(true) }
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    scope.launch { preferencesDataStore.updateVoiceAlert(false) }
                                }
                            },
                            enabled = isVoiceSupported,
                            colors = switchColors()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Configured Phrases Chips
                    Text(
                        text = "EMERGENCY TRIGGER PHRASES",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val allPhrases = remember(preferences.customEmergencyPhrases) {
                        val customs = preferences.customEmergencyPhrases.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        (VoiceEmergencyDetector.DEFAULT_EMERGENCY_PHRASES + customs).distinct()
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        allPhrases.chunked(3).forEach { rowPhrases ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowPhrases.forEach { phrase ->
                                    val isCustom = !VoiceEmergencyDetector.DEFAULT_EMERGENCY_PHRASES.contains(phrase)
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            if (isCustom) {
                                                val updated = preferences.customEmergencyPhrases.split(",")
                                                    .map { it.trim() }
                                                    .filter { it.isNotBlank() && it != phrase }
                                                    .joinToString(",")
                                                scope.launch {
                                                    try { preferencesDataStore.updateCustomEmergencyPhrases(updated) }
                                                    catch (e: Exception) { testResultText = "Phrase was not removed: ${e.message}" }
                                                }
                                            }
                                        },
                                        label = { Text(if (isCustom) "$phrase ✕" else phrase, fontSize = 11.sp) },
                                        colors = chipColors()
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Full-width controls remain readable with large fonts and the keyboard.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (isTestingVoice) {
                                    voiceDetector.stopListening()
                                    isTestingVoice = false
                                } else {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        voiceDetector.setCustomPhrases(preferences.customEmergencyPhrases.split(","))
                                        voiceDetector.startListening()
                                        isTestingVoice = voiceDetector.isListening
                                        testResultText = voiceDetector.status.value
                                        testMatchedPhrase = null
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8))
                        ) {
                            Icon(
                                imageVector = if (isTestingVoice) Icons.Default.MicOff else Icons.Default.Hearing,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTestingVoice) "Stop Test" else "Test Speech (Safe)", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = { showAddPhraseDialog = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Custom Phrase", fontSize = 11.sp)
                        }
                    }

                    Text(voiceStatus, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                    if (preferences.phraseStorageError.isNotBlank()) Text(preferences.phraseStorageError, color = EmergencyRed)
                    if (isTestingVoice || testResultText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceDark, RoundedCornerShape(8.dp))
                                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Speech: $testResultText",
                                    fontSize = 12.sp,
                                    color = TextPrimary
                                )
                                if (testMatchedPhrase != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "✓ MATCHED PHRASE: ${testMatchedPhrase!!} (Trigger Verified)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusSuccess
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Privacy: Voice recognition uses your microphone on Home or during a speech test. On-device recognition is preferred. Otherwise your phone's speech provider may send audio to its servers under its own privacy policy. SafeAlerts does not save raw microphone recordings. Turn off Voice Trigger to stop automatic listening on Home.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // 4. EMERGENCY SMS PREVIEW (Configurable template)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Sms, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Emergency SMS Preview",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Actual message template dispatched to your emergency contacts when SOS is triggered:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        val previewMsg = remember(realProfile, preferences.policeNumber, preferences.policeEnabled, preferences.womensHelplineNumber, preferences.womensHelplineEnabled) {
                            com.siddesh.smsalerts.utils.EmergencyMessageFormatter.formatEmergencyMessage(
                                userName = realProfile?.fullName.orEmpty(),
                                customMessage = null,
                                policeNumber = preferences.policeNumber,
                                isPoliceEnabled = preferences.policeEnabled,
                                womensHelplineNumber = preferences.womensHelplineNumber,
                                isWomensHelplineEnabled = preferences.womensHelplineEnabled,
                                latitude = null,
                                longitude = null,
                                accuracyMeters = null,
                                secureLiveTrackingUrl = null,
                                age = realProfile?.age
                            )
                        }
                        Text(
                            text = previewMsg,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // 5. OFFLINE BLUETOOTH MESH NETWORK
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.BluetoothSearching, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Offline Bluetooth Mesh Relay",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Relay emergency alerts through nearby devices via BLE store-and-forward when cellular and Internet are offline.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Switch(
                        checked = preferences.bluetoothMeshEnabled,
                        onCheckedChange = { checked ->
                            scope.launch { preferencesDataStore.updateBluetoothMeshEnabled(checked) }
                        },
                        colors = switchColors()
                    )
                }
            }

            // 5. PUBLIC EMERGENCY SERVICES & WOMEN'S SAFETY (INDIA)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalHospital, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Emergency Services & Public Helplines",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Verified official Indian emergency lines. Configure active responders, test dialers safely, or edit numbers.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Police / National ERSS (112)
                    EmergencyServiceItem(
                        title = "1. Police / All Emergencies (ERSS)",
                        number = preferences.policeNumber,
                        scopeNotes = "India-wide unified emergency response (MHA ERSS 112)",
                        icon = Icons.Default.Security,
                        isEnabled = preferences.policeEnabled,
                        onToggle = { enabled ->
                            scope.launch { preferencesDataStore.updatePoliceEnabled(enabled) }
                        },
                        onEdit = {
                            editingServiceType = "Police"
                            editingServiceNumber = preferences.policeNumber
                        },
                        onCall = {
                            val res = emergencyServicesManager.initiateEmergencyCall("Police (ERSS)", preferences.policeNumber)
                            android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onTestDialer = {
                            val res = emergencyServicesManager.launchSafeTestDialer("Police (ERSS)", preferences.policeNumber)
                            android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorder)

                    // 2. Ambulance (108)
                    EmergencyServiceItem(
                        title = "2. Ambulance / Medical Response",
                        number = preferences.ambulanceNumber,
                        scopeNotes = "Emergency medical service (108 Maharashtra / nationwide)",
                        icon = Icons.Default.LocalHospital,
                        isEnabled = preferences.ambulanceEnabled,
                        onToggle = { enabled ->
                            scope.launch { preferencesDataStore.updateAmbulanceEnabled(enabled) }
                        },
                        onEdit = {
                            editingServiceType = "Ambulance"
                            editingServiceNumber = preferences.ambulanceNumber
                        },
                        onCall = {
                            val res = emergencyServicesManager.initiateEmergencyCall("Ambulance", preferences.ambulanceNumber)
                            android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onTestDialer = {
                            val res = emergencyServicesManager.launchSafeTestDialer("Ambulance", preferences.ambulanceNumber)
                            android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorder)

                    // 3. Fire Brigade (101)
                    EmergencyServiceItem(
                        title = "3. Fire & Rescue Services",
                        number = preferences.fireNumber,
                        scopeNotes = "National fire brigade directory (101)",
                        icon = Icons.Default.Warning,
                        isEnabled = preferences.fireEnabled,
                        onToggle = { enabled ->
                            scope.launch { preferencesDataStore.updateFireEnabled(enabled) }
                        },
                        onEdit = {
                            editingServiceType = "Fire Brigade"
                            editingServiceNumber = preferences.fireNumber
                        },
                        onCall = {
                            val res = emergencyServicesManager.initiateEmergencyCall("Fire Brigade", preferences.fireNumber)
                            android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onTestDialer = {
                            val res = emergencyServicesManager.launchSafeTestDialer("Fire Brigade", preferences.fireNumber)
                            android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorder)

                    // 4. Women Helpline (181)
                    EmergencyServiceItem(
                        title = "4. Women Helpline (MWCD)",
                        number = preferences.womensHelplineNumber,
                        scopeNotes = "Government Women Helpline (Ministry of Women & Child Development - 181)",
                        icon = Icons.Default.Face,
                        isEnabled = preferences.womensHelplineEnabled,
                        onToggle = { enabled ->
                            scope.launch { preferencesDataStore.updateWomensHelplineEnabled(enabled) }
                        },
                        onEdit = {
                            editingServiceType = "Women Helpline"
                            editingServiceNumber = preferences.womensHelplineNumber
                        },
                        onCall = {
                            val res = emergencyServicesManager.initiateEmergencyCall("Women Helpline", preferences.womensHelplineNumber)
                            android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onTestDialer = {
                            val res = emergencyServicesManager.launchSafeTestDialer("Women Helpline", preferences.womensHelplineNumber)
                            android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorder)

                    // 5. Women Crisis Response (1091)
                    EmergencyServiceItem(
                        title = "5. Women Crisis Response (Police)",
                        number = preferences.womensCrisisNumber,
                        scopeNotes = "Police Women Cell & district crisis helpline (1091 Maharashtra)",
                        icon = Icons.Default.Security,
                        isEnabled = preferences.womensCrisisEnabled,
                        onToggle = { enabled ->
                            scope.launch { preferencesDataStore.updateWomensCrisisEnabled(enabled) }
                        },
                        onEdit = {
                            editingServiceType = "Women Crisis"
                            editingServiceNumber = preferences.womensCrisisNumber
                        },
                        onCall = {
                            val res = emergencyServicesManager.initiateEmergencyCall("Women Crisis Response", preferences.womensCrisisNumber)
                            android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onTestDialer = {
                            val res = emergencyServicesManager.launchSafeTestDialer("Women Crisis Response", preferences.womensCrisisNumber)
                            android.widget.Toast.makeText(context, res.message, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CardBorder)

                    // 6. Personal Emergency Contacts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "6. Personal Emergency Contacts",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Dispatches SMS, live GPS updates, and mesh packets to your saved trusted contacts.",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(StatusSuccess.copy(alpha = 0.15f))
                                .border(1.dp, StatusSuccess.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = StatusSuccess)
                        }
                    }
                }
            }

            // 4. EMERGENCY COUNTDOWN & CONFIRMATION
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Emergency Countdown",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${preferences.emergencyCountdownSeconds} seconds cancellation window before sending.",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10).forEach { sec ->
                            FilterChip(
                                selected = preferences.emergencyCountdownSeconds == sec,
                                onClick = {
                                    scope.launch { preferencesDataStore.updateCountdownDuration(sec) }
                                },
                                label = { Text("${sec}s", maxLines = 1) },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                colors = chipColors()
                            )
                        }
                    }
                }
            }

            // 5. REQUIRE CONFIRMATION
            TriggerSettingCard(
                title = "Require Confirmation",
                description = "Shows confirmation & review screen prior to final carrier SMS transmission.",
                icon = Icons.Default.CheckCircle,
                iconTint = Color(0xFFA855F7),
                checked = preferences.requireConfirmation,
                onCheckedChange = { checked ->
                    scope.launch { preferencesDataStore.updateRequireConfirmation(checked) }
                }
            )

            // 6. LOCATION ATTACHMENT
            TriggerSettingCard(
                title = "Location Attachment",
                description = "Includes clickable Google Maps link in all emergency alerts.",
                icon = Icons.Default.LocationOn,
                iconTint = Color(0xFF38BDF8),
                checked = preferences.locationSharingEnabled,
                onCheckedChange = { checked ->
                    scope.launch { preferencesDataStore.updateLocationSharing(checked) }
                }
            )

            // 7. ALLOW SMS WITHOUT LOCATION
            TriggerSettingCard(
                title = "Allow SMS Without Location",
                description = "If GPS is unavailable or permission denied, sends emergency SMS without coordinates instead of failing.",
                icon = Icons.Default.CloudDone,
                iconTint = StatusSuccess,
                checked = preferences.allowSmsWithoutLocation,
                onCheckedChange = { checked ->
                    scope.launch { preferencesDataStore.updateAllowSmsWithoutLocation(checked) }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showAddPhraseDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddPhraseDialog = false
                    newPhraseText = ""
                },
                title = { Text("Add Custom Trigger Phrase", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column(Modifier.imePadding().verticalScroll(rememberScrollState())) {
                        Text(
                            text = "Enter a distinct phrase to trigger emergency dispatch when spoken (e.g., 'CODE RED', 'SafeAlerts SOS').",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newPhraseText,
                            onValueChange = { newPhraseText = it.take(64) },
                            placeholder = { Text("e.g. CODE RED") },
                            singleLine = true,
                            isError = newPhraseText.isNotBlank() && !VoiceEmergencyDetector.validCustomPhrase(newPhraseText),
                            supportingText = { Text("Use 2–8 distinct words, up to 64 characters. Phrases apply while voice is enabled.") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmergencyRed,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val clean = VoiceEmergencyDetector.normalizePhrase(newPhraseText)
                            if (clean.isNotBlank()) {
                                val current = preferences.customEmergencyPhrases.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                    .toMutableList()
                                if (!current.contains(clean)) {
                                    current.add(clean)
                                    scope.launch {
                                        try { preferencesDataStore.updateCustomEmergencyPhrases(current.joinToString(",")) }
                                        catch (e: Exception) { testResultText = "Phrase was not saved: ${e.message}" }
                                    }
                                }
                            }
                            showAddPhraseDialog = false
                            newPhraseText = ""
                        },
                        enabled = VoiceEmergencyDetector.validCustomPhrase(newPhraseText) &&
                            VoiceEmergencyDetector.normalizePhrase(newPhraseText) !in (VoiceEmergencyDetector.DEFAULT_EMERGENCY_PHRASES + preferences.customEmergencyPhrases.split(",")) &&
                            preferences.customEmergencyPhrases.split(",").filter { it.isNotBlank() }.size < 12,
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                    ) {
                        Text("Add Phrase")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddPhraseDialog = false
                        newPhraseText = ""
                    }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = SurfaceCard,
                textContentColor = TextPrimary
            )
        }

        if (editingServiceType != null) {
            val clean = editingServiceNumber.trim()
            val isValidNumber = clean.matches(Regex("^[+]?[0-9]{3,15}$"))

            AlertDialog(
                onDismissRequest = {
                    editingServiceType = null
                    editingServiceNumber = ""
                },
                title = { Text("Edit ${editingServiceType} Number", fontWeight = FontWeight.Bold, color = TextPrimary) },
                text = {
                    Column {
                        Text(
                            text = "Enter verified dispatch number for $editingServiceType. Standard format: 3-15 digits (e.g. 112, 108, 101, 181, 1091).",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = editingServiceNumber,
                            onValueChange = { editingServiceNumber = it },
                            placeholder = { Text("Enter emergency phone number") },
                            singleLine = true,
                            isError = clean.isNotBlank() && !isValidNumber,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmergencyRed,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                errorBorderColor = StatusError
                            )
                        )
                        if (clean.isNotBlank() && !isValidNumber) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Please enter a valid phone number (3-15 digits, optional + prefix).",
                                color = StatusError,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isValidNumber) {
                                scope.launch {
                                    when (editingServiceType) {
                                        "Police" -> preferencesDataStore.updatePoliceNumber(clean)
                                        "Ambulance" -> preferencesDataStore.updateAmbulanceNumber(clean)
                                        "Fire Brigade" -> preferencesDataStore.updateFireNumber(clean)
                                        "Women Helpline", "Women's Safety" -> preferencesDataStore.updateWomensHelplineNumber(clean)
                                        "Women Crisis" -> preferencesDataStore.updateWomensCrisisNumber(clean)
                                    }
                                }
                                editingServiceType = null
                                editingServiceNumber = ""
                            }
                        },
                        enabled = isValidNumber,
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                    ) {
                        Text("Save Number")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        editingServiceType = null
                        editingServiceNumber = ""
                    }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = SurfaceCard,
                textContentColor = TextPrimary
            )
        }
    }
}

@Composable
private fun EmergencyServiceItem(
    title: String,
    number: String,
    scopeNotes: String = "",
    icon: ImageVector,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onCall: () -> Unit,
    onTestDialer: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, contentDescription = null, tint = if (isEnabled) EmergencyRed else TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Number: $number", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { onEdit() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Number",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    if (scopeNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = scopeNotes,
                            fontSize = 11.sp,
                            color = TextSecondary.copy(alpha = 0.8f),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = switchColors()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onTestDialer,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Test Dialer (Safe)", fontSize = 11.sp, color = TextPrimary, maxLines = 1)
            }
            Button(
                onClick = onCall,
                enabled = isEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmergencyRed,
                    disabledContainerColor = SurfaceDark
                )
            ) {
                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Call Now", fontSize = 11.sp, color = Color.White, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TriggerSettingCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = description,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = switchColors()
            )
        }
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = EmergencyRed,
    checkedTrackColor = EmergencyRed.copy(alpha = 0.3f),
    uncheckedThumbColor = TextMuted,
    uncheckedTrackColor = SurfaceDark
)

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = EmergencyRed.copy(alpha = 0.2f),
    selectedLabelColor = EmergencyRed,
    containerColor = SurfaceCard,
    labelColor = TextSecondary
)
