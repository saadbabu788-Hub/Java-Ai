package com.example.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AccessibilityBridge {

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    private val _isAutomationActive = MutableStateFlow(false)
    val isAutomationActive: StateFlow<Boolean> = _isAutomationActive.asStateFlow()

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
        _isAutomationActive.value = false
        _isInspectingScreen.value = false
        _latestSnapshot.value = null
    }

    fun setAutomationActive(active: Boolean) {
        _isAutomationActive.value = active
        if (!active) {
            _isInspectingScreen.value = false
            _latestSnapshot.value = null
        }
    }

    fun updateSnapshot(snapshot: ScreenSnapshot?) {
        if (!_isAutomationActive.value) {
            _latestSnapshot.value = null
            return
        }
        if (snapshot != null) {
            _latestSnapshot.value = snapshot
        }
    }

    fun setInspecting(inspecting: Boolean) {
        _isInspectingScreen.value = inspecting && _isAutomationActive.value
    }
}
