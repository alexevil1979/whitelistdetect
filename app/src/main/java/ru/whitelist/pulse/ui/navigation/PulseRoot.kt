package ru.whitelist.pulse.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import ru.whitelist.pulse.ui.titleRes
import ru.whitelist.pulse.ui.checks.ChecksScreen
import ru.whitelist.pulse.ui.home.HomeScreen
import ru.whitelist.pulse.ui.network.NetworkScreen
import ru.whitelist.pulse.ui.settings.SettingsScreen

private enum class Dest(val route: String, val label: Int) {
    Home("home", R.string.nav_home),
    Checks("checks", R.string.nav_checks),
    Network("network", R.string.nav_network),
    Settings("settings", R.string.nav_settings),
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

    LaunchedEffect(Unit) {
        viewModel.bootstrap()
    }

    val pickLists = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.importBundledLists(it) }
    }

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
            )
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
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (dest) {
                                        Dest.Home -> Icons.Outlined.Home
                                        Dest.Checks -> Icons.Outlined.TravelExplore
                                        Dest.Network -> Icons.Outlined.WifiTethering
                                        Dest.Settings -> Icons.Outlined.Settings
                                    },
                                    contentDescription = stringResource(dest.label),
                                )
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
                    .padding(padding)
                    .padding(12.dp),
            ) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { content(Dest.Home) }
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                    val side = Dest.entries.find { it.route == current && it != Dest.Home } ?: Dest.Checks
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

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(state.scanning, state.verdict, state.settings.notifyOnResult) {
        if (!state.scanning && state.settings.notifyOnResult && state.verdict != null && state.lastCheckedAt != null) {
            ProbeNotifier.notify(context, context.getString(state.verdict!!.kind.titleRes()))
        }
    }
}
