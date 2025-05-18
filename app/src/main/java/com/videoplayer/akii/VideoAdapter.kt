package com.videoplayer.akii

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import java.util.concurrent.TimeUnit

class VideoAdapter(
    private var videos: List<VideoItem>,
    private val onItemClicked: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val requestOptions = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .centerCrop()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.bind(video)
    }

    override fun getItemCount(): Int = videos.size

    fun updateVideos(newVideos: List<VideoItem>) {
        val diffCallback = VideoDiffCallback(videos, newVideos)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        videos = newVideos
        diffResult.dispatchUpdatesTo(this)
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailView: ImageView = itemView.findViewById(R.id.videoThumbnailImageView)
        private val titleView: TextView = itemView.findViewById(R.id.videoTitleTextView)
        private val durationView: TextView = itemView.findViewById(R.id.videoDurationTextView)

        fun bind(video: VideoItem) {
            titleView.text = video.title
            durationView.text = formatDuration(video.duration)

            // Load thumbnail using Glide
            Glide.with(itemView.context)
                .load(video.uri)
                .apply(requestOptions)
                .thumbnail(0.1f)
                .into(thumbnailView)

            itemView.setOnClickListener { onItemClicked(video) }
        }

        private fun formatDuration(durationMs: Long): String {
            val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

            return when {
                hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
                else -> String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    private class VideoDiffCallback(
        private val oldList: List<VideoItem>,
        private val newList: List<VideoItem>
    ) : DiffUtil.Callback() {
        
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            return oldItem == newItem
        }
    }
}