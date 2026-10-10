package com.spectra.player.auth

sealed interface AuthState {
    data object SignedOut : AuthState
    /** The Custom Tab is open; waiting for the spectra://callback redirect. */
    data object InProgress : AuthState
    data object SignedIn : AuthState
    data class Error(val message: String) : AuthState
}
