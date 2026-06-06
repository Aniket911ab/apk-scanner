package com.example.ui.viewmodel

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.ScanLog
import com.example.data.repository.ApkSecurityDetails
import com.example.data.repository.ScanRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class ScanState {
    object Idle : ScanState()
    object LoadingApps : ScanState()
    data class Scanning(val appName: String) : ScanState()
    data class ScanSuccess(val details: ApkSecurityDetails, val score: Int, val level: String) : ScanState()
    data class Error(val message: String) : ScanState()
}

class ScanViewModel(private val repository: ScanRepository) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _installedApps = MutableStateFlow<List<PackageInfo>>(emptyList())
    val installedApps: StateFlow<List<PackageInfo>> = _installedApps.asStateFlow()

    private val _aiAuditReport = MutableStateFlow<String?>(null)
    val aiAuditReport: StateFlow<String?> = _aiAuditReport.asStateFlow()

    private val _isAiAuditing = MutableStateFlow(false)
    val isAiAuditing: StateFlow<Boolean> = _isAiAuditing.asStateFlow()

    // Flow of history logs from Room database
    val scanHistory: StateFlow<List<ScanLog>> = repository.allScanLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Load available applications installed on-device
    fun loadInstalledApps(context: Context) {
        viewModelScope.launch {
            _scanState.value = ScanState.LoadingApps
            try {
                val apps = repository.getScannableApplications(context)
                _installedApps.value = apps
                _scanState.value = ScanState.Idle
            } catch (e: Exception) {
                _scanState.value = ScanState.Error("Error listing packages: ${e.localizedMessage}")
            }
        }
    }

    // Scan an installed app by package name
    fun scanInstalledApp(context: Context, packageName: String) {
        viewModelScope.launch {
            val pm = context.packageManager
            _scanState.value = ScanState.Scanning("Reading package...")
            _aiAuditReport.value = null
            try {
                // Get PackageInfo including all permissions & components
                val flags = PackageManager.GET_PERMISSIONS or 
                            PackageManager.GET_ACTIVITIES or 
                            PackageManager.GET_SERVICES or 
                            PackageManager.GET_RECEIVERS or 
                            PackageManager.GET_PROVIDERS
                val packageInfo = pm.getPackageInfo(packageName, flags)
                val details = repository.analyzePackageInfo(context, packageInfo, isLocalApk = false)
                val (score, level) = repository.calculateLocalRiskScore(details)

                _scanState.value = ScanState.ScanSuccess(details, score, level)
                
                // Keep history clean - save to database
                saveResultToHistory(details, score, level)
            } catch (e: Exception) {
                _scanState.value = ScanState.Error("Failed scanning package: ${e.localizedMessage}")
            }
        }
    }

    // Scan selected APK file from storage
    fun scanApkStorageFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning("Extracting APK file components...")
            _aiAuditReport.value = null
            try {
                val details = repository.scanLocalApk(context, uri)
                if (details != null) {
                    val (score, level) = repository.calculateLocalRiskScore(details)
                    _scanState.value = ScanState.ScanSuccess(details, score, level)
                    saveResultToHistory(details, score, level)
                } else {
                    _scanState.value = ScanState.Error("Parsing error: Invalid or corrupted APK file.")
                }
            } catch (e: Exception) {
                _scanState.value = ScanState.Error("Failed parsing APK file: ${e.localizedMessage}")
            }
        }
    }

    // Query Gemini API to obtain dynamic Security Threat Model Audits
    fun requestAiSecurityAudit(details: ApkSecurityDetails) {
        viewModelScope.launch {
            _isAiAuditing.value = true
            _aiAuditReport.value = null
            try {
                val auditText = repository.queryGeminiReview(details)
                _aiAuditReport.value = auditText
                
                // Update history item with Gemini report if successfully requested
                updateHistoryWithAiReport(details, auditText)
            } catch (e: Exception) {
                _aiAuditReport.value = "Failed to compile AI insights: ${e.localizedMessage}"
            } finally {
                _isAiAuditing.value = false
            }
        }
    }

    // Insert scan findings into Room Local DB
    private fun saveResultToHistory(details: ApkSecurityDetails, score: Int, level: String) {
        viewModelScope.launch {
            val highMediumPermsList = details.permissions
                .filter { it.riskCategory == "HIGH" || it.riskCategory == "MEDIUM" }
                .joinToString(",") { it.name }

            val log = ScanLog(
                appName = details.appName,
                packageName = details.packageName,
                versionName = details.versionName,
                targetSdk = details.targetSdk,
                isLocalApk = details.isLocalApk,
                riskScore = score,
                threatLevel = level,
                sensitivePermissions = highMediumPermsList,
                cleartextTrafficEnabled = details.usesCleartextTraffic,
                aiSummaryReport = "" // empty until analyzed
            )
            repository.saveScanLog(log)
        }
    }

    private fun updateHistoryWithAiReport(details: ApkSecurityDetails, aiReport: String) {
        viewModelScope.launch {
            // Find existing log for this package name and update it
            val (score, level) = repository.calculateLocalRiskScore(details)
            val highMediumPermsList = details.permissions
                .filter { it.riskCategory == "HIGH" || it.riskCategory == "MEDIUM" }
                .joinToString(",") { it.name }

            val log = ScanLog(
                appName = details.appName,
                packageName = details.packageName,
                versionName = details.versionName,
                targetSdk = details.targetSdk,
                isLocalApk = details.isLocalApk,
                riskScore = score,
                threatLevel = level,
                sensitivePermissions = highMediumPermsList,
                cleartextTrafficEnabled = details.usesCleartextTraffic,
                aiSummaryReport = aiReport
            )
            repository.saveScanLog(log)
        }
    }

    fun deleteHistoryLog(id: Int) {
        viewModelScope.launch {
            repository.deleteScanLog(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun forceScanSuccessState(details: ApkSecurityDetails, score: Int, level: String) {
        val (recalculatedScore, recalculatedLevel) = repository.calculateLocalRiskScore(details)
        _scanState.value = ScanState.ScanSuccess(details, recalculatedScore, recalculatedLevel)
    }

    fun resetState() {
        _scanState.value = ScanState.Idle
        _aiAuditReport.value = null
    }
}

class ScanViewModelFactory(private val repository: ScanRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScanViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScanViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
