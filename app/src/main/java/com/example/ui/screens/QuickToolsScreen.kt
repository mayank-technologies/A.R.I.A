package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AriaViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import androidx.compose.material.icons.filled.Code
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Info
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.widget.Toast
import com.example.assistant.background.AriaBackgroundWakeService
import com.example.assistant.glyph.AriaGlyphHardwareManager
import com.example.assistant.overlay.AriaEdgeGlowOverlayManager
import com.example.assistant.overlay.AriaEdgeGlowView
import com.example.assistant.screenshare.AriaScreenShareManager
import com.example.assistant.accessibility.AriaAccessibilityGestureService
import com.example.assistant.accessibility.GestureResult
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.example.assistant.battery.AriaBatterySaverManager

@Composable
fun QuickToolsScreen(
    viewModel: AriaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userName by viewModel.userName.collectAsState()
    val isSharingActive by AriaScreenShareManager.isSharingActive.collectAsState()
    val isBackgroundWakeRunning by AriaBackgroundWakeService.isRunning.collectAsState()
    val isGlowActive by AriaEdgeGlowOverlayManager.isGlowActive.collectAsState()
    val isNothingPhone by AriaGlyphHardwareManager.isNothingPhone.collectAsState()
    val isAccessibilityActive by AriaAccessibilityGestureService.isServiceEnabled.collectAsState()

    val isBatterySaverActive by AriaBatterySaverManager.isBatterySaverActive.collectAsState()
    val batteryLevel by AriaBatterySaverManager.batteryLevel.collectAsState()
    val isCharging by AriaBatterySaverManager.isCharging.collectAsState()
    val manualOverride by AriaBatterySaverManager.manualOverride.collectAsState()

    var showDeveloperHubDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var showIosInfoDialog by remember { mutableStateOf(false) }
    var showAccessibilityOnboardingDialog by remember { mutableStateOf(false) }

    var editNameText by remember(userName) { mutableStateOf(userName) }
    var weatherCity by remember { mutableStateOf("Delhi") }
    var wikiSearchText by remember { mutableStateOf("") }
    var calcInput by remember { mutableStateOf("") }
    var calcResult by remember { mutableStateOf("0") }
    var notepadText by remember { mutableStateOf("") }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            AriaScreenShareManager.startScreenShare(context, result.resultCode, result.data!!)
            Toast.makeText(context, "Screen sharing started with floating overlay", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Screen capture permission canceled", Toast.LENGTH_SHORT).show()
        }
    }

    if (showOverlayPermissionDialog) {
        Dialog(onDismissRequest = { showOverlayPermissionDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Overlay Permission",
                        tint = CyberCyan,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Display Over Other Apps Permission",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ARIA needs 'Draw Over Other Apps' (SYSTEM_ALERT_WINDOW) permission to show the WhatsApp-style floating overlay control while screen sharing in background.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showOverlayPermissionDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showOverlayPermissionDialog = false
                                AriaScreenShareManager.openOverlayPermissionSettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                        ) {
                            Text("Grant Permission", color = DeepSpace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showIosInfoDialog) {
        Dialog(onDismissRequest = { showIosInfoDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "iOS Information",
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "iOS Screen Sharing Architecture",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "1. ReplayKit Framework: iOS uses ReplayKit & RPBroadcastSampleHandler instead of Android MediaProjection.\n\n" +
                                "2. System Broadcast Picker: iOS requires launching RPSystemBroadcastPickerView so the user starts capture via iOS Control Center.\n\n" +
                                "3. No Floating Overlay: iOS strictly forbids drawing custom floating windows (SYSTEM_ALERT_WINDOW) over other apps due to sandboxing security. Controls are shown in the iOS status bar red recording banner or Control Center.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showIosInfoDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Understood", color = DeepSpace, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showAccessibilityOnboardingDialog) {
        Dialog(onDismissRequest = { showAccessibilityOnboardingDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(CyberCyan.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Accessibility",
                            tint = CyberCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Voice Gestures & Accessibility",
                        color = CyberCyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚡ What this enables:\n" +
                                "• Say 'scroll this reel/shorts' or 'next video' to swipe up automatically in YouTube Shorts, Instagram Reels, TikTok.\n" +
                                "• Say 'pause this video' or 'play video' to tap and toggle playback in any media app.\n" +
                                "• Say 'like this reel' for hands-free double tap.\n\n" +
                                "🔒 Privacy & Transparency:\n" +
                                "• Runs 100% on-device using Android's standard Gesture Dispatch API.\n" +
                                "• ARIA NEVER records your passwords, keystrokes, personal chats, or screen contents.\n\n" +
                                "🍎 iOS Note:\n" +
                                "• This system-wide gesture automation is EXCLUSIVE to Android. iOS Apple sandbox policy strictly blocks third-party automated gestures.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAccessibilityOnboardingDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Dismiss", color = TextSecondary, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                showAccessibilityOnboardingDialog = false
                                AriaAccessibilityGestureService.openAccessibilitySettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Text("Open Settings", color = DeepSpace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (showEditNameDialog) {
        Dialog(onDismissRequest = { showEditNameDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Edit Profile Name", color = CyberCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editNameText,
                        onValueChange = { editNameText = it },
                        placeholder = { Text("Enter your name...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CyberCyan.copy(alpha = 0.3f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showEditNameDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark)
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (editNameText.isNotBlank()) {
                                    viewModel.completeOnboarding(editNameText)
                                    showEditNameDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                        ) {
                            Text("Save Name", color = DeepSpace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showDeveloperHubDialog) {
        Dialog(
            onDismissRequest = { showDeveloperHubDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepSpace)
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🐍 Developer Hub & Guides",
                            color = CyberCyan,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showDeveloperHubDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Close", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    PythonTutorialHubScreen()
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "A.R.I.A. Tools & Utilities",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Profile Settings, Screen Sharing, Weather, Wikipedia, Calculator & Notepad",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        // WhatsApp-Style Screen Share Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSharingActive) Color(0xFFEF4444) else CyberCyan.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isSharingActive) Color(0xFFEF4444).copy(alpha = 0.2f)
                                    else CyberCyan.copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSharingActive) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                                contentDescription = "Screen Share",
                                tint = if (isSharingActive) Color(0xFFEF4444) else CyberCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "📱 WhatsApp Screen Share",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (isSharingActive) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444))
                                    )
                                }
                            }
                            Text(
                                text = if (isSharingActive) "Screen sharing LIVE with floating overlay control"
                                else "MediaProjection capture with floating bubble & 1-tap close",
                                color = if (isSharingActive) Color(0xFFEF4444) else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSharingActive) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        IconButton(onClick = { showIosInfoDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "iOS Info",
                                tint = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isSharingActive) {
                            Button(
                                onClick = {
                                    AriaScreenShareManager.stopScreenShare(context)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.StopScreenShare,
                                    contentDescription = "Stop",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop Screen Sharing", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (!AriaScreenShareManager.isOverlayPermissionGranted(context)) {
                                        showOverlayPermissionDialog = true
                                    } else {
                                        screenCaptureLauncher.launch(
                                            AriaScreenShareManager.getScreenCaptureIntent(context)
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenShare,
                                    contentDescription = "Start",
                                    tint = DeepSpace,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share Screen", color = DeepSpace, fontWeight = FontWeight.Bold)
                            }

                            if (!AriaScreenShareManager.isOverlayPermissionGranted(context)) {
                                Button(
                                    onClick = { showOverlayPermissionDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = "Permission",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Overlay Perm", color = CyberCyan, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Background Always-Listening Wake Word & Edge Glow Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isBackgroundWakeRunning) ElectricEmerald else CyberCyan.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isBackgroundWakeRunning) ElectricEmerald.copy(alpha = 0.2f)
                                    else CyberCyan.copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isBackgroundWakeRunning) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = "Background Wake Word",
                                tint = if (isBackgroundWakeRunning) ElectricEmerald else CyberCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🎙️ Background Always-Listening",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (isBackgroundWakeRunning) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(ElectricEmerald)
                                    )
                                }
                            }
                            Text(
                                text = if (isBackgroundWakeRunning) "Foreground Service ACTIVE: Say 'Hey ARIA' anytime"
                                else "Listen for 'Hey ARIA' even when app is minimized",
                                color = if (isBackgroundWakeRunning) ElectricEmerald else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isBackgroundWakeRunning) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        Switch(
                            checked = isBackgroundWakeRunning,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    AriaBackgroundWakeService.start(context)
                                    Toast.makeText(context, "ARIA Background Voice Engine started! Say 'Hey ARIA'", Toast.LENGTH_SHORT).show()
                                } else {
                                    AriaBackgroundWakeService.stop(context)
                                    Toast.makeText(context, "ARIA Background Voice Engine stopped", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ElectricEmerald,
                                checkedTrackColor = ElectricEmerald.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = SurfaceVariantDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Software Screen Edge Glow Visual Indicator Controls
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LightMode,
                                    contentDescription = "Screen Edge Glow",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "✨ Screen Edge Glow Overlay (SYSTEM_ALERT_WINDOW)",
                                    color = CyberCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Subtle cyan glowing border lights up across your screen whenever ARIA detects wake word, listens or speaks (works over ANY app).",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                            if (isNothingPhone) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "📱 Nothing Phone Detected: Physical Glyph Matrix LED animation supported.",
                                    color = ElectricEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (!AriaEdgeGlowOverlayManager.isOverlayPermissionGranted(context)) {
                                            showOverlayPermissionDialog = true
                                        } else {
                                            AriaEdgeGlowOverlayManager.pulseForDuration(context, durationMs = 4000)
                                            Toast.makeText(context, "Showing 4-sec Edge Glow pulse preview!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Test Glow",
                                        tint = DeepSpace,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Test Edge Glow", color = DeepSpace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                if (!AriaEdgeGlowOverlayManager.isOverlayPermissionGranted(context)) {
                                    Button(
                                        onClick = { showOverlayPermissionDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
                                    ) {
                                        Text("Grant Perm", color = CyberCyan, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Battery Saver & Power Optimization Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isBatterySaverActive) WarningAmber else CyberCyan.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isBatterySaverActive) WarningAmber.copy(alpha = 0.2f)
                                    else if (isCharging) ElectricEmerald.copy(alpha = 0.2f)
                                    else CyberCyan.copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCharging) Icons.Default.BatteryChargingFull
                                else if (isBatterySaverActive || batteryLevel <= 20) Icons.Default.BatteryAlert
                                else Icons.Default.BatteryFull,
                                contentDescription = "Battery Saver",
                                tint = if (isBatterySaverActive) WarningAmber
                                else if (isCharging) ElectricEmerald
                                else CyberCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🔋 Battery Saver Mode ($batteryLevel%)",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isBatterySaverActive) WarningAmber.copy(alpha = 0.2f)
                                            else ElectricEmerald.copy(alpha = 0.2f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isBatterySaverActive) "SAVER ON (<20%)" else "NORMAL",
                                        color = if (isBatterySaverActive) WarningAmber else ElectricEmerald,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = if (isBatterySaverActive)
                                    "Power-saving active: sync throttled & HUD animations paused"
                                else
                                    "Automatically throttles sync & pauses animations below 20%",
                                color = if (isBatterySaverActive) WarningAmber else TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Mode switch buttons (Auto / Force ON / Force OFF)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isAuto = manualOverride == null
                        Button(
                            onClick = {
                                AriaBatterySaverManager.resetToAutoMode(context)
                                Toast.makeText(context, "Battery Saver set to AUTO mode (<20%)", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAuto) CyberCyan.copy(alpha = 0.3f) else SurfaceVariantDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isAuto) CyberCyan else Color.Transparent)
                        ) {
                            Text("Auto (<20%)", fontSize = 11.sp, color = if (isAuto) CyberCyan else TextSecondary, fontWeight = FontWeight.Bold)
                        }

                        val isForceOn = manualOverride == true
                        Button(
                            onClick = {
                                AriaBatterySaverManager.setManualOverride(true, context)
                                Toast.makeText(context, "Battery Saver FORCED ON", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isForceOn) WarningAmber.copy(alpha = 0.3f) else SurfaceVariantDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isForceOn) WarningAmber else Color.Transparent)
                        ) {
                            Text("Force ON", fontSize = 11.sp, color = if (isForceOn) WarningAmber else TextSecondary, fontWeight = FontWeight.Bold)
                        }

                        val isForceOff = manualOverride == false
                        Button(
                            onClick = {
                                AriaBatterySaverManager.setManualOverride(false, context)
                                Toast.makeText(context, "Battery Saver FORCED OFF", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isForceOff) ElectricEmerald.copy(alpha = 0.3f) else SurfaceVariantDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isForceOff) ElectricEmerald else Color.Transparent)
                        ) {
                            Text("Off", fontSize = 11.sp, color = if (isForceOff) ElectricEmerald else TextSecondary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Protections Breakdown
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceVariantDark.copy(alpha = 0.5f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "⚡ Optimizations in effect when active:",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "• Background speech loop throttles delay to 3,500ms+ (prevents CPU drain).",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "• Arc Reactor HUD, pulse rings & Edge Glow pause continuous GPU drawing.",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "• Say voice command: 'Battery saver on/off' or 'Battery status'.",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Voice Gesture Automation (Accessibility Service) Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isAccessibilityActive) ElectricEmerald else CyberCyan.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    if (isAccessibilityActive) ElectricEmerald.copy(alpha = 0.2f)
                                    else CyberCyan.copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = "Voice Gestures",
                                tint = if (isAccessibilityActive) ElectricEmerald else CyberCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Voice Screen Gestures",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isAccessibilityActive) ElectricEmerald.copy(alpha = 0.2f) else WarningAmber.copy(alpha = 0.2f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isAccessibilityActive) "Active" else "Action Needed",
                                        color = if (isAccessibilityActive) ElectricEmerald else WarningAmber,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Automated Reels scroll & Video play/pause gestures",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        IconButton(onClick = { showAccessibilityOnboardingDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Accessibility Info",
                                tint = CyberCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Say 'scroll this reel/shorts' or 'pause this video' over YouTube, Instagram, etc. to dispatch simulated swipe/tap gestures hands-free!",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!AriaAccessibilityGestureService.isServiceRunning() && !AriaAccessibilityGestureService.checkAccessibilityEnabled(context)) {
                                    showAccessibilityOnboardingDialog = true
                                } else {
                                    AriaAccessibilityGestureService.scrollNextVideo { res ->
                                        when (res) {
                                            is GestureResult.Success -> Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                            is GestureResult.Failure -> Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardDoubleArrowUp,
                                contentDescription = "Swipe Up",
                                tint = DeepSpace,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Scroll", color = DeepSpace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                if (!AriaAccessibilityGestureService.isServiceRunning() && !AriaAccessibilityGestureService.checkAccessibilityEnabled(context)) {
                                    showAccessibilityOnboardingDialog = true
                                } else {
                                    AriaAccessibilityGestureService.togglePlayPauseVideo { res ->
                                        when (res) {
                                            is GestureResult.Success -> Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                            is GestureResult.Failure -> Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricEmerald),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Tap Center",
                                tint = DeepSpace,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Pause", color = DeepSpace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                AriaAccessibilityGestureService.openAccessibilitySettings(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceDark),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
                        ) {
                            Text("Settings", color = CyberCyan, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 0. User Profile & Saved Name Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(CyberCyan.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User Profile",
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "User Profile: ${if (userName.isBlank()) "Boss" else userName}",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Saved in SharedPreferences for personalized voice greetings.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Row {
                        IconButton(
                            onClick = {
                                editNameText = userName
                                showEditNameDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = "Edit Name",
                                tint = CyberCyan
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.resetOnboarding()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Onboarding",
                                tint = WarningAmber
                            )
                        }
                    }
                }
            }
        }

        // 0. Developer Hub & Python Guides Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDeveloperHubDialog = true },
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(CyberCyan.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Developer Hub",
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🐍 Python & Flutter Developer Hub",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Guides, Python Backend Code, Wake Word, GPS & Calendar Sync",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Button(
                        onClick = { showDeveloperHubDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Open", color = DeepSpace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 1. Weather Module Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceVariantDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = "Weather",
                            tint = WarningAmber,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live Weather Forecast (Public Weather API)",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = weatherCity,
                            onValueChange = { weatherCity = it },
                            placeholder = { Text("Enter city...", color = TextSecondary) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceVariantDark,
                                unfocusedContainerColor = SurfaceVariantDark,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CyberCyan.copy(alpha = 0.3f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                viewModel.submitTextCommand("Weather in $weatherCity")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "Check", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 2. Wikipedia Search Module Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "Wikipedia",
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Wikipedia Instant Knowledge",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = wikiSearchText,
                            onValueChange = { wikiSearchText = it },
                            placeholder = { Text("Search Wikipedia...", color = TextSecondary) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceVariantDark,
                                unfocusedContainerColor = SurfaceVariantDark,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CyberCyan.copy(alpha = 0.3f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (wikiSearchText.isNotBlank()) {
                                    viewModel.submitTextCommand("Wikipedia: $wikiSearchText")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
        }

        // 3. Quick Web Browser Launchers
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Browser Launchers",
                            tint = ElectricEmerald,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Browser & App Shortcuts",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        val shortcuts = listOf(
                            "YouTube" to "https://youtube.com",
                            "Google" to "https://google.com",
                            "GitHub" to "https://github.com",
                            "Maps" to "https://maps.google.com"
                        )

                        shortcuts.forEach { (label, url) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceVariantDark)
                                    .border(1.dp, CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.openWebUrlInApp(url)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = label,
                                        color = CyberCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = label,
                                        tint = CyberCyan,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Quick Calculator Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "Calculator",
                            tint = ElectricCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ARIA Math Calculator",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = calcInput,
                            onValueChange = { calcInput = it },
                            placeholder = { Text("e.g. 125 * 45", color = TextSecondary) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceVariantDark,
                                unfocusedContainerColor = SurfaceVariantDark,
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = CyberCyan.copy(alpha = 0.3f),
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                calcResult = try {
                                    evaluateSimpleExpression(calcInput)
                                } catch (e: Exception) {
                                    "Error"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "=", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Result: $calcResult",
                        color = ElectricEmerald,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 5. Quick Notepad Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Notepad",
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ARIA Quick Notepad",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = notepadText,
                        onValueChange = { notepadText = it },
                        placeholder = { Text("Write quick notes or code snippets...", color = TextSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceVariantDark,
                            unfocusedContainerColor = SurfaceVariantDark,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = CyberCyan.copy(alpha = 0.3f),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
    }
}

fun evaluateSimpleExpression(expr: String): String {
    val clean = expr.replace(" ", "")
    return when {
        clean.contains("+") -> {
            val parts = clean.split("+")
            (parts[0].toDouble() + parts[1].toDouble()).toString()
        }
        clean.contains("-") -> {
            val parts = clean.split("-")
            (parts[0].toDouble() - parts[1].toDouble()).toString()
        }
        clean.contains("*") -> {
            val parts = clean.split("*")
            (parts[0].toDouble() * parts[1].toDouble()).toString()
        }
        clean.contains("/") -> {
            val parts = clean.split("/")
            (parts[0].toDouble() / parts[1].toDouble()).toString()
        }
        else -> clean
    }
}
