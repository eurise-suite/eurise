package eu.eurise.feature.calendar.trips

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.calendar.DeviceCalendar
import eu.eurise.core.data.calendar.EventDraft
import eu.eurise.core.data.modes.Modes
import eu.eurise.core.data.trips.Leg
import eu.eurise.core.data.trips.PdfText
import eu.eurise.core.data.trips.Tickets
import eu.eurise.core.suite.ClockContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Da dove arriva il biglietto: un PDF (file o condiviso) oppure il testo di un codice a barre. */
sealed interface TripSource {
    data class Pdf(val uri: Uri, val name: String) : TripSource
    data class Barcode(val raw: String) : TripSource
}

data class TripState(
    val loading: Boolean = true,
    val title: String = "",
    val booking: String = "",
    val legs: List<Leg> = emptyList(),
    val calendars: List<DeviceCalendar> = emptyList(),
    val calendarId: Long? = null,
    val hasPdf: Boolean = false,
    // Che cosa preparare; ognuna si può togliere.
    val toCalendar: Boolean = true,
    val alarm: Boolean = true,
    val alarmTime: LocalTime? = null,
    val clockZone: Boolean = true,
    val folder: Boolean = true,
    val travelMode: Boolean = true,
    val done: Boolean = false,
    val failed: Boolean = false,
) {
    val first: Leg? get() = legs.firstOrNull()
    val destinationZone: String? get() = legs.firstOrNull()?.arriveZone
}

/**
 * Prepara un viaggio da un biglietto, tutto sul telefono: legge il PDF (testo e codici a barre) o la carta d'imbarco,
 * propone tratti modificabili e, alla conferma, crea ciò che la persona ha lasciato acceso.
 */
