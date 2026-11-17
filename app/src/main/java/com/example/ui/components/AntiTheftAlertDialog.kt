package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.assistant.security.AntiTheftSirenManager
import com.example.assistant.security.PermissionCategory
import com.example.assistant.security.PermissionUsageLogManager
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.io.File

/**
 * Visible, Transparent Anti-Theft Alert Dialog:
 * Displayed whenever AntiTheftSirenManager reaches SirenGuardState.TRIGGERED.
 *
 * Policy & Transparency Guarantee:
 * - Shows an unmistakable live front-camera preview (never silent or hidden) while capturing
 *   a visible alert snapshot using CameraX DEFAULT_FRONT_CAMERA.
 * - Provides an accessible one-tap DISARM button alongside voice disarm ("security off karo").
 */
@Composable
fun AntiTheftAlertDialog(
    reason: String,
    onDisarmClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val capturedPhotoPath by AntiTheftSirenManager.capturedAlertPhotoPath.collectAsState()
    var cameraError by remember { mutableStateOf<String?>(null) }

    val hasCameraPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(2.dp, Color(0xFFEF4444)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Security Alert",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "ANTI-THEFT SIREN ACTIVE",
                        color = Color(0xFFEF4444),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = reason,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                // Visible front camera preview / captured alert photo frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(DeepSpace)
                        .border(1.dp, WarningAmber.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val photoFile = capturedPhotoPath?.let { File(it) }
                    if (photoFile != null && photoFile.exists()) {
                        val bitmap = remember(capturedPhotoPath) {
                            BitmapFactory.decodeFile(photoFile.absolutePath)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Captured Security Alert Snapshot",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Text(
                                text = "VISIBLE ALERT PHOTO CAPTURED",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    } else if (hasCameraPermission && cameraError == null) {
                        val previewView = remember { PreviewView(context) }

                        DisposableEffect(lifecycleOwner) {
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                            val executor = ContextCompat.getMainExecutor(context)
                            var cameraProvider: ProcessCameraProvider? = null

                            cameraProviderFuture.addListener({
                                try {
                                    cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val imageCapture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()

                                    cameraProvider?.unbindAll()
                                    cameraProvider?.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_FRONT_CAMERA,
                                        preview,
                                        imageCapture
                                    )

                                    val outDir = File(context.cacheDir, "arc_security_photos").apply {
                                        if (!exists()) mkdirs()
                                    }
                                    val outFile = File(outDir, "ALERT_${System.currentTimeMillis()}.jpg")
                                    val outputOpts = ImageCapture.OutputFileOptions.Builder(outFile).build()

                                    imageCapture.takePicture(
                                        outputOpts,
                                        executor,
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                AntiTheftSirenManager.recordVisibleAlertPhoto(outFile.absolutePath)
                                                PermissionUsageLogManager.logUsage(
                                                    context = context,
                                                    category = PermissionCategory.CAMERA,
                                                    action = "Anti-Theft Alert Photo",
                                                    reason = "Visible front-camera alert snapshot triggered"
                                                )
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                Log.w("AntiTheftAlertDialog", "Front camera capture skipped: ${exception.message}")
                                                cameraError = "Camera capture unavailable on this device."
                                            }
                                        }
                                    )
                                } catch (e: Exception) {
                                    Log.w("AntiTheftAlertDialog", "Camera bind error: ${e.message}")
                                    cameraError = "Front camera unavailable."
                                }
                            }, executor)

                            onDispose {
                                try {
                                    cameraProvider?.unbindAll()
                                } catch (_: Exception) {
                                }
                            }
                        }

                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            text = "VISIBLE FRONT CAMERA ACTIVE",
                            color = WarningAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraFront,
                                contentDescription = "Front Camera Notice",
                                tint = CyberCyan,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = cameraError ?: "Camera permission allow karne par visible security snapshot save hoga.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onDisarmClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("disarm_security_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Disarm Security",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SECURITY OFF (DISARM)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
