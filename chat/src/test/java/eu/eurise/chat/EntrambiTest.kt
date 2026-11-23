package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** «Entrambi» (tavola ChatEntrambi): il riassunto leggero dell'altro account e l'elenco insieme. */
class EntrambiTest {
    private fun room(id: String, time: Long, unread: Int = 0) = Room(id, id, "", time, unread, encrypted = false)

    @Test fun mergesByTimeAndMarksTheAccount() {
        val rows = Entrambi.merge(
            listOf(room("mamma", 30), room("giulia", 10)), Account.Personal,
            listOf(Entrambi.Other(room("cantiere", 40, 3), Event.Kind.Encrypted)), hideWork = false,
        )
        assertEquals(listOf("cantiere", "mamma", "giulia"), rows.map { it.room.id })
        assertEquals(Account.Work, rows[0].account)
        assertEquals(false, rows[0].mine)
    }

    @Test fun workStepsAsideOutOfHours() {
        val rows = Entrambi.merge(listOf(room("cliente", 50)), Account.Work, listOf(Entrambi.Other(room("mamma", 20), null)), hideWork = true)
        assertEquals(listOf("mamma"), rows.map { it.room.id })
    }

    @Test fun summaryHidesEncryptedPreviewsAndKeepsCounts() {
        val json = JSONObject(
            """{"account_data":{"events":[{"type":"m.direct","content":{"@luca:x":["!a:x"]}}]},
               "rooms":{"join":{
                 "!a:x":{"unread_notifications":{"notification_count":2},
                   "state":{"events":[{"type":"m.room.encryption","state_key":"","content":{}},
                                      {"type":"m.room.member","state_key":"@luca:x","content":{"displayname":"Luca"}}]},
                   "summary":{"m.heroes":["@luca:x"]},
                   "timeline":{"events":[{"type":"m.room.encrypted","event_id":"${'$'}1","sender":"@luca:x","origin_server_ts":100,"content":{}}]}},
                 "!b:x":{"state":{"events":[{"type":"m.room.name","state_key":"","content":{"name":"Cantiere"}}]},
                   "timeline":{"events":[{"type":"m.room.message","event_id":"${'$'}2","sender":"@capo:x","origin_server_ts":200,"content":{"msgtype":"m.text","body":"Si parte alle 7"}}]}}
               }}}""",
        )
        val list = Entrambi.parse(json, "@me:x")
        assertEquals(listOf("Cantiere", "Luca"), list.map { it.room.name })
        assertEquals("Si parte alle 7", list[0].room.lastText)
        assertEquals("", list[1].room.lastText)
        assertEquals(2, list[1].room.unread)
        assertEquals(true, list[1].room.encrypted)
        assertEquals(true, list[1].room.direct)
    }
}
