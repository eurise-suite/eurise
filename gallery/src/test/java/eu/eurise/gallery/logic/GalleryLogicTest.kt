package eu.eurise.gallery.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneOffset

class GalleryLogicTest {
    private fun at(date: String) = LocalDate.parse(date).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun item(id: Long, date: String = "2026-11-08", bucket: String = "Camera", name: String = "f$id.jpg", size: Long = 10, video: Boolean = false, remote: String? = null) =
        MediaItem(id, "u$id", video, at(date), 0, bucket, false, name, size, remote)

    private fun record(id: Long, uri: String, state: Int = UploadRecord.UPLOADED) =
        UploadRecord(id, uri, "Eurise/Foto/$id.jpg", at("2026-01-01"), "$id.jpg", 5, false, 0, "Camera", state)

    @Test
    fun queueKeepsChosenFoldersFromTheDateAndSkipsKnown() {
        val media = listOf(item(1), item(2, bucket = "Screenshots"), item(3, "2020-01-01"), item(4))
        val queue = uploadQueue(media, UploadRule(setOf("Camera"), at("2026-01-01")), setOf("u4"))
        assertEquals(listOf(1L), queue.map { it.id })
    }

    @Test
    fun mergeMarksUploadedAndKeepsCloudOnly() {
        val merged = mergeCloud(listOf(item(1), item(2)), listOf(record(7, "u1"), record(8, "gone"), record(9, "u2", UploadRecord.EXCLUDED), record(10, "alsoGone", UploadRecord.DELETE_REMOTE)))
        assertEquals("Eurise/Foto/7.jpg", merged.first { it.id == 1L }.remote)
        assertNull(merged.first { it.id == 2L }.remote)
        assertEquals(listOf(-8L), merged.filter { it.cloudOnly }.map { it.id })
    }

    @Test
    fun deletePlanFollowsTheChoice() {
        val items = listOf(item(1, remote = "r1"), item(2), MediaItem(-3, "u3", false, 0, 0, "", false, "", 0, "r3", true))
        assertEquals(listOf(1L, 2L), deletePlan(items, DeleteChoice.PhoneOnly).phone.map { it.id })
        assertTrue(deletePlan(items, DeleteChoice.PhoneOnly).cloud.isEmpty())
        assertTrue(deletePlan(items, DeleteChoice.CloudOnly).phone.isEmpty())
        assertEquals(listOf(1L, -3L), deletePlan(items, DeleteChoice.Both).cloud.map { it.id })
    }

    @Test
    fun uniqueNameKeepsExtension() {
        assertEquals("a-5.jpg", uniqueName("a.jpg", 5))
        assertEquals("a-5", uniqueName("a", 5))
    }

    @Test
    fun cellsHideTheExactPosition() {
        assertEquals(placeCell(44.8312, 11.6201), placeCell(44.8399, 11.6899 - 0.05))
        assertEquals("44.85,11.65", placeCell(44.8312, 11.6201))
    }

    @Test
    fun videoLocationIsRead() {
        assertEquals(45.4642 to 9.19, parseVideoLocation("+45.4642+009.1900/"))
        assertEquals(-33.5 to -70.25, parseVideoLocation("-33.5-070.25+500.0/"))
        assertNull(parseVideoLocation("niente"))
        assertNull(parseVideoLocation(null))
    }

    @Test
    fun cellsWithTheSameNameBecomeOnePlace() {
        val list = places(listOf(item(1), item(2), item(3)), mapOf("u1" to "a", "u2" to "b", "u3" to "c"), mapOf("a" to "Siena", "b" to "Siena"))
        assertEquals(listOf(2, 1), list.map { it.items.size })
        assertEquals("Siena", list[0].name)
    }

    @Test
    fun searchUnderstandsDatesTypesAndPlaces() {
        val zone = ZoneOffset.UTC
        val today = LocalDate.parse("2026-11-09")
        val photo = item(1, "2023-07-14", bucket = "Viaggi")
        assertTrue(matchesSearch(photo, "luglio 2023", null, today, zone))
        assertFalse(matchesSearch(photo, "agosto", null, today, zone))
        assertTrue(matchesSearch(photo, "foto di Siena", "Siena", today, zone))
        assertFalse(matchesSearch(photo, "video", null, today, zone))
        assertTrue(matchesSearch(photo, "viaggi", null, today, zone))
        assertTrue(matchesSearch(item(2, "2026-11-08"), "ieri", null, today, zone))
    }

