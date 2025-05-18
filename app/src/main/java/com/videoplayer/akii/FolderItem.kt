package com.videoplayer.akii

import java.io.File // For File operations to get folder name

data class FolderItem(
    val id: String, // Using folder path as a unique ID
    val name: String,
    val path: String,
    val videoCount: Int
    // val firstVideoPath: String? // Optional: for a thumbnail if you want later
) {
    constructor(folderPath: String, videosInFolder: List<VideoItem>) : this(
        id = folderPath,
        name = File(folderPath).name, // Extracts the last part of the path as folder name
        path = folderPath,
        videoCount = videosInFolder.size
        // firstVideoPath = videosInFolder.firstOrNull()?.path
    )
}