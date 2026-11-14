package com.example.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.assistant.ArcLocationManager
import com.example.assistant.DeviceClockHelper
import com.example.assistant.battery.AriaBatterySaverManager
import com.example.geminilive.GeminiLiveSessionState
import com.example.ui.AriaViewModel
import com.example.ui.AssistantStatus
import com.example.ui.components.ListeningPulseIndicator
import com.example.ui.components.ParticleAvatar3D
import com.example.ui.theme.CarbonBlack
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorderBlue
import com.example.ui.theme.GlassBorderCyan
import com.example.ui.theme.HologramBlue
import com.example.ui.theme.ReactorGold
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.launch

/**
 * Live device clock string for the HUD Time Pill:
 * 1. Uses DeviceClockHelper so SimpleDateFormat & TimeZone are NEVER cached across ticks.
 * 2. Refreshes immediately on ACTION_TIME_TICK, ACTION_TIME_CHANGED, ACTION_TIMEZONE_CHANGED, and ON_RESUME.
 * 3. Aligns coroutine fallback ticker to exact minute boundaries (:00s) instead of drifting 15s delays.
 */
@Composable
fun rememberCompactTimeString(): String {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var timeStr by remember {
        mutableStateOf(
            DeviceClockHelper.getCurrentDeviceClockInfo(
                context = context,
                respect24HourSetting = false,
                logDebug = true,
                source = "HudTimePillInit"
            ).formattedTime
        )
    }

    // Listen to Android system time/timezone broadcasts + lifecycle resume
    DisposableEffect(context, lifecycleOwner) {
        val refreshNow: (String) -> Unit = { triggerSource ->
            val snapshot = DeviceClockHelper.getCurrentDeviceClockInfo(
                context = context,
                respect24HourSetting = false,
                logDebug = true,
                source = triggerSource
            )
            timeStr = snapshot.formattedTime
        }

        val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        DeviceClockHelper.onTimezoneChanged(intent.getStringExtra("time-zone"))
                        refreshNow("BroadcastTimezoneChanged")
                    }
                    Intent.ACTION_TIME_CHANGED -> refreshNow("BroadcastTimeChanged")
                    Intent.ACTION_TIME_TICK -> refreshNow("BroadcastTimeTick")
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            timeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshNow("LifecycleOnResume")
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            try {
                context.unregisterReceiver(timeReceiver)
            } catch (_: IllegalArgumentException) {
            }
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    // Minute-aligned fallback ticker in case broadcasts are throttled
    LaunchedEffect(context) {
        while (true) {
            kotlinx.coroutines.delay(DeviceClockHelper.millisUntilNextMinuteTick())
            val snapshot = DeviceClockHelper.getCurrentDeviceClockInfo(
                context = context,
                respect24HourSetting = false,
                logDebug = false,
                source = "MinuteAlignedTicker"
            )
            if (timeStr != snapshot.formattedTime) {
                timeStr = snapshot.formattedTime
                DeviceClockHelper.logClockSnapshot(snapshot, source = "MinuteAlignedTicker")
            }
        }
    }

    return timeStr
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceHudScreen(
    viewModel: AriaViewModel,
    modifier: Modifier = Modifier
) {
    val liveTime = rememberCompactTimeString()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val assistantStatus by viewModel.assistantStatus.collectAsState()
    val isArcPoweredOn by viewModel.isArcPoweredOn.collectAsState()
    val weatherTemp by viewModel.weatherTemp.collectAsState()
    val currentCity by viewModel.currentCity.collectAsState()
    val needsLocationPermission by viewModel.needsLocationPermission.collectAsState()
    val latestResponse by viewModel.latestResponse.collectAsState()
    val lastQuery by viewModel.lastQuery.collectAsState()
    val pendingWebUrl by viewModel.pendingWebUrl.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val voiceHistory by viewModel.voiceHistory.collectAsState()

    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isWakeWordModeEnabled by viewModel.isWakeWordModeEnabled.collectAsState()
    val isGeminiLiveMode by viewModel.isGeminiLiveMode.collectAsState()
    val geminiLiveState by viewModel.geminiLiveManager.sessionState.collectAsState()
    val micVolumeLevel by viewModel.micVolumeLevel.collectAsState()

    val isBatterySaverActive by AriaBatterySaverManager.isBatterySaverActive.collectAsState()
    val batteryLevel by AriaBatterySaverManager.batteryLevel.collectAsState()
    val isCharging by AriaBatterySaverManager.isCharging.collectAsState()
    val manualBatteryOverride by AriaBatterySaverManager.manualOverride.collectAsState()

    val context = LocalContext.current
    var hasPromptedLocationOnce by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.onLocationPermissionUpdated()
    }

    LaunchedEffect(needsLocationPermission) {
        if (needsLocationPermission && !hasPromptedLocationOnce) {
            hasPromptedLocationOnce = true
            locationPermissionLauncher.launch(ArcLocationManager.REQUIRED_LOCATION_PERMISSIONS)
        } else if (!needsLocationPermission) {
            viewModel.onLocationPermissionUpdated()
        }
    }

    var inputText by remember { mutableStateOf("") }
    var showChatSheet by remember { mutableStateOf(false) }
    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var showBatterySaverDialog by remember { mutableStateOf(false) }
    var newMemoryKey by remember { mutableStateOf("") }
    var newMemoryVal by remember { mutableStateOf("") }

    val activeTaskCount = remember(reminders) { reminders.count { !it.isCompleted } }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ArcDrawerContent(
                viewModel = viewModel,
                assistantStatus = assistantStatus,
                latestResponse = latestResponse,
                lastQuery = lastQuery,
                pendingWebUrl = pendingWebUrl,
                voiceHistory = voiceHistory,
                isMuted = isMuted,
                isSpeaking = isSpeaking,
                isWakeWordModeEnabled = isWakeWordModeEnabled,
                isGeminiLiveMode = isGeminiLiveMode,
                geminiLiveState = geminiLiveState,
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                isBatterySaverActive = isBatterySaverActive,
                onOpenBatteryDialog = {
                    scope.launch { drawerState.close() }
                    showBatterySaverDialog = true
                },
                onOpenAddMemory = {
                    scope.launch { drawerState.close() }
                    showAddMemoryDialog = true
                },
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        BoxWithConstraints(
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
        ) {
            val isCompactHeight = maxHeight < 640.dp
            val avatarSize = if (isCompactHeight) 188.dp else 236.dp
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isCompactHeight) Modifier.verticalScroll(scrollState) else Modifier)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // -------------------------------------------------------------
                // 1. TOP BAR: Menu (☰) | Glowing "ARC" Title | Status Pill
                // -------------------------------------------------------------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    // Left: Hamburger Menu Button (48dp accessible target)
                    IconButton(
                        onClick = { scope.launch { drawerState.open() } },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(48.dp)
                            .testTag("menu_drawer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open ARC Menu",
                            tint = ElectricCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Center: Bold Glowing "ARC" Title
                    Text(
                        text = "ARC",
                        style = TextStyle(
                            color = ElectricCyan,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 7.sp,
                            shadow = Shadow(
                                color = ElectricCyan.copy(alpha = 0.65f),
                                offset = Offset(0f, 0f),
                                blurRadius = 20f
                            )
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // Right: Compact ONLINE / STANDBY Status Pill (Synced with Power-Button & Routines)
                    val isStandby = !isArcPoweredOn || assistantStatus == AssistantStatus.SLEEP
                    val statusBadgeColor = if (isStandby) WarningAmber else ElectricEmerald
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { viewModel.toggleArcPower() }
                            .testTag("standby_toggle_button"),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(statusBadgeColor.copy(alpha = 0.12f))
                                .border(1.dp, statusBadgeColor.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusBadgeColor)
                            )
                            Text(
                                text = if (isStandby) "STANDBY" else "ONLINE",
                                color = statusBadgeColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }

                // -------------------------------------------------------------
                // 2. CENTRAL TRANSPARENT CORE PARTICLE AVATAR + POWER BUTTON + STATUS
                // -------------------------------------------------------------
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val effectiveStatus = when {
                        !isArcPoweredOn || assistantStatus == AssistantStatus.SLEEP -> AssistantStatus.SLEEP
                        geminiLiveState == GeminiLiveSessionState.SPEAKING -> AssistantStatus.SPEAKING
                        geminiLiveState == GeminiLiveSessionState.LISTENING -> AssistantStatus.LISTENING
                        else -> assistantStatus
                    }

                    // Transparent unclipped glow frame for the dense CORE particle sphere
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clickable { viewModel.activateArcWithGreeting() }
                            .testTag("central_activate_arc_orb"),
                        contentAlignment = Alignment.Center
                    ) {
                        ParticleAvatar3D(
                            status = effectiveStatus,
                            micVolume = micVolumeLevel,
                            disableAnimations = isBatterySaverActive || !isArcPoweredOn,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // MAX-Inspired Central Circular Power-Button Toggle (ON = pulsing glow, OFF = dim slate)
                    ArcPowerButton(
                        isPoweredOn = isArcPoweredOn,
                        assistantStatus = effectiveStatus,
                        disableAnimations = isBatterySaverActive,
                        onToggle = { viewModel.toggleArcPower() }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Centered Status Subtitle ("TAP TO START" when OFF, active state when ON)
                    val subtitleText = when (effectiveStatus) {
                        AssistantStatus.SLEEP -> "TAP TO START"
                        AssistantStatus.LISTENING -> "LISTENING..."
                        AssistantStatus.PROCESSING -> "THINKING..."
                        AssistantStatus.SPEAKING -> "SPEAKING..."
                        else -> "ARC VOICE ASSISTANT"
                    }
                    val subtitleColor = when (effectiveStatus) {
                        AssistantStatus.SLEEP -> TextMuted
                        AssistantStatus.LISTENING -> ElectricCyan
                        AssistantStatus.PROCESSING -> HologramBlue
                        AssistantStatus.SPEAKING -> ElectricCyan
                        else -> TextSecondary
                    }

                    Text(
                        text = subtitleText,
                        style = TextStyle(
                            color = subtitleColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.6.sp,
                            shadow = if (effectiveStatus != AssistantStatus.SLEEP) {
                                Shadow(
                                    color = subtitleColor.copy(alpha = 0.45f),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 12f
                                )
                            } else null
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("arc_status_subtitle")
                    )

                    // Subtle audio waveform indicator only while powered ON and listening
                    ListeningPulseIndicator(
                        isListening = effectiveStatus == AssistantStatus.LISTENING,
                        disableAnimations = isBatterySaverActive,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // -------------------------------------------------------------
                // 3. INFO PILLS ROW (Weather via GPS | Tasks | Time)
                // -------------------------------------------------------------
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                ) {
                    if (needsLocationPermission) {
                        Surface(
                            onClick = {
                                locationPermissionLauncher.launch(ArcLocationManager.REQUIRED_LOCATION_PERMISSIONS)
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = WarningAmber.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("enable_gps_location_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "Enable GPS Location",
                                    tint = WarningAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "ENABLE GPS FOR LOCAL WEATHER & RIDES",
                                    color = WarningAmber,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.7.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HudInfoPill(
                            icon = Icons.Default.WbSunny,
                            label = if (needsLocationPermission) weatherTemp else "$weatherTemp · $currentCity",
                            accentColor = ElectricCyan,
                            onClick = {
                                if (needsLocationPermission) {
                                    locationPermissionLauncher.launch(ArcLocationManager.REQUIRED_LOCATION_PERMISSIONS)
                                } else {
                                    viewModel.requestLocalWeatherUpdate()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("info_pill_weather")
                        )
                        HudInfoPill(
                            icon = Icons.Default.Notifications,
                            label = "$activeTaskCount Tasks",
                            accentColor = HologramBlue,
                            onClick = { viewModel.submitTextCommand("Show my tasks") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("info_pill_tasks")
                        )
                        HudInfoPill(
                            icon = Icons.Default.Schedule,
                            label = liveTime,
                            accentColor = ElectricCyan,
                            onClick = { viewModel.triggerBriefingSummary() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("info_pill_time")
                        )
                    }
                }

                // -------------------------------------------------------------
                // 4. BOTTOM ICON ROW: Screen Vision | Prominent Mic | Chat Input
                // -------------------------------------------------------------
                val bottomStatus = when {
                    !isArcPoweredOn || assistantStatus == AssistantStatus.SLEEP -> AssistantStatus.SLEEP
                    geminiLiveState == GeminiLiveSessionState.SPEAKING -> AssistantStatus.SPEAKING
                    geminiLiveState == GeminiLiveSessionState.LISTENING -> AssistantStatus.LISTENING
                    else -> assistantStatus
                }
                BottomHudActionRow(
                    assistantStatus = bottomStatus,
                    disableAnimations = isBatterySaverActive,
                    onScreenVisionClick = { viewModel.submitTextCommand("read screen") },
                    onMicClick = { viewModel.activateArcWithGreeting() },
                    onChatClick = { showChatSheet = true }
                )
            }
        }
    }

    // -------------------------------------------------------------
    // CHAT & TEXT COMMAND MODAL SHEET (Opened via Right Chat Icon)
    // -------------------------------------------------------------
    if (showChatSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showChatSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF0A101F),
            contentColor = TextPrimary,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ARC TERMINAL & CHAT",
                        color = ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleTtsMute() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Toggle Voice Mute",
                                tint = if (isMuted) WarningAmber else ElectricEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.replaySpeech() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "Replay Response",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Latest Response Highlight Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF070E1B))
                        .border(1.dp, GlassBorderCyan, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (lastQuery.isNotBlank()) {
                            Text(
                                text = "YOU: $lastQuery",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = latestResponse,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                        if (pendingWebUrl != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        pendingWebUrl?.let { viewModel.openWebUrlInApp(it) }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open Link",
                                        tint = DeepSpace,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Open Link",
                                        color = DeepSpace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(onClick = { viewModel.closeWebBrowser() }) {
                                    Text("Dismiss", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Recent Transcript Bubbles
                if (voiceHistory.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(voiceHistory.take(8)) { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "YOU: ${item.query}",
                                    color = ElectricCyan,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "ARC: ${item.response}",
                                    color = TextSecondary,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // Command Input Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                text = "Ask ARC anything...",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = GlassBackground,
                            unfocusedContainerColor = GlassBackground,
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = GlassBorderCyan,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("text_command_input")
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                viewModel.submitTextCommand(inputText)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan)
                            .testTag("send_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Command",
                            tint = DeepSpace,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // ADD MEMORY FACT DIALOG (Triggered from Drawer)
    // -------------------------------------------------------------
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
                    Text(
                        text = "Enter a fact or preference for ARC to remember:",
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
                    Text(
                        "Save Memory",
                        color = DeepSpace,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
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
    // BATTERY SAVER & POWER OPTIMIZATION DIALOG (Triggered from Drawer)
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                            .border(
                                1.dp,
                                if (isBatterySaverActive) WarningAmber.copy(alpha = 0.5f) else GlassBorderCyan,
                                RoundedCornerShape(14.dp)
                            )
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
                                    text = if (isCharging) "Status: Charging" else "Status: On Battery",
                                    color = if (isCharging) ElectricEmerald else TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isBatterySaverActive) WarningAmber.copy(alpha = 0.2f)
                                        else ElectricEmerald.copy(alpha = 0.15f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isBatterySaverActive) WarningAmber else ElectricEmerald,
                                        RoundedCornerShape(12.dp)
                                    )
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                                "Auto",
                                fontSize = 10.sp,
                                color = if (isAuto) CyberCyan else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

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

/**
 * SARA-inspired rounded info pill with transparent backdrop and glowing outline border.
 */
@Composable
private fun HudInfoPill(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = SurfaceDark.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * MAX-inspired circular Power-Button toggle.
 * - ON: Glowing cyan/emerald ring border with smooth pulsing radial halo.
 * - OFF: Dim muted grey outline with static non-glowing power icon.
 */
@Composable
private fun ArcPowerButton(
    isPoweredOn: Boolean,
    assistantStatus: AssistantStatus,
    disableAnimations: Boolean,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "power_btn_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isPoweredOn && !disableAnimations) 1.18f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (assistantStatus) {
                    AssistantStatus.SPEAKING, AssistantStatus.LISTENING -> 850
                    else -> 1800
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "power_halo_scale"
    )

    val ringColor = when {
        !isPoweredOn -> TextMuted.copy(alpha = 0.42f)
        assistantStatus == AssistantStatus.SPEAKING -> ElectricEmerald
        else -> ElectricCyan
    }
    val iconColor = if (isPoweredOn) ringColor else TextMuted

    Box(
        modifier = Modifier.size(78.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing glow halo only when ARC is ON
        if (isPoweredOn) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                ringColor.copy(alpha = 0.34f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Main 60dp circular power button (>= 48dp accessible touch target)
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    if (isPoweredOn) {
                        Brush.radialGradient(
                            colors = listOf(
                                ringColor.copy(alpha = 0.24f),
                                SurfaceDark.copy(alpha = 0.92f)
                            )
                        )
                    } else {
                        Brush.radialGradient(
                            colors = listOf(
                                SurfaceDark.copy(alpha = 0.55f),
                                CarbonBlack
                            )
                        )
                    }
                )
                .border(
                    width = if (isPoweredOn) 2.dp else 1.2.dp,
                    color = ringColor,
                    shape = CircleShape
                )
                .clickable(onClick = onToggle)
                .testTag("arc_power_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PowerSettingsNew,
                contentDescription = if (isPoweredOn) "Turn ARC Off" else "Turn ARC On",
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * MAX & SARA-inspired bottom circular action row:
 * Left: Screen Vision | Center: Large Glowing Mic | Right: Chat Text Input
 */
@Composable
private fun BottomHudActionRow(
    assistantStatus: AssistantStatus,
    disableAnimations: Boolean,
    onScreenVisionClick: () -> Unit,
    onMicClick: () -> Unit,
    onChatClick: () -> Unit
) {
    val isActiveMic = assistantStatus == AssistantStatus.LISTENING || assistantStatus == AssistantStatus.SPEAKING
    val infiniteTransition = rememberInfiniteTransition(label = "mic_halo")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isActiveMic && !disableAnimations) 1.18f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isActiveMic) 700 else 2200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mic_pulse_scale"
    )

    val micAccent = when (assistantStatus) {
        AssistantStatus.SPEAKING -> ElectricEmerald
        AssistantStatus.PROCESSING -> HologramBlue
        else -> ElectricCyan
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Camera / Screen-Vision Circular Action
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(SurfaceDark.copy(alpha = 0.55f))
                .border(1.dp, GlassBorderCyan, CircleShape)
                .clickable(onClick = onScreenVisionClick)
                .testTag("screen_vision_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Screen Vision",
                tint = ElectricCyan,
                modifier = Modifier.size(22.dp)
            )
        }

        // Center: Large Prominent Glowing Mic Button (MAX power-button inspired)
        Box(
            modifier = Modifier.size(86.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer pulsing halo
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .scale(haloScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                micAccent.copy(alpha = if (isActiveMic) 0.42f else 0.22f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                micAccent.copy(alpha = if (isActiveMic) 0.34f else 0.18f),
                                SurfaceDark.copy(alpha = 0.92f)
                            )
                        )
                    )
                    .border(
                        width = 1.8.dp,
                        color = micAccent.copy(alpha = if (isActiveMic) 0.95f else 0.72f),
                        shape = CircleShape
                    )
                    .clickable(onClick = onMicClick)
                    .testTag("direct_mic_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (assistantStatus == AssistantStatus.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Activate Voice Mic",
                    tint = micAccent,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Right: Chat / Text Input Circular Action
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(SurfaceDark.copy(alpha = 0.55f))
                .border(1.dp, GlassBorderCyan, CircleShape)
                .clickable(onClick = onChatClick)
                .testTag("open_chat_sheet_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = "Open Text Chat",
                tint = ElectricCyan,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Navigation Drawer containing Transcript History, Voice Terminal, Wake-Word,
 * Gemini Live toggle, Battery Saver, and Memory controls.
 */
@Composable
private fun ArcDrawerContent(
    viewModel: AriaViewModel,
    assistantStatus: AssistantStatus,
    latestResponse: String,
    lastQuery: String,
    pendingWebUrl: String?,
    voiceHistory: List<com.example.data.VoiceHistoryEntity>,
    isMuted: Boolean,
    isSpeaking: Boolean,
    isWakeWordModeEnabled: Boolean,
    isGeminiLiveMode: Boolean,
    geminiLiveState: GeminiLiveSessionState,
    batteryLevel: Int,
    isCharging: Boolean,
    isBatterySaverActive: Boolean,
    onOpenBatteryDialog: () -> Unit,
    onOpenAddMemory: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    val isLiveConnected = geminiLiveState == GeminiLiveSessionState.CONNECTED ||
            geminiLiveState == GeminiLiveSessionState.LISTENING ||
            geminiLiveState == GeminiLiveSessionState.SPEAKING

    ModalDrawerSheet(
        drawerContainerColor = CarbonBlack,
        drawerContentColor = TextPrimary,
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Drawer Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ARC CONTROL",
                        color = ElectricCyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.5.sp
                    )
                    Text(
                        text = "SYSTEMS & TRANSCRIPT",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
                IconButton(
                    onClick = onCloseDrawer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Drawer",
                        tint = TextSecondary
                    )
                }
            }

            HorizontalDivider(color = GlassBorderCyan.copy(alpha = 0.35f))

            // System Toggles Grid (Wake-Word, Gemini Live, Battery, Standby)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DrawerToggleChip(
                    label = if (isWakeWordModeEnabled) "WAKE: ON" else "WAKE: OFF",
                    isActive = isWakeWordModeEnabled,
                    activeColor = ElectricEmerald,
                    onClick = { viewModel.toggleWakeWordMode() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wake_word_toggle_badge")
                )
                DrawerToggleChip(
                    label = if (isLiveConnected) "LIVE: ON" else if (isGeminiLiveMode) "LIVE..." else "GEMINI LIVE",
                    isActive = isLiveConnected || isGeminiLiveMode,
                    activeColor = CyberCyan,
                    onClick = { viewModel.toggleGeminiLiveMode() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("gemini_live_mode_badge")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DrawerToggleChip(
                    label = if (isBatterySaverActive) "ECO $batteryLevel%" else "PWR $batteryLevel%",
                    isActive = isCharging || isBatterySaverActive,
                    activeColor = if (isBatterySaverActive) WarningAmber else ElectricEmerald,
                    onClick = onOpenBatteryDialog,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("battery_gauge_button")
                )
                DrawerToggleChip(
                    label = if (assistantStatus == AssistantStatus.SLEEP) "WAKE UP" else "STANDBY",
                    isActive = assistantStatus != AssistantStatus.SLEEP,
                    activeColor = ElectricCyan,
                    onClick = { viewModel.toggleSleepMode() },
                    modifier = Modifier.weight(1f)
                )
            }

            // Voice Controls (Mute / Replay / Brief / Memory)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DrawerToggleChip(
                    label = if (isMuted) "MUTED" else "VOICE ON",
                    isActive = !isMuted,
                    activeColor = if (isMuted) WarningAmber else ElectricEmerald,
                    onClick = { viewModel.toggleTtsMute() },
                    modifier = Modifier.weight(1f)
                )
                DrawerToggleChip(
                    label = "BRIEFING",
                    isActive = true,
                    activeColor = ReactorGold,
                    onClick = {
                        onCloseDrawer()
                        viewModel.triggerBriefingSummary()
                    },
                    modifier = Modifier.weight(1f)
                )
                DrawerToggleChip(
                    label = "+ MEMORY",
                    isActive = false,
                    activeColor = HologramBlue,
                    onClick = onOpenAddMemory,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = GlassBorderCyan.copy(alpha = 0.35f))

            // Latest Voice Terminal Response
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF070E1B))
                    .border(1.dp, GlassBorderCyan, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LATEST OUTPUT",
                            color = ElectricCyan,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Replay",
                                color = ElectricCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.clickable { viewModel.replaySpeech() }
                            )
                            if (voiceHistory.isNotEmpty()) {
                                Text(
                                    text = "Clear",
                                    color = WarningAmber,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.clickable { viewModel.clearVoiceHistory() }
                                )
                            }
                        }
                    }
                    if (lastQuery.isNotBlank()) {
                        Text(
                            text = "YOU: $lastQuery",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = latestResponse,
                        color = if (isSpeaking) ElectricCyan else TextPrimary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                    if (pendingWebUrl != null) {
                        TextButton(
                            onClick = { viewModel.openWebUrlInApp(pendingWebUrl) },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Open Link", color = ElectricCyan, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Transcript History List
            Text(
                text = "TRANSCRIPT (${voiceHistory.size})",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (voiceHistory.isEmpty()) {
                    item {
                        Text(
                            text = "No conversations recorded yet.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    items(voiceHistory.take(15)) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(GlassBackground)
                                .border(1.dp, GlassBorderBlue.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "YOU: ${item.query}",
                                color = ElectricCyan,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = item.response,
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerToggleChip(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) activeColor.copy(alpha = 0.16f) else GlassBackground)
            .border(
                1.dp,
                if (isActive) activeColor.copy(alpha = 0.7f) else GlassBorderCyan.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isActive) activeColor else TextSecondary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
