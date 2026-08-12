package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceVariantDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

/**
 * Data class representing individual permission item with icon, title, description, and android manifest keys.
 */
data class PermissionItemInfo(
    val id: String,
    val title: String,
    val explanation: String,
    val icon: ImageVector,
    val manifestPermissions: List<String>,
    val isBatteryOptimization: Boolean = false
)

@Composable
fun MandatoryPermissionsScreen(
    userName: String,
    onPermissionsCompleted: () -> Unit
) {
    val context = LocalContext.current

    // Prepare list of required permissions for A.R.I.A. app
    val permissionList = remember {
        val list = mutableListOf(
            PermissionItemInfo(
                id = "mic",
                title = "Microphone / Audio",
                explanation = "Voice commands aur ARIA voice recognition ke liye zaroori hai",
                icon = Icons.Default.Mic,
                manifestPermissions = listOf(Manifest.permission.RECORD_AUDIO)
            ),
            PermissionItemInfo(
                id = "notifications",
                title = "Notifications",
                explanation = "Reminders, scheduled alerts aur voice updates ke liye",
                icon = Icons.Default.Notifications,
                manifestPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else emptyList()
            ),
            PermissionItemInfo(
                id = "contacts",
                title = "Contacts & Phone Calls",
                explanation = "Voice commands par contacts call ya message karne ke liye",
                icon = Icons.Default.Contacts,
                manifestPermissions = listOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.CALL_PHONE
                )
            ),
            PermissionItemInfo(
                id = "location",
                title = "Location",
                explanation = "Mausam, live navigation aur local tools ke liye",
                icon = Icons.Default.LocationOn,
                manifestPermissions = listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            ),
            PermissionItemInfo(
                id = "storage",
                title = "Files & Media",
                explanation = "Media, photos aur documents search/storage ke liye",
                icon = Icons.Default.Folder,
                manifestPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO
                    )
                } else {
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            ),
            PermissionItemInfo(
                id = "battery",
                title = "Background Execution",
                explanation = "Background reminders aur instant voice assistant readiness ke liye",
                icon = Icons.Default.BatteryAlert,
                manifestPermissions = emptyList(),
                isBatteryOptimization = true
            )
        )
        list.filter { it.manifestPermissions.isNotEmpty() || it.isBatteryOptimization }
    }

    // Map to keep track of permission status (Granted = true, Pending/Denied = false)
    val permissionStates = remember { mutableStateMapOf<String, Boolean>() }
    var hasAttemptedRequest by remember { mutableStateOf(false) }
    var showDenialWarning by remember { mutableStateOf(false) }

    // Helper function to check if a specific permission item is granted
    fun checkItemGranted(item: PermissionItemInfo): Boolean {
        if (item.isBatteryOptimization) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            } else {
                true
            }
        }
        return item.manifestPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Refresh all permission statuses
    fun refreshPermissions() {
        var allGranted = true
        permissionList.forEach { item ->
            val granted = checkItemGranted(item)
            permissionStates[item.id] = granted
            if (!granted) allGranted = false
        }
        if (hasAttemptedRequest && !allGranted) {
            showDenialWarning = true
        } else if (allGranted) {
            showDenialWarning = false
        }
    }

    // Check permissions on screen load
    LaunchedEffect(Unit) {
        refreshPermissions()
    }

    // Permission launcher for multiple Android permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        refreshPermissions()
    }

    // Function to trigger system permission dialogs for all ungranted permissions
    fun requestAllPermissions() {
        hasAttemptedRequest = true
        val ungrantedManifestPermissions = permissionList
            .flatMap { it.manifestPermissions }
            .filter { perm ->
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
            }
            .distinct()

        if (ungrantedManifestPermissions.isNotEmpty()) {
            permissionLauncher.launch(ungrantedManifestPermissions.toTypedArray())
        }

        // Request battery optimization exemption if needed
        val batteryItem = permissionList.find { it.isBatteryOptimization }
        if (batteryItem != null && !checkItemGranted(batteryItem)) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                // Fallback if direct package intent is rejected by OEM
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            }
        }
    }

    // Function to open app details in Android Settings
    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    val totalCount = permissionList.size
    val grantedCount = permissionList.count { permissionStates[it.id] == true }
    val isAllGranted = grantedCount == totalCount

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(CyberCyan.copy(alpha = 0.12f))
                        .border(1.5.dp, CyberCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Security Permissions",
                        tint = CyberCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "A.R.I.A. PERMISSIONS SETUP",
                    color = CyberCyan,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Namaste $userName! ARIA ko full functionality ke liye niche diye gaye permissions ki zaroorat hai.",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            // Progress Bar Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceVariantDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Permission Grant Status: $grantedCount / $totalCount",
                            color = if (isAllGranted) ElectricEmerald else WarningAmber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { grantedCount.toFloat() / totalCount.toFloat() },
                            color = if (isAllGranted) ElectricEmerald else CyberCyan,
                            trackColor = SurfaceVariantDark,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    if (isAllGranted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "All Granted",
                            tint = ElectricEmerald,
                            modifier = Modifier.size(26.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Pending",
                            tint = WarningAmber,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Friendly Denial Warning Box (if user denied any permissions)
            AnimatedVisibility(
                visible = showDenialWarning && !isAllGranted,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = WarningAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Permissions Required for ARIA Features",
                                color = WarningAmber,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aapne kuch permissions allow nahi ki hain. Voice commands, reminders aur location functions ke liye ye zaroori hain. Kripya 'Allow All' par click karein ya Settings se allow karein.",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { openAppSettings() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "Open Settings",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open App Settings", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // List of Permission Items
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(permissionList) { item ->
                    val isGranted = permissionStates[item.id] == true

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isGranted) SurfaceDark else SurfaceDark.copy(alpha = 0.85f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isGranted) ElectricEmerald.copy(alpha = 0.5f) else SurfaceVariantDark
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            // Icon Container
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isGranted) ElectricEmerald.copy(alpha = 0.15f) else CyberCyan.copy(alpha = 0.1f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = if (isGranted) ElectricEmerald else CyberCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Title & 1-Line Explanation
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = item.title,
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.explanation,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Status Chip / Action
                            if (isGranted) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(ElectricEmerald.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Granted",
                                        color = ElectricEmerald,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        hasAttemptedRequest = true
                                        if (item.isBatteryOptimization) {
                                            try {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                        data = Uri.parse("package:${context.packageName}")
                                                    }
                                                    context.startActivity(intent)
                                                }
                                            } catch (e: Exception) {
                                                openAppSettings()
                                            }
                                        } else {
                                            permissionLauncher.launch(item.manifestPermissions.toTypedArray())
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberCyan.copy(alpha = 0.2f),
                                        contentColor = CyberCyan
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Section
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // "ALLOW ALL PERMISSIONS" Primary Action Button
                if (!isAllGranted) {
                    Button(
                        onClick = { requestAllPermissions() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = DeepSpace
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("allow_all_permissions_button")
                    ) {
                        Text(
                            text = "ALLOW ALL PERMISSIONS",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // "PROCEED TO ARIA HOME" Mandatory Completion Button
                Button(
                    onClick = {
                        // Refresh to make sure current state is up to date
                        refreshPermissions()
                        if (isAllGranted) {
                            onPermissionsCompleted()
                        } else {
                            hasAttemptedRequest = true
                            showDenialWarning = true
                            // Trigger permission dialogs for remaining
                            requestAllPermissions()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAllGranted) ElectricEmerald else SurfaceDark,
                        contentColor = if (isAllGranted) DeepSpace else TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (!isAllGranted) androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("continue_to_app_button")
                ) {
                    Text(
                        text = if (isAllGranted) "CONTINUE TO ARIA HOME 🚀" else "GRANT & PROCEED",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
