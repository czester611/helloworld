package com.fitlab.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fitlab.app.ui.appViewModel
import com.fitlab.app.ui.screens.calibration.CalibrationScreen
import com.fitlab.app.ui.screens.calibration.CalibrationViewModel
import com.fitlab.app.ui.screens.compensation.CompensationScreen
import com.fitlab.app.ui.screens.compensation.CompensationViewModel
import com.fitlab.app.ui.screens.home.HomeScreen
import com.fitlab.app.ui.screens.home.HomeViewModel
import com.fitlab.app.ui.screens.newpart.NewPartScreen
import com.fitlab.app.ui.screens.profiles.ProfilesScreen
import com.fitlab.app.ui.screens.diagnostics.DiagnosticsScreen
import com.fitlab.app.ui.screens.diagnostics.DiagnosticsViewModel
import com.fitlab.app.ui.screens.markers.MarkerSheetScreen
import com.fitlab.app.ui.screens.markers.MarkerSheetViewModel
import com.fitlab.app.ui.screens.outline.OutlineEditorScreen
import com.fitlab.app.ui.screens.outline.OutlineEditorViewModel
import com.fitlab.app.ui.screens.photo.PhotoCaptureScreen
import com.fitlab.app.ui.screens.photo.PhotoCaptureViewModel
import com.fitlab.app.ui.screens.part.PartDetailScreen
import com.fitlab.app.ui.screens.part.PartDetailViewModel
import com.fitlab.app.ui.screens.part.PartRoute
import com.fitlab.app.ui.screens.profiles.ProfilesViewModel
import com.fitlab.app.ui.screens.templates.TemplateEditorScreen
import com.fitlab.app.ui.screens.test.TestResultScreen
import com.fitlab.app.ui.screens.test.TestResultViewModel
import com.fitlab.app.ui.screens.templates.TemplateEditorViewModel
import com.fitlab.app.ui.screens.templates.TemplatePickerScreen
import com.fitlab.core.templates.TemplateId

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
        composable(Routes.TEMPLATES) {
            TemplatePickerScreen(onBack = back, onSelect = { id -> nav.navigate(Routes.templateEditor(id)) })
        }
        composable(
            Routes.TEMPLATE_EDITOR,
            arguments = listOf(
                navArgument("templateId") { type = NavType.StringType },
                navArgument("partId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val templateId = TemplateId.valueOf(entry.arguments?.getString("templateId") ?: TemplateId.BUSHING.name)
            val partId = entry.arguments?.getLong("partId") ?: -1L
            TemplateEditorScreen(
                viewModel = appViewModel { TemplateEditorViewModel(it, templateId, partId) },
                onBack = back,
                onSaved = { id ->
                    nav.navigate(Routes.part(id)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }
        composable(
            Routes.PART,
            arguments = listOf(navArgument("partId") { type = NavType.LongType }),
        ) { entry ->
            val partId = entry.arguments?.getLong("partId") ?: -1L
            PartDetailScreen(
                viewModel = appViewModel { PartDetailViewModel(it, partId) },
                onBack = back,
                onEdit = { target ->
                    when (target) {
                        is PartRoute.Outline -> nav.navigate(Routes.outline(target.partId))
                        is PartRoute.Template ->
                            nav.navigate(Routes.templateEditor(TemplateId.valueOf(target.templateId), target.partId))
                    }
                },
                onOpenIteration = { id -> nav.navigate(Routes.test(id)) },
            )
        }
        composable(
            Routes.TEST,
            arguments = listOf(navArgument("iterationId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("iterationId") ?: -1L
            TestResultScreen(appViewModel { TestResultViewModel(it, id) }, back)
        }
        composable(Routes.CALIBRATION) { CalibrationScreen(appViewModel { CalibrationViewModel(it) }, back) }
        composable(Routes.MARKERS) { MarkerSheetScreen(appViewModel { MarkerSheetViewModel(it) }, back) }
        composable(Routes.PHOTO) {
            PhotoCaptureScreen(
                viewModel = appViewModel { PhotoCaptureViewModel(it) },
                onBack = back,
                onCreated = { id ->
                    nav.navigate(Routes.outline(id)) { popUpTo(Routes.PHOTO) { inclusive = true } }
                },
            )
        }
        composable(
            Routes.OUTLINE,
            arguments = listOf(navArgument("partId") { type = NavType.LongType }),
        ) { entry ->
            val partId = entry.arguments?.getLong("partId") ?: -1L
            OutlineEditorScreen(
                viewModel = appViewModel { OutlineEditorViewModel(it, partId) },
                onBack = back,
                onSaved = { id -> nav.navigate(Routes.part(id)) { popUpTo(Routes.HOME) } },
            )
        }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(appViewModel { DiagnosticsViewModel(it) }, back) }
        composable(Routes.PROFILES) { ProfilesScreen(appViewModel { ProfilesViewModel(it) }, back) }
        composable(Routes.COMPENSATION) { CompensationScreen(appViewModel { CompensationViewModel(it) }, back) }
    }
}
