package com.videoplayer.akii

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FolderAdapter(
    private var folders: List<FolderItem>,
    private val onItemClicked: (FolderItem) -> Unit
) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folders[position]
        holder.bind(folder)
    }

    override fun getItemCount(): Int = folders.size

    fun updateFolders(newFolders: List<FolderItem>) {
        folders = newFolders
        notifyDataSetChanged() // Consider DiffUtil for better performance
    }

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.folderNameTextView)
        private val countTextView: TextView = itemView.findViewById(R.id.videoCountTextView)
        // private val iconImageView: ImageView = itemView.findViewById(R.id.folderIconImageView) // If you need to change icon

        fun bind(folder: FolderItem) {
            nameTextView.text = folder.name
            countTextView.text = "${folder.videoCount} video(s)"
            // Optionally load a thumbnail into iconImageView if you store it
            itemView.setOnClickListener {
                onItemClicked(folder)
            }
        }
    }
}