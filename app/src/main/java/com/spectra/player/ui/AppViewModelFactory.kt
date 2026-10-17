package com.spectra.player.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spectra.player.AppContainer
import com.spectra.player.ui.library.LibraryViewModel
import com.spectra.player.ui.library.PlaylistViewModel
import com.spectra.player.ui.login.AuthViewModel
import com.spectra.player.ui.nowplaying.NowPlayingViewModel
import com.spectra.player.ui.search.SearchViewModel

/** One factory for every ViewModel; each `initializer` pulls its dependencies from [AppContainer]. */
fun appViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { AuthViewModel(container.authManager) }
    initializer {
        LibraryViewModel(container.authManager, container.library, container.remote, container.network)
    }
    initializer { PlaylistViewModel(createSavedStateHandle(), container.library, container.remote) }
    initializer { NowPlayingViewModel(container.remote, container.visualizer, container.visualizerPrefs) }
    initializer { SearchViewModel(container.library, container.remote) }
}
