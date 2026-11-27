package eu.eurise.hub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import eu.eurise.core.suite.PostazioneContract as C
import eu.eurise.feature.launcher.files.RecentFilesRepository
import kotlinx.coroutines.runBlocking

/**
 * Apre nell'app Eurise una cosa che la Postazione ha mostrato o trovato con la lente (impegno, cosa da fare, nota,
 * documento, farmaci). Protetta dal permesso della suite nel manifesto: solo le app firmate con la nostra chiave la
 * avviano. Un documento non arriva mai come indirizzo da fuori: si riprende dal suo numero con le regole della ricerca
 * ([RecentFilesRepository.byId]) e si passa con il permesso di lettura all'app che lo mostra.
 */
class OpenFoundActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getLongExtra(C.EXTRA_ID, 0L) ?: 0L
        when (intent?.getStringExtra(C.EXTRA_KIND)) {
            C.KIND_EVENT -> section("event:$id:" + intent.getLongExtra(C.EXTRA_BEGIN, 0L))
            C.KIND_TASK -> section("task:$id")
            C.KIND_NOTE -> section("note:$id")
            C.KIND_MEDICINES -> section("medicines")
            C.KIND_FILE -> {
                val files = RecentFilesRepository(applicationContext)
                runCatching { runBlocking { files.byId(id) } }.getOrNull()?.let { files.open(it) }
            }
        }
        finish()
    }

    private fun section(value: String) {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SECTION, value)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }
}
