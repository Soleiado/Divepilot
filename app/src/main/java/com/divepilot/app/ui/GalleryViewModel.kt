package com.divepilot.app.ui

import android.app.Application
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaItem(
    val uri: Uri,
    val name: String,
    val dateMillis: Long,
    val isVideo: Boolean,
)

/** Liste les photos/vidéos récentes de MediaStore (galerie simple). */
class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val _media = MutableStateFlow<List<MediaItem>>(emptyList())
    val media: StateFlow<List<MediaItem>> = _media.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _media.value = queryMedia(application)
        }
    }

    private fun queryMedia(context: Context): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sort,
        )?.use { c ->
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0),
                )
                items.add(MediaItem(uri, c.getString(1), c.getLong(2) * 1000L, isVideo = false))
            }
        }

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, sort,
        )?.use { c ->
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0),
                )
                items.add(MediaItem(uri, c.getString(1), c.getLong(2) * 1000L, isVideo = true))
            }
        }

        return items.sortedByDescending { it.dateMillis }.take(200)
    }
}
