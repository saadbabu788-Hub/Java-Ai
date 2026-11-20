package com.example.automation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.example.domain.model.InstalledAppInfo

class InstalledAppsHelper(private val context: Context) {

    fun getLaunchableApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isBlank() || pkg == context.packageName) return@mapNotNull null
                InstalledAppInfo(appName = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    fun launchApp(target: String): LaunchResult {
        val query = target.trim()
        if (query.isEmpty()) return LaunchResult.Failed("No app name specified.")

        // Handle explicit Android Settings shortcuts directly and reliably
        val lower = query.lowercase()
        if (lower == "settings" || lower == "android settings" || lower == "system settings") {
            return startActivitySafe(Intent(Settings.ACTION_SETTINGS), "Settings")
        }
        if (lower.contains("wi-fi") || lower.contains("wifi settings")) {
            return startActivitySafe(Intent(Settings.ACTION_WIFI_SETTINGS), "Wi-Fi Settings")
        }
        if (lower.contains("bluetooth")) {
            return startActivitySafe(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Bluetooth Settings")
        }
        if (lower.contains("display settings")) {
            return startActivitySafe(Intent(Settings.ACTION_DISPLAY_SETTINGS), "Display Settings")
        }

        val pm = context.packageManager
        // 1. Direct package name match
        pm.getLaunchIntentForPackage(query)?.let { intent ->
            return startActivitySafe(intent, query)
        }

        // 2. Installed launcher apps fuzzy match
        val apps = getLaunchableApps()
        val exact = apps.firstOrNull { it.appName.equals(query, ignoreCase = true) }
        val startsWith = apps.firstOrNull { it.appName.startsWith(query, ignoreCase = true) }
        val contains = apps.firstOrNull {
            it.appName.contains(query, ignoreCase = true) ||
                query.contains(it.appName, ignoreCase = true)
        }
        val bestMatch = exact ?: startsWith ?: contains

        if (bestMatch != null) {
            val launchIntent = pm.getLaunchIntentForPackage(bestMatch.packageName)
            if (launchIntent != null) {
                return startActivitySafe(launchIntent, bestMatch.appName)
            }
        }

        // 3. Common well-known aliases fallback
        val knownPackage = KNOWN_ALIASES[lower]
        if (knownPackage != null) {
            pm.getLaunchIntentForPackage(knownPackage)?.let { intent ->
                return startActivitySafe(intent, query)
            }
        }

        return LaunchResult.Failed("App '$target' is not installed on this device.")
    }

    fun openUrl(url: String): LaunchResult {
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "https://$url"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
        return startActivitySafe(intent, normalized)
    }

    private fun startActivitySafe(intent: Intent, label: String): LaunchResult {
        return runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            LaunchResult.Launched(label)
        }.getOrElse { e ->
            LaunchResult.Failed("Failed to open $label: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    sealed class LaunchResult {
        data class Launched(val label: String) : LaunchResult()
        data class Failed(val reason: String) : LaunchResult()
    }

    companion object {
        private val KNOWN_ALIASES = mapOf(
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "google chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "whatsapp" to "com.whatsapp",
            "messages" to "com.google.android.apps.messaging",
            "play store" to "com.android.vending",
            "camera" to "com.android.camera2",
            "calculator" to "com.google.android.calculator"
        )
    }
}
