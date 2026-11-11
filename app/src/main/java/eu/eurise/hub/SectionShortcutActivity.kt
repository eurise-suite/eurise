package eu.eurise.hub

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * Le icone di File e Scanner tra le app di Android (anche con un altro launcher): ogni icona è un activity-alias con la
 * sezione nei meta-data; qui si apre l'app Eurise su quella sezione e basta.
 */
class SectionShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val section = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getActivityInfo(componentName, PackageManager.GET_META_DATA).metaData?.getString(META_SECTION)
        }.getOrNull()
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SECTION, section ?: "drive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }

    companion object {
        const val META_SECTION = "eu.eurise.section"
    }
}
