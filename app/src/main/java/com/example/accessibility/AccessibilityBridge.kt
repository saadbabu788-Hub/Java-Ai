package com.example.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AccessibilityBridge {

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val _latestSnapshot = MutableStateFlow<ScreenSnapshot?>(null)
    val latestSnapshot: StateFlow<ScreenSnapshot?> = _latestSnapshot.asStateFlow()

    private val _isInspectingScreen = MutableStateFlow(false)
    val isInspectingScreen: StateFlow<Boolean> = _isInspectingScreen.asStateFlow()

    @Volatile
    var activeService: AuraAccessibilityService? = null
        private set

    fun attachService(service: AuraAccessibilityService) {
        activeService = service
        _isServiceConnected.value = true
    }

    fun detachService() {
        activeService = null
        _isServiceConnected.value = false
        _isInspectingScreen.value = false
    }

    fun updateSnapshot(snapshot: ScreenSnapshot?) {
        if (snapshot != null) {
            _latestSnapshot.value = snapshot
        }
    }

    fun setInspecting(inspecting: Boolean) {
        _isInspectingScreen.value = inspecting
    }
}
