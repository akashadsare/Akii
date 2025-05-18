package com.videoplayer.akii

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlaylistAdapter(
    private val videos: List<Pair<Uri, String>>,
    private val currentIndex: Int,
    private val onVideoSelected: (Int) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.videoTitleTextView)
        val indexText: TextView = view.findViewById(R.id.videoIndexTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (_, title) = videos[position]
        holder.titleText.text = title
        holder.indexText.text = "${position + 1}"
        
        // Highlight current video
        holder.itemView.isSelected = position == currentIndex
        
        holder.itemView.setOnClickListener {
            onVideoSelected(position)
        }
    }

    override fun getItemCount() = videos.size
} 