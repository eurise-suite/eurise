package eu.eurise.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class PrivacyScoreTest {
    @Test fun onThePhoneIsTen() = assertEquals(10, privacyScore(DataOut.None, Jurisdiction.Phone))

    @Test fun jurisdictionMovesOnePoint() {
        assertEquals(7, privacyScore(DataOut.Minimal, Jurisdiction.CH))
        assertEquals(5, privacyScore(DataOut.Minimal, Jurisdiction.US))
    }

    @Test fun endToEndLiftsACloud() {
        assertEquals(5, privacyScore(DataOut.Personal, Jurisdiction.EU))
        assertEquals(9, privacyScore(DataOut.Personal, Jurisdiction.EU, endToEnd = true))
    }

    @Test fun onlyThePhoneReachesTen() = assertEquals(9, privacyScore(DataOut.Anonymous, Jurisdiction.Own, endToEnd = true))

    @Test fun closedServicesStayLow() {
        assertEquals(2, privacyScore(DataOut.Minimal, Jurisdiction.US, closed = true))
        assertEquals(1, privacyScore(DataOut.Profiled, Jurisdiction.US, closed = true))
    }

    @Test fun hiddenIpHelpsOnlyWhereTheAddressWasTheTrace() {
        assertEquals(8, privacyScore(DataOut.Minimal, Jurisdiction.EU, hiddenIp = 1))
        assertEquals(9, privacyScore(DataOut.Minimal, Jurisdiction.EU, hiddenIp = 2))
        assertEquals(9, privacyScore(DataOut.Anonymous, Jurisdiction.EU, hiddenIp = 2))
        assertEquals(5, privacyScore(DataOut.Personal, Jurisdiction.EU, hiddenIp = 2))
        assertEquals(2, privacyScore(DataOut.Personal, Jurisdiction.US, closed = true, hiddenIp = 2))
    }

    @Test fun bands() {
        assertEquals(PrivacyBand.Good, privacyBand(8))
        assertEquals(PrivacyBand.Fair, privacyBand(5))
        assertEquals(PrivacyBand.Poor, privacyBand(4))
    }
}
