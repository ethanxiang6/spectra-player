package com.spectra.player.data.remote

import com.spectra.player.auth.SpotifyAuthManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/** Adds `Authorization: Bearer <token>`, refreshing the token first if it is about to expire. */
class BearerInterceptor(private val auth: SpotifyAuthManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { auth.validAccessToken() }
        val request = if (token != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

/** On a 401, refresh once and replay the request. */
class TokenAuthenticator(private val auth: SpotifyAuthManager) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null // already retried once: give up
        val stale = response.request.header("Authorization")?.removePrefix("Bearer ")
        val fresh = runBlocking { auth.refreshAfterUnauthorized(stale) } ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
    }
}

/** Honors short `Retry-After` windows on 429; longer ones surface as an error to the UI. */
class RateLimitInterceptor(private val maxWaitSeconds: Long = 5) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        if (response.code == 429) {
            val wait = response.header("Retry-After")?.toLongOrNull()
            if (wait != null && wait <= maxWaitSeconds) {
                response.close()
                Thread.sleep(wait * 1000)
                response = chain.proceed(chain.request())
            }
        }
        return response
    }
}
