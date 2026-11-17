package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.assistant.security.PermissionCategory
import com.example.assistant.security.PermissionUsageEntry
import com.example.assistant.security.PermissionUsageLogManager
import com.example.assistant.security.SirenGuardState
import com.example.assistant.whatsapp.WhatsAppAutomationManager
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

private data class TransparencyPermissionSummary(
    val title: String,
    val usageSummary: String,
    val isGranted: Boolean,
    val icon: ImageVector
)

/**
 * Full-transparency About / Privacy & Security dialog accessible from ARC's ☰ drawer.
 * Built to give friends/family installing ARC complete clarity on:
 * - Cloud AI & Voice endpoints (Gemini Live + ElevenLabs/Claude fallbacks)
 * - No silent or hidden background recording guarantee
 * - Loud-siren Anti-Theft transparency (never covert surveillance)
 * - Every permission's exact purpose + live grant status
 * - Live ON/OFF status toggles for auto-triggered systems + shortcut to Recent Activity log
 */
@Composable
fun AboutPrivacyDialog(
    isBackgroundWakeRunning: Boolean,
    isInAppWakeWordEnabled: Boolean,
    sirenGuardState: SirenGuardState,
    onToggleBackgroundWake: () -> Unit,
    onToggleAntiTheftGuard: () -> Unit,
    onToggleInAppWakeWord: () -> Unit,
    onOpenRecentActivity: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val auditEntries by PermissionUsageLogManager.entries.collectAsState()
    val permissionSummaries = remember(context) { buildPermissionSummaries(context) }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .widthIn(max = 580.dp)
                .testTag("about_privacy_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = CarbonBlack,
            border = BorderStroke(1.dp, GlassBorderCyan)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ElectricEmerald.copy(alpha = 0.16f))
                                .border(1.dp, ElectricEmerald.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "ARC Privacy & Trust Shield",
                                tint = ElectricEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "ABOUT & PRIVACY",
                                color = ElectricCyan,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.6.sp
                            )
                            Text(
                                text = "FULL TRUST & TRANSPARENCY REPORT",
                                color = TextSecondary,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("about_privacy_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close About and Privacy",
                            tint = TextSecondary
                        )
                    }
                }

                HorizontalDivider(
                    color = GlassBorderCyan.copy(alpha = 0.35f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // Scrollable body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Live Auto-Triggered Systems Status Card
                    PrivacySectionCard(
                        icon = Icons.Default.NotificationsActive,
                        iconTint = CyberCyan,
                        title = "AUTO-TRIGGERED FEATURES STATUS"
                    ) {
                        Text(
                            text = "Aap kisi bhi auto feature ko yahan ya Home HUD se turant ON/OFF kar sakte hain:",
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        StatusControlRow(
                            label = "Background 'Hey ARC' Listening",
                            detail = if (isBackgroundWakeRunning) {
                                "ON • Persistent notification active"
                            } else {
                                "OFF • No background mic running"
                            },
                            statusBadge = if (isBackgroundWakeRunning) "ON" else "OFF",
                            isActive = isBackgroundWakeRunning,
                            activeColor = ElectricEmerald,
                            onClick = onToggleBackgroundWake
                        )

                        StatusControlRow(
                            label = "In-App Wake Word ('Hey ARC')",
                            detail = if (isInAppWakeWordEnabled) {
                                "ON • Active only while app is open"
                            } else {
                                "OFF • Tap mic manually to talk"
                            },
                            statusBadge = if (isInAppWakeWordEnabled) "ON" else "OFF",
                            isActive = isInAppWakeWordEnabled,
                            activeColor = ElectricCyan,
                            onClick = onToggleInAppWakeWord
                        )

                        val isGuardArmedOrTriggered = sirenGuardState != SirenGuardState.DISARMED
                        StatusControlRow(
                            label = "Anti-Theft Siren Guard",
                            detail = when (sirenGuardState) {
                                SirenGuardState.DISARMED -> "OFF • Motion & unplug siren disarmed"
                                SirenGuardState.ARMED -> "ARMED • Triggers loud siren on movement/unplug"
                                SirenGuardState.TRIGGERED -> "TRIGGERED • Loud alarm siren & visible alert active"
                            },
                            statusBadge = sirenGuardState.name,
                            isActive = isGuardArmedOrTriggered,
                            activeColor = if (sirenGuardState == SirenGuardState.TRIGGERED) WarningAmber else CyberCyan,
                            onClick = onToggleAntiTheftGuard
                        )
                    }

                    // 2. Cloud Voice & AI Processing Disclosure
                    PrivacySectionCard(
                        icon = Icons.Default.Cloud,
                        iconTint = HologramBlue,
                        title = "VOICE & CLOUD AI PROCESSING"
                    ) {
                        Text(
                            text = "• Voice aur text processing ke liye Google Gemini Live primary engine use hota hai.\n" +
                                "• Fallback speech/reasoning ke liye ElevenLabs (voice synthesis) aur Claude / Gemini REST APIs ko voice/text sirf tab bheja jata hai jab aap command trigger karein.",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }

                    // 3. No Silent Recording Guarantee
                    PrivacySectionCard(
                        icon = Icons.Default.Lock,
                        iconTint = ElectricEmerald,
                        title = "ZERO SILENT RECORDING GUARANTEE"
                    ) {
                        Text(
                            text = "• Koi bhi audio, camera photo ya screen data chhup ke record ya store nahi hota bina aapke trigger kiye.\n" +
                                "• Background 'Hey ARC' listening jab bhi ON hoti hai, hamesha ek persistent, non-dismissible notification ('ARC is listening in background') dikhti hai — bina notification ke mic background me kabhi nahi chalta.",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }

                    // 4. Anti-Theft Transparency
                    PrivacySectionCard(
                        icon = Icons.Default.Security,
                        iconTint = WarningAmber,
                        title = "ANTI-THEFT FEATURE TRANSPARENCY"
                    ) {
                        Text(
                            text = "• Anti-Theft Guard ek tez loud-siren alarm (+ flashlight strobe aur screen par dikhne wala visible alert) hai.\n" +
                                "• Isme koi silent ya hidden audio/video recording nahi hoti; user ya phone chhoone wale ko turant full-screen alert aur siren dikhta hai.",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }

                    // 5. Simple List of Where Each Permission Is Used
                    PrivacySectionCard(
                        icon = Icons.Default.CheckCircle,
                        iconTint = ElectricCyan,
                        title = "PERMISSION USAGE GUIDE (${permissionSummaries.count { it.isGranted }}/${permissionSummaries.size} GRANTED)"
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            permissionSummaries.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(DeepSpace.copy(alpha = 0.7f))
                                        .border(1.dp, GlassBorderCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = if (item.isGranted) ElectricEmerald else TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.title,
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(
                                                        if (item.isGranted) ElectricEmerald.copy(alpha = 0.16f)
                                                        else WarningAmber.copy(alpha = 0.14f)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (item.isGranted) "GRANTED" else "OFF",
                                                    color = if (item.isGranted) ElectricEmerald else WarningAmber,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.usageSummary,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Footer button to view Recent Activity permission log
                Button(
                    onClick = onOpenRecentActivity,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = DeepSpace
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("about_open_recent_activity_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Open Recent Permission Activity Log",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VIEW RECENT PERMISSION ACTIVITY (${auditEntries.size})",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * In-app Permission-Usage Transparency Log ("Recent Activity") showing real runtime accesses
 * to sensitive permissions (Microphone, Location, Contacts, Phone Call, Camera) with timestamps.
 */
@Composable
fun RecentActivityDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        PermissionUsageLogManager.ensureLoaded(context)
    }

    val entries by PermissionUsageLogManager.entries.collectAsState()
    var selectedCategory by remember { mutableStateOf<PermissionCategory?>(null) }
    val filteredEntries = remember(entries, selectedCategory) {
        if (selectedCategory == null) entries
        else entries.filter { it.category == selectedCategory }
    }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.84f)
                .widthIn(max = 580.dp)
                .testTag("recent_activity_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = CarbonBlack,
            border = BorderStroke(1.dp, GlassBorderCyan)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.16f))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Permission Usage Audit Log",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "RECENT PERMISSION ACTIVITY",
                                color = ElectricCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "LIVE AUDIT OF MIC, LOCATION, CONTACTS & CAMERA",
                                color = TextSecondary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_recent_activity_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Recent Activity Log",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Category filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChipButton(
                        label = "All (${entries.size})",
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                    PermissionCategory.entries.forEach { category ->
                        val count = entries.count { it.category == category }
                        FilterChipButton(
                            label = "${category.label} ($count)",
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category }
                        )
                    }
                }

                HorizontalDivider(
                    color = GlassBorderCyan.copy(alpha = 0.3f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                if (filteredEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "No sensitive access recorded",
                                tint = ElectricEmerald,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No sensitive permission activity recorded yet.",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Jab bhi Microphone, Location, Contacts, Phone Call ya Camera access hoga, exact time aur reason yahan live dikhai dega.",
                                color = TextSecondary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("recent_activity_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredEntries, key = { it.id }) { entry ->
                            RecentActivityEntryCard(entry = entry)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (entries.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { PermissionUsageLogManager.clearLogs(context) },
                            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("clear_recent_activity_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Clear Audit Log",
                                tint = WarningAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Clear Log",
                                color = WarningAmber,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = DeepSpace
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(
                            text = "Done",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacySectionCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark.copy(alpha = 0.85f))
            .border(1.dp, GlassBorderCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = title,
                color = iconTint,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.9.sp
            )
        }
        content()
    }
}

@Composable
private fun StatusControlRow(
    label: String,
    detail: String,
    statusBadge: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(DeepSpace.copy(alpha = 0.72f))
            .border(
                1.dp,
                if (isActive) activeColor.copy(alpha = 0.55f) else GlassBorderCyan.copy(alpha = 0.25f),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = detail,
                color = TextSecondary,
                fontSize = 10.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isActive) activeColor.copy(alpha = 0.2f) else GlassBackground)
                .border(
                    1.dp,
                    if (isActive) activeColor else TextMuted.copy(alpha = 0.5f),
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = statusBadge,
                color = if (isActive) activeColor else TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun FilterChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) ElectricCyan.copy(alpha = 0.2f) else SurfaceDark)
            .border(
                1.dp,
                if (selected) ElectricCyan else GlassBorderCyan.copy(alpha = 0.35f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) ElectricCyan else TextSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RecentActivityEntryCard(entry: PermissionUsageEntry) {
    val (catIcon, accent) = when (entry.category) {
        PermissionCategory.MICROPHONE -> Icons.Default.Mic to ElectricCyan
        PermissionCategory.LOCATION -> Icons.Default.LocationOn to ElectricEmerald
        PermissionCategory.CONTACTS -> Icons.Default.Contacts to HologramBlue
        PermissionCategory.PHONE -> Icons.Default.Phone to CyberCyan
        PermissionCategory.CAMERA -> Icons.Default.CameraAlt to WarningAmber
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark.copy(alpha = 0.85f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = catIcon,
                contentDescription = entry.category.label,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.category.label.uppercase(),
                    color = accent,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = entry.formattedTime(),
                    color = TextMuted,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = entry.action,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (entry.reason.isNotBlank()) {
                Text(
                    text = entry.reason,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

private fun buildPermissionSummaries(context: Context): List<TransparencyPermissionSummary> {
    fun hasPerm(perm: String): Boolean {
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    val mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasPerm(Manifest.permission.READ_MEDIA_IMAGES) ||
            hasPerm(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        hasPerm(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        hasPerm(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        true
    }

    return listOf(
        TransparencyPermissionSummary(
            title = "Microphone",
            usageSummary = "Sirf jab aap mic tap karein ya Wake-Word ('Hey ARC') ON rakhen tab voice command sunne ke liye.",
            isGranted = hasPerm(Manifest.permission.RECORD_AUDIO),
            icon = Icons.Default.Mic
        ),
        TransparencyPermissionSummary(
            title = "Location (GPS)",
            usageSummary = "Sirf local weather aur aapke bolne par cab/navigation pickup dhundhne ke liye.",
            isGranted = hasPerm(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPerm(Manifest.permission.ACCESS_COARSE_LOCATION),
            icon = Icons.Default.LocationOn
        ),
        TransparencyPermissionSummary(
            title = "Contacts & Phone Calls",
            usageSummary = "Sirf jab aap 'Call [naam]' ya 'WhatsApp [naam]' bolte hain tab contact search aur dial karne ke liye.",
            isGranted = hasPerm(Manifest.permission.READ_CONTACTS) && hasPerm(Manifest.permission.CALL_PHONE),
            icon = Icons.Default.Contacts
        ),
        TransparencyPermissionSummary(
            title = "Camera & Flashlight",
            usageSummary = "Sirf Torch ON/OFF karne aur Anti-Theft Siren trigger hone par visible alert snapshot dikhane ke liye.",
            isGranted = hasPerm(Manifest.permission.CAMERA),
            icon = Icons.Default.CameraAlt
        ),
        TransparencyPermissionSummary(
            title = "Notifications",
            usageSummary = "Scheduled reminders, morning weather alerts aur persistent background listening badge dikhane ke liye.",
            isGranted = notifGranted,
            icon = Icons.Default.NotificationsActive
        ),
        TransparencyPermissionSummary(
            title = "Files & Media",
            usageSummary = "Sirf aapke bolne par device ke local photos, audio ya files search karne ke liye.",
            isGranted = mediaGranted,
            icon = Icons.Default.Layers
        ),
        TransparencyPermissionSummary(
            title = "Optional: Overlay / WhatsApp Reader",
            usageSummary = "Edge glow feedback ya unread WhatsApp messages aloud padhne ke liye (Settings se optional).",
            isGranted = Settings.canDrawOverlays(context) || WhatsAppAutomationManager.isNotificationAccessGranted(context),
            icon = Icons.Default.Warning
        )
    )
}
