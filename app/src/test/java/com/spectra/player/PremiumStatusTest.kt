package com.spectra.player

import com.spectra.player.data.PremiumStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PremiumStatusTest {

    @Test
    fun webApiProductWinsWhenPresent() {
        assertEquals(PremiumStatus.PREMIUM, PremiumStatus.resolve("premium", canPlayOnDemand = false))
        assertEquals(PremiumStatus.FREE, PremiumStatus.resolve("free", canPlayOnDemand = true))
        assertEquals(PremiumStatus.FREE, PremiumStatus.resolve("open", canPlayOnDemand = null))
    }

    @Test
    fun fallsBackToAppRemoteCapabilityWhenProductIsMissing() {
        // Dev-mode apps no longer receive `product` from GET /me.
        assertEquals(PremiumStatus.PREMIUM, PremiumStatus.resolve(null, canPlayOnDemand = true))
        assertEquals(PremiumStatus.FREE, PremiumStatus.resolve(null, canPlayOnDemand = false))
    }

    @Test
    fun unknownUntilEitherSignalArrives() {
        assertEquals(PremiumStatus.UNKNOWN, PremiumStatus.resolve(null, canPlayOnDemand = null))
    }
}
