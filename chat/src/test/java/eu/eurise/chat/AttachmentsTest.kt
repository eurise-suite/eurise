package eu.eurise.chat

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentsTest {

    @Test fun andata_e_ritorno() {
        val plain = ByteArray(100_000) { (it * 31 + 7).toByte() }
        val s = Attachments.encrypt(plain)
        assertEquals(plain.size, s.bytes.size)
        assertFalse(plain.contentEquals(s.bytes))
        assertArrayEquals(plain, Attachments.decrypt(s.bytes, s.info))
    }

    @Test fun blocco_come_la_specifica() {
        val info = Attachments.encrypt("ciao".toByteArray()).info
        assertEquals("v2", info.getString("v"))
        val key = info.getJSONObject("key")
        assertEquals("oct", key.getString("kty"))
        assertEquals("A256CTR", key.getString("alg"))
        assertTrue(key.getBoolean("ext"))
        // Chiave in base64 per indirizzi senza «=»: 32 byte = 43 caratteri.
        assertEquals(43, key.getString("k").length)
        assertFalse(key.getString("k").contains('=') || key.getString("k").contains('+') || key.getString("k").contains('/'))
        // Vettore iniziale: 16 byte, gli ultimi 8 a zero.
        val iv = Attachments.unb64(info.getString("iv"))
        assertEquals(16, iv.size)
        assertTrue((8 until 16).all { iv[it] == 0.toByte() })
        assertEquals(43, info.getJSONObject("hashes").getString("sha256").length)
    }

    @Test fun chiavi_nuove_a_ogni_file() {
        val a = Attachments.encrypt("stesso".toByteArray())
        val b = Attachments.encrypt("stesso".toByteArray())
        assertNotEquals(a.info.getJSONObject("key").getString("k"), b.info.getJSONObject("key").getString("k"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun un_byte_cambiato_non_si_apre() {
        val s = Attachments.encrypt(ByteArray(64) { it.toByte() })
        s.bytes[10] = (s.bytes[10] + 1).toByte()
        Attachments.decrypt(s.bytes, s.info)
    }

    @Test fun legge_anche_base64_con_uguale_e_normale() {
        // Altri client mandano a volte la chiave con «=» o con l'alfabeto normale.
        val s = Attachments.encrypt("referto".toByteArray())
        val info = JSONObject(s.info.toString())
        val k = info.getJSONObject("key").getString("k")
        info.getJSONObject("key").put("k", k.replace('-', '+').replace('_', '/') + "=")
        info.put("iv", info.getString("iv") + "==")
        assertEquals("referto", String(Attachments.decrypt(s.bytes, info)))
    }
}
