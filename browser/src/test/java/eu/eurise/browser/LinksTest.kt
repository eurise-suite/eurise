package eu.eurise.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    private val brave = Links.engine(null)

    @Test
    fun addressesAndSearches() {
        assertEquals("https://eurise.eu", Links.resolve("eurise.eu", brave))
        assertEquals("https://www.comune.monza.it/orari", Links.resolve("www.comune.monza.it/orari", brave))
        assertEquals("https://search.brave.com/search?q=sushi%20via%20Italia", Links.resolve("sushi via Italia", brave))
        assertEquals("https://search.brave.com/search?q=meteo", Links.resolve("meteo", brave))
        assertEquals("http://192.168.1.10:8080/", Links.resolve("http://192.168.1.10:8080/", brave))
        assertEquals("https://192.168.1.10:8080", Links.resolve("192.168.1.10:8080", brave))
    }

    @Test
    fun queryShownInTheBar() {
        assertEquals("sushi via Italia", Links.queryOf("https://search.brave.com/search?q=sushi%20via%20Italia"))
        assertEquals("treni", Links.queryOf("https://www.qwant.com/?q=treni&t=web"))
        assertNull(Links.queryOf("https://example.org/?q=altro"))
    }

    @Test
    fun cleanLinks() {
        assertEquals(
            "https://example.org/articolo?id=7#parte",
            Links.clean("https://example.org/articolo?utm_source=x&id=7&fbclid=abc&utm_medium=y#parte"),
        )
        assertEquals("https://example.org/p", Links.clean("https://example.org/p?gclid=1&utm_campaign=2"))
        assertEquals("https://youtu.be/abc?t=30", Links.clean("https://youtu.be/abc?si=XYZ&t=30"))
        assertEquals("https://shop.example/?si=12", Links.clean("https://shop.example/?si=12"))
        assertEquals("https://example.org/", Links.clean("https://example.org/"))
    }

    @Test
    fun hostName() {
        assertEquals("eurise.eu", Links.host("https://www.eurise.eu/chi-siamo"))
    }
}
