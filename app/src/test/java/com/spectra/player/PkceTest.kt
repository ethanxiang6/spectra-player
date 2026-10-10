package com.spectra.player

import com.spectra.player.auth.Pkce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {

    @Test
    fun challengeMatchesRfc7636AppendixB() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challengeFor("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun verifierHasRequestedLengthAndOnlyUnreservedCharacters() {
        val verifier = Pkce.newVerifier()
        assertEquals(96, verifier.length)
        assertTrue(verifier.all { it.isLetterOrDigit() || it in "-._~" })
    }

    @Test
    fun verifiersAndStatesAreNotReused() {
        assertNotEquals(Pkce.newVerifier(), Pkce.newVerifier())
        assertNotEquals(Pkce.newState(), Pkce.newState())
    }
}
