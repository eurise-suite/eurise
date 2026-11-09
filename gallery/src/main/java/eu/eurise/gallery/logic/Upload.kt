package eu.eurise.gallery.logic

import java.time.Instant
import java.time.ZoneId

/** Una foto nota al cloud. */
data class UploadRecord(
    val id: Long,
    val uri: String,
    val remote: String,
    val takenAt: Long,
    val name: String,
    val size: Long,
    val isVideo: Boolean,
    val durationMs: Long,
    val bucket: String,
    val state: Int,
) {
    companion object {
        const val UPLOADED = 0

        /** Tolta dal cloud: resta sul telefono e non torna su. */
        const val EXCLUDED = 2

        /** Da togliere dal cloud al prossimo passaggio. */
        const val DELETE_REMOTE = 3
    }
}

/** Che cosa caricare: le cartelle scelte, dalla data scelta in poi. */
data class UploadRule(val buckets: Set<String>, val fromMillis: Long)

/** Le foto del telefono ancora da caricare, dalla più recente. [known] sono gli indirizzi già registrati. */
fun uploadQueue(media: List<MediaItem>, rule: UploadRule, known: Set<String>): List<MediaItem> =
    media.filter { !it.cloudOnly && it.bucket in rule.buckets && it.takenAt >= rule.fromMillis && it.uri !in known }
        .sortedByDescending { it.takenAt }

/** Eurise/Foto/AAAA/MM/nome: il nome si tiene, così la cartella resta leggibile anche senza l'app. */
fun remoteFolder(takenAt: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val date = Instant.ofEpochMilli(takenAt).atZone(zone).toLocalDate()
    return "Eurise/Foto/%04d/%02d".format(date.year, date.monthValue)
}

/** Nome alternativo quando sul cloud c'è già un file diverso con lo stesso nome. */
fun uniqueName(name: String, id: Long): String {
    val dot = name.lastIndexOf('.')
    return if (dot <= 0) "$name-$id" else "${name.substring(0, dot)}-$id${name.substring(dot)}"
}

/** Unisce telefono e cloud: le foto caricate sanno dove stanno, quelle sparite dal telefono restano come "solo cloud". */
fun mergeCloud(phone: List<MediaItem>, records: List<UploadRecord>): List<MediaItem> {
    val byUri = records.associateBy { it.uri }
    val onPhone = HashSet<String>(phone.size * 2)
    val merged = phone.map { item ->
        onPhone += item.uri
        val record = byUri[item.uri]
        if (record != null && record.state == UploadRecord.UPLOADED) item.copy(remote = record.remote) else item
    }
    val cloud = records.filter { it.state == UploadRecord.UPLOADED && it.uri !in onPhone }.map {
        MediaItem(-it.id, it.uri, it.isVideo, it.takenAt, it.durationMs, it.bucket, false, it.name, it.size, it.remote, true)
    }
    return merged + cloud
}

/** Da dove togliere le foto scelte. */
enum class DeleteChoice { PhoneOnly, CloudOnly, Both }

/** Che cosa fare davvero: [phone] va nel cestino del telefono, [cloud] va tolto dal cloud. */
data class DeletePlan(val phone: List<MediaItem>, val cloud: List<MediaItem>)

fun deletePlan(items: List<MediaItem>, choice: DeleteChoice): DeletePlan = DeletePlan(
    phone = if (choice == DeleteChoice.CloudOnly) emptyList() else items.filter { !it.cloudOnly },
    cloud = if (choice == DeleteChoice.PhoneOnly) emptyList() else items.filter { it.remote != null },
)
