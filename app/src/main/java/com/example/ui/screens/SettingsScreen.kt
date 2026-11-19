package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.assistant.accessibility.AriaAccessibilityGestureService
import com.example.assistant.accessibility.PersonalProfileInfo
import com.example.assistant.background.AriaBackgroundWakeService
import com.example.assistant.notification.AppNotificationPreference
import com.example.assistant.security.AntiTheftSirenManager
import com.example.assistant.whatsapp.WhatsAppAutomationManager
import com.example.data.MemoryFactEntity
import com.example.speech.ArcVoiceGender
import com.example.ui.AriaViewModel
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.HologramBlue
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

private data class SettingsPermissionRowData(
    val title: String,
    val purpose: String,
    val granted: Boolean
)

/**
 * Dedicated Settings tab housing 6 transparent control sections:
 * 1. Voice (Male Charon/Adam vs Female Aoede/Rachel live switcher)
 * 2. Notifications (Notification Listener access + per-app enable/disable toggles)
 * 3. Memory (Room-persisted long-term memories + Privacy Guard confirmation + delete/clear)
 * 4. Personal Info (Saved Name/Email/Phone/Address for safe "Form fill karo" auto-fill)
 * 5. Permissions Overview (Live grant audit across runtime and system services)
 * 6. About / Privacy (Cloud AI disclosure, zero silent recording guarantee, & activity log)
 */
