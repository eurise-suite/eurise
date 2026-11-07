package eu.eurise.core.cloud

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class WebDavTest {

    private lateinit var server: MockWebServer
    private lateinit var dav: WebDav

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        dav = WebDav(server.url("/remote.php/dav/files/anna%40murena.io/").toString(), "anna@murena.io", "segreta")
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun listsFolderWithoutItself() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(207).setBody(
                """<?xml version="1.0"?>
                <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
                  <d:response><d:href>/remote.php/dav/files/anna%40murena.io/Eurise/Note/</d:href>
                    <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype><d:getetag>"f1"</d:getetag></d:prop></d:propstat></d:response>
                  <d:response><d:href>/remote.php/dav/files/anna%40murena.io/Eurise/Note/ab%20c.html</d:href>
                    <d:propstat><d:prop><d:resourcetype/><d:getetag>"e1"</d:getetag><d:getcontentlength>120</d:getcontentlength></d:prop></d:propstat></d:response>
                </d:multistatus>"""
            )
        )
        val list = dav.list("Eurise/Note")
        assertEquals(listOf(DavEntry("ab c.html", false, "\"e1\"", 120)), list)
        val req = server.takeRequest()
        assertEquals("PROPFIND", req.method)
        assertEquals("1", req.getHeader("Depth"))
        assertEquals("/remote.php/dav/files/anna%40murena.io/Eurise/Note/", req.path)
        assertTrue(req.getHeader("Authorization")!!.startsWith("Basic "))
    }

    @Test
    fun missingFolderIsEmpty() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(emptyList<DavEntry>(), dav.list("Eurise/Note"))
    }

    @Test
    fun putSendsPreconditionsAndReturnsEtag() = runTest {
        server.enqueue(MockResponse().setResponseCode(204).setHeader("ETag", "\"e2\""))
        val etag = dav.put("Eurise/Note/x.html", "<p>ciao</p>", "text/html; charset=utf-8", ifMatch = "\"e1\"")
        assertEquals("\"e2\"", etag)
        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("\"e1\"", req.getHeader("If-Match"))
        assertEquals("<p>ciao</p>", req.body.readUtf8())
    }

    @Test
    fun changedMeanwhileIs412() = runTest {
        server.enqueue(MockResponse().setResponseCode(412))
        try {
            dav.put("Eurise/Note/x.html", "x", "text/html", ifMatch = "\"old\"")
            fail("doveva fallire")
        } catch (e: DavException) {
            assertEquals(412, e.code)
        }
    }

    @Test
    fun getMissingIsNull() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(dav.get("Eurise/Note/nessuna.html"))
    }

    @Test
    fun mkdirsCreatesEachLevelAndAcceptsExisting() = runTest {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(201))
        dav.mkdirs("Eurise/Note")
        assertEquals("/remote.php/dav/files/anna%40murena.io/Eurise/", server.takeRequest().path)
        assertEquals("/remote.php/dav/files/anna%40murena.io/Eurise/Note/", server.takeRequest().path)
    }
}
