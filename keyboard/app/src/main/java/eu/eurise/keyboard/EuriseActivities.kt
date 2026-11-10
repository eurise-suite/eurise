package eu.eurise.keyboard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import java.io.File

/**
 * Una tastiera non può mostrare la richiesta di un permesso: questa schermata trasparente la mostra al posto suo
 * e si chiude subito dopo. Si apre solo quando si tocca "Permetti" in un pannello di Eurise.
 */
class EurisePermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wanted = intent.getStringArrayExtra(EXTRA_PERMISSIONS).orEmpty()
        if (wanted.isEmpty()) finish() else requestPermissions(wanted, 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }

    companion object {
        const val EXTRA_PERMISSIONS = "permissions"
    }
}

/**
 * Sceglie un file con il selettore di Android (telefono e cloud che espongono le loro cartelle) e ne tiene una copia
 * privata della tastiera, da allegare quando si torna a scrivere. La copia si cancella alla scelta successiva.
 */
class EuriseFilePickActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            runCatching { startActivityForResult(pick, 1) }.onFailure { finish() }
        }
    }

    @Deprecated("API di Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data?.data?.takeIf { resultCode == RESULT_OK }?.let { copy(it) }
        finish()
    }

    private fun copy(uri: Uri) = runCatching {
        val dir = File(cacheDir, "eurise_pick").apply { deleteRecursively(); mkdirs() }
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }?.replace('/', '_')?.takeIf { it.isNotBlank() } ?: "file"
        val target = File(dir, name)
        contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
        pending = PickedFile(target, contentResolver.getType(uri) ?: "application/octet-stream")
    }

    data class PickedFile(val file: File, val mime: String)

    companion object {
        /** Il file appena scelto, da allegare quando la tastiera torna visibile. */
        @Volatile var pending: PickedFile? = null
    }
}
