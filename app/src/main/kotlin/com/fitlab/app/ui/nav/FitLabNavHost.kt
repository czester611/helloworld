package com.fitlab.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fitlab.app.ui.appViewModel
import com.fitlab.app.ui.screens.compensation.CompensationScreen
import com.fitlab.app.ui.screens.compensation.CompensationViewModel
import com.fitlab.app.ui.screens.home.HomeScreen
import com.fitlab.app.ui.screens.home.HomeViewModel
import com.fitlab.app.ui.screens.newpart.NewPartScreen
import com.fitlab.app.ui.screens.profiles.ProfilesScreen
import com.fitlab.app.ui.screens.profiles.ProfilesViewModel

/** Graf nawigacji aplikacji (trasy tekstowe). */
@Composable
fun FitLabNavHost() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(appViewModel { HomeViewModel(it) }, onNavigate = { r -> nav.navigate(r) })
        }
        composable(Routes.NEW) {
            NewPartScreen(
                onBack = back,
                onPhoto = { nav.navigate(Routes.PHOTO) },
                onTemplates = { nav.navigate(Routes.TEMPLATES) },
                onMarkers = { nav.navigate(Routes.MARKERS) },
            )
        }
        composable(Routes.PROFILES) { ProfilesScreen(appViewModel { ProfilesViewModel(it) }, back) }
        composable(Routes.COMPENSATION) { CompensationScreen(appViewModel { CompensationViewModel(it) }, back) }
    }
}
