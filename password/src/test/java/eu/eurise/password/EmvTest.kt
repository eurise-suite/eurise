package eu.eurise.password

import eu.eurise.password.logic.Emv
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class EmvTest {
    private fun bytes(hex: String) = hex.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun ppseGivesAidsByPriority() {
        // 6F > A5 > BF0C > 61 (AID Maestro, priorità 2) e 61 (AID Visa, priorità 1)
        val ppse = bytes("6F 2E 84 0E 32504159 2E5359532E4444463031 A5 1C BF0C 19 61 0C 4F 07 A0000000043060 87 01 02 61 09 4F 07 A0000000031010")
        val aids = Emv.aids(ppse)
        assertEquals(2, aids.size)
        assertEquals("A0000000043060", Emv.hex(aids[0]))
    }

    @Test fun pdolIsFilled() {
        val fci = bytes("6F 13 84 07 A0000000031010 A5 08 9F38 05 9F66 04 9A 03")
        val pdol = Emv.pdol(fci)
        assertEquals(listOf(0x9F66 to 4, 0x9A to 3), pdol)
        val data = Emv.pdolData(pdol, LocalDate.of(2026, 11, 10), byteArrayOf(1, 2, 3, 4))
        assertArrayEquals(bytes("36004000 261110"), data)
        assertEquals("80A8000009830736004000261110" + "00", Emv.hex(Emv.gpo(data)))
    }

    @Test fun aflListsRecords() {
        val gpo = bytes("77 0E 82 02 1980 94 08 08010100 10010300")
        assertEquals(listOf(1 to 1, 2 to 1, 2 to 2, 2 to 3), Emv.afl(gpo))
        val format1 = bytes("80 06 1980 08010100")
        assertEquals(listOf(1 to 1), Emv.afl(format1))
    }

    @Test fun cardFromRecords() {
        val rec = bytes("70 1E 5A 08 4111111111111111 5F24 03 281231 5F20 0B 524F535349 2F4D4152494F")
        val card = Emv.card(listOf(rec))!!
        assertEquals("4111 1111 1111 1111", card.number)
        assertEquals("12/28", card.expiry)
        assertEquals("ROSSI MARIO", card.holder)
    }

    @Test fun cardFromTrack2() {
        val rec = bytes("70 13 57 11 5555555555554444 D2905 201 000000000F")
        val card = Emv.card(listOf(rec))!!
        assertEquals("5555 5555 5555 4444", card.number)
        assertEquals("05/29", card.expiry)
        assertEquals("", card.holder)
    }

    @Test fun badNumberIsRefused() {
        assertNull(Emv.card(listOf(bytes("70 0A 5A 08 4111111111111112"))))
        assertEquals("00B2010C00", Emv.hex(Emv.readRecord(1, 1)))
    }
}
