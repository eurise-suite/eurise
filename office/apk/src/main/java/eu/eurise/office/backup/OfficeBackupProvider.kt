package eu.eurise.office.backup

import android.content.Context
import eu.eurise.core.suite.SuiteBackupProvider
import eu.eurise.office.R
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Office nel backup della suite (area Backup, 21 novembre 2026): i documenti creati in Office (cartella `documenti`
 * nella memoria dell'app), i rapporti e le impostazioni. I file aperti da altre cartelle restano dove sono; i «recenti»
 * restano fuori perché puntano a indirizzi di questo telefono.
 *
 * Al ripristino un documento con lo stesso nome ma diverso non si sovrascrive: quello del backup arriva come
 * «nome (dal backup).estensione».
 */
class OfficeBackupProvider : SuiteBackupProvider() {
    override fun summary(context: Context): String? {
        val n = docs(context).walkTopDown().count { it.isFile }
        return if (n > 0) context.getString(R.string.backup_summary_office, n) else null
    }

    override fun export(context: Context, zip: ZipOutputStream) {
        docs(context).listFiles().orEmpty().forEach { addFile(zip, it, "documenti/${it.name}") }
        addFile(zip, File(context.filesDir, "rapporti.json"), "files/rapporti.json")
        addPrefs(context, zip, *PREFS.toTypedArray())
    }

    override fun restore(context: Context, zip: ZipInputStream) {
        while (true) {
            val e = zip.nextEntry ?: break
            val name = safeName(e) ?: continue
            when {
                name.startsWith("documenti/") -> {
                    val rel = name.removePrefix("documenti/").takeIf { it.isNotEmpty() } ?: continue
                    val target = File(docs(context), rel)
                    val tmp = File(context.cacheDir, "documento-ripristino").also { t -> t.outputStream().use { zip.copyTo(it) } }
                    when {
                        !target.exists() -> { target.parentFile?.mkdirs(); tmp.renameTo(target) || run { tmp.copyTo(target); tmp.delete() } }
                        target.readBytes().contentEquals(tmp.readBytes()) -> tmp.delete()
                        else -> {
                            val other = File(target.parentFile, "${target.nameWithoutExtension} (dal backup)" + (target.extension.takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ""))
                            tmp.copyTo(other, overwrite = true); tmp.delete()
                        }
                    }
                }
                name == "files/rapporti.json" -> {
                    val target = File(context.filesDir, "rapporti.json")
                    if (!target.exists()) writeEntry(zip, target)
                }
                else -> restorePrefs(context, zip, name, PREFS)
            }
        }
    }

    private fun docs(context: Context) = File(context.filesDir, "documenti")

    companion object {
        private val PREFS = setOf("office", "office_rapporti")
    }
}
