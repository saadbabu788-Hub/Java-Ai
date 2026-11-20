package com.example.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.accessibility.AccessibilityBridge
import com.example.accessibility.AuraAccessibilityService
import com.example.domain.model.PermissionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PermissionManager(private val context: Context) {

    private val _permissions = MutableStateFlow(readCurrentSnapshot())
    val permissions: StateFlow<PermissionSnapshot> = _permissions.asStateFlow()

    fun refresh() {
        _permissions.value = readCurrentSnapshot()
    }

    fun readCurrentSnapshot(): PermissionSnapshot {
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return PermissionSnapshot(
            isAccessibilityEnabled = isAccessibilityServiceEnabled(),
            isMicrophoneGranted = micGranted,
            isNotificationsGranted = notifGranted
        )
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        if (AccessibilityBridge.isServiceConnected.value) return true

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        val expectedPackage = context.packageName
        val expectedClass = AuraAccessibilityService::class.java.name
        val matched = enabledServices.any { info ->
            val serviceInfo = info.resolveInfo?.serviceInfo
            serviceInfo?.packageName == expectedPackage &&
                (serviceInfo.name == expectedClass || serviceInfo.name.endsWith("AuraAccessibilityService"))
        }
        if (matched) return true

        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        return settingValue.contains(expectedPackage) &&
            settingValue.contains("AuraAccessibilityService")
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
