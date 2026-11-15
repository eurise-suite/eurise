package eu.eurise.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DavTest {
    @Test fun calendariDalMultistatus() {
        val xml = """<?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/" xmlns:ic="http://apple.com/ns/ical/">
            <d:response><d:href>/remote.php/dav/calendars/marco/personale/</d:href>
              <d:propstat><d:prop><d:resourcetype><d:collection/><c:calendar/></d:resourcetype><d:displayname>Personale</d:displayname>
              <c:supported-calendar-component-set><c:comp name="VEVENT"/></c:supported-calendar-component-set><cs:getctag>42</cs:getctag><ic:calendar-color>#3E5C8A</ic:calendar-color></d:prop>
              <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              <d:propstat><d:prop><d:sync-token/></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response>
            <d:response><d:href>/remote.php/dav/calendars/marco/con%20spazio/</d:href>
              <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>
            </d:multistatus>"""
        val r = DavHttp.parseMultistatus(xml)
        assertEquals(2, r.size)
        assertEquals("Personale", r[0].props["displayname"])
        assertEquals("42", r[0].props["getctag"])
        assertTrue("calendar" in r[0].types)
        assertTrue("comp:VEVENT" in r[0].types)
        assertTrue(r[0].collection)
        assertEquals(null, r[0].props["sync-token"])
        assertEquals("/remote.php/dav/calendars/marco/con spazio/", r[1].href)
    }
}
