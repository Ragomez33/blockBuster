package com.blockbuster.app.ui.tabs

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.blockbuster.app.R
import com.blockbuster.app.navigation.Routes
import com.blockbuster.app.ui.blocking.BlockingScreen
import com.blockbuster.app.ui.blocklist.BlocklistScreen
import com.blockbuster.app.ui.focus.FocusModeSheet
import com.blockbuster.app.ui.settings.SettingsScreen

@Composable
fun MainTabsScaffold(
    onOpenSubscription: () -> Unit,
    onOpenPrivacy: () -> Unit,
    tabNavController: NavHostController = rememberNavController(),
) {
    var showFocusSheet by rememberSaveable { mutableStateOf(false) }
    val backStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Routes.BLOCKING

    Scaffold(
        bottomBar = {
            NavigationBar {
                TabItem(
                    route = Routes.BLOCKING,
                    currentRoute = currentRoute,
                    label = stringResource(R.string.tab_blocking),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                        )
                    },
                    onClick = { tabNavController.navigateTab(Routes.BLOCKING) },
                )
                TabItem(
                    route = Routes.BLOCKLIST,
                    currentRoute = currentRoute,
                    label = stringResource(R.string.tab_blocklist),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                        )
                    },
                    onClick = { tabNavController.navigateTab(Routes.BLOCKLIST) },
                )
                TabItem(
                    route = Routes.SETTINGS,
                    currentRoute = currentRoute,
                    label = stringResource(R.string.tab_settings),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                        )
                    },
                    onClick = { tabNavController.navigateTab(Routes.SETTINGS) },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showFocusSheet = true },
                icon = {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                    )
                },
                text = { Text(text = stringResource(R.string.focus_mode_title)) },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = Routes.BLOCKING,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.BLOCKING) {
                BlockingScreen(onOpenSubscription = onOpenSubscription)
            }
            composable(Routes.BLOCKLIST) {
                BlocklistScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenSubscription = onOpenSubscription,
                    onOpenPrivacy = onOpenPrivacy,
                )
            }
        }
    }

    if (showFocusSheet) {
        FocusModeSheet(onDismiss = { showFocusSheet = false })
    }
}

@Composable
private fun RowScope.TabItem(
    route: String,
    currentRoute: String,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = currentRoute == route,
        onClick = onClick,
        icon = icon,
        label = { Text(text = label) },
    )
}

private fun NavHostController.navigateTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
