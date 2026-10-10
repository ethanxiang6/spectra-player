package com.spectra.player.auth

import android.content.Context

/** Persists OAuth tokens and the short-lived PKCE session, encrypted with a Keystore key. */
class SecureTokenStore(context: Context) {

    data class Tokens(val accessToken: String, val refreshToken: String, val expiresAtMs: Long)
    data class PendingLogin(val verifier: String, val state: String)

    private val prefs = context.getSharedPreferences("spectra_secure", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher()

    fun saveTokens(tokens: Tokens) {
        prefs.edit()
            .putString(K_ACCESS, cipher.encrypt(tokens.accessToken))
            .putString(K_REFRESH, cipher.encrypt(tokens.refreshToken))
            .putString(K_EXPIRES, cipher.encrypt(tokens.expiresAtMs.toString()))
            .apply()
    }

    fun tokens(): Tokens? {
        val access = read(K_ACCESS) ?: return null
        val refresh = read(K_REFRESH) ?: return null
        val expires = read(K_EXPIRES)?.toLongOrNull() ?: return null
        return Tokens(access, refresh, expires)
    }

    fun savePending(login: PendingLogin) {
        prefs.edit()
            .putString(K_VERIFIER, cipher.encrypt(login.verifier))
            .putString(K_STATE, cipher.encrypt(login.state))
            .apply()
    }

    /** Reads and clears the pending PKCE session: a verifier/state pair is single-use. */
    fun takePending(): PendingLogin? {
        val verifier = read(K_VERIFIER)
        val state = read(K_STATE)
        prefs.edit().remove(K_VERIFIER).remove(K_STATE).apply()
        return if (verifier != null && state != null) PendingLogin(verifier, state) else null
    }

    fun clear() = prefs.edit().clear().apply()

    private fun read(key: String): String? = prefs.getString(key, null)?.let(cipher::decrypt)

    private companion object {
        const val K_ACCESS = "access"
        const val K_REFRESH = "refresh"
        const val K_EXPIRES = "expires_at"
        const val K_VERIFIER = "pkce_verifier"
        const val K_STATE = "pkce_state"
    }
}
