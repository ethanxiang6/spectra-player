package com.spectra.player.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.spectra.player.AppContainer
import com.spectra.player.auth.AuthState
import com.spectra.player.ui.library.LibraryScreen
import com.spectra.player.ui.library.LibraryViewModel
import com.spectra.player.ui.library.PlaylistScreen
import com.spectra.player.ui.library.PlaylistViewModel
import com.spectra.player.ui.login.AuthViewModel
import com.spectra.player.ui.login.LoginScreen
import com.spectra.player.ui.navigation.Route
import com.spectra.player.ui.nowplaying.NowPlayingScreen
import com.spectra.player.ui.nowplaying.NowPlayingViewModel
import com.spectra.player.ui.nowplaying.SkyBackdrop
import com.spectra.player.ui.settings.AppSettingsScreen
import com.spectra.player.ui.search.SearchScreen
import com.spectra.player.ui.search.SearchViewModel

@Composable
fun SpectraRoot(container: AppContainer, onLogin: () -> Unit) {
    val factory = remember(container) { appViewModelFactory(container) }
    val nav = rememberNavController()

    val authViewModel: AuthViewModel = viewModel(factory = factory)
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val startDestination: Route = remember {
        if (authState is AuthState.SignedIn) Route.Library else Route.Login
    }

    // Auth is the single gate: signing in lands on the library, signing out (or an expired
    // session) clears the back stack and returns to login.
    LaunchedEffect(authState) {
        val current = nav.currentDestination ?: return@LaunchedEffect
        when (authState) {
            is AuthState.SignedIn ->
                if (current.hasRoute<Route.Login>()) {
                    nav.navigate(Route.Library) { popUpTo<Route.Login> { inclusive = true } }
                }
            is AuthState.SignedOut, is AuthState.Error ->
                if (!current.hasRoute<Route.Login>()) {
                    nav.navigate(Route.Login) { popUpTo(0) { inclusive = true } }
                }
            AuthState.InProgress -> Unit
        }
    }

    val appSettings by container.appPrefs.settings.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize()) {
        // The chosen sky, drawn once behind every screen (static: no animation outside the visualizer).
        SkyBackdrop(
            backdrop = appSettings.theme,
            showStars = appSettings.stars,
            artwork = null,
            imageUrl = null,
            time = { 0f },
            energy = { 0f },
            modifier = Modifier.fillMaxSize(),
        )
        NavHost(navController = nav, startDestination = startDestination) {
            composable<Route.Login> {
                LoginScreen(state = authState, onLogin = onLogin)
            }
            composable<Route.Library> {
                val vm: LibraryViewModel = viewModel(factory = factory)
                LibraryScreen(
                    viewModel = vm,
                    onOpenPlaylist = { nav.navigate(Route.PlaylistDetail(it.id, it.name)) },
                    onOpenNowPlaying = { nav.navigate(Route.NowPlaying) },
                    onOpenSearch = { nav.navigate(Route.Search) },
                    onOpenSettings = { nav.navigate(Route.Settings) },
                )
            }
            composable<Route.Settings> {
                AppSettingsScreen(prefs = container.appPrefs, onBack = { nav.navigateUp() })
            }
            composable<Route.Search> {
                val vm: SearchViewModel = viewModel(factory = factory)
                SearchScreen(
                    viewModel = vm,
                    onBack = { nav.navigateUp() },
                    onOpenNowPlaying = { nav.navigate(Route.NowPlaying) },
                )
            }
            composable<Route.PlaylistDetail> {
                val vm: PlaylistViewModel = viewModel(factory = factory)
                PlaylistScreen(
                    viewModel = vm,
                    onBack = { nav.navigateUp() },
                    onOpenNowPlaying = { nav.navigate(Route.NowPlaying) },
                )
            }
            composable<Route.NowPlaying>(
                enterTransition = { slideInVertically(tween(380)) { it } + fadeIn(tween(380)) },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = { slideOutVertically(tween(320)) { it } + fadeOut(tween(320)) },
            ) {
                val vm: NowPlayingViewModel = viewModel(factory = factory)
                NowPlayingScreen(viewModel = vm, onBack = { nav.navigateUp() })
            }
        }
    }
}
