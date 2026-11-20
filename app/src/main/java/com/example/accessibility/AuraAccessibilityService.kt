package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

class AuraAccessibilityService : AccessibilityService() {

    @Volatile
    private var lastWindowClass: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.attachService(this)
        captureFreshSnapshot()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastWindowClass = event.className?.toString().orEmpty()
        }
        val root = rootInActiveWindow ?: return
        try {
            val snapshot = ScreenHierarchyParser.parse(root, lastWindowClass)
            AccessibilityBridge.updateSnapshot(snapshot)
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    override fun onInterrupt() {
        AccessibilityBridge.setInspecting(false)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityBridge.detachService()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityBridge.detachService()
        super.onDestroy()
    }

    fun captureFreshSnapshot(): ScreenSnapshot? {
        AccessibilityBridge.setInspecting(true)
        return try {
            val root = rootInActiveWindow ?: return AccessibilityBridge.latestSnapshot.value
            try {
                ScreenHierarchyParser.parse(root, lastWindowClass)?.also {
                    AccessibilityBridge.updateSnapshot(it)
                }
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        } finally {
            AccessibilityBridge.setInspecting(false)
        }
    }

    suspend fun tapNodeByIndex(nodeIndex: Int): Boolean {
        val snapshot = captureFreshSnapshot() ?: return false
        val target = snapshot.nodes.getOrNull(nodeIndex) ?: return false

        val root = rootInActiveWindow
        if (root != null) {
            try {
                val matchedNode = findNodeAtBounds(root, target.bounds)
                if (matchedNode != null) {
                    var current: AccessibilityNodeInfo? = matchedNode
                    while (current != null) {
                        if (current.isClickable) {
                            val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            if (clicked) return true
                        }
                        current = current.parent
                    }
                }
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        }
        return tapCoordinates(target.bounds.centerX.toFloat(), target.bounds.centerY.toFloat())
    }

    suspend fun tapByTextOrDescription(query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        val snapshot = captureFreshSnapshot() ?: return false

        val exactOrPartial = snapshot.nodes.firstOrNull {
            !it.isPassword && (
                it.text.equals(trimmed, ignoreCase = true) ||
                    it.contentDescription.equals(trimmed, ignoreCase = true)
                )
        } ?: snapshot.nodes.firstOrNull {
            !it.isPassword && (
                it.text.contains(trimmed, ignoreCase = true) ||
                    it.contentDescription.contains(trimmed, ignoreCase = true)
                )
        } ?: return false

        return tapNodeByIndex(exactOrPartial.index)
    }

    suspend fun inputTextIntoNode(
        nodeIndex: Int?,
        text: String,
        submitAfter: Boolean
    ): Boolean {
        val snapshot = captureFreshSnapshot() ?: return false
        val targetNode = if (nodeIndex != null) {
            snapshot.nodes.getOrNull(nodeIndex)
        } else {
            snapshot.nodes.firstOrNull { it.isEditable && it.isFocused }
                ?: snapshot.nodes.firstOrNull { it.isEditable }
        } ?: return false

        if (targetNode.isPassword) {
            // Security guard: never automate password fields
            return false
        }

        val root = rootInActiveWindow ?: return false
        var textEntered = false
        try {
            val editable = findEditableNearBounds(root, targetNode.bounds)
            if (editable != null && !editable.isPassword) {
                editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                editable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text
                    )
                }
                textEntered = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (textEntered && submitAfter) {
                    editable.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
                    val imeActionId = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
                    editable.performAction(imeActionId)
                }
            }
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }

        if (textEntered && submitAfter) {
            // If IME_ENTER didn't submit, try tapping a visible Search/Go/Submit icon if present
            val refreshed = captureFreshSnapshot()
            val searchBtn = refreshed?.nodes?.firstOrNull {
                it.isClickable && !it.isEditable && (
                    it.contentDescription.contains("search", ignoreCase = true) ||
                        it.text.equals("Search", ignoreCase = true) ||
                        it.text.equals("Go", ignoreCase = true) ||
                        it.viewId.contains("search_button", ignoreCase = true) ||
                        it.viewId.contains("go", ignoreCase = true)
                    )
            }
            if (searchBtn != null) {
                tapNodeByIndex(searchBtn.index)
            }
        }
        return textEntered
    }

    suspend fun scrollScreen(scrollDown: Boolean): Boolean {
        val root = rootInActiveWindow
        if (root != null) {
            try {
                val scrollable = findFirstScrollable(root)
                if (scrollable != null) {
                    val action = if (scrollDown) {
                        AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                    } else {
                        AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                    }
                    if (scrollable.performAction(action)) return true
                }
            } finally {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        }

        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * 0.5f
        val startY = if (scrollDown) metrics.heightPixels * 0.72f else metrics.heightPixels * 0.30f
        val endY = if (scrollDown) metrics.heightPixels * 0.28f else metrics.heightPixels * 0.75f
        return performSwipe(x, startY, x, endY, 320L)
    }

    fun performBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun performHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    suspend fun tapCoordinates(x: Float, y: Float): Boolean {
        if (x < 0f || y < 0f) return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAwait(gesture)
    }

    private suspend fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAwait(gesture)
    }

    private suspend fun dispatchGestureAwait(gesture: GestureDescription): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    deferred.complete(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    deferred.complete(false)
                }
            },
            null
        )
        if (!dispatched) return false
        return withTimeoutOrNull(2500L) { deferred.await() } ?: false
    }

    private fun findNodeAtBounds(
        node: AccessibilityNodeInfo,
        target: BoundsRect
    ): AccessibilityNodeInfo? {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.left == target.left &&
            rect.top == target.top &&
            rect.right == target.right &&
            rect.bottom == target.bottom
        ) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findNodeAtBounds(child, target)
            if (match != null) return match
        }
        return null
    }

    private fun findEditableNearBounds(
        node: AccessibilityNodeInfo,
        target: BoundsRect
    ): AccessibilityNodeInfo? {
        if (node.isEditable && !node.isPassword) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.contains(target.centerX, target.centerY) ||
                (rect.left == target.left && rect.top == target.top)
            ) {
                return node
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val match = findEditableNearBounds(child, target)
            if (match != null) return match
        }
        return null
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstScrollable(child)
            if (found != null) return found
        }
        return null
    }
}
