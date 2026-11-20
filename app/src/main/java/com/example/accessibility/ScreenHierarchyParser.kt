package com.example.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object ScreenHierarchyParser {

    private const val MAX_DEPTH = 18
    private const val MAX_NODES = 90

    fun parse(root: AccessibilityNodeInfo?, windowClassName: String = ""): ScreenSnapshot? {
        if (root == null) return null

        val nodes = mutableListOf<ScreenNode>()
        val textLines = linkedSetOf<String>()
        val packageName = root.packageName?.toString().orEmpty()

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
            visibleTextLines = textLines.toList()
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

        val isPassword = node.isPassword
        val rawText = if (isPassword) "" else node.text?.toString()?.trim().orEmpty()
        val rawDesc = if (isPassword) "" else node.contentDescription?.toString()?.trim().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()
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
