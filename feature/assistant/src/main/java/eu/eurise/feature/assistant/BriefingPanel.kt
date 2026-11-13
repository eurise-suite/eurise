package eu.eurise.feature.assistant

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import eu.eurise.core.suite.SpeechText
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.time.LocalDateTime

/** Da dove Alfred prende i fatti del punto: li raccoglie l'app (calendario, meteo, Borse, notizie). */
fun interface BriefingSource {
    suspend fun facts(now: LocalDateTime): BriefFacts
}

/**
 * Il punto della giornata (tavole AlfGiornata e AlfSera): un foglio in basso con il saluto, la massima e gli argomenti,
 * letti dalla voce di Alfred uno dopo l'altro. Pausa riprende dall'argomento in corso, il tasto accanto salta al
 * successivo; toccando un argomento si legge da lì. Si apre dal pulsante «Il punto» in «I tuoi impegni».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BriefingPanel(source: BriefingSource, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AlfredPrefs(context) }
    var briefing by remember { mutableStateOf<Briefing?>(null) }
    val reader = remember { BriefReader(context) }
    DisposableEffect(Unit) { onDispose { reader.release() } }
    LaunchedEffect(Unit) {
        val facts = runCatching { source.facts(LocalDateTime.now()) }.getOrNull()
            ?: BriefFacts(LocalDateTime.now(), addressSuffix(prefs), emptyList(), emptyList(), null, null, emptyList(), emptyList(), emptyList())
        val b = BriefingWriter.write(facts)
        briefing = b
        reader.start(b.spoken(), 0)
    }

    Box(
        Modifier.fillMaxSize().background(Color(0x66000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 720.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color.White)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFD6D6D0)))
            val b = briefing
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Seal), contentAlignment = Alignment.Center) {
                    BowTie(if (b == null) Mood.Thinking else if (reader.finished) Mood.Bow else Mood.Attentive, Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        (b?.label ?: stringResource(R.string.alfred_brief_preparing)).uppercase(),
                        fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.9.sp, color = Brass,
                    )
                    Text(b?.greeting ?: stringResource(R.string.alfred_brief_wait), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
                }
            }
            if (b == null) return@Column
            Text(b.motto, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 23.sp, color = Brass)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val playing = reader.playing
                RoundButton(
                    if (playing) EuriseIcons.pause() else EuriseIcons.play(),
                    stringResource(if (playing) R.string.alfred_brief_pause else R.string.alfred_brief_play), Navy, Color.White,
                ) { if (playing) reader.pause() else reader.start(b.spoken(), if (reader.finished) 0 else reader.current) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    val total = b.sections.size.coerceAtLeast(1)
                    val done = if (reader.finished) 1f else reader.current.toFloat() / total
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x241F2A44))) {
                        Box(Modifier.fillMaxWidth(done.coerceIn(0.03f, 1f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Navy))
                    }
                    Text(
                        when {
                            reader.finished -> stringResource(R.string.alfred_brief_done)
                            playing -> stringResource(R.string.alfred_brief_reading, reader.current + 1, total)
                            else -> stringResource(R.string.alfred_brief_paused, reader.current + 1, total)
                        },
                        fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = InkSoft,
                    )
                }
                RoundButton(EuriseIcons.next(), stringResource(R.string.alfred_brief_skip), Color(0xFFF4F4F1), Navy) {
                    reader.start(b.spoken(), (reader.current + 1).coerceAtMost(b.sections.size - 1))
                }
            }

            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                b.sections.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x121E1D1B)))
                    Row(
                        Modifier.fillMaxWidth().clickable { reader.start(b.spoken(), i) }.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val (tile, ink) = tint(s.kind)
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(tile), contentAlignment = Alignment.Center) {
                            Icon(icon(s.kind), null, tint = ink, modifier = Modifier.size(15.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(s.title, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                            Text(s.text, fontSize = 12.5.sp, lineHeight = 18.sp, color = InkSoft)
                            s.music?.let { pearl -> MusicRow(pearl, prefs) }
                        }
                        if (i == reader.current && !reader.finished) {
                            Text(stringResource(R.string.alfred_brief_now), fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = Plum)
                        }
                    }
                }
            }
        }
    }
}

/** «Ascolta»: la perla nell'app di musica scelta (la prima volta si sceglie qui, poi resta nelle disposizioni). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusicRow(pearl: Pearl, prefs: AlfredPrefs) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(prefs.musicBriefing) }
    var choosing by remember { mutableStateOf(false) }
    val apps = remember { AlfredMusic.apps(context) }
    if (apps.isEmpty()) {
        Text(stringResource(R.string.alfred_music_none), fontSize = 11.5.sp, color = InkSoft)
        return
    }
    val current = apps.firstOrNull { it.first == chosen }
    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (current != null && !choosing) {
            Pill(stringResource(R.string.alfred_music_listen_in, current.second), Navy, Color.White) { AlfredMusic.play(context, pearl, current.first) }
            Pill(stringResource(R.string.alfred_music_change), Color(0xFFF4F4F1), Navy) { choosing = true }
        } else {
            apps.forEach { (pkg, label) ->
                Pill(label, Color(0xFFF4F4F1), Navy) {
                    prefs.musicBriefing = pkg
                    chosen = pkg
                    choosing = false
                    AlfredMusic.play(context, pearl, pkg)
                }
            }
        }
    }
    if (current == null || choosing) Text(stringResource(R.string.alfred_music_note), fontSize = 11.sp, lineHeight = 15.sp, color = InkSoft)
}

@Composable
private fun Pill(label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 32.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = fg) }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(bg).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)) }
}

private fun icon(kind: BriefKind): ImageVector = when (kind) {
    BriefKind.Events -> EuriseIcons.calendar()
    BriefKind.Weather -> EuriseIcons.sun()
    BriefKind.Markets -> EuriseIcons.chart()
    BriefKind.News -> EuriseIcons.news()
    BriefKind.Music -> EuriseIcons.music()
    BriefKind.Art -> EuriseIcons.palette()
    BriefKind.Place -> EuriseIcons.place()
    BriefKind.Curiosity -> EuriseIcons.spark()
}

private fun tint(kind: BriefKind): Pair<Color, Color> = when (kind) {
    BriefKind.Events -> Color(0xFFDDE3F0) to Color(0xFF3E5C8A)
    BriefKind.Weather -> Color(0xFFF5E9CF) to Color(0xFF85672B)
    BriefKind.Markets -> Color(0xFFDCE7DF) to Color(0xFF2F6B45)
    BriefKind.News -> Color(0xFFE6E1EC) to Color(0xFF5B4A7A)
    BriefKind.Music -> Color(0xFFF2DDE5) to Plum
    BriefKind.Art -> Color(0xFFF3E2CF) to Color(0xFF7A4A2A)
    BriefKind.Place -> Color(0xFFDCE7DF) to Color(0xFF4F6B5A)
    BriefKind.Curiosity -> Color(0xFFF5E9CF) to Color(0xFF85672B)
}

private val Seal = Brush.linearGradient(listOf(Color(0xFF3E5C8A), Color(0xFF8A3A5C)))
private val Brass = Color(0xFF8A6A2A)
private val Navy = Color(0xFF1F2A44)
private val Ink = Color(0xFF1E1D1B)
private val InkSoft = Color(0xFF56564F)
private val Plum = Color(0xFF8A3A5C)

/**
 * La lettura del punto: un argomento dopo l'altro con la sintesi del telefono (voce di Alfred), sapendo sempre quale
 * si sta leggendo. Gli avvisi della sintesi arrivano su un altro thread: lo stato si aggiorna sul thread principale.
 */
