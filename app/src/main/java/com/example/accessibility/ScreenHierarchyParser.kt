package com.example.accessibility

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

object ScreenHierarchyParser {

    private const val MAX_DEPTH = 18
    private const val MAX_NODES = 90

    private val BLOCKED_PACKAGES = setOf(
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.vpndialogs",
        "com.android.keychain",
        "com.android.managedprovisioning",
        "com.google.android.gms.auth",
        "com.android.systemui",
        "com.agilebits.onepassword",
        "com.onepassword.android",
        "com.x8bit.bitwarden",
        "com.lastpass.lpandroid",
        "com.dashlane",
        "keepass2android.keepass2android",
        "proton.android.pass",
        "io.enpass.app",
        "com.callpod.android_apps.keeper"
    )

    private val BLOCKED_WINDOW_CLASS_TOKENS = listOf(
        "keyguard",
        "biometric",
        "confirmdevicecredential",
        "confirmlock",
        "grantpermissions",
        "packageinstaller",
        "installinstalling",
        "deviceadminadd",
        "accessibilitysettings",
        "autofill"
    )

    private val SENSITIVE_FIELD_TOKENS = listOf(
        "password",
        "passcode",
        "pin_code",
        "pincode",
        "pin code",
        "otp",
        "cvv",
        "card_number",
        "card number",
        "credit_card",
        "credit card",
        "secret_key",
        "secret key",
        "api_key",
        "api key",
        "token_input"
    )

    private val PIN_WORD_REGEX = Regex("""\bpin\b""")

    fun isSensitiveScreen(packageName: String, windowClassName: String): Boolean {
        val pkgLower = packageName.trim().lowercase()
        val winLower = windowClassName.trim().lowercase()
        if (pkgLower in BLOCKED_PACKAGES) return true
        if (pkgLower.contains("password") || pkgLower.contains("authenticator")) return true
        if (BLOCKED_WINDOW_CLASS_TOKENS.any { winLower.contains(it) }) return true
        return false
    }

    fun isSensitiveFieldMetadata(
        isPassword: Boolean,
        viewId: String,
        hintText: String
    ): Boolean {
        if (isPassword) return true
        val combined = "$viewId $hintText".lowercase()
        return SENSITIVE_FIELD_TOKENS.any { combined.contains(it) } ||
            PIN_WORD_REGEX.containsMatchIn(combined)
    }

    fun parse(root: AccessibilityNodeInfo?, windowClassName: String = ""): ScreenSnapshot? {
        if (root == null) return null

        val packageName = root.packageName?.toString().orEmpty()
        if (isSensitiveScreen(packageName, windowClassName)) {
            return ScreenSnapshot(
                packageName = packageName,
                className = windowClassName,
                timestamp = System.currentTimeMillis(),
                nodes = emptyList(),
                visibleTextLines = emptyList(),
                isProtectedScreen = true
            )
        }

        val nodes = mutableListOf<ScreenNode>()
        val textLines = linkedSetOf<String>()

        traverse(
            node = root,
            depth = 0,
            nodes = nodes,
            textLines = textLines
        )

        return ScreenSnapshot(
            packageName = packageName,
            className = windowClassName,
            timestamp = System.currentTimeMillis(),
            nodes = nodes,
            visibleTextLines = textLines.toList(),
            isProtectedScreen = false
        )
    }

    private fun traverse(
        node: AccessibilityNodeInfo,
        depth: Int,
        nodes: MutableList<ScreenNode>,
        textLines: MutableSet<String>
    ) {
        if (depth > MAX_DEPTH || nodes.size >= MAX_NODES) return
        if (!node.isVisibleToUser) return

        val viewId = node.viewIdResourceName.orEmpty()
        val hintText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString().orEmpty()
        } else {
            ""
        }
        val isPassword = isSensitiveFieldMetadata(node.isPassword, viewId, hintText)
        val rawText = if (isPassword) "" else node.text?.toString()?.trim().orEmpty()
        val rawDesc = if (isPassword) "" else node.contentDescription?.toString()?.trim().orEmpty()
        val className = node.className?.toString().orEmpty()

        val rect = Rect()
        node.getBoundsInScreen(rect)
        val bounds = BoundsRect(rect.left, rect.top, rect.right, rect.bottom)

        if (!isPassword) {
            if (rawText.isNotBlank()) textLines.add(rawText.take(240))
            else if (rawDesc.isNotBlank()) textLines.add(rawDesc.take(240))
        }

        val isInteresting = node.isClickable ||
            node.isEditable ||
            node.isScrollable ||
            rawText.isNotBlank() ||
            rawDesc.isNotBlank()

        if (isInteresting && bounds.isValid) {
            nodes.add(
                ScreenNode(
                    index = nodes.size,
                    text = rawText.take(160),
                    contentDescription = rawDesc.take(160),
                    viewId = viewId,
                    className = className,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    isFocused = node.isFocused,
                    isPassword = isPassword,
                    bounds = bounds
                )
            )
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (nodes.size >= MAX_NODES) break
            val child = node.getChild(i) ?: continue
            try {
                traverse(child, depth + 1, nodes, textLines)
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
    }
}
