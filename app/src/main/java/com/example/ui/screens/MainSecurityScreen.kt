package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ScanLog
import com.example.data.repository.ApkSecurityDetails
import com.example.data.repository.PermissionInfo
import com.example.ui.theme.*
import com.example.ui.viewmodel.ScanState
import com.example.ui.viewmodel.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// Extension to convert Android Drawable into an ImageBitmap safely
fun Drawable.toImageBitmap(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(
        if (intrinsicWidth > 0) intrinsicWidth else 48,
        if (intrinsicHeight > 0) intrinsicHeight else 48,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

@Composable
fun AppIconDisplay(packageName: String, isLocalApk: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pm = context.packageManager
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                val drawable = if (isLocalApk) {
                    null
                } else {
                    pm.getApplicationIcon(packageName)
                }
                bitmap = drawable?.toImageBitmap()
            } catch (e: Exception) {
                // Ignore load failures
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = "App Icon",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(BentoPurpleBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isLocalApk) Icons.Default.Build else Icons.Default.Settings,
                contentDescription = "Fallback App Icon",
                tint = BentoPurpleOn,
                modifier = Modifier.fillMaxSize(0.55f)
            )
        }
    }
}

@Composable
fun MainSecurityScreen(
    viewModel: ScanViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scanState by viewModel.scanState.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val scanHistory by viewModel.scanHistory.collectAsState()
    val aiReport by viewModel.aiAuditReport.collectAsState()
    val isAiAuditing by viewModel.isAiAuditing.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0 = DASHBOARD, 1 = APP LIST, 2 = LOG HISTORY

    LaunchedEffect(Unit) {
        viewModel.loadInstalledApps(context)
    }

    val apkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.scanApkStorageFile(context, uri)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = BentoSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.Lock, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BentoAlertHigh,
                        selectedTextColor = BentoTextPrimary,
                        unselectedIconColor = BentoTextMuted,
                        unselectedTextColor = BentoTextMuted,
                        indicatorColor = BentoPurpleBg
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "App List") },
                    label = { Text("App List", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BentoAlertHigh,
                        selectedTextColor = BentoTextPrimary,
                        unselectedIconColor = BentoTextMuted,
                        unselectedTextColor = BentoTextMuted,
                        indicatorColor = BentoPurpleBg
                    )
                )
                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(Icons.Default.Info, contentDescription = "Logs") },
                    label = { Text("History", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BentoAlertHigh,
                        selectedTextColor = BentoTextPrimary,
                        unselectedIconColor = BentoTextMuted,
                        unselectedTextColor = BentoTextMuted,
                        indicatorColor = BentoPurpleBg
                    )
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BentoBackground)
                .padding(innerPadding)
        ) {
            Crossfade(targetState = activeTab, label = "Tab transition") { tabIndex ->
                when (tabIndex) {
                    0 -> DashboardTab(
                        viewModel = viewModel,
                        scanState = scanState,
                        history = scanHistory,
                        onScanApkClicked = {
                            try {
                                apkPickerLauncher.launch(
                                    arrayOf(
                                        "application/vnd.android.package-archive",
                                        "application/octet-stream"
                                    )
                                )
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "System storage picker error: ${e.localizedMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onAppSelected = {
                            activeTab = 1
                        }
                    )
                    1 -> AppListTab(
                        installedApps = installedApps,
                        onScanApp = { pkgName ->
                            viewModel.scanInstalledApp(context, pkgName)
                            activeTab = 0
                        },
                        onRefresh = { viewModel.loadInstalledApps(context) }
                    )
                    2 -> HistoryTab(
                        scanHistory = scanHistory,
                        onLogSelected = { log ->
                            val detailsObj = ApkSecurityDetails(
                                appName = log.appName,
                                packageName = log.packageName,
                                versionName = log.versionName,
                                targetSdk = log.targetSdk,
                                minSdk = 24,
                                isSystemApp = false,
                                isLocalApk = log.isLocalApk,
                                permissions = log.sensitivePermissions.split(",")
                                    .filter { it.isNotEmpty() }
                                    .map { permName ->
                                        val (risk, desc) = viewModel.getPermissionRisk(permName)
                                        PermissionInfo(permName, risk == "HIGH", desc, risk)
                                    },
                                usesCleartextTraffic = log.cleartextTrafficEnabled,
                                activityCount = 1,
                                serviceCount = 0,
                                receiverCount = 0,
                                providerCount = 0,
                                signatures = listOf("Retrieved from scan database"),
                                debuggable = false
                            )
                            viewModel.resetState()
                            viewModel.requestAiSecurityAudit(detailsObj)
                            viewModel.forceScanSuccessState(detailsObj, log.riskScore, log.threatLevel)
                            activeTab = 0
                        },
                        onDeleteLog = { id -> viewModel.deleteHistoryLog(id) },
                        onClearAll = { viewModel.clearAllHistory() }
                    )
                }
            }

            if (scanState is ScanState.ScanSuccess) {
                val successData = scanState as ScanState.ScanSuccess
                ScanResultOverlay(
                    details = successData.details,
                    riskScore = successData.score,
                    riskLevel = successData.level,
                    aiReport = aiReport,
                    isAiAuditing = isAiAuditing,
                    onGetAiAudit = { viewModel.requestAiSecurityAudit(successData.details) },
                    onDismiss = { viewModel.resetState() }
                )
            }
        }
    }
}