class TripViewModel(private val app: Context, private val source: TripSource) : ViewModel() {
    private val data = EuriseData.create(app)
    private val _state = MutableStateFlow(TripState())
    val state: StateFlow<TripState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    when (source) {
                        is TripSource.Barcode -> Tickets.read(listOf(source.raw), "", LocalDate.now())
                        is TripSource.Pdf -> {
                            val bytes = app.contentResolver.openInputStream(source.uri)?.use { it.readBytes() } ?: ByteArray(0)
                            Tickets.read(barcodes(bytes), PdfText.extract(bytes), LocalDate.now())
                        }
                    }
                }.getOrNull()
            }
            val calendars = data.calendar.observeCalendars().first().filter { it.writable }
            val legs = read?.legs.orEmpty().ifEmpty { listOf(Leg(Leg.Kind.Other, "", "", "", null, null)) }
            _state.value = TripState(
                loading = false,
                title = titleFor(legs),
                booking = read?.booking.orEmpty(),
                legs = legs,
                calendars = calendars,
                calendarId = calendars.firstOrNull()?.id,
                hasPdf = source is TripSource.Pdf,
                failed = read == null || read.legs.isEmpty(),
            ).withAlarm()
        }
    }

    private fun titleFor(legs: List<Leg>): String {
        val to = legs.firstOrNull()?.to?.substringBefore(" (")?.takeIf { it.isNotBlank() } ?: return ""
        return "Viaggio a $to"
    }

    /** La sveglia proposta: prima della partenza (2 ore e mezza per i voli, un'ora per i treni), ai 5 minuti. */
    private fun TripState.withAlarm(): TripState {
        val depart = first?.let { localDepart(it) } ?: return copy(alarmTime = null)
        val lead = if (first?.kind == Leg.Kind.Flight) 150L else 60L
        val t = depart.minusMinutes(lead)
        return copy(alarmTime = LocalTime.of(t.hour, t.minute / 5 * 5))
    }

    /** L'orario del biglietto è quello del luogo: sul telefono diventa quello del fuso attuale. */
    private fun localDepart(leg: Leg): LocalDateTime? = leg.depart?.let { convert(it, leg.departZone) }
    private fun localArrive(leg: Leg): LocalDateTime? = leg.arrive?.let { convert(it, leg.arriveZone) }

    private fun convert(at: LocalDateTime, zone: String?): LocalDateTime {
        val from = zone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: return at
        return at.atZone(from).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setCalendar(id: Long) = _state.update { it.copy(calendarId = id) }
    fun setLeg(index: Int, leg: Leg) = _state.update { s -> s.copy(legs = s.legs.mapIndexed { i, l -> if (i == index) leg else l }).withAlarm() }
    fun addLeg() = _state.update { s ->
        // Il ritorno proposto: al contrario del primo tratto.
        val f = s.first
        s.copy(legs = s.legs + Leg(f?.kind ?: Leg.Kind.Other, "", f?.to.orEmpty(), f?.from.orEmpty(), null, null, departZone = f?.arriveZone, arriveZone = f?.departZone))
    }
    fun removeLeg(index: Int) = _state.update { s -> s.copy(legs = s.legs.filterIndexed { i, _ -> i != index }).withAlarm() }
    fun toggle(which: String) = _state.update {
        when (which) {
            "calendar" -> it.copy(toCalendar = !it.toCalendar)
            "alarm" -> it.copy(alarm = !it.alarm)
            "zone" -> it.copy(clockZone = !it.clockZone)
            "folder" -> it.copy(folder = !it.folder)
            "mode" -> it.copy(travelMode = !it.travelMode)
            else -> it
        }
    }
    fun setAlarmTime(t: LocalTime) = _state.update { it.copy(alarmTime = t) }

    fun create() {
        val s = _state.value
        viewModelScope.launch {
            val legs = s.legs.filter { it.depart != null }
            if (s.toCalendar) s.calendarId?.let { cal ->
                legs.forEach { leg ->
                    val start = localDepart(leg)!!
                    val end = localArrive(leg)?.takeIf { it.isAfter(start) } ?: start.plusHours(if (leg.kind == Leg.Kind.Flight) 2 else 1)
                    val timed = leg.depart!!.toLocalTime() != LocalTime.MIDNIGHT
                    val title = listOf(if (leg.kind == Leg.Kind.Train) "🚆" else "✈", leg.number, "${leg.from} → ${leg.to}".takeIf { leg.from.isNotBlank() })
                        .filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
                    val notes = listOfNotNull(
                        s.booking.takeIf { it.isNotBlank() }?.let { "Prenotazione $it" },
                        leg.seat.takeIf { it.isNotBlank() }?.let { "Posto $it" },
                        s.title.takeIf { it.isNotBlank() },
                    ).joinToString("\n")
                    data.calendar.save(
                        if (timed) EventDraft(null, cal, title, leg.from, notes, false, start, end)
                        else EventDraft(null, cal, title, leg.from, notes, true, start.toLocalDate().atStartOfDay(), start.toLocalDate().plusDays(1).atStartOfDay()),
                    )
                }
            }
            val first = legs.firstOrNull()
            val departAt = first?.let { localDepart(it) }
            ClockContract.trip(
                app,
                date = if (s.alarm && s.alarmTime != null && departAt != null) {
                    // Una sveglia dopo mezzanotte per un volo del mattino resta nello stesso giorno; se la sveglia viene
                    // dopo l'orario della partenza, è del giorno prima.
                    if (s.alarmTime.isAfter(departAt.toLocalTime())) departAt.toLocalDate().minusDays(1) else departAt.toLocalDate()
                } else null,
                time = if (s.alarm) s.alarmTime else null,
                label = s.title.ifBlank { first?.number.orEmpty() },
                zone = if (s.clockZone) s.destinationZone else null,
            )
            if (s.folder && source is TripSource.Pdf) withContext(Dispatchers.IO) { runCatching { saveToFolder(source, s.title) } }
            if (s.travelMode && departAt != null) {
                val last = legs.last()
                val end = (localArrive(last) ?: localDepart(last)!!.plusHours(3)).let {
                    // Solo andata: la modalità resta fino a fine giornata dell'arrivo.
                    if (legs.size == 1) it.toLocalDate().plusDays(1).atStartOfDay() else it
                }
                Modes.addTrip(app, departAt.minusHours(3), end)
            }
            _state.update { it.copy(done = true) }
        }
    }

    /** Copia il biglietto in Documenti › Viaggi › <nome del viaggio>, dove File lo trova. */
    private fun saveToFolder(pdf: TripSource.Pdf, title: String) {
        val folder = "Viaggi/" + title.ifBlank { "Viaggio" }.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim()
        val bytes = app.contentResolver.openInputStream(pdf.uri)?.use { it.readBytes() } ?: return
        val shown = if (pdf.uri.scheme == "content") runCatching {
            app.contentResolver.query(pdf.uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() else null
        val name = (shown ?: pdf.name).ifBlank { "biglietto.pdf" }.let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/" + folder)
            }
            val uri = app.contentResolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return
            app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), folder).apply { mkdirs() }
            File(dir, name).writeBytes(bytes)
        }
    }

    /** I codici a barre delle prime pagine (carte d'imbarco: PDF417 o Aztec), letti sul telefono. */
    private fun barcodes(bytes: ByteArray): List<String> {
        val file = File(app.cacheDir, "trip-ticket.pdf").apply { writeBytes(bytes) }
        val out = mutableListOf<String>()
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    for (i in 0 until minOf(renderer.pageCount, 3)) {
                        renderer.openPage(i).use { page ->
                            val scale = 2.5f
                            val bmp = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            decode(bmp)?.let { out += it }
                            bmp.recycle()
                        }
                    }
                }
            }
        }
        file.delete()
        return out
    }

    private fun decode(bmp: Bitmap): String? {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val source = com.google.zxing.RGBLuminanceSource(bmp.width, bmp.height, pixels)
        val reader = com.google.zxing.MultiFormatReader().apply {
            setHints(
                mapOf(
                    com.google.zxing.DecodeHintType.TRY_HARDER to true,
                    com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(
                        com.google.zxing.BarcodeFormat.PDF_417, com.google.zxing.BarcodeFormat.AZTEC, com.google.zxing.BarcodeFormat.QR_CODE,
                    ),
                ),
            )
        }
        return runCatching { reader.decodeWithState(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))).text }.getOrNull()
    }
}
