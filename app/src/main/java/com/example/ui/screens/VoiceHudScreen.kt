package com.example.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.assistant.battery.AriaBatterySaverManager
import com.example.ui.ActivityLogItem
import com.example.ui.AriaViewModel
import com.example.ui.AssistantStatus
import com.example.ui.MemoryFactItem
import com.example.ui.components.ArcReactorVisualizer
import com.example.ui.components.ListeningPulseIndicator
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.ReactorGold
import com.example.ui.theme.CobaltTech
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.GlassBorderBlue
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.LuminousIris
import com.example.ui.theme.HologramBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SoftPink
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BatteryInfo(
    val percentage: Int = 100,
    val isCharging: Boolean = false,
    val isFull: Boolean = false
)

@Composable
fun rememberBatteryState(): BatteryInfo {
    val context = LocalContext.current
    var batteryInfo by remember { mutableStateOf(BatteryInfo()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(cntx: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                    val pct = if (level >= 0 && scale > 0) {
                        (level * 100) / scale
                    } else 0

                    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    val full = status == BatteryManager.BATTERY_STATUS_FULL

                    batteryInfo = BatteryInfo(percentage = pct, isCharging = charging, isFull = full)
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        stickyIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            val pct = if (level >= 0 && scale > 0) {
                (level * 100) / scale
            } else 0

            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val full = status == BatteryManager.BATTERY_STATUS_FULL

            batteryInfo = BatteryInfo(percentage = pct, isCharging = charging, isFull = full)
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    return batteryInfo
}

@Composable
fun rememberLiveTimeString(): String {
    var timeStr by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        while (true) {
            timeStr = sdf.format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }
    return timeStr.ifEmpty { "11:00:00 AM" }
}

enum class HudTab(val label: String, val icon: String) {
    ALL("ALL", "⚡"),
    TRANSCRIPT("TRANSCRIPT", "💬"),
    ACTIVITY("ACTIVITY", "📊"),
    MEMORY("MEMORY", "🧠"),
    TASKS("TASKS", "⏰")
}

@Composable
fun VoiceHudScreen(
    viewModel: AriaViewModel,
    modifier: Modifier = Modifier
) {
    val batteryInfo = rememberBatteryState()
    val liveTime = rememberLiveTimeString()

    val userName by viewModel.userName.collectAsState()
    val assistantStatus by viewModel.assistantStatus.collectAsState()
    val latestResponse by viewModel.latestResponse.collectAsState()
    val lastQuery by viewModel.lastQuery.collectAsState()
    val pendingWebUrl by viewModel.pendingWebUrl.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val voiceHistory by viewModel.voiceHistory.collectAsState()
    val activityLogs by viewModel.activityLogs.collectAsState()
    val memoryFacts by viewModel.memoryFacts.collectAsState()

    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isSynthesizing by viewModel.isSynthesizing.collectAsState()
    val isWakeWordModeEnabled by viewModel.isWakeWordModeEnabled.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(HudTab.ALL) }

    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var showBatterySaverDialog by remember { mutableStateOf(false) }
    var newMemoryKey by remember { mutableStateOf("") }
    var newMemoryVal by remember { mutableStateOf("") }

    val isBatterySaverActive by AriaBatterySaverManager.isBatterySaverActive.collectAsState()
    val batteryLevel by AriaBatterySaverManager.batteryLevel.collectAsState()
    val isCharging by AriaBatterySaverManager.isCharging.collectAsState()
    val manualBatteryOverride by AriaBatterySaverManager.manualOverride.collectAsState()

    // Glassmorphism Card Brush & Border Stroke (Charcoal Titanium & Electric Cyan HUD Palette)
    val glassCardBrush = Brush.linearGradient(
        colors = listOf(
            SurfaceDark,
            Color(0xEB0D1527)
        )
    )
    val glassBorderStroke = BorderStroke(
        width = 1.dp,
        brush = Brush.horizontalGradient(
            colors = listOf(
                GlassBorderCyan,
                GlassBorderBlue.copy(alpha = 0.4f)
            )
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DeepSpace,
                        CarbonBlack,
                        DeepSpace
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // -------------------------------------------------------------
        // 1. TOP STATUS BAR (Centered Title, Live Status & Power Gauge)
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            // Left: Status Indicator & Live Time
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (assistantStatus == AssistantStatus.SLEEP) WarningAmber else ElectricEmerald)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (assistantStatus == AssistantStatus.SLEEP) "STANDBY" else "ONLINE",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (assistantStatus == AssistantStatus.SLEEP) WarningAmber else ElectricEmerald,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "| $liveTime",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            }

            // Center: Prominent Modern A.R.I.A. Title
            Text(
                text = "A.R.I.A.",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = ElectricCyan,
                letterSpacing = 2.5.sp,
                modifier = Modifier.align(Alignment.Center)
            )

            // Right: Battery Power Gauge & Standby Mode Toggle
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Battery Saver Pill Tag (if active)
                if (isBatterySaverActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(WarningAmber.copy(alpha = 0.2f))
                            .border(1.dp, WarningAmber.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .clickable { showBatterySaverDialog = true }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ECO",
                            color = WarningAmber,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Battery Core Power Gauge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isCharging) ElectricEmerald.copy(alpha = 0.15f)
                            else if (batteryLevel <= 20) WarningAmber.copy(alpha = 0.15f)
                            else GlassBackground
                        )
                        .border(
                            1.dp,
                            if (isCharging) ElectricEmerald.copy(alpha = 0.5f)
                            else if (isBatterySaverActive) WarningAmber.copy(alpha = 0.7f)
                            else GlassBorderCyan,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { showBatterySaverDialog = true }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        .testTag("battery_gauge_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isCharging) Icons.Default.BatteryChargingFull
                            else if (batteryLevel <= 20) Icons.Default.BatteryAlert
                            else Icons.Default.BatteryFull,
                            contentDescription = "Battery Level",
                            tint = if (isCharging) ElectricEmerald
                            else if (batteryLevel <= 20) WarningAmber
                            else ElectricCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "$batteryLevel%",
                            color = if (batteryLevel <= 20 && !isCharging) WarningAmber else TextPrimary,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Sleep / Standby Mode Toggle
                IconButton(
                    onClick = { viewModel.toggleSleepMode() },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (assistantStatus == AssistantStatus.SLEEP) Color(0xFF0F172A) else GlassBackground)
                        .border(1.dp, GlassBorderCyan, CircleShape)
                        .testTag("standby_toggle_button")
                ) {
                    Icon(
                        imageVector = if (assistantStatus == AssistantStatus.SLEEP) Icons.Default.PowerSettingsNew else Icons.Default.NightsStay,
                        contentDescription = "Toggle Standby Mode",
                        tint = if (assistantStatus == AssistantStatus.SLEEP) ElectricCyan else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // -------------------------------------------------------------
        // 2. CENTRAL ORB / ARC REACTOR (Decluttered & Compact Size: 120dp)
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Interactive Central Orb Container (Compact size 120dp for minimal vertical footprint)
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clickable {
                            if (assistantStatus == AssistantStatus.SLEEP) {
                                viewModel.wakeUp()
                            } else if (assistantStatus == AssistantStatus.LISTENING) {
                                viewModel.speechEngine.stopListening()
                            } else {
                                viewModel.startListening()
                            }
                        }
                        .testTag("central_activate_aria_orb"),
                    contentAlignment = Alignment.Center
                ) {
                    // Animated Arc Reactor HUD Canvas (compact & optimized)
                    ArcReactorVisualizer(
                        status = assistantStatus,
                        disableAnimations = isBatterySaverActive,
                        modifier = Modifier.size(120.dp)
                    )

                    // Overlay "ACTIVATE ARIA" CTA inside the central core when IDLE or SLEEP
                    if (assistantStatus == AssistantStatus.IDLE || assistantStatus == AssistantStatus.SLEEP) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(DeepSpace.copy(alpha = 0.85f))
                                .border(1.dp, ElectricCyan, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "ACTIVATE ARIA",
                                color = ElectricCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.2.sp
                            )
                        }
                    } else {
                        // Display Active Mode Status Overlay
                        val statusLabel = when (assistantStatus) {
                            AssistantStatus.LISTENING -> "LISTENING..."
                            AssistantStatus.PROCESSING -> "PROCESSING..."
                            AssistantStatus.SPEAKING -> "SPEAKING..."
                            else -> "ACTIVE"
                        }
                        val statusTint = when (assistantStatus) {
                            AssistantStatus.LISTENING -> WarningAmber
                            AssistantStatus.PROCESSING -> HologramBlue
                            AssistantStatus.SPEAKING -> ElectricEmerald
                            else -> ElectricCyan
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(statusTint.copy(alpha = 0.2f))
                                .border(1.dp, statusTint, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = statusLabel,
                                color = statusTint,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Hands-Free Wake-Word Toggle Badge (Compact pill)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isWakeWordModeEnabled) ElectricEmerald.copy(alpha = 0.15f)
                            else Color(0xFF0F172A).copy(alpha = 0.7f)
                        )
                        .border(
                            1.dp,
                            if (isWakeWordModeEnabled) ElectricEmerald.copy(alpha = 0.6f) else GlassBorderCyan,
                            RoundedCornerShape(18.dp)
                        )
                        .clickable { viewModel.toggleWakeWordMode() }
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                        .testTag("wake_word_toggle_badge")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Wake Word Status",
                            tint = if (isWakeWordModeEnabled) ElectricEmerald else TextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (isWakeWordModeEnabled) "WAKE-WORD: 'HEY ARIA' 🎙️" else "WAKE-WORD: 'HEY ARIA' (TAP TO ENABLE)",
                            color = if (isWakeWordModeEnabled) ElectricEmerald else TextSecondary,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.6.sp
                        )
                    }
                }

                // Active Listening Audio Wave Ripple Indicator
                ListeningPulseIndicator(
                    isListening = assistantStatus == AssistantStatus.LISTENING,
                    disableAnimations = isBatterySaverActive,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // -------------------------------------------------------------
        // 3. SEGMENTED CONTROL BAR (Unified Rounded Carbon Container)
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0A101F).copy(alpha = 0.85f))
                .border(1.dp, GlassBorderCyan, RoundedCornerShape(22.dp))
                .padding(3.dp)
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(HudTab.entries.toTypedArray()) { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isSelected) ElectricCyan.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ElectricCyan else Color.Transparent,
                                shape = RoundedCornerShape(18.dp)
                            )
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = tab.icon,
                                fontSize = 10.5.sp
                            )
                            Text(
                                text = tab.label,
                                color = if (isSelected) ElectricCyan else TextSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }

        // Distinct Visual Separation between Tabs and Voice Terminal Section (18dp)
        Spacer(modifier = Modifier.height(18.dp))

        // -------------------------------------------------------------
        // 4. MAIN SCROLLABLE HUD CONTENT PANELS
        // -------------------------------------------------------------
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 6.dp)
        ) {
            // =========================================================
            // MERGED PANEL: CONVERSATION TRANSCRIPT & LIVE VOICE TERMINAL
            // =========================================================
            if (selectedTab == HudTab.ALL || selectedTab == HudTab.TRANSCRIPT) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(18.dp),
                        border = glassBorderStroke
                    ) {
                        Box(
                            modifier = Modifier
                                .background(glassCardBrush)
                                .padding(14.dp)
                        ) {
                            Column {
                                // Panel Header with Muted Monospace Hierarchy
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.ChatBubbleOutline,
                                            contentDescription = "Transcript",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "VOICE TERMINAL & TRANSCRIPT",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = ElectricCyan,
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.toggleTtsMute() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                                contentDescription = "Mute",
                                                tint = if (isMuted) WarningAmber else ElectricEmerald,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.replaySpeech() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Replay,
                                                contentDescription = "Replay",
                                                tint = ElectricCyan,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        if (voiceHistory.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Clear",
                                                fontSize = 9.5.sp,
                                                color = WarningAmber,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable { viewModel.clearVoiceHistory() }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Active Live Voice Response Block
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF070E1B).copy(alpha = 0.8f))
                                        .border(1.dp, GlassBorderCyan, RoundedCornerShape(12.dp))
                                        .padding(11.dp)
                                 ) {
                                    Column {
                                        if (lastQuery.isNotBlank()) {
                                            Text(
                                                text = "USER: $lastQuery",
                                                color = TextSecondary,
                                                fontSize = 10.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                        }

                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(
                                                imageVector = Icons.Default.RecordVoiceOver,
                                                contentDescription = "ARIA",
                                                tint = if (isSpeaking) ElectricEmerald else ElectricCyan,
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .padding(top = 2.dp)
                                            )
                                            Spacer(modifier = Modifier.width(7.dp))
                                            Text(
                                                text = latestResponse,
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                lineHeight = 17.sp,
                                                fontWeight = FontWeight.Normal,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        if (pendingWebUrl != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = {
                                                        val url = pendingWebUrl
                                                        if (url != null) viewModel.openWebUrlInApp(url)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.OpenInNew,
                                                        contentDescription = "Open In-App",
                                                        tint = DeepSpace,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(text = "Open In-App", color = DeepSpace, fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                                }
                                                Button(
                                                    onClick = { viewModel.closeWebBrowser() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
                                                ) {
                                                    Text(text = "Close", fontSize = 9.5.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                                                }
                                            }
                                        }
                                    }
                                }

                                // CONDITIONAL TRANSCRIPT HISTORY: Hidden completely if empty so no wasted space
                                if (voiceHistory.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Subtle Glass Divider
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(GlassBorderCyan.copy(alpha = 0.3f))
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Past Chat History Bubbles
                                    Text(
                                        text = "TRANSCRIPT HISTORY",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextSecondary,
                                        letterSpacing = 1.sp
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        voiceHistory.take(6).forEach { item ->
                                            // User Query Bubble (Right-aligned)
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(0.85f)
                                                        .clip(RoundedCornerShape(12.dp, 12.dp, 2.dp, 12.dp))
                                                        .background(ElectricCyan.copy(alpha = 0.12f))
                                                        .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp, 12.dp, 2.dp, 12.dp))
                                                        .padding(7.dp)
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = "USER",
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = ElectricCyan
                                                        )
                                                        Text(
                                                            text = item.query,
                                                            color = TextPrimary,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }

                                            // ARIA Response Bubble (Left-aligned)
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(0.88f)
                                                        .clip(RoundedCornerShape(12.dp, 12.dp, 12.dp, 2.dp))
                                                        .background(HologramBlue.copy(alpha = 0.10f))
                                                        .border(1.dp, GlassBorderBlue, RoundedCornerShape(12.dp, 12.dp, 12.dp, 2.dp))
                                                        .padding(7.dp)
                                                ) {
                                                    Column {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                             Icon(
                                                                 imageVector = Icons.Default.RecordVoiceOver,
                                                                 contentDescription = "ARIA",
                                                                 tint = HologramBlue,
                                                                 modifier = Modifier.size(10.dp)
                                                             )
                                                             Spacer(modifier = Modifier.width(4.dp))
                                                             Text(
                                                                 text = "ARIA",
                                                                 fontSize = 8.sp,
                                                                 fontWeight = FontWeight.Bold,
                                                                 fontFamily = FontFamily.Monospace,
                                                                 color = HologramBlue
                                                             )
                                                         }
                                                         Spacer(modifier = Modifier.height(2.dp))
                                                         Text(
                                                             text = item.response,
                                                             color = TextPrimary,
                                                             fontSize = 11.sp,
                                                             lineHeight = 15.sp
                                                         )
                                                     }
                                                 }
                                             }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================
            // PANEL C: SYSTEM ACTIVITY LOG PANEL
            // =========================================================
            if (selectedTab == HudTab.ALL || selectedTab == HudTab.ACTIVITY) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(18.dp),
                        border = glassBorderStroke
                    ) {
                        Box(
                            modifier = Modifier
                                .background(glassCardBrush)
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.FlashOn,
                                            contentDescription = "System Activity",
                                            tint = ElectricEmerald,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "SYSTEM ACTIVITY LOG",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = ElectricEmerald,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Text(
                                        text = "${activityLogs.size} Events",
                                        fontSize = 9.5.sp,
                                        color = TextSecondary,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    activityLogs.take(6).forEach { log ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF070E1B).copy(alpha = 0.6f))
                                                .border(1.dp, GlassBorderCyan, RoundedCornerShape(10.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(text = log.icon, fontSize = 12.sp)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = log.title,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextPrimary,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    if (log.detail.isNotBlank()) {
                                                        Text(
                                                            text = log.detail,
                                                            fontSize = 9.5.sp,
                                                            color = TextSecondary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = log.timeAgo,
                                                fontSize = 8.5.sp,
                                                color = TextSecondary,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================
            // PANEL D: MEMORY & USER PREFERENCES SECTION
            // =========================================================
            if (selectedTab == HudTab.ALL || selectedTab == HudTab.MEMORY) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(18.dp),
                        border = glassBorderStroke
                    ) {
                        Box(
                            modifier = Modifier
                                .background(glassCardBrush)
                                .padding(14.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Psychology,
                                            contentDescription = "Memory",
                                            tint = HologramBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "A.R.I.A. MEMORY & FACTS",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = HologramBlue,
                                            letterSpacing = 1.sp
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { showAddMemoryDialog = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Memory",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "Add Fact",
                                            fontSize = 9.5.sp,
                                            color = ElectricCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    memoryFacts.forEach { fact ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(HologramBlue.copy(alpha = 0.08f))
                                                .border(1.dp, GlassBorderBlue, RoundedCornerShape(12.dp))
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = fact.key.uppercase(Locale.ROOT),
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = HologramBlue,
                                                    letterSpacing = 1.sp
                                                )
                                                Text(
                                                    text = fact.value,
                                                    fontSize = 11.5.sp,
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.removeMemoryFact(fact.id) },
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Memory Fact",
                                                    tint = TextSecondary.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================
            // PANEL E: QUICK TASKS & REMINDERS HUD
            // =========================================================
            if (selectedTab == HudTab.ALL || selectedTab == HudTab.TASKS) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Weather Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.submitTextCommand("Weather in Delhi") },
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(16.dp),
                            border = glassBorderStroke
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(glassCardBrush)
                                    .padding(11.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "WEATHER HUD",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = ElectricCyan,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "28°C",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.WbSunny,
                                            contentDescription = "Weather",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(text = "Clear Sky, Delhi", fontSize = 9.5.sp, color = TextSecondary)
                                }
                            }
                        }

                        // Tasks Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(16.dp),
                            border = glassBorderStroke
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(glassCardBrush)
                                    .padding(11.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "TASKS HUD",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = HologramBlue,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${reminders.filter { !it.isCompleted }.size} Active",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = "Reminders",
                                            tint = HologramBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    val topReminder = reminders.firstOrNull { !it.isCompleted }?.title ?: "No upcoming tasks"
                                    Text(
                                        text = topReminder,
                                        fontSize = 9.5.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // -------------------------------------------------------------
        // 5. COMPACT QUICK VOICE COMMAND SUGGESTION CHIPS (Tight single row)
        // -------------------------------------------------------------
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                SuggestionChip(
                    onClick = { viewModel.submitTextCommand("Hey ARIA") },
                    label = { Text("🎙️ Hey ARIA", fontSize = 8.5.sp, color = ElectricEmerald, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = ElectricEmerald.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, ElectricEmerald.copy(alpha = 0.45f))
                )
            }
            item {
                SuggestionChip(
                    onClick = { viewModel.submitTextCommand("WhatsApp message Rahul Hello Boss") },
                    label = { Text("💬 WhatsApp", fontSize = 8.5.sp, color = HologramBlue, fontWeight = FontWeight.Bold) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = HologramBlue.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, HologramBlue.copy(alpha = 0.4f))
                )
            }
            item {
                SuggestionChip(
                    onClick = { viewModel.triggerBriefingSummary() },
                    label = { Text("☀️ Morning Brief", fontSize = 8.5.sp, color = ReactorGold, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = ReactorGold.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, ReactorGold.copy(alpha = 0.4f))
                )
            }
            item {
                SuggestionChip(
                    onClick = { viewModel.submitTextCommand("Weather in Delhi") },
                    label = { Text("🌤️ Weather", fontSize = 8.5.sp, color = ElectricCyan) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = GlassBackground),
                    border = BorderStroke(1.dp, GlassBorderCyan)
                )
            }
            item {
                SuggestionChip(
                    onClick = { viewModel.submitTextCommand("Open Flipkart") },
                    label = { Text("🛍️ Flipkart", fontSize = 8.5.sp, color = TextPrimary) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = GlassBackground),
                    border = BorderStroke(1.dp, GlassBorderCyan)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // -------------------------------------------------------------
        // 6. COMMAND INPUT BAR WITH SEND BUTTON & VOICE MIC
        // -------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Type command for ARIA...", color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("text_command_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = GlassBackground,
                    unfocusedContainerColor = GlassBackground,
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = GlassBorderCyan,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(22.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Mic Direct Button
            IconButton(
                onClick = {
                    if (assistantStatus == AssistantStatus.LISTENING) {
                        viewModel.speechEngine.stopListening()
                    } else {
                        viewModel.startListening()
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (assistantStatus == AssistantStatus.LISTENING) WarningAmber else Color(0xFF0F172A))
                    .border(1.dp, if (assistantStatus == AssistantStatus.LISTENING) WarningAmber else GlassBorderCyan, CircleShape)
                    .testTag("direct_mic_button")
            ) {
                Icon(
                    imageVector = if (assistantStatus == AssistantStatus.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Voice Mic Input",
                    tint = if (assistantStatus == AssistantStatus.LISTENING) Color.Black else ElectricCyan,
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Send Button
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        viewModel.submitTextCommand(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ElectricCyan)
                    .testTag("send_command_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Command",
                    tint = DeepSpace,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }

    // -------------------------------------------------------------
    // ADD MEMORY FACT DIALOG
    // -------------------------------------------------------------
    if (showAddMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddMemoryDialog = false },
            title = {
                Text(
                    text = "ADD FACT TO ARIA MEMORY",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = ElectricCyan
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter a fact or preference for ARIA to remember:",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = newMemoryKey,
                        onValueChange = { newMemoryKey = it },
                        label = { Text("Memory Key (e.g. Favorite Food)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = newMemoryVal,
                        onValueChange = { newMemoryVal = it },
                        label = { Text("Value / Preference (e.g. Pizza & Pasta)") },
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
                        if (newMemoryKey.isNotBlank() && newMemoryVal.isNotBlank()) {
                            viewModel.addMemoryFact(newMemoryKey, newMemoryVal)
                            newMemoryKey = ""
                            newMemoryVal = ""
                            showAddMemoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                ) {
                    Text("Save Memory", color = DeepSpace, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemoryDialog = false }) {
                    Text("Cancel", color = TextSecondary, fontSize = 12.sp)
                }
            },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(20.dp)
        )
    }

    // -------------------------------------------------------------
    // BATTERY SAVER & POWER OPTIMIZATION DIALOG
    // -------------------------------------------------------------
    if (showBatterySaverDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showBatterySaverDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isCharging) Icons.Default.BatteryChargingFull
                        else if (batteryLevel <= 20) Icons.Default.BatteryAlert
                        else Icons.Default.BatteryFull,
                        contentDescription = "Battery Manager",
                        tint = if (isCharging) ElectricEmerald
                        else if (isBatterySaverActive) WarningAmber
                        else CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "POWER & BATTERY SAVER",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = CyberCyan
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Battery Level & Status Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                            .border(1.dp, if (isBatterySaverActive) WarningAmber.copy(alpha = 0.5f) else GlassBorderCyan, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Current Level: $batteryLevel%",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (isCharging) "Status: Charging ⚡" else "Status: On Battery 🔋",
                                    color = if (isCharging) ElectricEmerald else TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isBatterySaverActive) WarningAmber.copy(alpha = 0.2f) else ElectricEmerald.copy(alpha = 0.15f))
                                    .border(1.dp, if (isBatterySaverActive) WarningAmber else ElectricEmerald, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isBatterySaverActive) "SAVER ACTIVE" else "OPTIMAL",
                                    color = if (isBatterySaverActive) WarningAmber else ElectricEmerald,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Text(
                        text = "Mode Selection (Auto activates below 20%):",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    // Mode Selection Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // AUTO
                        val isAuto = manualBatteryOverride == null
                        Button(
                            onClick = { AriaBatterySaverManager.resetToAutoMode(context) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAuto) CyberCyan.copy(alpha = 0.3f) else Color(0xFF1E293B)
                            ),
                            border = BorderStroke(1.dp, if (isAuto) CyberCyan else Color.Transparent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "Auto (<20%)",
                                fontSize = 10.sp,
                                color = if (isAuto) CyberCyan else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // FORCE ON
                        val isForceOn = manualBatteryOverride == true
                        Button(
                            onClick = { AriaBatterySaverManager.setManualOverride(true, context) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isForceOn) WarningAmber.copy(alpha = 0.3f) else Color(0xFF1E293B)
                            ),
                            border = BorderStroke(1.dp, if (isForceOn) WarningAmber else Color.Transparent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "Force ON",
                                fontSize = 10.sp,
                                color = if (isForceOn) WarningAmber else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // FORCE OFF
                        val isForceOff = manualBatteryOverride == false
                        Button(
                            onClick = { AriaBatterySaverManager.setManualOverride(false, context) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isForceOff) ElectricEmerald.copy(alpha = 0.3f) else Color(0xFF1E293B)
                            ),
                            border = BorderStroke(1.dp, if (isForceOff) ElectricEmerald else Color.Transparent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                "Off",
                                fontSize = 10.sp,
                                color = if (isForceOff) ElectricEmerald else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Active Power Protections
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF070E1B))
                            .border(1.dp, GlassBorderCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "⚡ Battery Saver Power Optimizations:",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                        Text(
                            text = "• Background sync & wake-loop frequency throttled (saving CPU wake locks).",
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "• Non-essential GPU/Canvas animations disabled (Arc Reactor & Edge Glow).",
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "• Speech recognition restart duty cycle optimized.",
                            fontSize = 9.5.sp,
                            color = TextSecondary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showBatterySaverDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Close", color = DeepSpace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(20.dp)
        )
    }
}
