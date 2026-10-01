package com.bblvn.composebaseproject.features.photopicker

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.core.UiEvent
import com.bblvn.composebaseproject.core.UiText
import com.bblvn.composebaseproject.core.file.FileManager
import com.bblvn.composebaseproject.corefeatures.generation.GenerationDraft
import com.bblvn.composebaseproject.utils.PermissionUtils
import com.bblvn.composebaseproject.utils.PhotoAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One picture in the device gallery. [id] is the MediaStore row id, used as the list key. */
data class GalleryPhoto(
    val id: Long,
    val uri: Uri,
    val bucketId: String,
    val bucketName: String,
)

/** An album (MediaStore bucket) plus the photo used as its cover. */
data class GalleryAlbum(
    val id: String,
    val name: String,
    val count: Int,
    val coverUri: Uri?,
) {
    companion object {
        const val ALL_ID = "__all__"
    }
}

data class PhotoPickerUiState(
    val access: PhotoAccess = PhotoAccess.DENIED,
    val permanentlyDenied: Boolean = false,
    val isLoading: Boolean = false,
    val albums: List<GalleryAlbum> = emptyList(),
    val selectedAlbumId: String = GalleryAlbum.ALL_ID,
    val photos: List<GalleryPhoto> = emptyList(),
    val albumSheetOpen: Boolean = false,
    /** Set while a pick is being copied into app storage, to block a second tap. */
    val isCopying: Boolean = false,
) {
    val selectedAlbum: GalleryAlbum?
        get() = albums.firstOrNull { it.id == selectedAlbumId }

    val visiblePhotos: List<GalleryPhoto>
        get() = if (selectedAlbumId == GalleryAlbum.ALL_ID) {
            photos
        } else {
            photos.filter { it.bucketId == selectedAlbumId }
        }

    val isEmpty: Boolean
        get() = !isLoading && access != PhotoAccess.DENIED && visiblePhotos.isEmpty()
}

/**
 * Backs the single-select photo picker.
 *
 * Single-select is deliberate (spec R5): tapping a photo fills the slot the caller asked for and
 * returns immediately. Two-image flows come back here once per slot, so the order of the two
 * images is decided by the slot the user tapped on the previous screen — never by the order they
 * happen to tap photos here. That ordering is a contract with the generation prompt (Risks #17).
 */
class PhotoPickerViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(PhotoPickerUiState())
    val state: StateFlow<PhotoPickerUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private fun update(block: (PhotoPickerUiState) -> PhotoPickerUiState) {
        _state.value = block(_state.value)
    }

    fun onSlotChanged(slotIndex: Int) {
        GenerationDraft.setActiveSlot(slotIndex)
    }

    /** Called on entry and again after the permission dialog closes. */
    fun refreshAccess() {
        val access = PermissionUtils.photoAccess(getApplication())
        update { it.copy(access = access) }
        if (access != PhotoAccess.DENIED) loadPhotos()
    }

    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        if (granted) {
            update { it.copy(permanentlyDenied = false) }
            refreshAccess()
        } else {
            // Only a denial with no "ask again" left should send the user to system settings —
            // showing that button after a single decline is confusing.
            update { it.copy(permanentlyDenied = !canAskAgain) }
        }
    }

    fun onAlbumSheetToggle(open: Boolean) = update { it.copy(albumSheetOpen = open) }

    fun onAlbumSelected(albumId: String) =
        update { it.copy(selectedAlbumId = albumId, albumSheetOpen = false) }

    /**
     * Copy the picked photo into app storage before returning.
     *
     * The copy is not an optimisation: a `content://` grant can be revoked as soon as this screen
     * goes away, and the generation pipeline may run minutes later, so holding the uri would break
     * the upload with no obvious cause.
     */
    fun onPhotoPicked(slotIndex: Int, photo: GalleryPhoto) {
        if (_state.value.isCopying) return
        update { it.copy(isCopying = true) }
        viewModelScope.launch {
            val result = FileManager.copyUriToSources(photo.uri)
            update { it.copy(isCopying = false) }
            result
                .onSuccess { file ->
                    GenerationDraft.setSlot(slotIndex, file.absolutePath)
                    _events.send(UiEvent.NavigateBack)
                }
                .onFailure {
                    _events.send(
                        UiEvent.ShowToast(UiText.Res(R.string.picker_error_copy_failed))
                    )
                }
        }
    }

    /** The system camera wrote straight into `sources/`, so there is nothing to copy. */
    fun onCameraCaptured(slotIndex: Int, path: String) {
        GenerationDraft.setSlot(slotIndex, path)
        viewModelScope.launch { _events.send(UiEvent.NavigateBack) }
    }

    fun onCameraFailed() {
        viewModelScope.launch {
            _events.send(UiEvent.ShowToast(UiText.Res(R.string.picker_error_camera)))
        }
    }

    private fun loadPhotos() {
        if (_state.value.isLoading) return
        update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val photos = withContext(Dispatchers.IO) { queryPhotos() }
            update { current ->
                val albums = buildAlbums(photos)
                current.copy(
                    isLoading = false,
                    photos = photos,
                    albums = albums,
                    // A previously selected album can vanish under partial access.
                    selectedAlbumId = albums
                        .firstOrNull { it.id == current.selectedAlbumId }
                        ?.id
                        ?: GalleryAlbum.ALL_ID,
                )
            }
        }
    }

    private fun queryPhotos(): List<GalleryPhoto> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val out = ArrayList<GalleryPhoto>()
        runCatching {
            getApplication<Application>().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
                val nameCol =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    out += GalleryPhoto(
                        id = id,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id,
                        ),
                        bucketId = cursor.getString(bucketCol).orEmpty(),
                        bucketName = cursor.getString(nameCol).orEmpty(),
                    )
                }
            }
        }
        return out
    }

    private fun buildAlbums(photos: List<GalleryPhoto>): List<GalleryAlbum> {
        if (photos.isEmpty()) return emptyList()
        val all = GalleryAlbum(
            id = GalleryAlbum.ALL_ID,
            name = getApplication<Application>().getString(R.string.picker_album_all),
            count = photos.size,
            coverUri = photos.first().uri,
        )
        val buckets = photos
            .filter { it.bucketId.isNotBlank() }
            .groupBy { it.bucketId }
            .map { (bucketId, items) ->
                GalleryAlbum(
                    id = bucketId,
                    name = items.first().bucketName,
                    count = items.size,
                    coverUri = items.first().uri,
                )
            }
            .sortedByDescending { it.count }
        return listOf(all) + buckets
    }
}
