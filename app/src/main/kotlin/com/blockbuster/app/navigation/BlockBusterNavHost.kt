package com.blockbuster.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blockbuster.app.ui.premium.SubscriptionScreen
import com.blockbuster.app.ui.settings.PrivacyPolicyScreen
import com.blockbuster.app.ui.tabs.MainTabsScaffold

object Routes {
    const val MAIN = "main"
    const val BLOCKING = "tabs/blocking"
    const val BLOCKLIST = "tabs/blocklist"
    const val SETTINGS = "tabs/settings"
    const val SUBSCRIPTION = "subscription"
    const val PRIVACY = "settings/privacy"
}

@Composable
fun BlockBusterNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
    ) {
        composable(Routes.MAIN) {
            MainTabsScaffold(
                onOpenSubscription = { navController.navigate(Routes.SUBSCRIPTION) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
            )
        }
        composable(Routes.SUBSCRIPTION) {
            SubscriptionScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PRIVACY) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }
    }
}