@Composable
fun SettingsScreen(
    viewModel: AriaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val voiceGender by viewModel.voiceGender.collectAsState()
    val notificationAppPrefs by viewModel.notificationAppPrefs.collectAsState()
    val memories by viewModel.longTermMemories.collectAsState()
    val pendingSensitiveMemory by viewModel.pendingSensitiveMemory.collectAsState()
    val personalProfile by viewModel.personalProfile.collectAsState()

    val isBackgroundWakeRunning by AriaBackgroundWakeService.isRunning.collectAsState()
    val isWakeWordEnabled by viewModel.isWakeWordModeEnabled.collectAsState()
    val sirenGuardState by AntiTheftSirenManager.guardState.collectAsState()

    // Incrementing refresh tick updates live Android Settings checks when user returns from Settings
    var resumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
                viewModel.refreshNotificationAppPrefs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationAccessGranted = remember(resumeTick) {
        WhatsAppAutomationManager.isNotificationAccessGranted(context)
    }
    val accessibilityEnabled = remember(resumeTick) {
        AriaAccessibilityGestureService.checkAccessibilityEnabled(context) ||
            AriaAccessibilityGestureService.isServiceRunning()
    }
    val permissionRows = remember(resumeTick, notificationAccessGranted, accessibilityEnabled) {
        buildSettingsPermissionRows(context, notificationAccessGranted, accessibilityEnabled)
    }

    // Local editable fields synced with PersonalProfileInfo
    var fullNameInput by remember(personalProfile.fullName) { mutableStateOf(personalProfile.fullName) }
    var emailInput by remember(personalProfile.email) { mutableStateOf(personalProfile.email) }
    var phoneInput by remember(personalProfile.phone) { mutableStateOf(personalProfile.phone) }
    var addressInput by remember(personalProfile.address) { mutableStateOf(personalProfile.address) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    // Manual Add Memory dialog state
    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var newMemKey by remember { mutableStateOf("") }
    var newMemVal by remember { mutableStateOf("") }

    // Privacy & Audit dialogs
    var showAboutPrivacyDialog by remember { mutableStateOf(false) }
    var showRecentActivityDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshNotificationAppPrefs()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepSpace, CarbonBlack, DeepSpace)
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .testTag("settings_screen_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.14f))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "ARC Settings",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "ARC SETTINGS",
                                color = ElectricCyan,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 2.sp,
                                modifier = Modifier.testTag("settings_header_title")
                            )
                            Text(
                                text = "VOICE · NOTIFICATIONS · MEMORY · FORMS · PRIVACY",
                                color = TextSecondary,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
            }

            if (!statusBanner.isNullOrBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElectricEmerald.copy(alpha = 0.14f))
                            .border(1.dp, ElectricEmerald.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("settings_status_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusBanner.orEmpty(),
                            color = ElectricEmerald,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { statusBanner = null },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("OK", color = ElectricEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // 1. VOICE SECTION (Male / Female Toggle)
            // -----------------------------------------------------------------
            item {
                val isFemale = voiceGender == ArcVoiceGender.FEMALE
                SettingsSectionCard(
                    icon = Icons.Default.GraphicEq,
                    iconTint = ElectricCyan,
                    title = "1. VOICE (MALE / FEMALE)",
                    headerTestTag = "settings_section_voice"
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DeepSpace.copy(alpha = 0.7f))
                            .border(1.dp, GlassBorderCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .clickable {
                                val next = if (isFemale) ArcVoiceGender.MALE else ArcVoiceGender.FEMALE
                                viewModel.setVoiceGender(next)
                                statusBanner = "Voice switched to ${if (next == ArcVoiceGender.FEMALE) "Female (Aoede / Rachel)" else "Male (Charon / Adam)"}."
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "Voice: ${if (isFemale) "Female" else "Male"}",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isFemale) {
                                    "Gemini Live: Aoede · ElevenLabs: Rachel · Soft female TTS"
                                } else {
                                    "Gemini Live: Charon · ElevenLabs: Adam · Warm male TTS"
                                },
                                color = if (isFemale) HologramBlue else ElectricCyan,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isFemale,
                            onCheckedChange = { checked ->
                                val next = if (checked) ArcVoiceGender.FEMALE else ArcVoiceGender.MALE
                                viewModel.setVoiceGender(next)
                                statusBanner = "Voice switched to ${if (checked) "Female (Aoede / Rachel)" else "Male (Charon / Adam)"}."
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepSpace,
                                checkedTrackColor = HologramBlue,
                                uncheckedThumbColor = DeepSpace,
                                uncheckedTrackColor = ElectricCyan
                            ),
                            modifier = Modifier.testTag("settings_voice_gender_switch")
                        )
                    }
                    Text(
                        text = "Applies on your very next spoken turn without restarting ARC. Saved automatically.",
                        color = TextSecondary,
                        fontSize = 10.5.sp
                    )
                }
            }

            // -----------------------------------------------------------------
            // 2. NOTIFICATIONS SECTION (Multi-App Listener + Per-App Toggles)
            // -----------------------------------------------------------------
            item {
                SettingsSectionCard(
                    icon = Icons.Default.NotificationsActive,
                    iconTint = CyberCyan,
                    title = "2. NOTIFICATIONS (ALL APPS READ-ALOUD)",
                    headerTestTag = "settings_section_notifications"
                ) {
                    Text(
                        text = "Say \"Notifications padho\" or \"Read notifications\" to hear unread alerts across SMS, Instagram, Gmail, WhatsApp and more.",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DeepSpace.copy(alpha = 0.7f))
                            .border(
                                1.dp,
                                if (notificationAccessGranted) ElectricEmerald.copy(alpha = 0.5f) else WarningAmber.copy(alpha = 0.55f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (notificationAccessGranted) "Notification Listener Active" else "Notification Access Needed",
                                color = if (notificationAccessGranted) ElectricEmerald else WarningAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Required by Android to read notifications aloud when you ask.",
                                color = TextSecondary,
                                fontSize = 10.5.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { WhatsAppAutomationManager.openNotificationAccessSettings(context) },
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("settings_open_notification_access_button")
                        ) {
                            Text(
                                text = if (notificationAccessGranted) "Manage" else "Enable",
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = GlassBorderCyan.copy(alpha = 0.25f))

                    Text(
                        text = "APPS ALLOWED FOR READ-ALOUD (${notificationAppPrefs.count { it.isEnabled }}/${notificationAppPrefs.size} ON)",
                        color = ElectricCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        notificationAppPrefs.forEach { appPref ->
                            NotificationAppToggleRow(
                                pref = appPref,
                                onToggle = { enabled ->
                                    viewModel.toggleAppNotification(appPref.packageName, appPref.appName, enabled)
                                }
                            )
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // 3. LONG-TERM MEMORY SECTION (Room SQLite + Privacy Guard)
            // -----------------------------------------------------------------
            item {
                SettingsSectionCard(
                    icon = Icons.Default.Psychology,
                    iconTint = ElectricEmerald,
                    title = "3. MEMORY (${memories.size} SAVED)",
                    headerTestTag = "settings_section_memory"
                ) {
                    Text(
                        text = "ARC automatically remembers preferences and routines (like favorite drinks or office hours), and blocks passwords/PINs/OTPs unless you confirm.",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )

                    pendingSensitiveMemory?.let { candidate ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WarningAmber.copy(alpha = 0.14f))
                                .border(1.dp, WarningAmber.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "PRIVACY GUARD · CONFIRM SENSITIVE SAVE",
                                color = WarningAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${candidate.sensitiveReason}. Save \"${candidate.factKey}\"?",
                                color = TextPrimary,
                                fontSize = 11.5.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.confirmSaveSensitiveMemory() },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Confirm Save", color = DeepSpace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = { viewModel.dismissPendingSensitiveMemory() },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Don't Save", color = WarningAmber, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddMemoryDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = DeepSpace
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("settings_add_memory_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Memory Fact",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add Fact",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (memories.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.clearAllMemories()
                                    statusBanner = "All saved ARC memories cleared."
                                },
                                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("settings_clear_memories_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Clear All Memories",
                                    tint = WarningAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Clear All",
                                    color = WarningAmber,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (memories.isEmpty()) {
                        Text(
                            text = "No saved memories yet. Try saying \"Mujhe chai pasand hai\" or tap Add Fact.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            memories.forEach { fact ->
                                MemoryFactRow(
                                    fact = fact,
                                    onDelete = { viewModel.deleteMemoryFactById(fact.id) }
                                )
                            }
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // 4. PERSONAL INFO SECTION (Forms Auto-Fill)
            // -----------------------------------------------------------------
            item {
                SettingsSectionCard(
                    icon = Icons.Default.Person,
                    iconTint = HologramBlue,
                    title = "4. PERSONAL INFO (FORMS AUTO-FILL)",
                    headerTestTag = "settings_section_personal_info"
                ) {
                    Text(
                        text = "Used only when you say \"Form fill karo\". Passwords, PINs, OTPs, CVVs, and unknown inputs are always skipped.",
                        color = TextSecondary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DeepSpace.copy(alpha = 0.7f))
                            .border(
                                1.dp,
                                if (accessibilityEnabled) ElectricEmerald.copy(alpha = 0.5f) else WarningAmber.copy(alpha = 0.55f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = if (accessibilityEnabled) "Auto-Fill Accessibility Active" else "Enable Accessibility for Auto-Fill",
                                color = if (accessibilityEnabled) ElectricEmerald else WarningAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Lets ARC safely paste Name, Email, Phone & Address into open forms.",
                                color = TextSecondary,
                                fontSize = 10.5.sp
                            )
                        }
                        OutlinedButton(
                            onClick = { AriaAccessibilityGestureService.openAccessibilitySettings(context) },
                            border = BorderStroke(1.dp, HologramBlue.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("settings_open_accessibility_button")
                        ) {
                            Text(
                                text = if (accessibilityEnabled) "Manage" else "Enable",
                                color = HologramBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = GlassBorderCyan.copy(alpha = 0.45f),
                        focusedContainerColor = DeepSpace.copy(alpha = 0.6f),
                        unfocusedContainerColor = DeepSpace.copy(alpha = 0.6f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = ElectricCyan,
                        unfocusedLabelColor = TextSecondary
                    )

                    OutlinedTextField(
                        value = fullNameInput,
                        onValueChange = { fullNameInput = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_profile_name")
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_profile_email")
                    )

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_profile_phone")
                    )

                    OutlinedTextField(
                        value = addressInput,
                        onValueChange = { addressInput = it },
                        label = { Text("Home / Street Address") },
                        maxLines = 2,
                        colors = fieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_profile_address")
                    )

                    Button(
                        onClick = {
                            viewModel.savePersonalProfile(
                                PersonalProfileInfo(
                                    fullName = fullNameInput,
                                    email = emailInput,
                                    phone = phoneInput,
                                    address = addressInput
                                )
                            )
                            statusBanner = "Personal Info saved for voice Forms Auto-Fill."
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricEmerald,
                            contentColor = DeepSpace
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("settings_save_profile_button")
                    ) {
                        Text(
                            text = "Save Personal Info",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // 5. PERMISSIONS OVERVIEW SECTION
            // -----------------------------------------------------------------
            item {
                val grantedCount = permissionRows.count { it.granted }
                SettingsSectionCard(
                    icon = Icons.Default.VerifiedUser,
                    iconTint = ElectricCyan,
                    title = "5. PERMISSIONS OVERVIEW ($grantedCount/${permissionRows.size})",
                    headerTestTag = "settings_section_permissions"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        permissionRows.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DeepSpace.copy(alpha = 0.65f))
                                    .border(1.dp, GlassBorderCyan.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = row.title,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = row.purpose,
                                        color = TextSecondary,
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (row.granted) ElectricEmerald.copy(alpha = 0.16f)
                                            else WarningAmber.copy(alpha = 0.14f)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (row.granted) "GRANTED" else "OFF",
                                        color = if (row.granted) ElectricEmerald else WarningAmber,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // 6. ABOUT / PRIVACY SECTION
            // -----------------------------------------------------------------
            item {
                SettingsSectionCard(
                    icon = Icons.Default.Shield,
                    iconTint = ElectricEmerald,
                    title = "6. ABOUT / PRIVACY",
                    headerTestTag = "settings_section_about_privacy"
                ) {
                    Text(
                        text = "• Cloud AI: Google Gemini Live (+ ElevenLabs & Claude fallbacks) process voice/text only when you trigger a command.\n" +
                            "• Zero Silent Recording: Background listening always shows a persistent non-dismissible notification ('ARC is listening in background').\n" +
                            "• Transparent Anti-Theft: Loud audible siren + visible alert dialog, never hidden surveillance.",
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAboutPrivacyDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = DeepSpace
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("settings_open_about_privacy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Open Full Privacy Report",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Full Report",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { showRecentActivityDialog = true },
                            border = BorderStroke(1.dp, ElectricEmerald.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("settings_open_recent_activity_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Open Permission Activity Log",
                                tint = ElectricEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Activity Log",
                                color = ElectricEmerald,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddMemoryDialog = false },
            title = {
                Text(
                    text = "ADD FACT TO ARC MEMORY",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = ElectricCyan
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newMemKey,
                        onValueChange = { newMemKey = it },
                        label = { Text("Label (e.g. Favorite Drink)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = newMemVal,
                        onValueChange = { newMemVal = it },
                        label = { Text("Detail (e.g. Masala Chai)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newMemKey.isNotBlank() && newMemVal.isNotBlank()) {
                            viewModel.addMemoryFact(newMemKey, newMemVal)
                            newMemKey = ""
                            newMemVal = ""
                            showAddMemoryDialog = false
                            statusBanner = "Saved to ARC Long-Term Memory."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Save", color = DeepSpace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddMemoryDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showAboutPrivacyDialog) {
        AboutPrivacyDialog(
            isBackgroundWakeRunning = isBackgroundWakeRunning,
            isInAppWakeWordEnabled = isWakeWordEnabled,
            sirenGuardState = sirenGuardState,
            onToggleBackgroundWake = { viewModel.toggleBackgroundWakeService() },
            onToggleAntiTheftGuard = { viewModel.toggleAntiTheftGuard() },
            onToggleInAppWakeWord = { viewModel.toggleWakeWordMode() },
            onOpenRecentActivity = {
                showAboutPrivacyDialog = false
                showRecentActivityDialog = true
            },
            onDismiss = { showAboutPrivacyDialog = false }
        )
    }

    if (showRecentActivityDialog) {
        RecentActivityDialog(
            onDismiss = { showRecentActivityDialog = false }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    headerTestTag: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark.copy(alpha = 0.85f))
            .border(1.dp, GlassBorderCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag(headerTestTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = iconTint,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.9.sp
            )
        }
        content()
    }
}

@Composable
private fun NotificationAppToggleRow(
    pref: AppNotificationPreference,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DeepSpace.copy(alpha = 0.65f))
            .border(1.dp, GlassBorderCyan.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .clickable { onToggle(!pref.isEnabled) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = pref.appName,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (pref.capturedCount > 0) "${pref.capturedCount} captured recently" else pref.packageName,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = pref.isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = DeepSpace,
                checkedTrackColor = ElectricEmerald,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = GlassBackground
            ),
            modifier = Modifier.testTag("notif_toggle_${pref.packageName}")
        )
    }
}

@Composable
private fun MemoryFactRow(
    fact: MemoryFactEntity,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DeepSpace.copy(alpha = 0.72f))
            .border(1.dp, GlassBorderCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = fact.factKey,
                    color = ElectricCyan,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = fact.category.uppercase(),
                    color = TextMuted,
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = fact.factValue,
                color = TextPrimary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(48.dp)
                .testTag("delete_memory_${fact.id}")
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Delete ${fact.factKey}",
                tint = WarningAmber,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun buildSettingsPermissionRows(
    context: Context,
    notificationAccessGranted: Boolean,
    accessibilityEnabled: Boolean
): List<SettingsPermissionRowData> {
    fun hasPerm(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    val postNotifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasPerm(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        true
    }

    return listOf(
        SettingsPermissionRowData(
            title = "Microphone",
            purpose = "Voice commands & optional 'Hey ARC' wake-word listening.",
            granted = hasPerm(Manifest.permission.RECORD_AUDIO)
        ),
        SettingsPermissionRowData(
            title = "Location (GPS)",
            purpose = "Local weather updates and ride pickup detection on request.",
            granted = hasPerm(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPerm(Manifest.permission.ACCESS_COARSE_LOCATION)
        ),
        SettingsPermissionRowData(
            title = "Contacts & Phone Calls",
            purpose = "Calling contacts or opening WhatsApp chats by voice.",
            granted = hasPerm(Manifest.permission.READ_CONTACTS) && hasPerm(Manifest.permission.CALL_PHONE)
        ),
        SettingsPermissionRowData(
            title = "Camera & Flashlight",
            purpose = "Torch ON/OFF and visible Anti-Theft Siren Guard.",
            granted = hasPerm(Manifest.permission.CAMERA)
        ),
        SettingsPermissionRowData(
            title = "Notifications",
            purpose = "Reminders, daily briefings & background listening indicator.",
            granted = postNotifGranted
        ),
        SettingsPermissionRowData(
            title = "Notification Listener",
            purpose = "Reads notifications aloud across allowed apps when asked.",
            granted = notificationAccessGranted
        ),
        SettingsPermissionRowData(
            title = "Accessibility Service",
            purpose = "Voice reels/play-pause gestures and safe Forms Auto-Fill.",
            granted = accessibilityEnabled
        ),
        SettingsPermissionRowData(
            title = "Screen Overlay",
            purpose = "Edge glow feedback during voice interactions.",
            granted = Settings.canDrawOverlays(context)
        )
    )
}
