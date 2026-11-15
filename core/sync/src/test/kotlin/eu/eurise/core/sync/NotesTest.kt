package eu.eurise.core.sync

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesTest {
    private fun doc(title: String, vararg blocks: String, updated: String = "2026-11-15T08:00:00Z") =
        NoteDoc(title, linkedMapOf("id" to "n1", "notebook" to "Lavoro", "updated" to updated), blocks.toList(), "body{}").write()

    @Test fun letturaEScritturaUguali() {
        val text = doc("Riunione & piano", "<p>uno</p>", "<p>due</p>")
        val d = NoteDoc.read(text)
        assertEquals("Riunione & piano", d.title)
        assertEquals(listOf("<p>uno</p>", "<p>due</p>"), d.blocks)
        assertEquals("Lavoro", d.meta["notebook"])
        assertEquals(text, d.write())
    }

    @Test fun unioneDiNote() {
        val base = NoteDoc.read(doc("Riunione", "<p>uno</p>", "<p>due</p>"))
        val local = NoteDoc.read(doc("Riunione", "<p>UNO</p>", "<p>due</p>", updated = "2026-11-15T09:00:00Z"))
        val remote = NoteDoc.read(doc("Riunione Villa", "<p>uno</p>", "<p>due</p>", "<p>tre</p>", updated = "2026-11-15T08:50:00Z"))
        val m = NoteDoc.merge(base, local, remote)
        assertNotNull(m)
        assertEquals("Riunione Villa", m!!.title)
        assertEquals(listOf("<p>UNO</p>", "<p>due</p>", "<p>tre</p>"), m.blocks)
        assertEquals("2026-11-15T09:00:00Z", m.meta["updated"])
        assertNull(NoteDoc.merge(base, NoteDoc.read(doc("A", "<p>uno</p>")), NoteDoc.read(doc("B", "<p>uno</p>"))))
    }

    private class Store(val states: MutableList<NoteState>) : NoteStore {
        val saved = HashMap<String, Pair<String, String?>>()
        val conflicts = HashSet<String>()
        override fun states() = states.toList()
        override fun synced(uid: String, text: String, etag: String?) { saved[uid] = text to etag }
        override fun conflict(uid: String, remote: String, etag: String?) { conflicts += uid }
        override fun remove(uid: String) { saved.remove(uid) }
    }

    @Test fun giroConIlServer() {
        val base = doc("Riunione", "<p>uno</p>", "<p>due</p>")
        val local = doc("Riunione", "<p>UNO</p>", "<p>due</p>")
        val remote = doc("Riunione", "<p>uno</p>", "<p>due</p>", "<p>tre</p>")
        var put: String? = null
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.method) {
                "MKCOL" -> MockResponse().setResponseCode(405)
                "PROPFIND" -> MockResponse().setResponseCode(207).setBody(
                    """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">
                    <d:response><d:href>/dav/Eurise/Note/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                    <d:response><d:href>/dav/Eurise/Note/n1.html</d:href><d:propstat><d:prop><d:resourcetype/><d:getetag>"e2"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                    <d:response><d:href>/dav/Eurise/Note/n2.html</d:href><d:propstat><d:prop><d:resourcetype/><d:getetag>"x1"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
                    </d:multistatus>""")
                "GET" -> if (request.path!!.endsWith("n1.html")) MockResponse().setBody(remote).setHeader("ETag", "\"e2\"")
                    else MockResponse().setBody(doc("Nuova", "<p>dal telefono</p>")).setHeader("ETag", "\"x1\"")
                "PUT" -> {
                    assertEquals("\"e2\"", request.getHeader("If-Match"))
                    put = request.body.readUtf8()
                    MockResponse().setResponseCode(204).setHeader("ETag", "\"e3\"")
                }
                else -> MockResponse().setResponseCode(500)
            }
        }
        server.start()
        try {
            val store = Store(mutableListOf(NoteState("n1", local, base, "\"e1\"")))
            val events = NotesSync(DavHttp(server.url("/dav/").toString(), "u", "p")).run(store)
            assertTrue(NoteEvent.Merged("n1") in events)
            assertTrue(NoteEvent.Received("n2") in events)
            assertEquals(listOf("<p>UNO</p>", "<p>due</p>", "<p>tre</p>"), NoteDoc.read(put!!).blocks)
            assertEquals("\"e3\"", store.saved["n1"]!!.second)
        } finally {
            server.shutdown()
        }
    }
}
