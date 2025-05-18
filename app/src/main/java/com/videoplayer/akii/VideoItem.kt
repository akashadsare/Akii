package com.videoplayer.akii // <<<< Make sure this matches YOUR package name

import android.net.Uri

data class VideoItem(
    val id: Long,
    val title: String,
    val duration: Long, // in milliseconds
    val path: String,   // Path is still here, but contentUri is preferred
    val uri: Uri        // Content URI
)