package com.spectra.player.auth

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

/**
 * Spotify Web API login using Authorization Code + PKCE (the flow Spotify recommends for
 * mobile apps; the implicit grant has been retired).
 *
 *  1. [launchLogin]    -> Custom Tab to accounts.spotify.com with code_challenge + state
 *  2. [handleRedirect] -> verify state, exchange code + verifier for tokens
 *  3. [validAccessToken] / [refreshAfterUnauthorized] keep the session alive
 *
 * Tokens live only in [SecureTokenStore] (Keystore-encrypted); they are never logged.
 */
class SpotifyAuthManager(
    private val store: SecureTokenStore,
    private val accounts: SpotifyAccountsApi,
) {
    private val refreshLock = Mutex()

    private val _state = MutableStateFlow<AuthState>(
        if (store.tokens() != null) AuthState.SignedIn else AuthState.SignedOut,
    )
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun launchLogin(activity: Activity) {
        if (!AuthConfig.isConfigured) {
            _state.value = AuthState.Error(
                "No Spotify Client ID. Add spotify.clientId to local.properties and rebuild.",
            )
            return
        }
        val verifier = Pkce.newVerifier()
        val oauthState = Pkce.newState()
        store.savePending(SecureTokenStore.PendingLogin(verifier, oauthState))

        val uri = Uri.parse(AuthConfig.AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", AuthConfig.clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", AuthConfig.redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", Pkce.challengeFor(verifier))
            .appendQueryParameter("state", oauthState)
            .appendQueryParameter("scope", AuthConfig.scopes.joinToString(" "))
            .build()

        _state.value = AuthState.InProgress
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, uri)
        } catch (e: Exception) {
            _state.value = AuthState.Error("No browser available to sign in.")
        }
    }

    /** Called from MainActivity when spectra://callback is opened. */
    suspend fun handleRedirect(uri: Uri) {
        val pending = store.takePending()
        val error = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        val returnedState = uri.getQueryParameter("state")

        when {
            pending == null -> fail("Sign-in session expired. Please try again.")
            error != null -> fail(
                if (error == "access_denied") "Spotify access was declined." else "Spotify returned an error ($error).",
            )
            code == null || returnedState != pending.state ->
                fail("The sign-in response could not be verified.")
            else -> try {
                val response = accounts.exchangeCode(
                    grantType = "authorization_code",
                    code = code,
                    redirectUri = AuthConfig.redirectUri,
                    clientId = AuthConfig.clientId,
                    codeVerifier = pending.verifier,
                )
                persist(response, previousRefreshToken = null)
                _state.value = AuthState.SignedIn
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Token exchange failed: ${e.javaClass.simpleName}")
                fail("Couldn't complete sign-in. Check your connection and try again.")
            }
        }
    }

    /** A token valid for at least another minute, refreshing if needed. Null when signed out. */
    suspend fun validAccessToken(): String? = refreshLock.withLock {
        val tokens = store.tokens() ?: return@withLock null
        if (tokens.expiresAtMs - System.currentTimeMillis() > EXPIRY_SKEW_MS) {
            tokens.accessToken
        } else {
            refreshLocked(tokens)
        }
    }

    /**
     * Called after a 401. If another request already refreshed (stored token differs from the
     * one that failed) the fresh token is reused instead of burning a second refresh.
     */
    suspend fun refreshAfterUnauthorized(staleToken: String?): String? = refreshLock.withLock {
        val tokens = store.tokens() ?: return@withLock null
        if (staleToken != null && tokens.accessToken != staleToken) {
            tokens.accessToken
        } else {
            refreshLocked(tokens)
        }
    }

    fun signOut() {
        store.clear()
        _state.value = AuthState.SignedOut
    }

    fun dismissError() {
        if (_state.value is AuthState.Error) _state.value = AuthState.SignedOut
    }

    private suspend fun refreshLocked(tokens: SecureTokenStore.Tokens): String? = try {
        val response = accounts.refresh(
            grantType = "refresh_token",
            refreshToken = tokens.refreshToken,
            clientId = AuthConfig.clientId,
        )
        persist(response, previousRefreshToken = tokens.refreshToken)
    } catch (e: HttpException) {
        // 400 invalid_grant => the refresh token was revoked or expired: force a fresh login.
        if (e.code() == 400 || e.code() == 401) {
            store.clear()
            _state.value = AuthState.Error("Your Spotify session expired. Please sign in again.")
        }
        null
    } catch (e: IOException) {
        null // transient network failure: keep the session, let the caller retry later
    }

    private fun persist(response: TokenResponse, previousRefreshToken: String?): String {
        val refresh = response.refreshToken ?: previousRefreshToken
            ?: error("Token response had no refresh token")
        store.saveTokens(
            SecureTokenStore.Tokens(
                accessToken = response.accessToken,
                refreshToken = refresh,
                expiresAtMs = System.currentTimeMillis() + response.expiresIn * 1000,
            ),
        )
        return response.accessToken
    }

    private fun fail(message: String) {
        _state.value = AuthState.Error(message)
    }

    private companion object {
        const val TAG = "SpotifyAuth"
        const val EXPIRY_SKEW_MS = 60_000L
    }
}
