package eu.eurise.password

import eu.eurise.password.kdbx.EntryDraft
import eu.eurise.password.kdbx.Kdbx
import eu.eurise.password.kdbx.Vault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le immagini delle note: allegati di KeePass dentro il file cifrato (24 novembre 2026). */
class AttachmentsTest {
    private val fixture get() = javaClass.getResource("/prova.kdbx")!!.readBytes()
    private val key = Kdbx.compositeKey("prova-eurise")
    private fun vault(file: eu.eurise.password.kdbx.KdbxFile) = Vault(file.document, file.binaries)

    // ---- Immagini nelle note (allegati di KeePass) ----

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3)
    private val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 9, 9, 9)

    @Test fun imagesStayInTheFileEncrypted() {
        val file = Kdbx.read(fixture, key)
        val v = vault(file)
        val id = v.save(EntryDraft("Passaporto", "", "", "", "scadenza 2031", null, addAttachments = listOf("Pagina 1.jpg" to jpg, "Pagina 1.jpg" to png)), null)
        val e = v.entries().first { it.uuid == id }
        assertEquals(listOf("Pagina 1.jpg", "Pagina 1 (2).jpg"), e.attachments.map { it.name })
        assertTrue(e.attachments.all { it.isImage })
        assertEquals(jpg.size, e.attachments[0].size)
        // Si scrive e si riapre: le immagini tornano uguali, e nel file non si leggono in chiaro.
        val bytes = Kdbx.write(file)
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("Pagina 1"))
        val again = vault(Kdbx.read(bytes, key))
        assertTrue(jpg.contentEquals(again.attachment(id, "Pagina 1.jpg")))
        assertTrue(png.contentEquals(again.attachment(id, "Pagina 1 (2).jpg")))
    }

    @Test fun removedImageLeavesNoTrace() {
        val file = Kdbx.read(fixture, key)
        val v = vault(file)
        val id = v.save(EntryDraft("Ricevuta", "", "", "", "garanzia", null, addAttachments = listOf("scontrino.jpg" to jpg)), null)
        v.save(EntryDraft("Ricevuta", "", "", "", "garanzia 2 anni", null), id)            // la versione con l'immagine va in cronologia
        v.save(EntryDraft("Ricevuta", "", "", "", "garanzia 2 anni", null, removeAttachments = setOf("scontrino.jpg")), id)
        assertTrue(v.entries().first { it.uuid == id }.attachments.isEmpty())
        assertEquals(0, file.binaries.size)                                                 // né nella voce né in cronologia
        val again = Kdbx.read(Kdbx.write(file), key)
        assertEquals(0, again.binaries.size)
    }

    @Test fun deletedForeverDropsItsImages() {
        val file = Kdbx.read(fixture, key)
        val v = vault(file)
        val keep = v.save(EntryDraft("Tessera", "", "", "", "", null, addAttachments = listOf("fronte.jpg" to jpg)), null)
        val gone = v.save(EntryDraft("Vecchia", "", "", "", "", null, addAttachments = listOf("retro.png" to png)), null)
        assertEquals(2, file.binaries.size)
        v.delete(gone)
        assertEquals(2, file.binaries.size)                                                 // nel cestino si può ancora recuperare
        assertTrue(v.deleteForever(gone))
        assertEquals(1, file.binaries.size)
        assertTrue(jpg.contentEquals(v.attachment(keep, "fronte.jpg")))                    // i riferimenti rinumerati restano giusti
    }

    @Test fun mergeBringsImagesFromTheOtherCopy() {
        val here = Kdbx.read(fixture, key)
        val there = Kdbx.read(fixture, key)
        val local = vault(here)
        local.save(EntryDraft("Qui", "", "", "", "", null, addAttachments = listOf("qui.jpg" to jpg)), null)
        val remote = vault(there)
        val id = remote.save(EntryDraft("Là", "", "", "", "", null, addAttachments = listOf("la.png" to png, "qui-uguale.jpg" to jpg)), null)
        local.mergeFrom(remote)
        assertTrue(png.contentEquals(local.attachment(id, "la.png")))
        assertTrue(jpg.contentEquals(local.attachment(id, "qui-uguale.jpg")))
        assertEquals(2, here.binaries.size)                                                 // la stessa immagine una volta sola
        val reopened = vault(Kdbx.read(Kdbx.write(here), key))
        assertTrue(png.contentEquals(reopened.attachment(id, "la.png")))
    }

}