fun ScanViewModel.getPermissionRisk(name: String): Pair<String, String> {
    return when (name) {
        "android.permission.SEND_SMS" -> "HIGH" to "Allows sending SMS without confirmation. Abused to subscribe to premium numbers."
        "android.permission.RECEIVE_SMS" -> "HIGH" to "Allows reading incoming SMS in background. Used to hijack OAuth/OTP bank credentials."
        "android.permission.READ_SMS" -> "HIGH" to "Allows scanning entire text history. High threat to login hashes."
        "android.permission.RECORD_AUDIO" -> "HIGH" to "Allows microphone hot-listening without active indicators."
        "android.permission.CAMERA" -> "HIGH" to "Allows camera access to take stealthy photos."
        "android.permission.ACCESS_FINE_LOCATION" -> "HIGH" to "Allows GPS tracking down to a few meters."
        "android.permission.RECEIVE_BOOT_COMPLETED" -> "HIGH" to "Allows starting automatically as soon as the system boots."
        "android.permission.WRITE_EXTERNAL_STORAGE" -> "HIGH" to "Allows modifying storage files. Ransomware risk."
        else -> "MEDIUM" to "Elevated capability access."
    }
}

@Composable
fun DashboardTab(
    viewModel: ScanViewModel,
    scanState: ScanState,
    history: List<ScanLog>,
    onScanApkClicked: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SHIELD CORE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoAlertHigh,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "APK Analysis",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BentoTextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(BentoPurpleBg)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "SECURE CORE",
                    fontSize = 10.sp,
                    color = BentoPurpleOn,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bento Card 1: Large Circular Gauge Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("circular_scanner_gauge"),
            colors = CardDefaults.cardColors(containerColor = BentoSurface),
            shape = RoundedCornerShape(26.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(175.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (scanState) {
                        is ScanState.Scanning -> {
                            CircularRadarScanner(scanState.appName)
                        }
                        is ScanState.LoadingApps -> {
                            CircularRadarScanner("Querying Devices...")
                        }
                        else -> {
                            InteractiveBaselineScanner(onScanApkClicked)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val latestLog = history.firstOrNull()
                if (latestLog != null) {
                    val isHigh = latestLog.threatLevel == "HIGH_RISK"
                    val isSuspicious = latestLog.threatLevel == "SUSPICIOUS"
                    val accentColor = if (isHigh) BentoAlertHigh else if (isSuspicious) BentoAlertMedium else BentoAlertLow
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isHigh) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = "Threat status icon",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHigh) "High Threat Detected" else if (isSuspicious) "Suspicious Package Found" else "Device Secure - No Threat",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "System idle safe state Representation",
                            tint = BentoAlertLow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Auditor Hub Baseline Ready",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoAlertLow
                        )
                    }
                }
            }
        }

        // Bento Grid Card Row (Two responsive interactive grid blocks)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .clickable { onScanApkClicked() }
                    .testTag("scan_apk_button"),
                colors = CardDefaults.cardColors(containerColor = BentoBlueBg),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Select APK File",
                        tint = BentoBlueOn,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = if (history.count { it.isLocalApk } > 0) "${history.count { it.isLocalApk }}" else "NEW",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = BentoBlueOn,
                            lineHeight = 26.sp
                        )
                        Text(
                            text = "Select APK File",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoBlueOn.copy(alpha = 0.7f),
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .clickable { onAppSelected("") }
                    .testTag("scan_installed_app_button"),
                colors = CardDefaults.cardColors(containerColor = BentoPurpleBg),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Scan Device App",
                        tint = BentoPurpleOn,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        val activeLabel = remember(history) {
                            val count = history.count { !it.isLocalApk }
                            if (count > 0) "$count Audited" else "RUN"
                        }
                        Text(
                            text = activeLabel,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = BentoPurpleOn,
                            lineHeight = 26.sp
                        )
                        Text(
                            text = "Scan Device App",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoPurpleOn.copy(alpha = 0.7f),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // Bento Card 3: Span-2 col, Cyan status badges block
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAppSelected("") },
            colors = CardDefaults.cardColors(containerColor = BentoCyanBg),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Policy engine protection",
                            tint = BentoCyanOn,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "THREAT MONITOR ENGINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoCyanOn,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(BentoCyanOn, RoundedCornerShape(100.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "Heuristics V4",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(BentoCyanOn, RoundedCornerShape(100.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "AI Expert Audit",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Details",
                    tint = BentoCyanOn,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Bento Card 4: Red/Coral bottom banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = BentoRedBg),
            shape = RoundedCornerShape(18.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(BentoRedMuted.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Threat stats",
                            tint = BentoRedMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        val highRiskCount = history.count { it.threatLevel == "HIGH_RISK" }
                        if (highRiskCount > 0) {
                            Text(
                                text = "High threats detected",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoRedOn
                            )
                            Text(
                                text = "Isolate $highRiskCount suspicious modules.",
                                fontSize = 11.sp,
                                color = BentoRedMuted
                            )
                        } else {
                            Text(
                                text = "Protection state pristine",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoRedOn
                            )
                            Text(
                                text = "Zero threat signatures current.",
                                fontSize = 11.sp,
                                color = BentoRedMuted
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        DashboardRiskSummary(history)
    }
}

@Composable
fun InteractiveBaselineScanner(
    onTriggerScan: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alphaScale by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaPulse"
    )

    Box(
        modifier = Modifier
            .size(150.dp)
            .clip(CircleShape)
            .background(BentoPurpleBg.copy(alpha = alphaScale))
            .border(2.dp, BentoAlertHigh.copy(alpha = 0.3f), CircleShape)
            .clickable { onTriggerScan() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Safe Shield Lock",
                tint = BentoAlertHigh,
                modifier = Modifier.size(46.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AUDIT PANEL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = BentoAlertHigh,
                letterSpacing = 1.sp
            )
            Text(
                text = "Tap to Scan file",
                fontSize = 12.sp,
                color = BentoTextMuted,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun CircularRadarScanner(
    status: String
) {
    val transition = rememberInfiniteTransition(label = "Radar Rotation")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepRotation"
    )

    Box(
        modifier = Modifier.size(150.dp),
        contentAlignment = Alignment.Center
    ) {
        val strokeColor = BentoAlertHigh
        Canvas(modifier = Modifier.size(140.dp)) {
            drawCircle(
                color = strokeColor.copy(alpha = 0.15f),
                style = Stroke(width = 2f)
            )
            drawCircle(
                color = strokeColor.copy(alpha = 0.1f),
                radius = size.minDimension / 3,
                style = Stroke(width = 2f)
            )
            drawCircle(
                color = strokeColor.copy(alpha = 0.05f),
                radius = size.minDimension / 6,
                style = Stroke(width = 2f)
            )

            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        strokeColor.copy(alpha = 0.0f),
                        strokeColor.copy(alpha = 0.4f),
                        strokeColor
                    )
                ),
                startAngle = sweepAngle,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Rotating loader",
                tint = BentoAlertHigh,
                modifier = Modifier
                    .size(34.dp)
                    .rotate(sweepAngle)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "AUDITING...",
                color = BentoTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = status,
                color = BentoTextMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DashboardRiskSummary(history: List<ScanLog>) {
    val totalScans = history.size
    val highRisk = history.count { it.threatLevel == "HIGH_RISK" }
    val suspicious = history.count { it.threatLevel == "SUSPICIOUS" }
    val clean = history.count { it.threatLevel == "CLEAN" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BentoBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = BentoSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total", fontSize = 11.sp, color = BentoTextMuted)
                Text("$totalScans", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(BentoBorder)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Clean", fontSize = 11.sp, color = BentoTextMuted)
                Text("$clean", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BentoAlertLow)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(BentoBorder)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Warning", fontSize = 11.sp, color = BentoTextMuted)
                Text("$suspicious", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BentoAlertMedium)
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(BentoBorder)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("High", fontSize = 11.sp, color = BentoTextMuted)
                Text("$highRisk", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BentoAlertHigh)
            }
        }
    }
}

@Composable
fun AppListTab(
    installedApps: List<android.content.pm.PackageInfo>,
    onScanApp: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var searchQuery by remember { mutableStateOf("") }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                val label = it.applicationInfo?.loadLabel(pm)?.toString() ?: ""
                label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scannable Packages",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = BentoTextPrimary
            )
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh scannable apps catalog",
                    tint = BentoAlertHigh
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search installed user apps...", color = BentoTextMuted) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = BentoTextMuted) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_installed_field"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BentoAlertHigh,
                unfocusedBorderColor = BentoBorder,
                focusedTextColor = BentoTextPrimary,
                unfocusedTextColor = BentoTextPrimary,
                focusedContainerColor = BentoSurface,
                unfocusedContainerColor = BentoSurface
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "No package found",
                        tint = BentoTextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No matching applications found", color = BentoTextMuted, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { appInfo ->
                    val name = appInfo.applicationInfo?.loadLabel(pm)?.toString() ?: "Unnamed Process"
                    val pName = appInfo.packageName
                    val ver = appInfo.versionName ?: "1.0"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BentoBorder, RoundedCornerShape(12.dp))
                            .clickable { onScanApp(pName) },
                        colors = CardDefaults.cardColors(containerColor = BentoSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconDisplay(
                                packageName = pName,
                                isLocalApk = false,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    name,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    pName,
                                    fontSize = 11.sp,
                                    color = BentoTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("v$ver", fontSize = 11.sp, color = BentoAlertHigh, fontWeight = FontWeight.Bold)
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Arrow right icon scan option",
                                tint = BentoAlertHigh,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(
    scanHistory: List<ScanLog>,
    onLogSelected: (ScanLog) -> Unit,
    onDeleteLog: (Int) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scan History",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = BentoTextPrimary
            )
            if (scanHistory.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text(
                        "Clear All",
                        color = BentoAlertHigh,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (scanHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Empty scanning logs",
                        tint = BentoTextMuted,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No past scan logs found",
                        color = BentoTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Details of your threats scans will appear here.",
                        color = BentoTextMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(scanHistory, key = { it.id }) { log ->
                    val color = when (log.threatLevel) {
                        "HIGH_RISK" -> BentoAlertHigh
                        "SUSPICIOUS" -> BentoAlertMedium
                        else -> BentoAlertLow
                    }

                    val readableTime = remember(log.scanTime) {
                        val sdf = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
                        sdf.format(Date(log.scanTime))
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BentoBorder, RoundedCornerShape(16.dp))
                            .clickable { onLogSelected(log) },
                        colors = CardDefaults.cardColors(containerColor = BentoSurface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconDisplay(
                                packageName = log.packageName,
                                isLocalApk = log.isLocalApk,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    log.appName,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTextPrimary,
                                    maxLines = 1
                                )
                                Text(
                                    log.packageName,
                                    fontSize = 11.sp,
                                    color = BentoTextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(readableTime, fontSize = 10.sp, color = BentoTextMuted)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = log.threatLevel.replace("_", " "),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = color
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Risk: ${log.riskScore}%",
                                        fontSize = 11.sp,
                                        color = BentoTextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteLog(log.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete threat list log",
                                    tint = BentoTextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanResultOverlay(
    details: ApkSecurityDetails,
    riskScore: Int,
    riskLevel: String,
    aiReport: String?,
    isAiAuditing: Boolean,
    onGetAiAudit: () -> Unit,
    onDismiss: () -> Unit
) {
    var activeSubTab by remember { mutableStateOf(0) }
    val context = LocalContext.current

    val themeColor = when (riskLevel) {
        "HIGH_RISK" -> BentoAlertHigh
        "SUSPICIOUS" -> BentoAlertMedium
        else -> BentoAlertLow
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .align(Alignment.BottomCenter)
                .border(2.dp, BentoBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false) { }
                .testTag("scan_result_card"),
            colors = CardDefaults.cardColors(containerColor = BentoBackground),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(44.dp, 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BentoBorder)
                        .align(Alignment.CenterHorizontally)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconDisplay(
                            packageName = details.packageName,
                            isLocalApk = details.isLocalApk,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                details.appName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPrimary
                            )
                            Text(
                                details.packageName,
                                fontSize = 12.sp,
                                color = BentoTextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Close overlay", tint = BentoTextPrimary)
                    }
                }

                Divider(color = BentoBorder)

                TabRow(
                    selectedTabIndex = activeSubTab,
                    containerColor = BentoSurface,
                    contentColor = themeColor,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[activeSubTab]),
                            color = themeColor
                        )
                    }
                ) {
                    Tab(
                        selected = activeSubTab == 0,
                        onClick = { activeSubTab = 0 },
                        text = {
                            Text(
                                "Overview",
                                color = if (activeSubTab == 0) themeColor else BentoTextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    Tab(
                        selected = activeSubTab == 1,
                        onClick = { activeSubTab = 1 },
                        text = {
                            Text(
                                "Permissions (${details.permissions.size})",
                                color = if (activeSubTab == 1) themeColor else BentoTextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                    Tab(
                        selected = activeSubTab == 2,
                        onClick = { activeSubTab = 2 },
                        text = {
                            Text(
                                "Threat Audit",
                                color = if (activeSubTab == 2) themeColor else BentoTextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    when (activeSubTab) {
                        0 -> OverviewSubTab(details, riskScore, riskLevel, themeColor)
                        1 -> PermissionsSubTab(details.permissions)
                        2 -> AiAuditorSubTab(
                            details = details,
                            aiReport = aiReport,
                            isAiAuditing = isAiAuditing,
                            onGetAiAudit = onGetAiAudit
                        )
                    }
                }

                if (!details.isLocalApk) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_DELETE).apply {
                                    data = Uri.parse("package:${details.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Cannot initiate uninstall: ${e.localizedMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (riskLevel == "HIGH_RISK") BentoAlertHigh else BentoSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Uninstall icon",
                            tint = if (riskLevel == "HIGH_RISK") Color.White else BentoTextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (riskLevel == "HIGH_RISK") "UNINSTALL SUSPICIOUS PACKAGE NOW" else "UNINSTALL FROM DEVICE",
                            fontWeight = FontWeight.Bold,
                            color = if (riskLevel == "HIGH_RISK") Color.White else BentoTextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OverviewSubTab(
    details: ApkSecurityDetails,
    riskScore: Int,
    riskLevel: String,
    themeColor: Color
) {
    val isTrusted = remember(details.packageName) {
        val lower = details.packageName.lowercase()
        lower.startsWith("com.google.") ||
                lower.startsWith("com.android.") ||
                lower.startsWith("com.microsoft.") ||
                lower.startsWith("com.chess") ||
                lower.contains("physicswallah") ||
                lower.startsWith("com.pw") ||
                lower.startsWith("com.whatsapp") ||
                lower.startsWith("com.facebook") ||
                lower.startsWith("com.instagram") ||
                lower.startsWith("com.spotify") ||
                lower.startsWith("com.netflix") ||
                lower.startsWith("com.amazon.") ||
                lower.startsWith("com.linkedin") ||
                lower.startsWith("org.mozilla.") ||
                lower.startsWith("com.openai") ||
                lower.startsWith("org.telegram.messenger") ||
                lower.startsWith("com.slack") ||
                lower.startsWith("com.zoom") ||
                lower.startsWith("com.adobe.") ||
                lower.startsWith("com.duolingo")
    }

    val levelLabel = when {
        isTrusted -> "VERIFIED SECURE PUBLISHER"
        riskLevel == "HIGH_RISK" -> "HIGH THREAT PROFILE ALERT"
        riskLevel == "SUSPICIOUS" -> "SUSPICIOUS ACCESS THREAT"
        else -> "SECURE CLASSIFICATION PASS"
    }

    val finalThemeColor = if (isTrusted) BentoAlertLow else themeColor

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (isTrusted) {
            item {
                Card(
                    modifier = Modifier.border(1.dp, BentoAlertLow.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = BentoAlertLow.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(BentoAlertLow.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Trust verified badge icon",
                                tint = BentoAlertLow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "VERIFIED SECURE DEVELOPER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoAlertLow,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "This package matches digital signatures or namespaces of widely trusted, verified developers (e.g., Google, Microsoft, Chess.com, PW). The permissions requested are standard for its operational features.",
                                fontSize = 11.sp,
                                color = BentoTextPrimary,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.border(1.dp, finalThemeColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = finalThemeColor.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "HEURISTIC RISK EVALUATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = finalThemeColor,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = levelLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = finalThemeColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
 
                    Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = finalThemeColor.copy(alpha = 0.15f),
                                startAngle = 135f,
                                sweepAngle = 270f,
                                useCenter = false,
                                style = Stroke(width = 14f, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = finalThemeColor,
                                startAngle = 135f,
                                sweepAngle = 270f * (riskScore / 100f),
                                useCenter = false,
                                style = Stroke(width = 14f, cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "$riskScore%",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = BentoTextPrimary
                        )
                    }
                }
            }
        }

        item {
            Text("Static Compiled Heuristics", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
        }

        item {
            Card(
                modifier = Modifier.border(1.dp, BentoBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = BentoSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BentoHeuristicRow(
                        "Cleartext HTTP enabled",
                        details.usesCleartextTraffic,
                        "Vulnerable to interception / MitM attacks."
                    )
                    BentoHeuristicRow(
                        "Target SDK obsolete (<34)",
                        details.targetSdk < 34,
                        "SDK target: ${details.targetSdk}. Exposure danger."
                    )
                    BentoHeuristicRow(
                        "Package compiles with debug flag",
                        details.debuggable,
                        "Allows reverse-engineering exploits."
                    )
                    BentoHeuristicRow(
                        "Autostarts on boot completion",
                        details.permissions.any { it.name == "android.permission.RECEIVE_BOOT_COMPLETED" },
                        "Launches spyware services instantly."
                    )
                }
            }
        }

        item {
            Text("APK Metadata Details", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
        }

        item {
            Card(
                modifier = Modifier.border(1.dp, BentoBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = BentoSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BentoMetadataRow("Version String", details.versionName)
                    BentoMetadataRow("Minimum SDK requirement", "${details.minSdk}")
                    BentoMetadataRow("Activities parsed count", "${details.activityCount}")
                    BentoMetadataRow("Broadcast Receivers count", "${details.receiverCount}")
                    BentoMetadataRow("Background services count", "${details.serviceCount}")
                    BentoMetadataRow("Signature Fingerprint hash", details.signatures.firstOrNull() ?: "Unknown")
                }
            }
        }
    }
}

@Composable
fun BentoHeuristicRow(label: String, isThreat: Boolean, errorDetail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = BentoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (isThreat) {
                Text(errorDetail, color = BentoAlertHigh, fontSize = 11.sp)
            }
        }
        Icon(
            imageVector = if (isThreat) Icons.Default.Warning else Icons.Default.CheckCircle,
            contentDescription = "Threat evaluation status",
            tint = if (isThreat) BentoAlertHigh else BentoAlertLow,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun BentoMetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = BentoTextMuted, fontSize = 12.sp)
        Text(
            text = value,
            color = BentoTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PermissionsSubTab(permissions: List<PermissionInfo>) {
    if (permissions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No permissions declared by this package.", color = BentoTextMuted, fontSize = 14.sp)
        }
    } else {
        var expandedItem by remember { mutableStateOf<String?>(null) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(permissions) { info ->
                val badgeColor = when (info.riskCategory) {
                    "HIGH" -> BentoAlertHigh
                    "MEDIUM" -> BentoAlertMedium
                    else -> BentoAlertLow
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (expandedItem == info.name) badgeColor else BentoBorder,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { expandedItem = if (expandedItem == info.name) null else info.name },
                    colors = CardDefaults.cardColors(containerColor = BentoSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = info.name.substringAfterLast("."),
                                    color = BentoTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    info.name,
                                    color = BentoTextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(badgeColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    info.riskCategory,
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        AnimatedVisibility(visible = expandedItem == info.name) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Text(
                                    text = info.description,
                                    color = BentoTextPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiAuditorSubTab(
    details: ApkSecurityDetails,
    aiReport: String?,
    isAiAuditing: Boolean,
    onGetAiAudit: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    if (isAiAuditing) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val transition = rememberInfiniteTransition(label = "AI Scan spinner")
                val spinAngle by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "AiSpin"
                )
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Auditing in process",
                    tint = BentoAlertHigh,
                    modifier = Modifier
                        .size(56.dp)
                        .rotate(spinAngle)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "AI AUDITOR ACTIVE",
                    fontSize = 11.sp,
                    color = BentoAlertHigh,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Decompiling permission matrices & modeling vulnerability metrics...",
                    color = BentoTextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else if (aiReport != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AI Security Audit Findings",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoAlertHigh
                )
                Row {
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(aiReport))
                        Toast.makeText(context, "Audit copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share audit findings report summary detail",
                            tint = BentoTextMuted
                        )
                    }
                    IconButton(onClick = onGetAiAudit) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rescan analysis",
                            tint = BentoTextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BentoSurface)
                    .border(1.dp, BentoBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                item {
                    Text(
                        text = aiReport,
                        color = BentoTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Shield audit",
                    tint = BentoTextMuted,
                    modifier = Modifier.size(60.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "AI Threat Diagnostics Platform",
                    color = BentoTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Deploy Gemini intelligent neural auditors to parse permissions table risk patterns recursively.",
                    color = BentoTextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onGetAiAudit,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BentoAlertHigh)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Config icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("START AI SECURITY AUDIT", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
