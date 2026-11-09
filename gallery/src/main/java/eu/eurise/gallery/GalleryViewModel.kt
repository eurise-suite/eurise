package eu.eurise.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.cloud.CloudAccount
import eu.eurise.core.cloud.CloudAccounts
import eu.eurise.core.cloud.CloudKind
import eu.eurise.core.cloud.NextcloudLogin
import eu.eurise.gallery.data.Diary
import eu.eurise.gallery.data.GallerySettings
import eu.eurise.gallery.data.GalleryStore
import eu.eurise.gallery.data.MediaRepository
import eu.eurise.gallery.data.PlaceIndexer
import eu.eurise.gallery.data.SecureFolder
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.UploadRecord
import eu.eurise.gallery.logic.mergeCloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Le posizioni lette: la casella di ogni foto e il nome delle caselle. */
data class PlaceData(val cells: Map<String, String> = emptyMap(), val names: Map<String, String> = emptyMap())

/** [media] è nullo finché non c'è il permesso o il telefono non ha risposto la prima volta. */
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repository = MediaRepository(app)
    private val granted = MutableStateFlow(false)
    val store = GalleryStore.get(app)
    val secure = SecureFolder.get(app)
    val accounts: StateFlow<List<CloudAccount>> = CloudAccounts.get(app).accounts
    val settings: StateFlow<GallerySettings> = store.settings

    private val phone = granted.flatMapLatest { if (it) repository.observe() else flowOf(null) }

    val media: StateFlow<List<MediaItem>?> =
        combine(phone, store.version) { list, _ -> list?.let { mergeCloud(it, store.records()) } }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val places: StateFlow<PlaceData> = store.version.map {
        PlaceData(store.places().mapNotNull { (uri, cell) -> cell?.let { uri to it } }.toMap(), store.placeNames())
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaceData())

    val diaries: StateFlow<List<Diary>> = store.version.map { store.diaries() }
        .flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Quanto i registri dicono caricato, compreso ciò che è stato escluso: serve a calcolare la coda. */
    val knownUploads: StateFlow<Set<String>> = store.version.map { store.records().map { it.uri }.toSet() }
        .flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _remaining = MutableStateFlow(0)
    val remaining: StateFlow<Int> = _remaining
    private var indexing: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) { File(app.cacheDir, "secure-play").deleteRecursively() }
    }

    fun onPermission(allowed: Boolean) {
        granted.value = allowed
        val s = settings.value
        if (allowed && s.uploadOn) eu.eurise.gallery.data.UploadWorker.runNow(app, s)
    }

    fun updateCloud(change: (GallerySettings) -> GallerySettings) {
        store.update(change)
        eu.eurise.gallery.data.UploadWorker.schedule(app, settings.value)
    }

    fun setPlaces(on: Boolean) {
        store.update { it.copy(placesOn = on, namesOn = on && it.namesOn) }
        if (!on) {
            indexing?.cancel()
            viewModelScope.launch(Dispatchers.IO) { store.clearPlaces() }
        }
    }

    fun setNames(on: Boolean) {
        store.update { it.copy(namesOn = on) }
        if (!on) viewModelScope.launch(Dispatchers.IO) { store.clearPlaceNames() }
    }

    /** Legge le posizioni mancanti, solo mentre l'app è aperta. */
    fun indexPlaces(list: List<MediaItem>) {
        if (!settings.value.placesOn || indexing?.isActive == true) return
        indexing = viewModelScope.launch(Dispatchers.IO) {
            val indexer = PlaceIndexer(app, store)
            indexer.index(list) { _remaining.value = it }
            if (settings.value.namesOn) indexer.fetchNames(store.places().values.filterNotNull().toSet())
        }
    }

    /** Toglie dal cloud al prossimo passaggio, e subito dalla vista. */
    fun removeFromCloud(items: List<MediaItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            store.setState(items.map { it.uri }, UploadRecord.DELETE_REMOTE)
            eu.eurise.gallery.data.UploadWorker.runNow(app, settings.value)
        }
    }

    /** Accesso dal browser: [open] apre la pagina; true se l'account è stato collegato. */
    suspend fun connect(kind: CloudKind, server: String, open: (String) -> Unit): Boolean = runCatching {
        val login = NextcloudLogin()
        val start = login.start(server)
        withContext(Dispatchers.Main) { open(start.loginUrl) }
        val result = login.await(start) ?: return false
        val id = CloudAccounts.get(app).add(kind, result.loginName, result.loginName, CloudAccounts.nextcloudDav(result.server, result.davUser), result.appPassword)
        store.update { it.copy(accountId = id) }
        true
    }.getOrDefault(false)

    fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }
}
