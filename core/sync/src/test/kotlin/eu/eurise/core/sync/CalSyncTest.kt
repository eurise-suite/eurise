package eu.eurise.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalSyncTest {
    private fun l(href: String?, etag: String?, dirty: Boolean = false, deleted: Boolean = false, uid: String = href ?: "nuovo") =
        LocalObject(href, uid, etag, dirty, deleted)

    @Test fun regole() {
        val local = listOf(
            l("/c/uguale.ics", "1"),
            l("/c/cambiato-qui.ics", "1", dirty = true),
            l("/c/cambiato-la.ics", "1"),
            l("/c/entrambi.ics", "1", dirty = true),
            l("/c/eliminato-qui.ics", "1", deleted = true),
            l("/c/eliminato-qui-cambiato-la.ics", "1", deleted = true),
            l("/c/eliminato-la.ics", "1"),
            l(null, null, dirty = true, uid = "nuovo"),
        )
        val remote = listOf(
            DavObject("/c/uguale.ics", "1"),
            DavObject("/c/cambiato-qui.ics", "1"),
            DavObject("/c/cambiato-la.ics", "2"),
            DavObject("/c/entrambi.ics", "2"),
            DavObject("/c/eliminato-qui.ics", "1"),
            DavObject("/c/eliminato-qui-cambiato-la.ics", "2"),
            DavObject("/c/arrivato.ics", "1"),
        )
        val plan = CalSync.plan(local, remote)
        assertTrue(CalAction.Upload(local[1]) in plan)
        assertTrue(CalAction.Download("/c/cambiato-la.ics") in plan)
        assertTrue(CalAction.Conflict(local[3], "/c/entrambi.ics") in plan)
        assertTrue(CalAction.DeleteRemote("/c/eliminato-qui.ics", "1") in plan)
        assertTrue(CalAction.Restore("/c/eliminato-qui-cambiato-la.ics") in plan)
        assertTrue(CalAction.DeleteLocal("/c/eliminato-la.ics") in plan)
        assertTrue(CalAction.Create(local[7]) in plan)
        assertTrue(CalAction.Download("/c/arrivato.ics") in plan)
        assertEquals(8, plan.size)
    }
}
