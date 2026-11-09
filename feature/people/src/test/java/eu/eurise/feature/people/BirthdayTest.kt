package eu.eurise.feature.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.MonthDay

class BirthdayTest {
    @Test fun withYear() = assertEquals(MonthDay.of(11, 19), parseBirthday("1990-11-19"))
    @Test fun withoutYear() = assertEquals(MonthDay.of(11, 19), parseBirthday("--11-19"))
    @Test fun invalid() {
        assertNull(parseBirthday("--13-40"))
        assertNull(parseBirthday("19 novembre"))
        assertNull(parseBirthday(null))
    }
}
