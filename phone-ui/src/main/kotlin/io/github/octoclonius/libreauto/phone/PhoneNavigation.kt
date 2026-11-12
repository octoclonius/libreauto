package io.github.octoclonius.libreauto.phone

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import io.github.octoclonius.libreauto.phone.licenses.LicenseScreen
import io.github.octoclonius.libreauto.phone.licenses.LicensesScreen
import io.github.octoclonius.libreauto.phone.licenses.LicensesViewModel
import io.github.octoclonius.libreauto.phone.permissions.PermissionsScreen
import io.github.octoclonius.libreauto.phone.permissions.PermissionsViewModel
import io.github.octoclonius.libreauto.phone.settings.AboutScreen
import io.github.octoclonius.libreauto.phone.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable private data object Main : NavKey

@Serializable private data object Settings : NavKey

@Serializable private data object Permissions : NavKey

@Serializable private data object About : NavKey

@Serializable private data object Licenses : NavKey

@Serializable private data class License(val uniqueId: String) : NavKey

@Composable
fun PhoneNavigation(mainViewModel: MainViewModel) {
    val backStack = rememberNavBackStack(Main)
    val onBack: () -> Unit = { backStack.removeLastOrNull() }
    val forward = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val push: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        slideInHorizontally { it * forward } togetherWith slideOutHorizontally { -it * forward }
    }
    val pop: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        slideInHorizontally { -it * forward } togetherWith slideOutHorizontally { it * forward }
    }
    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        transitionSpec = push,
        popTransitionSpec = pop,
        predictivePopTransitionSpec = { pop() },
        entryProvider =
            entryProvider {
                entry<Main> {
                    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
                    MainScreen(
                        uiState = uiState,
                        onRefresh = mainViewModel::refresh,
                        onOpenCertificatePicker = mainViewModel::openCertificatePicker,
                        onChooseCertificateProvider = mainViewModel::chooseCertificateProvider,
                        onCloseCertificatePicker = mainViewModel::closeCertificatePicker,
                        onRequestShizukuPermission = mainViewModel::requestShizukuPermission,
                        onSettingsClick = { backStack.add(Settings) },
                    )
                }
                entry<Settings> {
                    SettingsScreen(
                        onBack = onBack,
                        onPermissionsClick = { backStack.add(Permissions) },
                        onAboutClick = { backStack.add(About) },
                    )
                }
                entry<Permissions> {
                    val viewModel: PermissionsViewModel =
                        viewModel(factory = PermissionsViewModel.Factory)
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    PermissionsScreen(
                        uiState = uiState,
                        notificationListener = viewModel.notificationListener,
                        onBack = onBack,
                        onRefresh = viewModel::refresh,
                    )
                }
                entry<About> {
                    AboutScreen(onBack = onBack, onLicensesClick = { backStack.add(Licenses) })
                }
                entry<Licenses> {
                    val viewModel: LicensesViewModel =
                        viewModel(factory = LicensesViewModel.Factory)
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    LicensesScreen(
                        uiState = uiState,
                        onBack = onBack,
                        onLibraryClick = { backStack.add(License(it.uniqueId)) },
                    )
                }
                entry<License> { key ->
                    val viewModel: LicensesViewModel =
                        viewModel(factory = LicensesViewModel.Factory)
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    LicenseScreen(uiState = uiState, uniqueId = key.uniqueId, onBack = onBack)
                }
            },
    )
}
