package eu.eurise.feature.launcher.sounds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundRulesTest {
    private val mamma = PersonRule("k1", "Mamma", phones = listOf("+39 347 123 4567"), calls = "p:primavera", messages = "a:gocce")
    private val anna = PersonRule("k2", "Anna Bianchi", emails = listOf("anna@esempio.it"), calls = "m:nostra", messages = "m:nostra", email = "m:nostra", breakDnd = true)
    private val settings = SoundSettings(
        defaults = mapOf(SoundUse.Messages to "a:lieve"),
        persons = listOf(mamma, anna),
        apps = listOf(
            AppRule("com.whatsapp", "WhatsApp", "a:gocce", groups = "a:bolla"),
            AppRule("eu.eurise.mail", "Email", "a:campanella", fromHour = 9, toHour = 18),
            AppRule("org.telegram.messenger", "Telegram", SoundIds.SILENT),
        ),
    )

    @Test fun personWinsOverApp() {
        val c = SoundDecider.pick(settings, "com.whatsapp", SoundUse.Messages, Sender(names = listOf("Mamma")), isGroup = false, hour = 10)
        assertEquals(SoundChoice.Play("a:gocce"), c)
        val a = SoundDecider.pick(settings, "com.whatsapp", SoundUse.Messages, Sender(names = listOf("anna bianchi")), isGroup = false, hour = 10)
        assertEquals(SoundChoice.Play("m:nostra", breakDnd = true), a)
    }

    @Test fun groupsUseTheAppGroupSound() {
        val c = SoundDecider.pick(settings, "com.whatsapp", SoundUse.Messages, Sender(names = listOf("Mamma")), isGroup = true, hour = 10)
        assertEquals(SoundChoice.Play("a:bolla"), c)
    }

    @Test fun hoursAndSilence() {
        assertEquals(SoundChoice.Silent, SoundDecider.pick(settings, "eu.eurise.mail", SoundUse.Email, null, false, 20))
        assertEquals(SoundChoice.Play("a:campanella"), SoundDecider.pick(settings, "eu.eurise.mail", SoundUse.Email, null, false, 9))
        assertEquals(SoundChoice.Silent, SoundDecider.pick(settings, "org.telegram.messenger", SoundUse.Messages, null, false, 12))
        assertTrue(SoundDecider.inHours(23, 22, 7))
        assertTrue(SoundDecider.inHours(3, 22, 7))
        assertFalse(SoundDecider.inHours(12, 22, 7))
    }

    @Test fun defaultsOnlyForMessagesAndEmail() {
        assertEquals(SoundChoice.Play("a:lieve"), SoundDecider.pick(settings, "org.other", SoundUse.Messages, null, false, 12))
        assertEquals(SoundChoice.Untouched, SoundDecider.pick(settings, "org.other", SoundUse.Other, null, false, 12))
        assertEquals(SoundChoice.Untouched, SoundDecider.pick(settings, "org.other", SoundUse.Email, null, false, 12))
    }

    @Test fun matchesByPhoneEmailAndName() {
        assertTrue(SoundDecider.matches(mamma, Sender(phones = listOf("3471234567"))))
        assertTrue(SoundDecider.matches(anna, Sender(emails = listOf("ANNA@esempio.it"))))
        assertTrue(SoundDecider.matches(anna, Sender(names = listOf("Anna  Bianchi"))))
        assertFalse(SoundDecider.matches(anna, Sender(names = listOf("Anna"))))
        assertTrue(SoundDecider.matches(PersonRule("k", "Nicolò"), Sender(names = listOf("Nicolo"))))
    }

    @Test fun roundTrip() {
        assertEquals(settings, SoundStore.decode(SoundStore.encode(settings)))
        assertEquals(SoundSettings(), SoundStore.decode(null))
    }
}
