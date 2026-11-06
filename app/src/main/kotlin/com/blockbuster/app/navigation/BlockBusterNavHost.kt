package com.blockbuster.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blockbuster.app.ui.home.HomeScreen
import com.blockbuster.app.ui.rules.RulesScreen

object Routes {
    const val HOME = "home"
    const val RULES = "rules"
}

@Composable
fun BlockBusterNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onManageRules = { navController.navigate(Routes.RULES) },
            )
        }
        composable(Routes.RULES) {
            RulesScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
