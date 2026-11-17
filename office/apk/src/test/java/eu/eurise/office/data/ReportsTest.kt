package eu.eurise.office.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportsTest {
    private fun exit(reason: Int, status: Int = 0, description: String = "") = Reports.Exit(1L, true, reason, status, 300_000L, description)

    @Test fun problemiVeri() {
        assertTrue(Reports.isProblem(exit(Reports.REASON_CRASH_NATIVE)))
        assertTrue(Reports.isProblem(exit(Reports.REASON_CRASH)))
        assertTrue(Reports.isProblem(exit(Reports.REASON_LOW_MEMORY)))
        assertTrue(Reports.isProblem(exit(Reports.REASON_ANR)))
        assertTrue(Reports.isProblem(exit(Reports.REASON_SIGNALED, 11)))
        assertTrue(Reports.isProblem(exit(Reports.REASON_SIGNALED, 9, "lmk: top app")))
    }

    @Test fun chiusureNormaliNonContano() {
        assertFalse(Reports.isProblem(exit(Reports.REASON_EXIT_SELF)))
        assertFalse(Reports.isProblem(exit(Reports.REASON_SIGNALED, 9, "user request")))
        assertFalse(Reports.isProblem(exit(10))) // USER_REQUESTED
        assertFalse(Reports.isProblem(exit(13))) // OTHER
    }

    @Test fun tipoDelProblema() {
        assertEquals(Reports.Kind.Memory, Reports.kindOf(exit(Reports.REASON_LOW_MEMORY)))
        assertEquals(Reports.Kind.Memory, Reports.kindOf(exit(Reports.REASON_SIGNALED, 9, "lmk")))
        assertEquals(Reports.Kind.NativeCrash, Reports.kindOf(exit(Reports.REASON_SIGNALED, 6)))
        assertEquals(Reports.Kind.NotResponding, Reports.kindOf(exit(Reports.REASON_ANR)))
    }

    @Test fun parteTecnicaSenzaNomeDelFile() {
        val r = Reports.Report(1L, exit(Reports.REASON_CRASH_NATIVE, description = "crash"), "Text", "Segreto.docx", "0.1", "0.2", "Fairphone 6", "15")
        val text = Reports.technical(r)
        assertTrue(text.contains("arresto nativo"))
        assertTrue(text.contains("motore"))
        assertTrue(text.contains("memoria usata: 292 MB"))
        assertFalse(text.contains("Segreto"))
    }
}
