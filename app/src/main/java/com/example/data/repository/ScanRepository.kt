package com.example.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.example.data.database.ScanLog
import com.example.data.database.ScanLogDao
import com.example.data.network.Content
import com.example.data.network.GenerateContentRequest
import com.example.data.network.Part
import com.example.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

data class ApkSecurityDetails(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val targetSdk: Int,
    val minSdk: Int,
    val isSystemApp: Boolean,
    val isLocalApk: Boolean,
    val permissions: List<PermissionInfo>,
    val usesCleartextTraffic: Boolean,
    val activityCount: Int,
    val serviceCount: Int,
    val receiverCount: Int,
    val providerCount: Int,
    val signatures: List<String>,
    val debuggable: Boolean
)

data class PermissionInfo(
    val name: String,
    val isDangerous: Boolean,
    val description: String,
    val riskCategory: String // "HIGH", "MEDIUM", "LOW"
)

class ScanRepository(private val scanLogDao: ScanLogDao) {

    val allScanLogs: Flow<List<ScanLog>> = scanLogDao.getAllScanLogs()

    suspend fun saveScanLog(log: ScanLog) = withContext(Dispatchers.IO) {
        scanLogDao.insertScanLog(log)
    }

    suspend fun deleteScanLog(id: Int) = withContext(Dispatchers.IO) {
        scanLogDao.deleteScanLogById(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        scanLogDao.deleteAllScanLogs()
    }

    // List all non-system launcher applications
    fun getScannableApplications(context: Context): List<PackageInfo> {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        return packages.filter { packageInfo ->
            val appInfo = packageInfo.applicationInfo
            if (appInfo != null) {
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isSelf = packageInfo.packageName == context.packageName
                !isSystem && !isSelf
            } else {
                false
            }
        }.sortedBy {
            val appLabel = it.applicationInfo?.loadLabel(pm)?.toString() ?: ""
            appLabel.lowercase()
        }
    }

    // Extract package info from local APK Uri
    suspend fun scanLocalApk(context: Context, uri: Uri): ApkSecurityDetails? = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            tempFile = File.createTempFile("security_scan_", ".apk", context.cacheDir)
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS or 
                        PackageManager.GET_ACTIVITIES or 
                        PackageManager.GET_SERVICES or 
                        PackageManager.GET_RECEIVERS or 
                        PackageManager.GET_PROVIDERS
            val packageInfo = pm.getPackageArchiveInfo(tempFile.absolutePath, flags)
            if (packageInfo != null) {
                // Ensure ApplicationInfo variables are populated for archive parsing
                packageInfo.applicationInfo?.let { appInfo ->
                    appInfo.sourceDir = tempFile.absolutePath
                    appInfo.publicSourceDir = tempFile.absolutePath
                }
                return@withContext analyzePackageInfo(context, packageInfo, isLocalApk = true)
            }
            null
        } catch (e: Exception) {
            Log.e("ScanRepository", "Failed to scan local APK uri: $uri", e)
            null
        } finally {
            try {
                tempFile?.delete()
            } catch (e: Exception) {
                // Ignore cleanup failures
            }
        }
    }

    // Analyzes a PackageInfo object to compile detailed security heuristics
    fun analyzePackageInfo(context: Context, packageInfo: PackageInfo, isLocalApk: Boolean): ApkSecurityDetails {
        val pm = context.packageManager
        val appInfo = packageInfo.applicationInfo ?: ApplicationInfo()

        val appName = try {
            if (isLocalApk) {
                appInfo.loadLabel(pm).toString().ifEmpty { "External File" }
            } else {
                appInfo.loadLabel(pm).toString()
            }
        } catch (e: Exception) {
            "Unknown Package"
        }

        val packageName = packageInfo.packageName ?: "com.external.apk"
        val versionName = packageInfo.versionName ?: "v1.0"
        val targetSdk = appInfo.targetSdkVersion
        val minSdk = appInfo.minSdkVersion
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val debuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        // Cleartext HTTP check
        val usesCleartextTraffic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            (appInfo.flags and 0x08000000) != 0
        } else {
            true
        }

        // Extract and analyze requested permissions
        val permissionInfos = mutableListOf<PermissionInfo>()
        packageInfo.requestedPermissions?.forEach { permName ->
            val (risk, desc) = getPermissionRiskInfo(permName)
            permissionInfos.add(
                PermissionInfo(
                    name = permName,
                    isDangerous = risk == "HIGH",
                    description = desc,
                    riskCategory = risk
                )
            )
        }

        val activityCount = packageInfo.activities?.size ?: 0
        val serviceCount = packageInfo.services?.size ?: 0
        val receiverCount = packageInfo.receivers?.size ?: 0
        val providerCount = packageInfo.providers?.size ?: 0

        val signatureList = mutableListOf<String>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners.forEach {
                            signatureList.add(it.toCharsString().take(30) + "...")
                        }
                    } else {
                        signingInfo.signingCertificateHistory?.forEach {
                            signatureList.add(it.toCharsString().take(30) + "...")
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val signatures = packageInfo.signatures
                if (signatures != null) {
                    for (sig in signatures) {
                        signatureList.add(sig.toCharsString().take(30) + "...")
                    }
                }
            }
        } catch (e: Exception) {
            signatureList.add("Unsigned or unavailable")
        }

        return ApkSecurityDetails(
            appName = appName,
            packageName = packageName,
            versionName = versionName,
            targetSdk = targetSdk,
            minSdk = minSdk,
            isSystemApp = isSystemApp,
            isLocalApk = isLocalApk,
            permissions = permissionInfos,
            usesCleartextTraffic = usesCleartextTraffic,
            activityCount = activityCount,
            serviceCount = serviceCount,
            receiverCount = receiverCount,
            providerCount = providerCount,
            signatures = signatureList,
            debuggable = debuggable
        )
    }

    // Heuristics mapping for permission risk categories
    fun getPermissionRiskInfo(permName: String): Pair<String, String> {
        return when (permName) {
            "android.permission.SEND_SMS" -> "HIGH" to "Allows sending SMS without confirmation. Abused to subscribe to premium numbers or execute fraud."
            "android.permission.RECEIVE_SMS" -> "HIGH" to "Allows reading incoming SMS in background. Used to hijack OAuth/OTP bank authentication."
            "android.permission.READ_SMS" -> "HIGH" to "Allows scanning entire text history. High threat to logins and sensitive security logs."
            "android.permission.RECORD_AUDIO" -> "HIGH" to "Allows microphone hot-listening without active indicators. Massive spying threat."
            "android.permission.CAMERA" -> "HIGH" to "Allows camera access to take stealthy photos/videos in background."
            "android.permission.ACCESS_FINE_LOCATION" -> "HIGH" to "Allows GPS tracking down to a few meters. Risk of persistent tracking."
            "android.permission.ACCESS_COARSE_LOCATION" -> "HIGH" to "Allows approximate location tracking via networks."
            "android.permission.WRITE_EXTERNAL_STORAGE" -> "HIGH" to "Allows file encryption, renaming, and removal. High ransomware risk."
            "android.permission.READ_EXTERNAL_STORAGE" -> "HIGH" to "Allows scanning gallery and private docs. Direct data harvesting threat."
            "android.permission.SYSTEM_ALERT_WINDOW" -> "HIGH" to "Allows showing overlays on top of popular apps. Frequently facilitates credential injection/phishing."
            "android.permission.RECEIVE_BOOT_COMPLETED" -> "HIGH" to "Allows persistent background start-up on boot hook. Core characteristic of spyware packages."
            "android.permission.REQUEST_INSTALL_PACKAGES" -> "HIGH" to "Allows side-loading malware helper packages. Dropper vulnerability risk."
            "android.permission.CALL_PHONE" -> "HIGH" to "Allows placing system phone calls, bypassing standard confirmation dialogues."
            "android.permission.READ_PHONE_STATE" -> "HIGH" to "Allows harvesting hardware credentials (IMEI, SIM state) for device cloning."
            
            "android.permission.READ_CONTACTS" -> "MEDIUM" to "Allows querying the device contacts index. Often mined for social engineering targets."
            "android.permission.WRITE_CONTACTS" -> "MEDIUM" to "Allows editing or wiping database contacts."
            "android.permission.READ_CALL_LOG" -> "MEDIUM" to "Allows cataloging communication graphs, duration, and metrics."
            "android.permission.WRITE_CALL_LOG" -> "MEDIUM" to "Allows editing or pruning the standard dialer history database."
            "android.permission.GET_ACCOUNTS" -> "MEDIUM" to "Allows indexing user-linked ecosystem accounts (Google, Facebook) on-device."
            "android.permission.BLUETOOTH" -> "MEDIUM" to "Allows scanning nearby trackers and connecting to wireless assets."
            "android.permission.BLUETOOTH_ADMIN" -> "MEDIUM" to "Allows discovering and forcing bluetooth links."
            
            "android.permission.INTERNET" -> "LOW" to "Enables standard TCP/UDP socket creation for external online data exchange."
            "android.permission.ACCESS_NETWORK_STATE" -> "LOW" to "Checks network availability and connectivity status queries."
            "android.permission.ACCESS_WIFI_STATE" -> "LOW" to "Polls surrounding Hotspot states and configurations."
            "android.permission.VIBRATE" -> "LOW" to "Controls the physical vibration hardware."
            "android.permission.WAKE_LOCK" -> "LOW" to "Prevents the device processor from going to standby."
            else -> {
                val shortName = permName.substringAfterLast(".")
                if (permName.startsWith("android.permission")) {
                    "MEDIUM" to "Custom system permission ($shortName) with specialized capability profiles."
                } else {
                    "LOW" to "App-specific custom permission ($shortName)."
                }
            }
        }
    }

    fun isTrustedDeveloper(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return lower.startsWith("com.google.") ||
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

    // Heuristics-based risk assessment scorer
    fun calculateLocalRiskScore(details: ApkSecurityDetails): Pair<Int, String> {
        if (details.isSystemApp) {
            return 5 to "CLEAN" // System core apps are always safe
        }

        val isTrusted = isTrustedDeveloper(details.packageName)

        var score = 5 // lower realistic baseline (reduced from 10)

        // Calculate specific permission weights based on actual threat surface
        var permScore = 0.0
        details.permissions.forEach { info ->
            val pts = when (info.name) {
                // Highly critical, malware-sensitive abuse vectors
                "android.permission.BIND_ACCESSIBILITY_SERVICE" -> 25.0
                "android.permission.SEND_SMS" -> 15.0
                "android.permission.RECEIVE_SMS" -> 15.0
                "android.permission.READ_SMS" -> 15.0
                "android.permission.SYSTEM_ALERT_WINDOW" -> 15.0
                "android.permission.REQUEST_INSTALL_PACKAGES" -> 12.0
                "android.permission.WRITE_SECURE_SETTINGS" -> 15.0
                "android.permission.CALL_PHONE" -> 8.0
                "android.permission.READ_PHONE_STATE" -> 8.0
                
                // Contextual standard permissions matching regular app capabilities
                "android.permission.RECORD_AUDIO" -> 4.0
                "android.permission.CAMERA" -> 3.0
                "android.permission.ACCESS_FINE_LOCATION" -> 3.0
                "android.permission.ACCESS_COARSE_LOCATION" -> 1.0
                "android.permission.WRITE_EXTERNAL_STORAGE" -> 2.0
                "android.permission.READ_EXTERNAL_STORAGE" -> 2.0
                "android.permission.RECEIVE_BOOT_COMPLETED" -> 3.0
                
                else -> {
                    if (info.name.startsWith("android.permission")) {
                        0.5 // Standard unmapped permission (avoids massive score accumulation)
                    } else {
                        0.0 // Non-system / app-specific custom permissions
                    }
                }
            }
            permScore += pts
        }

        score += permScore.toInt()

        if (details.usesCleartextTraffic) score += 5
        if (details.targetSdk < 28) score += 10 // target SDK extremely outdated
        if (details.debuggable) {
            // Debuggable flag is only an external hazard for third-party APK files
            if (details.isLocalApk) {
                score += 10
            }
        }

        // Spyware pattern check (Boot complete + Internet + SMS/Record Audio)
        val hasBoot = details.permissions.any { it.name == "android.permission.RECEIVE_BOOT_COMPLETED" }
        val hasInternet = details.permissions.any { it.name == "android.permission.INTERNET" }
        val hasSms = details.permissions.any { it.name.contains("SMS") }
        val hasMic = details.permissions.any { it.name == "android.permission.RECORD_AUDIO" }

        if (hasBoot && hasInternet && (hasSms || hasMic)) {
            // Only add spyware pattern danger if they are NOT a trusted developer namespace
            if (!isTrusted) {
                score += 15
            }
        }

        if (isTrusted) {
            // Verified developer namespace automatically overrides high risk/suspicious limits
            score = score.coerceIn(0, 18)
        }

        score = score.coerceIn(0, 100)

        val level = when {
            score >= 60 -> "HIGH_RISK"
            score >= 35 -> "SUSPICIOUS"
            else -> "CLEAN"
        }

        return score to level
    }

    suspend fun queryGeminiReview(details: ApkSecurityDetails): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "⚠️ Gemini API Key is missing. Please add your key to the Secrets panel in AI Studio to enable interactive AI security audits."
        }

        val maxDangerousListed = details.permissions.filter { it.isDangerous }
        val permText = if (maxDangerousListed.isEmpty()) {
            "None declared."
        } else {
            maxDangerousListed.joinToString("\n") { "• ${it.name}: ${it.description}" }
        }

        val prompt = """
You are an expert mobile security engineer, malware reverse-engineer, and virus analyst. Evaluate the threat profile of the following Android APK:
- App Name: ${details.appName}
- Package Name: ${details.packageName}
- Target SDK: ${details.targetSdk} (Min SDK: ${details.minSdk})
- Debuggable: ${details.debuggable}
- Cleartext HTTP Traffic Enabled (No HTTPS enforcement): ${details.usesCleartextTraffic}

Declared Permissions categorized as Risky or Dangerous:
$permText

Suspicious component counts:
- Activities: ${details.activityCount}
- Services: ${details.serviceCount}
- Broadcast Receivers: ${details.receiverCount}
- Content Providers: ${details.providerCount}

Based on these compiled telemetry points, write a professional mobile app security audit. Keep paragraphs clear and informative. Avoid fluff or self-praising expressions. Use the following markdown structure exactly:
### 1. Risk Threat Summary
Provide a clear threat level (CLEAN, SUSPICIOUS, or HIGH RISK) and a 2-3 sentence overview explaining why this rating was assigned.

### 2. Permission Abuse Vector Analysis
Analyze the declared dangerous permissions. Explain exactly how a malicious actor or malware compiler could abuse this specific set of accesses (e.g. keylogging, SMS interception, credential stealing, background tracking, overlay phishing). Be highly developer-centric and technical.

### 3. Component & Execution Vulnerability Audit
Explain the implications of:
- Cleartext HTTP configuration (${details.usesCleartextTraffic})
- Target SDK configuration (${details.targetSdk})
- Background components (Broadcast Receivers: ${details.receiverCount} or Services: ${details.serviceCount}). Specify risks such as boot persistence or stealth collection.

### 4. Immediate Safety Recommendations
Offer concise, clear recommendations to the user (e.g. keep or block, check parameters, uninstall immediately, run with network access disabled).
""".trimIndent()

        try {
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                systemInstruction = Content(parts = listOf(Part(text = "You are a professional virus detection and mobile app threat modeling assistant.")))
            )
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: "Failed to generate security model details."
        } catch (e: Exception) {
            "Failed to consult Security AI: ${e.localizedMessage ?: "Connection Timeout"}"
        }
    }
}
