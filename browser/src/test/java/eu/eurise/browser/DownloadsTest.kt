package eu.eurise.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsTest {
    private val ext: (String) -> String? = { mapOf("application/pdf" to "pdf", "text/csv" to "csv")[it] }
    private fun name(url: String, disposition: String? = null, mime: String? = null) = Downloads.fileName(url, disposition, mime, ext)

    @Test fun nomeDalSito() {
        assertEquals("Polizza RCA 2026.pdf", name("https://x.it/get?id=7", "attachment; filename=\"Polizza RCA 2026.pdf\""))
        assertEquals("fattura.pdf", name("https://x.it/get", "attachment; filename=fattura.pdf; size=3"))
    }

    @Test fun nomeConAccentiCodificato() {
        assertEquals("Città è.pdf", name("https://x.it/a", "attachment; filename=\"x.pdf\"; filename*=UTF-8''Citt%C3%A0%20%C3%A8.pdf"))
    }

    @Test fun nomeDallIndirizzoSenzaDomanda() {
        assertEquals("Orario estivo.pdf", name("https://x.it/doc/Orario%20estivo.pdf?v=3#p2"))
    }

    @Test fun estensioneDalTipo() {
        assertEquals("export.csv", name("https://x.it/export", mime = Downloads.mime("text/csv; charset=utf-8")))
        assertEquals("download.pdf", name("blob:https://x.it/1234", mime = "application/pdf"))
    }

    @Test fun niente_barre_ne_caratteri_vietati() {
        assertEquals("bad_name.txt", name("https://x.it/a", "attachment; filename=\"../dir/bad:name.txt\""))
    }
}
