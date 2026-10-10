package com.spectra.player.ui.login

import androidx.lifecycle.ViewModel
import com.spectra.player.auth.AuthState
import com.spectra.player.auth.SpotifyAuthManager
import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes the sign-in state to the UI. Launching the login page needs an Activity, so that
 * single call stays in MainActivity (`authManager.launchLogin(this)`).
 */
class AuthViewModel(private val auth: SpotifyAuthManager) : ViewModel() {
    val state: StateFlow<AuthState> = auth.state
    fun dismissError() = auth.dismissError()
}
