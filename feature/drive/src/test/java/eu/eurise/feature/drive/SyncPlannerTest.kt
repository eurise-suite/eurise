package eu.eurise.feature.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlannerTest {
    private fun side(s: String, size: Long = 1) = Side(s, size)
    private val k = mapOf("a" to Known("l1", "r1"))

    private fun one(local: Side?, remote: Side?, known: Map<String, Known> = k) =
        SyncPlanner.plan(listOfNotNull(local?.let { "a" to it }).toMap(), listOfNotNull(remote?.let { "a" to it }).toMap(), known)

    @Test fun nothingChanged() = assertEquals(emptyList<SyncAction>(), one(side("l1"), side("r1")))
    @Test fun localEdit() = assertEquals(listOf(SyncAction.Upload("a")), one(side("l2"), side("r1")))
    @Test fun remoteEdit() = assertEquals(listOf(SyncAction.Download("a")), one(side("l1"), side("r2")))
    @Test fun bothEdited() = assertEquals(listOf(SyncAction.Conflict("a")), one(side("l2", 5), side("r2", 9)))
    @Test fun bothEditedSameSize() = assertEquals(listOf(SyncAction.Record("a")), one(side("l2", 5), side("r2", 5)))
    @Test fun deletedOnCloud() = assertEquals(listOf(SyncAction.DeleteLocal("a")), one(side("l1"), null))
    @Test fun deletedOnPhone() = assertEquals(listOf(SyncAction.DeleteRemote("a")), one(null, side("r1")))
    @Test fun deletedOnCloudButEditedHereSurvives() = assertEquals(listOf(SyncAction.Upload("a")), one(side("l2"), null))
    @Test fun deletedHereButEditedOnCloudSurvives() = assertEquals(listOf(SyncAction.Download("a")), one(null, side("r2")))
    @Test fun brandNew() {
        assertEquals(listOf(SyncAction.Upload("a")), one(side("x"), null, emptyMap()))
        assertEquals(listOf(SyncAction.Download("a")), one(null, side("x"), emptyMap()))
    }

    @Test fun massDeletionIsStopped() {
        val many = List(50) { SyncAction.DeleteRemote("f$it") }
        assertTrue(SyncPlanner.tooManyDeletions(many, 50))
        assertFalse(SyncPlanner.tooManyDeletions(many.take(5), 50))
    }

    @Test fun conflictNames() {
        assertEquals("doc/foto (conflitto).jpg", SyncPlanner.conflictName("doc/foto.jpg"))
        assertEquals("a.b/nota (conflitto)", SyncPlanner.conflictName("a.b/nota"))
    }
}
