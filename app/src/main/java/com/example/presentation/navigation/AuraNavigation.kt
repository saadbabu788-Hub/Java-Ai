package com.example.presentation.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.AppContainer
import com.example.presentation.AuraViewModelFactory
import com.example.presentation.HistoryViewModel
import com.example.presentation.HomeViewModel
import com.example.presentation.PermissionsViewModel
import com.example.presentation.SettingsViewModel
import com.example.presentation.TasksViewModel
import com.example.presentation.screens.AboutScreen
import com.example.presentation.screens.HistoryScreen
import com.example.presentation.screens.HomeScreen
import com.example.presentation.screens.PermissionsScreen
import com.example.presentation.screens.SettingsScreen
import com.example.presentation.screens.TasksScreen
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextSecondary

object Routes {
    const val ROUTE_HOME = "home"
    const val ROUTE_HISTORY = "history"
    const val ROUTE_TASKS = "tasks"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_PERMISSIONS = "permissions"
    const val ROUTE_ABOUT = "about"
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navDestinations = listOf(
    NavDestinationItem(Routes.ROUTE_HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavDestinationItem(Routes.ROUTE_HISTORY, "History", Icons.Filled.History, Icons.Outlined.History),
    NavDestinationItem(Routes.ROUTE_TASKS, "Tasks", Icons.Filled.AutoAwesomeMotion, Icons.Outlined.AutoAwesomeMotion),
    NavDestinationItem(Routes.ROUTE_SETTINGS, "Settings", Icons.Filled.Tune, Icons.Outlined.Tune),
    NavDestinationItem(Routes.ROUTE_PERMISSIONS, "Access", Icons.Filled.Security, Icons.Outlined.Security),
    NavDestinationItem(Routes.ROUTE_ABOUT, "About", Icons.Filled.Info, Icons.Outlined.Info)
)

@Composable
fun AuraPilotApp(container: AppContainer) {
    val factory = AuraViewModelFactory(container)
    val homeViewModel: HomeViewModel = viewModel(factory = factory)
    val historyViewModel: HistoryViewModel = viewModel(factory = factory)
    val tasksViewModel: TasksViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)
    val permissionsViewModel: PermissionsViewModel = viewModel(factory = factory)

    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val historyMessages by historyViewModel.messages.collectAsStateWithLifecycle()
    val historyQuery by historyViewModel.searchQuery.collectAsStateWithLifecycle()
    val tasksState by tasksViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val permissionsState by permissionsViewModel.permissions.collectAsStateWithLifecycle()
    val screenContextConsent by permissionsViewModel.screenContextConsentAccepted.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        homeViewModel.refreshPermissions()
        permissionsViewModel.refresh()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionsViewModel.refresh()
        homeViewModel.refreshPermissions()
        if (granted && !homeState.voice.isListening) {
            homeViewModel.startVoiceListening()
        }
    }

    val micSettingsOnlyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionsViewModel.refresh()
        homeViewModel.refreshPermissions()
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionsViewModel.refresh()
        homeViewModel.refreshPermissions()
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianVoid,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navDestinations.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == item.route
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = CyanContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_${item.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = ObsidianSurface
                    ) {
                        navDestinations.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == item.route
                            } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label, maxLines = 1) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = ElectricCyan,
                                    selectedTextColor = ElectricCyan,
                                    indicatorColor = CyanContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("rail_${item.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.ROUTE_HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.ROUTE_HOME) {
                        HomeScreen(
                            uiState = homeState,
                            onCommandChange = homeViewModel::onCommandInputChange,
                            onSendCommand = homeViewModel::sendCurrentCommand,
                            onQuickCommand = homeViewModel::submitQuickCommand,
                            onMicClick = {
                                if (homeState.voice.isListening) {
                                    homeViewModel.stopVoiceListening()
                                } else if (homeState.permissions.isMicrophoneGranted) {
                                    homeViewModel.startVoiceListening()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onStopClick = homeViewModel::stopExecution,
                            onNewSession = homeViewModel::startNewSession,
                            onApproveConfirmation = homeViewModel::respondToConfirmation,
                            onApproveScreenContextConsent = homeViewModel::approveScreenContextConsentAndRun,
                            onDismissScreenContextConsent = homeViewModel::dismissScreenContextConsent,
                            onNavigateToSettings = { navigateTo(Routes.ROUTE_SETTINGS) },
                            onNavigateToPermissions = { navigateTo(Routes.ROUTE_PERMISSIONS) }
                        )
                    }

                    composable(Routes.ROUTE_HISTORY) {
                        HistoryScreen(
                            messages = historyMessages,
                            searchQuery = historyQuery,
                            onSearchQueryChange = historyViewModel::onSearchQueryChange,
                            onClearHistory = historyViewModel::clearHistory,
                            onReuseCommand = { cmd ->
                                homeViewModel.submitQuickCommand(cmd)
                                navigateTo(Routes.ROUTE_HOME)
                            }
                        )
                    }

                    composable(Routes.ROUTE_TASKS) {
                        TasksScreen(
                            uiState = tasksState,
                            onClearTasks = tasksViewModel::clearTasks,
                            onReplayTask = { cmd ->
                                homeViewModel.submitQuickCommand(cmd)
                                navigateTo(Routes.ROUTE_HOME)
                            }
                        )
                    }

                    composable(Routes.ROUTE_SETTINGS) {
                        SettingsScreen(
                            uiState = settingsState,
                            onApiKeyDraftChange = settingsViewModel::onApiKeyDraftChange,
                            onSaveApiKey = settingsViewModel::saveApiKey,
                            onTestConnection = settingsViewModel::testConnection,
                            onRemoveApiKey = settingsViewModel::removeApiKey,
                            onSelectModel = settingsViewModel::selectModel,
                            onTemperatureChange = settingsViewModel::updateTemperature,
                            onSaveSystemInstructions = settingsViewModel::updateSystemInstructions,
                            onToggleVoiceFeedback = settingsViewModel::toggleVoiceFeedback,
                            onSelectVoiceLanguage = settingsViewModel::selectVoiceLanguage,
                            onToggleSensitiveConfirmation = settingsViewModel::toggleSensitiveConfirmation,
                            onResetTokens = settingsViewModel::resetTokenUsage
                        )
                    }

                    composable(Routes.ROUTE_PERMISSIONS) {
                        PermissionsScreen(
                            permissions = permissionsState,
                            screenContextConsentAccepted = screenContextConsent,
                            onToggleScreenContextConsent = permissionsViewModel::setScreenContextConsent,
                            onRefresh = permissionsViewModel::refresh,
                            onOpenAccessibilitySettings = permissionsViewModel::openAccessibilitySettings,
                            onRequestMicrophone = {
                                micSettingsOnlyLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onOpenAppSettings = permissionsViewModel::openAppSettings
                        )
                    }

                    composable(Routes.ROUTE_ABOUT) {
                        AboutScreen()
                    }
                }
            }
        }
    }
}
