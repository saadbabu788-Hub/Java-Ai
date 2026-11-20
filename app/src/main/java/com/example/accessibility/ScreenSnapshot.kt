package com.example.accessibility

data class BoundsRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val isValid: Boolean get() = right > left && bottom > top
}

data class ScreenNode(
    val index: Int,
    val text: String,
    val contentDescription: String,
    val viewId: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isFocused: Boolean,
    val isPassword: Boolean,
    val bounds: BoundsRect
)

data class ScreenSnapshot(
    val packageName: String,
    val className: String,
    val timestamp: Long,
    val nodes: List<ScreenNode>,
    val visibleTextLines: List<String>
) {
    fun toPromptText(): String {
        val sb = StringBuilder()
        sb.appendLine("package=$packageName | window=$className | total_interactive_nodes=${nodes.size}")
        sb.appendLine("NODES:")
        nodes.take(65).forEach { node ->
            val flags = buildList {
                if (node.isClickable) add("CLICK")
                if (node.isEditable) add("EDIT")
                if (node.isScrollable) add("SCROLL")
                if (node.isFocused) add("FOCUSED")
                if (node.isPassword) add("PASSWORD_MASKED")
            }.joinToString(",")

            val shortClass = node.className.substringAfterLast('.')
            val idPart = node.viewId.substringAfterLast('/').takeIf { it.isNotBlank() }?.let { " id=$it" }.orEmpty()
            val label = when {
                node.isPassword -> "[PROTECTED PASSWORD]"
                node.text.isNotBlank() && node.contentDescription.isNotBlank() && node.text != node.contentDescription ->
                    "\"${node.text}\" (desc=\"${node.contentDescription}\")"
                node.text.isNotBlank() -> "\"${node.text}\""
                node.contentDescription.isNotBlank() -> "desc=\"${node.contentDescription}\""
                else -> ""
            }
            sb.appendLine("[${node.index}] $shortClass [$flags]$idPart $label @(${node.bounds.centerX},${node.bounds.centerY})")
        }
        if (visibleTextLines.isNotEmpty()) {
            sb.appendLine("VISIBLE_TEXT_SUMMARY:")
            sb.appendLine(visibleTextLines.take(45).joinToString(" | "))
        }
        return sb.toString()
    }

    fun fullReadableText(): String {
        return visibleTextLines.joinToString("\n").ifBlank {
            nodes.filter { !it.isPassword }
                .mapNotNull { it.text.ifBlank { it.contentDescription }.takeIf { s -> s.isNotBlank() } }
                .distinct()
                .joinToString("\n")
        }
    }
}
