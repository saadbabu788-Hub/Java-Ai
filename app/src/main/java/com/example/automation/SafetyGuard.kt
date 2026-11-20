package com.example.automation

import com.example.accessibility.ScreenSnapshot
import com.example.domain.model.ActionType
import com.example.domain.model.AgentDecision
import com.example.domain.model.SafetyCheckResult

object SafetyGuard {

    private val blockedFinancialKeywords = listOf(
        "pay now", "confirm payment", "send money", "transfer funds",
        "upi pin", "cvv", "card number", "complete purchase", "place order",
        "wire transfer", "bank transfer", "checkout"
    )

    private val deleteKeywords = listOf(
        "delete", "remove", "erase", "clear all", "uninstall", "discard", "trash"
    )

    private val sendMessageKeywords = listOf(
        "send", "post", "publish", "reply", "submit message", "send email", "compose"
    )

    private val authKeywords = listOf(
        "password", "passcode", "otp", "pin code", "biometric", "fingerprint"
    )

    fun evaluate(
        decision: AgentDecision,
        snapshot: ScreenSnapshot?,
        requireConfirmation: Boolean
    ): SafetyCheckResult {
        val targetNode = decision.nodeIndex?.let { idx -> snapshot?.nodes?.getOrNull(idx) }
        val targetLabel = listOfNotNull(
            decision.targetText,
            targetNode?.text,
            targetNode?.contentDescription,
            decision.inputText
        ).joinToString(" ").lowercase()

        // 0. Strictly block protected screens (lockscreen, permission prompts, package installer, password managers)
        if (snapshot?.isProtectedScreen == true) {
            return SafetyCheckResult.Blocked(
                "Blocked for your security: AuraPilot never reads or automates protected security, credential, or permission screens."
            )
        }

        // 0b. Strictly block unsafe/non-HTTPS URLs
        if (decision.action == ActionType.OPEN_URL) {
            val rawUrl = decision.url.orEmpty()
            if (InstalledAppsHelper.validateAndNormalizeHttpsUrl(rawUrl) == null) {
                return SafetyCheckResult.Blocked(
                    "Blocked for your security: Only valid public HTTPS URLs can be opened."
                )
            }
        }

        // 1. Strictly block password / authentication tampering
        if (targetNode?.isPassword == true ||
            (decision.action == ActionType.TYPE_TEXT && authKeywords.any { targetLabel.contains(it) })
        ) {
            return SafetyCheckResult.Blocked(
                "Blocked for your security: AuraPilot never automates passwords, OTPs, or authentication fields."
            )
        }

        // 2. Strictly block financial transactions / payments
        if (blockedFinancialKeywords.any { targetLabel.contains(it) } ||
            decision.thought.lowercase().let { t ->
                t.contains("transfer money") || t.contains("make payment") || t.contains("enter upi")
            }
        ) {
            return SafetyCheckResult.Blocked(
                "Blocked for safety: AuraPilot does not automate financial transactions or payments."
            )
        }

        // 3. Require explicit user confirmation for planner-flagged or sensitive actions
        if (decision.action == ActionType.ASK_CONFIRMATION ||
            (requireConfirmation && decision.requiresConfirmation)
        ) {
            return SafetyCheckResult.RequiresConfirmation(
                reason = decision.confirmationReason ?: "Sensitive action requires your approval.",
                summary = decision.spokenMessage ?: describeAction(decision, targetLabel)
            )
        }

        if (requireConfirmation &&
            (decision.action == ActionType.TAP_NODE || decision.action == ActionType.TAP_TEXT)
        ) {
            if (deleteKeywords.any { targetLabel.contains(it) }) {
                return SafetyCheckResult.RequiresConfirmation(
                    reason = "Confirm deletion or removal",
                    summary = "AuraPilot is about to tap '$targetLabel' which may delete or remove content."
                )
            }
            if (sendMessageKeywords.any { targetLabel == it || targetLabel.startsWith("$it ") }) {
                return SafetyCheckResult.RequiresConfirmation(
                    reason = "Confirm sending message",
                    summary = "AuraPilot is ready to tap '$targetLabel' to send or publish content."
                )
            }
        }

        return SafetyCheckResult.Safe
    }

    private fun describeAction(decision: AgentDecision, label: String): String {
        return when (decision.action) {
            ActionType.TYPE_TEXT -> "Type \"${decision.inputText.orEmpty()}\" into field"
            ActionType.TAP_NODE, ActionType.TAP_TEXT -> "Tap \"$label\""
            ActionType.OPEN_APP -> "Open ${decision.targetApp.orEmpty()}"
            else -> decision.action.name
        }
    }
}
