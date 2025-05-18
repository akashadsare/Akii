package com.videoplayer.akii

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FolderVideosActivity : AppCompatActivity() {

    private lateinit var folderVideosRecyclerView: RecyclerView
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var searchEditText: EditText
    private lateinit var backFromSearch: ImageButton
    private lateinit var clearSearch: ImageButton
    private lateinit var emptyView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var fabSort: FloatingActionButton
    
    private var folderPath: String? = null
    private var folderName: String? = null
    private lateinit var viewModel: MainViewModel
    private var currentVideos: List<VideoItem> = emptyList()

    private enum class SortOrder {
        NAME_ASC, NAME_DESC, DATE_NEWEST, DATE_OLDEST
    }

    private var currentSortOrder = SortOrder.NAME_ASC

    companion object {
        const val EXTRA_FOLDER_PATH = "extra_folder_path"
        const val EXTRA_FOLDER_NAME = "extra_folder_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_videos2)

        initializeViews()
        setupToolbar()
        
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(applicationContext as ViewModelStoreOwner, factory)[MainViewModel::class.java]

        folderPath = intent.getStringExtra(EXTRA_FOLDER_PATH)
        folderName = intent.getStringExtra(EXTRA_FOLDER_NAME)

        if (folderPath == null) {
            Toast.makeText(this, "Error: Folder path not received.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupRecyclerView()
        setupSwipeRefresh()
        setupSearch()
        setupFab()
        
        loadVideosForFolder()
    }

    private fun initializeViews() {
        try {
            folderVideosRecyclerView = findViewById(R.id.folderVideosRecyclerView)
            swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
            searchEditText = findViewById(R.id.searchEditText)
            backFromSearch = findViewById(R.id.backFromSearch)
            clearSearch = findViewById(R.id.clearSearch)
            emptyView = findViewById(R.id.emptyView)
            progressBar = findViewById(R.id.progressBar)
            fabSort = findViewById(R.id.fabSort)
        } catch (e: Exception) {
            Log.e("FolderVideosActivity", "Error initializing views: ${e.message}", e)
            Toast.makeText(this, "Error initializing views", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupToolbar() {
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = folderName ?: "Videos"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    @OptIn(UnstableApi::class)
    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this)
        videoAdapter = VideoAdapter(emptyList()) { videoItem ->
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_VIDEO_URI, videoItem.uri.toString())
                putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, videoItem.title)
                
                // Create and pass the video list
                val videoList = currentVideos.map { video ->
                    Pair(video.uri.toString(), video.title)
                }
                putExtra(PlayerActivity.EXTRA_VIDEO_LIST, ArrayList(videoList))
                
                // Pass the current video index
                val currentIndex = currentVideos.indexOfFirst { it.uri == videoItem.uri }
                putExtra(PlayerActivity.EXTRA_VIDEO_INDEX, currentIndex)
            }
            startActivity(intent)
        }
        
        folderVideosRecyclerView.apply {
            this.layoutManager = layoutManager
            adapter = videoAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(20)
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadVideosForFolder()
        }
    }

    private fun loadVideosForFolder() {
        progressBar.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        folderVideosRecyclerView.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                folderPath?.let { path ->
                    val videos = viewModel.getVideosForFolderPath(path)
                    
                    withContext(Dispatchers.Main) {
                        currentVideos = videos
                        videoAdapter.updateVideos(videos)
                        updateEmptyState(videos.isEmpty())
                        progressBar.visibility = View.GONE
                        swipeRefreshLayout.isRefreshing = false
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FolderVideosActivity, "Error loading videos: ${e.message}", Toast.LENGTH_LONG).show()
                    updateEmptyState(true)
                    progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            emptyView.visibility = View.VISIBLE
            folderVideosRecyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            folderVideosRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                lifecycleScope.launch(Dispatchers.Default) {
                    val filteredVideos = if (query.isBlank()) {
                        currentVideos
                    } else {
                        currentVideos.filter { 
                            it.title.contains(query, ignoreCase = true) 
                        }
                    }
                    withContext(Dispatchers.Main) {
                        videoAdapter.updateVideos(filteredVideos)
                        updateEmptyState(filteredVideos.isEmpty())
                    }
                }
            }
        })
    }

    private fun setupFab() {
        fabSort.setOnClickListener { view ->
            showSortMenu(view)
        }
    }

    private fun showSortMenu(view: View) {
        PopupMenu(this, view).apply {
            menuInflater.inflate(R.menu.sort_menu, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.sort_name_asc -> {
                        currentSortOrder = SortOrder.NAME_ASC
                        sortVideos()
                        true
                    }
                    R.id.sort_name_desc -> {
                        currentSortOrder = SortOrder.NAME_DESC
                        sortVideos()
                        true
                    }
                    R.id.sort_date_newest -> {
                        currentSortOrder = SortOrder.DATE_NEWEST
                        sortVideos()
                        true
                    }
                    R.id.sort_date_oldest -> {
                        currentSortOrder = SortOrder.DATE_OLDEST
                        sortVideos()
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun sortVideos() {
        lifecycleScope.launch(Dispatchers.Default) {
            val sortedVideos = when (currentSortOrder) {
                SortOrder.NAME_ASC -> currentVideos.sortedBy { it.title.lowercase() }
                SortOrder.NAME_DESC -> currentVideos.sortedByDescending { it.title.lowercase() }
                SortOrder.DATE_NEWEST -> currentVideos.sortedByDescending { File(it.path).lastModified() }
                SortOrder.DATE_OLDEST -> currentVideos.sortedBy { File(it.path).lastModified() }
            }
            withContext(Dispatchers.Main) {
                videoAdapter.updateVideos(sortedVideos)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}