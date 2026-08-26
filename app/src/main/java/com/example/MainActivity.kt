package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.assistant.screenshare.AriaScreenShareManager
import com.example.ui.AriaViewModel
import com.example.ui.components.AriaWebViewDialog
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.QuickToolsScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.VoiceHudScreen
import com.example.ui.theme.AriaTheme
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private val viewModel: AriaViewModel by viewModels()

    /**
     * Native MediaProjection ActivityResultLauncher for screen capture system permission dialog.
     */
    lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register MediaProjection Screen Capture launcher
        screenCaptureLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                AriaScreenShareManager.startScreenShare(this, result.resultCode, result.data!!)
                Toast.makeText(this, "Screen sharing started with floating overlay", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Screen capture permission canceled", Toast.LENGTH_SHORT).show()
            }
        }

        // Initialize MethodChannel Bridge
        methodChannelBridge = MethodChannelBridge(this)

        // Initialize Notification Channel for scheduled reminders & weather
        com.example.notification.AriaNotificationScheduler.createNotificationChannel(this)

        // Initialize Battery Saver & Power Optimization Manager (<20% Auto Threshold)
        com.example.assistant.battery.AriaBatterySaverManager.init(this)

        // Schedule daily morning weather briefing notification at 8:00 AM
        com.example.notification.AriaNotificationScheduler.scheduleDailyMorningWeatherNotification(this, hourOfDay = 8, minute = 0)

        // Request POST_NOTIFICATIONS runtime permission on Android 13+ (API 33+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Check and request CALL_PHONE & READ_CONTACTS permissions if needed
        val requiredCallPermissions = arrayOf(
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_CONTACTS
        )
        val ungrantedCallPermissions = requiredCallPermissions.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (ungrantedCallPermissions.isNotEmpty()) {
            requestPermissions(ungrantedCallPermissions.toTypedArray(), 102)
        }

        enableEdgeToEdge()
        setContent {
            AriaTheme {
                val isInitializing by viewModel.isInitializing.collectAsState()
                val isOnboardingComplete by viewModel.isOnboardingComplete.collectAsState()
                val isPermissionsGranted by viewModel.isPermissionsGranted.collectAsState()
                val userName by viewModel.userName.collectAsState()

                if (isInitializing) {
                    AriaSplashScreen()
                } else if (!isOnboardingComplete) {
                    OnboardingScreen(
                        onComplete = { name ->
                            viewModel.completeOnboarding(name)
                        }
                    )
                } else if (!isPermissionsGranted) {
                    com.example.ui.screens.MandatoryPermissionsScreen(
                        userName = userName,
                        onPermissionsCompleted = {
                            viewModel.completePermissionsSetup()
                        }
                    )
                } else {
                    AriaMainApp(viewModel = viewModel)
                }
            }
        }
    }

    companion object {
        var methodChannelBridge: MethodChannelBridge? = null
            private set
    }
}

/**
 * Native MethodChannel Bridge handler for managing Screen Sharing / MediaProjection requests.
 * Supports MethodChannel channel name "com.example.aria/screenshare".
 */
class MethodChannelBridge(private val activity: MainActivity) {

    companion object {
        const val CHANNEL_NAME = "com.example.aria/screenshare"
    }

    fun handleMethodCall(
        method: String,
        arguments: Map<String, Any>? = null,
        resultCallback: ((Any?) -> Unit)? = null
    ) {
        when (method) {
            "startScreenShare", "requestScreenShare" -> {
                if (!AriaScreenShareManager.isOverlayPermissionGranted(activity)) {
                    AriaScreenShareManager.openOverlayPermissionSettings(activity)
                    resultCallback?.invoke(mapOf("status" to "overlay_permission_required"))
                } else {
                    val captureIntent = AriaScreenShareManager.getScreenCaptureIntent(activity)
                    activity.screenCaptureLauncher.launch(captureIntent)
                    resultCallback?.invoke(mapOf("status" to "permission_dialog_triggered"))
                }
            }
            "stopScreenShare" -> {
                AriaScreenShareManager.stopScreenShare(activity)
                resultCallback?.invoke(mapOf("status" to "stopped"))
            }
            "isSharingActive" -> {
                resultCallback?.invoke(AriaScreenShareManager.isSharingActive.value)
            }
            "isOverlayPermissionGranted" -> {
                resultCallback?.invoke(AriaScreenShareManager.isOverlayPermissionGranted(activity))
            }
            "openOverlaySettings" -> {
                AriaScreenShareManager.openOverlayPermissionSettings(activity)
                resultCallback?.invoke(true)
            }
            else -> {
                resultCallback?.invoke(mapOf("error" to "Method $method not implemented"))
            }
        }
    }
}

@Composable
fun AriaSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = CyberCyan,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "A.R.I.A. Initializing...",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AriaMainApp(viewModel: AriaViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    val activeWebUrl by viewModel.activeWebUrl.collectAsState()

    activeWebUrl?.let { url ->
        com.example.assistant.SmartAppOpener.launchExternalBrowser(context, url)
        viewModel.closeWebBrowser()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = CyberCyan,
                tonalElevation = 12.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice HUD",
                            tint = if (selectedTab == 0) CyberCyan else TextSecondary
                        )
                    },
                    label = {
                        Text(
                            text = "A.I. CORE",
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) CyberCyan else TextSecondary,
                            letterSpacing = 1.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = CyberCyan.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.testTag("nav_voice_hud")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Reminders",
                            tint = if (selectedTab == 1) CyberCyan else TextSecondary
                        )
                    },
                    label = {
                        Text(
                            text = "TASKS",
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) CyberCyan else TextSecondary,
                            letterSpacing = 1.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = CyberCyan.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.testTag("nav_reminders")
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = "Quick Tools",
                            tint = if (selectedTab == 2) CyberCyan else TextSecondary
                        )
                    },
                    label = {
                        Text(
                            text = "TOOLS",
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 2) CyberCyan else TextSecondary,
                            letterSpacing = 1.sp
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = CyberCyan.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.testTag("nav_quick_tools")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DeepSpace)
        ) {
            when (selectedTab) {
                0 -> VoiceHudScreen(viewModel = viewModel)
                1 -> RemindersScreen(viewModel = viewModel)
                2 -> QuickToolsScreen(viewModel = viewModel)
            }
        }
    }
}
