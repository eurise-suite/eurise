package eu.eurise.gallery.logic

const val BIG_VIDEO_BYTES = 200L * 1024 * 1024
private const val MONTH_MS = 30L * 24 * 3600 * 1000
private const val BURST_GAP_MS = 2_000L

/** Gli screenshot più vecchi di un mese, che di solito hanno già fatto il loro lavoro. */
fun oldScreenshots(media: List<MediaItem>, now: Long): List<MediaItem> =
    media.filter { !it.cloudOnly && isToTidy(it) && now - it.takenAt > MONTH_MS }

fun bigVideos(media: List<MediaItem>): List<MediaItem> =
    media.filter { !it.cloudOnly && it.isVideo && it.size >= BIG_VIDEO_BYTES }.sortedByDescending { it.size }

/** Già al sicuro sul cloud: si possono togliere dal telefono per fare spazio. */
fun alreadyUploaded(media: List<MediaItem>): List<MediaItem> =
    media.filter { !it.cloudOnly && it.remote != null }.sortedBy { it.takenAt }

/** Possibili doppioni: stesso peso e stessa data, o stesso peso e stesso nome. Il contenuto si confronta dopo. */
fun duplicateCandidates(media: List<MediaItem>): List<List<MediaItem>> =
    media.filter { !it.cloudOnly && it.size > 0 }.groupBy { it.size }.values
        .filter { it.size > 1 }
        .flatMap { same -> same.groupBy { it.takenAt }.values + same.groupBy { it.name }.values }
        .filter { it.size > 1 }
        .distinctBy { group -> group.map { it.id }.sorted() }

/** Raffiche: tre o più foto della stessa cartella a meno di due secondi l'una dall'altra. */
fun bursts(media: List<MediaItem>): List<List<MediaItem>> {
    val photos = media.filter { !it.cloudOnly && !it.isVideo && !isToTidy(it) }.sortedWith(compareBy({ it.bucket }, { it.takenAt }))
    val out = ArrayList<List<MediaItem>>()
    var run = ArrayList<MediaItem>()
    for (item in photos) {
        val last = run.lastOrNull()
        if (last != null && (last.bucket != item.bucket || item.takenAt - last.takenAt > BURST_GAP_MS)) {
            if (run.size >= 3) out += run
            run = ArrayList()
        }
        run += item
    }
    if (run.size >= 3) out += run
    return out
}

/** Da ogni gruppo se ne tiene una, la preferita se c'è: le altre sono la proposta di eliminazione. */
fun extras(groups: List<List<MediaItem>>): List<MediaItem> = groups.flatMap { group ->
    val keep = group.firstOrNull { it.favorite } ?: group.first()
    group.filter { it.id != keep.id }
}
