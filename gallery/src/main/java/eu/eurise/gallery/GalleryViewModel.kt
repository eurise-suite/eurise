package eu.eurise.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.gallery.data.MediaRepository
import eu.eurise.gallery.logic.MediaItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** [media] è nullo finché non c'è il permesso o il telefono non ha risposto la prima volta. */
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MediaRepository(app)
    private val granted = MutableStateFlow(false)

    val media: StateFlow<List<MediaItem>?> =
        granted.flatMapLatest { if (it) repository.observe() else flowOf(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onPermission(allowed: Boolean) {
        granted.value = allowed
    }
}
