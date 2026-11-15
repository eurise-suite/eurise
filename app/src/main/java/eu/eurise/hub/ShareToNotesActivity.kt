package eu.eurise.hub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Condividi › Note» (15 novembre): un testo o un link da qualsiasi app diventa una nota, senza aprire Note. Il titolo
 * è l'oggetto (se l'app lo passa) o la prima riga; un avviso dice che è fatto, senza altre schermate.
 */
class ShareToNotesActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        if (text.isEmpty() && subject.isEmpty()) { finish(); return }
        val (title, body) = when {
            subject.isNotEmpty() -> subject to text
            text.contains('\n') -> text.substringBefore('\n').trim() to text.substringAfter('\n').trim()
            text.length <= 80 -> text to ""
            else -> text.take(60).substringBeforeLast(' ') + "…" to text
        }
        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val ok = runCatching { (app as EuriseApplication).data.notes.save(null, title, body) }.isSuccess
            withContext(Dispatchers.Main) {
                Toast.makeText(app, if (ok) R.string.share_note_done else R.string.share_note_failed, Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
