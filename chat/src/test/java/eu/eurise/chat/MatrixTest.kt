package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatrixTest {
    @Test fun identificativi() {
        assertEquals("@anna:studio.it", Matrix.normalizeId(" Anna:studio.it "))
        assertEquals("@anna:matrix.org", Matrix.normalizeId("@anna:matrix.org"))
        assertNull(Matrix.normalizeId("anna"))
        assertEquals("anna", Matrix.localpart("@anna:studio.it"))
    }

    @Test fun serverEAccesso() {
        assertEquals("https://matrix.studio.it", Matrix.wellKnown("""{"m.homeserver":{"base_url":"https://matrix.studio.it/"}}"""))
        assertNull(Matrix.wellKnown("{}"))
        assertEquals(listOf("m.login.sso", "m.login.token"), Matrix.loginFlows("""{"flows":[{"type":"m.login.sso"},{"type":"m.login.token"}]}"""))
    }

    @Test fun messaggi() {
        val a = JSONArray(
            """[
            {"type":"m.room.message","event_id":"${'$'}1","sender":"@anna:x.it","origin_server_ts":10,"content":{"msgtype":"m.text","body":"Ciao"}},
            {"type":"m.room.encrypted","event_id":"${'$'}2","sender":"@anna:x.it","origin_server_ts":11,"content":{}},
            {"type":"m.room.message","event_id":"${'$'}3","sender":"@anna:x.it","origin_server_ts":12,"content":{"msgtype":"m.image","body":"foto.jpg","url":"mxc://x.it/abc"}},
            {"type":"m.room.message","event_id":"${'$'}4","sender":"@anna:x.it","origin_server_ts":13,"content":{"msgtype":"m.text","body":"* Ciao!","m.new_content":{"body":"Ciao!"}}},
            {"type":"m.reaction","event_id":"${'$'}5","sender":"@anna:x.it","origin_server_ts":14,"content":{}}
            ]""",
        )
        val list = Matrix.events(a)
        assertEquals(listOf(Event.Kind.Text, Event.Kind.Encrypted, Event.Kind.Image), list.map { it.kind })
        assertEquals("Ciao", list[0].text)
        assertEquals("https://hs/_matrix/client/v1/media/download/x.it/abc", Matrix.mediaUrl("https://hs", list[2].mediaUrl!!, thumbnail = false))
    }

    @Test fun nomeDellaStanza() {
        val named = mapOf("m.room.name" to JSONObject("""{"name":"Famiglia"}"""))
        assertEquals("Famiglia", Matrix.roomName(named, emptyList(), emptyMap(), "@me:x.it"))
        assertEquals("Anna", Matrix.roomName(emptyMap(), listOf("@me:x.it", "@anna:x.it"), mapOf("@anna:x.it" to "Anna"), "@me:x.it"))
        assertEquals("a, b, c +1", Matrix.roomName(emptyMap(), listOf("@a:x", "@b:x", "@c:x", "@d:x"), emptyMap(), "@me:x"))
    }

    @Test fun richiestaDiVerifica() {
        val e = JSONObject(
            """{"type":"m.room.message","event_id":"${'$'}9","sender":"@anna:x.it","origin_server_ts":20,
            "content":{"msgtype":"m.key.verification.request","body":"Anna vuole verificare","to":"@me:x.it","from_device":"ABC","methods":["m.sas.v1"]}}""",
        )
        val ev = Matrix.event(e, mapOf("@anna:x.it" to "Anna"))!!
        assertEquals(Event.Kind.State, ev.kind)
        assertEquals("verify:Anna", ev.text)
        // I passi successivi (pronto, figure…) non sono messaggi.
        assertEquals(null, Matrix.event(JSONObject("""{"type":"m.key.verification.ready","event_id":"${'$'}10","sender":"@anna:x.it","content":{}}""")))
    }
}