    @Test
    fun cleanupFindsBurstsAndDuplicates() {
        val base = at("2026-01-01")
        val shots = (0..2).map { MediaItem(it.toLong(), "b$it", false, base + it * 1000, 0, "Camera", false) }
        assertEquals(1, bursts(shots + MediaItem(9, "far", false, base + 60_000, 0, "Camera", false)).size)
        assertEquals(listOf(1L, 2L), extras(bursts(shots)).map { it.id })
        val dup = duplicateCandidates(listOf(item(1, name = "a.jpg"), item(2, "2020-01-01", name = "a.jpg"), item(3, "2021-01-01", name = "c.jpg")))
        assertEquals(listOf(listOf(1L, 2L)), dup.map { g -> g.map { it.id } })
    }

    @Test
    fun cropStaysInsideAndNeverEmpties() {
        assertArrayEquals(intArrayOf(10, 20, 80, 60), cropPixels(Crop(0.1f, 0.2f, 0.9f, 0.8f), 100, 100))
        val dragged = dragCrop(Crop(0.5f, 0.5f, 0.6f, 0.6f), 0, 0.5f, 0.5f)
        assertTrue(dragged.right - dragged.left >= 0.079f)
        assertEquals(0.5f, dragCrop(Crop(0f, 0f, 0.5f, 0.5f), 4, 0.9f, 0f).left, 0.001f)
    }

    @Test
    fun neutralAdjustIsIdentity() {
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f), adjustMatrix(Adjust()), 0.001f)
    }

    @Test
    fun marksFollowRotationAndFlip() {
        val state = EditState(texts = listOf(TextMark(1, "a", 0.25f, 0.1f, 0.05f, 0)), crop = Crop(0.2f, 0.2f, 0.8f, 0.8f))
        val turned = state.rotated()
        assertEquals(0.9f, turned.texts[0].x, 0.001f)
        assertEquals(0.25f, turned.texts[0].y, 0.001f)
        assertEquals(Crop(), turned.crop)
        assertEquals(0.75f, state.flipped().texts[0].x, 0.001f)
        // Quattro giri tornano al punto di partenza.
        val back = state.rotated().rotated().rotated().rotated()
        assertEquals(0, back.turns)
        assertEquals(0.25f, back.texts[0].x, 0.001f)
    }

    @Test
    fun flipAfterRotationInvertsTheTurns() {
        val state = EditState().rotated().flipped()
        assertEquals(3, state.turns)
        assertTrue(state.flip)
        assertEquals(1, state.flipped().turns)
    }

    @Test
    fun straightenCoversTheCorners() {
        assertEquals(1f, straightenScale(0f, 4f, 3f), 0.001f)
        assertTrue(straightenScale(10f, 4f, 3f) > 1f)
    }

    @Test
    fun ratioCropFitsAndKeepsTheFormatWhileDragging() {
        val square = ratioCrop(0.75f)
        assertEquals(0.75f, square.right - square.left, 0.001f)
        assertEquals(1f, square.bottom - square.top, 0.001f)
        val start = Crop(0.2f, 0.2f, 0.6f, 0.6f)
        val dragged = dragCropRatio(start, 3, -0.1f, 0f, 1f)
        assertEquals(0.3f, dragged.right - dragged.left, 0.001f)
        assertEquals(0.3f, dragged.bottom - dragged.top, 0.001f)
    }

    private val key = ByteArray(32) { it.toByte() }

    private fun sealed(plain: ByteArray, label: String = "a") =
        ByteArrayOutputStream().also { SecureStream.encrypt(key, label, ByteArrayInputStream(plain), it) }.toByteArray()

    private fun open(data: ByteArray, label: String = "a") =
        ByteArrayOutputStream().also { SecureStream.decrypt(key, label, ByteArrayInputStream(data), it) }.toByteArray()

    @Test
    fun secureStreamRoundTripsAcrossChunkBoundaries() {
        for (size in listOf(0, 1, SecureStream.CHUNK - 1, SecureStream.CHUNK, SecureStream.CHUNK + 1, SecureStream.CHUNK * 2)) {
            val plain = ByteArray(size) { (it * 31).toByte() }
            assertArrayEquals(plain, open(sealed(plain)))
        }
    }

    @Test
    fun secureStreamRejectsTamperingTruncationAndWrongLabel() {
        val data = sealed(ByteArray(SecureStream.CHUNK + 10) { 7 })
        val flipped = data.clone().also { it[40] = (it[40] + 1).toByte() }
        assertThrows(SecureStream.Corrupted::class.java) { open(flipped) }
        // Togliere l'ultimo blocco lascia un primo blocco che non è segnato come ultimo.
        assertThrows(SecureStream.Corrupted::class.java) { open(data.copyOf(12 + SecureStream.CHUNK + 16)) }
        assertThrows(SecureStream.Corrupted::class.java) { open(data, "b") }
    }
}
