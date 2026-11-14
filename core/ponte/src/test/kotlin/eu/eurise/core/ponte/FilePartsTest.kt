package eu.eurise.core.ponte

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

class FilePartsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun splitsAndReassemblesInAnyOrder() {
        val bytes = Random(7).nextBytes(FileParts.PART * 2 + 1234)
        val parts = FileParts.split("../Preventivo Rossi.pdf", "application/pdf", bytes)
        assertEquals(3, parts.size)
        val inbox = FileParts.Inbox(tmp.newFolder())
        assertNull(inbox.add(parts[2]))
        assertNull(inbox.add(parts[0]))
        val done = inbox.add(parts[1])
        assertNotNull(done)
        assertArrayEquals(bytes, done!!.bytes)
        assertEquals("Preventivo Rossi.pdf", done.name)
        assertEquals("application/pdf", done.mime)
    }

    @Test
    fun rejectsDamagedFiles() {
        val parts = FileParts.split("a.txt", "text/plain", "ciao".toByteArray())
        val inbox = FileParts.Inbox(tmp.newFolder())
        assertNull(inbox.add(parts[0] + ("sha" to "0".repeat(64))))
        assertNull(inbox.add(parts[0] + ("fid" to "../../x")))
        assertNull(inbox.add(parts[0] + ("part" to "3")))
        assertNotNull(FileParts.Inbox(tmp.newFolder()).add(parts[0]))
    }

    @Test
    fun emptyFileIsOnePart() {
        val parts = FileParts.split("vuoto", "text/plain", ByteArray(0))
        assertEquals(1, parts.size)
        assertEquals(0, FileParts.Inbox(tmp.newFolder()).add(parts[0])!!.bytes.size)
    }
}