class BriefReader(context: Context) {
    var current by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set

    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var pending: Pair<List<String>, Int>? = null
    private var parts: List<String> = emptyList()

    private var tts: TextToSpeech? = null
    private val holder = AlfredTts.open(context) { engine, _ -> main.post { tts = engine; onReady(engine) } }

    private fun onReady(tts: TextToSpeech) {
        ready = true
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {
                main.post { id.removePrefix("brief-").toIntOrNull()?.let { current = it }; playing = true; finished = false }
            }
            override fun onDone(id: String) {
                main.post { if (id == "brief-${parts.size - 1}") { playing = false; finished = true } }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) { main.post { playing = false } }
            override fun onStop(id: String, interrupted: Boolean) { main.post { playing = false } }
        })
        pending?.let { (p, i) -> start(p, i) }
        pending = null
    }

    /** Legge da [from] in poi; ciò che stava leggendo si ferma. */
    fun start(all: List<String>, from: Int) {
        if (all.isEmpty()) return
        val at = from.coerceIn(0, all.size - 1)
        current = at
        finished = false
        if (!ready) { pending = all to at; return }
        parts = all
        val tts = tts ?: return
        tts.stop()
        for (i in at until all.size) {
            tts.speak(SpeechText.clean(all[i]), if (i == at) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "brief-$i")
        }
        playing = true
    }

    fun pause() {
        runCatching { tts?.stop() }
        playing = false
    }

    fun release() = holder.shutdown()
}

/**
 * La musica di Alfred: il brano va all'app scelta dall'utente con l'intento standard «riproduci da ricerca» (Spotify,
 * YouTube Music, Deezer, VLC… e i lettori di musica sul telefono). Riceve solo titolo e autore.
 */
object AlfredMusic {
    private fun intent() = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)

    /** Le app che sanno riprodurre da una ricerca: pacchetto e nome. */
    fun apps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        return runCatching { pm.queryIntentActivities(intent(), 0) }.getOrDefault(emptyList())
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    fun play(context: Context, pearl: Pearl, pkg: String): Boolean {
        val i = intent()
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Media.ENTRY_CONTENT_TYPE)
            .putExtra(MediaStore.EXTRA_MEDIA_TITLE, pearl.title)
            .putExtra(MediaStore.EXTRA_MEDIA_ARTIST, pearl.artist)
            .putExtra(SearchManager.QUERY, pearl.query)
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(i) }.isSuccess
    }
}
