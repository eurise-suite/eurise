package eu.eurise.feature.launcher.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoriesTest {

    private fun app(key: String, category: Category = Category.Other) = AppEntry(key, key, "pkg.$key", null, category)

    @Test
    fun knownPackagesFindTheirCategory() {
        assertEquals(Category.Communication, classifyApp("org.thoughtcrime.securesms", "Signal"))
        assertEquals(Category.Communication, classifyApp("com.microsoft.office.outlook", "Outlook"))
        assertEquals(Category.Work, classifyApp("com.microsoft.office.word", "Word"))
        assertEquals(Category.Browser, classifyApp("org.mozilla.firefox", "Firefox"))
        assertEquals(Category.Tools, classifyApp("com.beemdevelopment.aegis", "Aegis"))
    }

    @Test
    fun wordsCatchAppsNoListKnows() {
        assertEquals(Category.Money, classifyApp("it.example.mobile", "Banca Sella"))
        assertEquals(Category.Ai, classifyApp("xyz.assistant", "ChatGPT"))
        assertNull(classifyApp("com.example.game", "Scacchi"))
    }

    @Test
    fun partOfAWordDoesNotCount() {
        assertNull(classifyApp("com.example.cooperative", "Cooperative"))
    }

    @Test
    fun unknownAppsStayOnlyInAll() {
        assertNull(resolveCategory(app("x"), DefaultBarCategories, emptyMap()))
    }

    @Test
    fun manualMoveWinsOverTheType() {
        val moved = resolveCategory(app("x", Category.Work), DefaultBarCategories, mapOf("x" to "media"))
        assertEquals("media", moved?.id)
    }

    @Test
    fun nothingCanBeMovedIntoAll() {
        val staying = resolveCategory(app("x", Category.Work), DefaultBarCategories, mapOf("x" to ALL_APPS))
        assertEquals("work", staying?.id)
    }

    @Test
    fun untouchedFirstBarBecomesTheNewOne() {
        val raw = "favorites;Preferiti;star|communication;Comunicazione;chat|organization;Organizzazione;calendar|photos;Foto e file;folder|tools;Strumenti;tools|other;Altre app;grid"
        assertEquals(DefaultBarCategories, decodeCategories(raw))
    }

    @Test
    fun customisedBarIsKept() {
        val raw = "favorites;Preferiti;star|custom-1;Viaggi;plane"
        assertEquals(listOf("favorites", "custom-1"), decodeCategories(raw).map { it.id })
    }

    @Test
    fun legacyCategoriesKeepTheirApps() {
        val bar = listOf(BarCategory("organization", "Organizzazione", "calendar"))
        assertEquals("organization", resolveCategory(app("x", Category.Work), bar, emptyMap())?.id)
    }

    @Test
    fun chosenOrderComesFirstNewAppsAfter() {
        val list = listOf(app("a"), app("b"), app("c")).inUserOrder(listOf("c", "a"))
        assertEquals(listOf("c", "a", "b"), list.map { it.key })
    }

    @Test
    fun alphabetTakesTheFirstAppOfEachLetter() {
        assertEquals(listOf('A' to 0, 'E' to 2, '#' to 3), alphabetIndex(listOf("Aegis", "Amazon", "Èlite", "7zip")))
    }

    @Test
    fun appOrderSurvivesSaving() {
        val order = mapOf("work" to listOf("eurise:notes", "org.x/org.x.Main"), "media" to listOf("a"))
        assertEquals(order, decodeAppOrder(order.encodeAppOrder()))
    }
}
