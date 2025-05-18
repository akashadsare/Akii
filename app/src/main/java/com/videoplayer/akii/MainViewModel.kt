package com.videoplayer.akii

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _folders = MutableLiveData<List<FolderItem>>()
    val folders: LiveData<List<FolderItem>> get() = _folders

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _permissionGranted = MutableLiveData(false)
    val permissionGranted: LiveData<Boolean> get() = _permissionGranted

    // All videos list
    private val _allVideosList = MutableLiveData<List<VideoItem>>(emptyList())
    val allVideosList: LiveData<List<VideoItem>> get() = _allVideosList

    private val _searchedVideos = MutableLiveData<List<VideoItem>>()
    val searchedVideos: LiveData<List<VideoItem>> get() = _searchedVideos

    private val _isSearchActive = MutableLiveData(false)
    val isSearchActive: LiveData<Boolean> get() = _isSearchActive

    // Cache for folder contents
    private val folderCache = ConcurrentHashMap<String, List<VideoItem>>()

    // Set permission and load folders if granted
    fun setPermissionGranted(isGranted: Boolean) {
        _permissionGranted.value = isGranted
        if (isGranted) {
            loadFolders()
        } else {
            _folders.value = emptyList()
        }
    }

    fun loadFolders() {
        if (_permissionGranted.value != true) {
            _folders.value = emptyList()
            _isLoading.value = false
            Log.e("MainViewModel", "Permission not granted, cannot load folders")
            return
        }

        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val videoListTemp = mutableListOf<VideoItem>()
            val contentResolver = getApplication<Application>().contentResolver
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DATA  // Add DATA column to get the file path
            )
            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            try {
                Log.d("MainViewModel", "Starting video scan with URI: $collectionUri")
                contentResolver.query(collectionUri, projection, null, null, sortOrder)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

                    Log.d("MainViewModel", "Found ${cursor.count} potential videos")

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val title = cursor.getString(titleColumn)
                        val duration = cursor.getLong(durationColumn)
                        val size = cursor.getLong(sizeColumn)
                        val path = cursor.getString(dataColumn)

                        if (size > 0 && path != null) {
                            val contentUri = ContentUris.withAppendedId(
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            videoListTemp.add(VideoItem(id, title, duration, path, contentUri))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error scanning videos: ${e.message}", e)
            }

            withContext(Dispatchers.Main) {
                _allVideosList.value = videoListTemp
                val groupedFolders = groupVideosIntoFolders(videoListTemp)
                _folders.value = groupedFolders
                _isLoading.value = false
            }
        }
    }

    private fun groupVideosIntoFolders(videos: List<VideoItem>): List<FolderItem> {
        return videos
            .groupBy { videoItem ->
                try {
                    val file = File(videoItem.path)
                    file.parentFile?.absolutePath ?: ""
                } catch (e: IOException) {
                    Log.e("MainViewModel", "Error getting parent path for video: ${videoItem.path}", e)
                    ""
                }
            }
            .filterKeys { it.isNotEmpty() }
            .map { (folderPath, videosInFolder) ->
                try {
                    val folder = File(folderPath)
                    folderCache[folderPath] = videosInFolder
                    FolderItem(
                        id = folderPath,
                        name = folder.name,
                        path = folderPath,
                        videoCount = videosInFolder.size
                    )
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error creating FolderItem for path: $folderPath", e)
                    null
                }
            }
            .filterNotNull()
            .sortedBy { it.name.lowercase() }
    }

    fun getVideosForFolderPath(folderPath: String): List<VideoItem> {
        folderCache[folderPath]?.let { cachedVideos ->
            Log.d("MainViewModel", "Retrieved ${cachedVideos.size} videos from cache for folder: $folderPath")
            return cachedVideos
        }

        return allVideosList.value.orEmpty().filter { videoItem ->
            try {
                val videoFile = File(videoItem.path)
                val normalizedParentPath = videoFile.parentFile?.canonicalPath
                normalizedParentPath == File(folderPath).canonicalPath
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error comparing paths for video ${videoItem.title}: ${e.message}")
                false
            }
        }.also { videos ->
            folderCache[folderPath] = videos
        }
    }

    fun setSearchActive(isActive: Boolean) {
        _isSearchActive.value = isActive
        if (!isActive) {
            _searchedVideos.value = emptyList()
        }
    }

    fun searchVideos(query: String) {
        if (query.isBlank()) {
            _searchedVideos.value = emptyList()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            delay(300) // Optional debounce
            val results = allVideosList.value?.filter {
                it.title.contains(query, ignoreCase = true)
            }?.sortedBy { it.title.lowercase() }.orEmpty()

            withContext(Dispatchers.Main) {
                _searchedVideos.value = results
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        folderCache.clear()
    }
}