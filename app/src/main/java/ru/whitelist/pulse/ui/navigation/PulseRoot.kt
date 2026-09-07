package ru.whitelist.pulse.ui.navigation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ru.whitelist.pulse.R
import ru.whitelist.pulse.notify.ProbeNotifier
import ru.whitelist.pulse.ui.PulseViewModel
import ru.whitelist.pulse.ui.UiEvent
import ru.whitelist.pulse.ui.titleRes
import ru.whitelist.pulse.ui.checks.ChecksScreen
import ru.whitelist.pulse.ui.home.HomeScreen
import ru.whitelist.pulse.ui.network.NetworkScreen
import ru.whitelist.pulse.ui.settings.SettingsScreen

private enum class Dest(val route: String, val label: Int, val icon: ImageVector) {
    Home("home", R.string.nav_home, Icons.Outlined.Home),
    Checks("checks", R.string.nav_checks, Icons.Outlined.TravelExplore),
    Network("network", R.string.nav_network, Icons.Outlined.WifiTethering),
    Settings("settings", R.string.nav_settings, Icons.Outlined.Settings),
}

@Composable
fun PulseRoot(viewModel: PulseViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: Dest.Home.route
    val isTablet = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
    var tabletSide by rememberSaveable { mutableStateOf(Dest.Checks.route) }

    LaunchedEffect(Unit) {
        viewModel.bootstrap()
    }

    val pickLists = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importBundledLists(it) }
    }
    val pickDomains = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importCustomFile(it) }
    }
    val notifyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val content: @Composable (Dest) -> Unit = { dest ->
        when (dest) {
            Dest.Home -> HomeScreen(state = state, onCheck = { viewModel.checkAll() })
            Dest.Checks -> ChecksScreen(
                state = state,
                onCheckGroup = { viewModel.checkGroup(it) },
                onCheckAll = { viewModel.checkAll() },
                onAddSite = { host, tag -> viewModel.addSite(host, tag) },
                onToggleSite = { viewModel.toggleSite(it) },
                onDeleteSite = { viewModel.deleteSite(it) },
                onImport = { viewModel.importSites(it) },
                onImportFile = { pickDomains.launch(arrayOf("text/plain", "*/*")) },
            )
            Dest.Network -> NetworkScreen(
                state = state,
                onShare = { text ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject))
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_share_report)))
                },
            )
            Dest.Settings -> SettingsScreen(
                settings = state.settings,
                onChange = { viewModel.updateSettings(it) },
                onClearHistory = { viewModel.clearHistory() },
                onExport = {
                    viewModel.exportSites { text ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.action_export)))
                    }
                },
                onPickLists = { pickLists.launch(arrayOf("application/json", "text/plain")) },
                onImportCustomFile = { pickDomains.launch(arrayOf("text/plain", "*/*")) },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
        }
    }

    fun navigate(dest: Dest) {
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!isTablet) {
                NavigationBar {
                    Dest.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = current == dest.route,
                            onClick = { navigate(dest) },
                            icon = {
                                Icon(dest.icon, contentDescription = stringResource(dest.label))
                            },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (isTablet) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                NavigationRail {
                    Dest.entries.forEach { dest ->
                        NavigationRailItem(
                            selected = tabletSide == dest.route,
                            onClick = {
                                tabletSide = if (dest == Dest.Home) Dest.Checks.route else dest.route
                            },
                            icon = { Icon(dest.icon, contentDescription = stringResource(dest.label)) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
                Box(Modifier.weight(1f).padding(12.dp)) { content(Dest.Home) }
                Box(Modifier.weight(1f).padding(12.dp)) {
                    val side = Dest.entries.find { it.route == tabletSide && it != Dest.Home } ?: Dest.Checks
                    content(side)
                }
            }
        } else {
            NavHost(
                navController = navController,
                startDestination = Dest.Home.route,
                modifier = Modifier.padding(padding),
            ) {
                Dest.entries.forEach { dest ->
                    composable(dest.route) { content(dest) }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                UiEvent.SiteAdded -> context.getString(R.string.site_added)
                UiEvent.InvalidDomain -> context.getString(R.string.invalid_domain)
                UiEvent.SiteRemoved -> context.getString(R.string.site_removed)
                is UiEvent.SitesImported -> context.getString(R.string.sites_imported, event.accepted, event.rejected)
                UiEvent.HistoryCleared -> context.getString(R.string.history_cleared)
                UiEvent.ListsUpdated -> context.getString(R.string.lists_updated)
                UiEvent.ListsFailed -> context.getString(R.string.lists_update_failed)
            }
            snackbar.showSnackbar(message)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.scanning, state.verdict, state.settings.notifyOnResult) {
        if (!state.scanning && state.settings.notifyOnResult && state.verdict != null && state.lastCheckedAt != null) {
            ProbeNotifier.notify(context, context.getString(state.verdict!!.kind.titleRes()))
        }
    }
}
