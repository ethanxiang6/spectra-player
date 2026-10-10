package com.spectra.player.data

import retrofit2.HttpException
import java.io.IOException

/** Turns transport / HTTP failures into something a person can act on. */
fun Throwable.userMessage(): String = when (this) {
    is HttpException -> when (code()) {
        401 -> "Your Spotify session expired. Sign in again."
        403 -> "Spotify refused this request (403). Development-mode apps only work for users added " +
            "under \"User Management\" in the Spotify dashboard, and the app owner needs Premium."
        404 -> "Spotify couldn't find that."
        429 -> "Spotify is rate-limiting requests. Try again in a moment."
        else -> "Spotify returned an error (${code()})."
    }
    is IOException -> "No connection. Check your network and try again."
    else -> "Something went wrong. Please try again."
}
