package com.videoplayer.akii

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.SearchView
import android.view.MenuItem
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.videoplayer.akii.FolderAdapter
import com.videoplayer.akii.VideoAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var foldersRecyclerView: RecyclerView
    private lateinit var searchedVideosRecyclerView: RecyclerView // New RecyclerView for searched videos
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyTextView: TextView
    private lateinit var folderAdapter: FolderAdapter
    private lateinit var videoAdapter: VideoAdapter // Adapter for searched videos

    private lateinit var viewModel: MainViewModel
    private var searchView: SearchView? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                viewModel.setPermissionGranted(true)
            } else {
                viewModel.setPermissionGranted(false)
                Toast.makeText(this, "Storage permission denied. Cannot load videos.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(applicationContext as ViewModelStoreOwner, factory).get(MainViewModel::class.java)
        Log.d("MainActivity", "Using ViewModel instance: $viewModel")

        foldersRecyclerView = findViewById(R.id.foldersRecyclerView)
        searchedVideosRecyclerView = findViewById(R.id.searchedVideosRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyTextView = findViewById(R.id.emptyTextView)

        updateEmptyTextViewVisibility()
        setupRecyclerViews()
        observeViewModel()
        checkAndRequestPermissions()
    }

    @OptIn(UnstableApi::class)
    private fun setupRecyclerViews() {
        // Folder Adapter
        folderAdapter = FolderAdapter(emptyList()) { folderItem ->
            Log.d("MainActivity", "Clicked folder: ${folderItem.name}, Path: ${folderItem.path}")
            val intent = Intent(this, FolderVideosActivity::class.java).apply {
                putExtra(FolderVideosActivity.EXTRA_FOLDER_PATH, folderItem.path)
                putExtra(FolderVideosActivity.EXTRA_FOLDER_NAME, folderItem.name)
            }
            startActivity(intent)
        }
        foldersRecyclerView.layoutManager = LinearLayoutManager(this)
        foldersRecyclerView.adapter = folderAdapter

        // Searched Videos Adapter
        videoAdapter = VideoAdapter(emptyList()) { videoItem ->
            val intent = Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_VIDEO_URI, videoItem.uri.toString())
                putExtra(PlayerActivity.EXTRA_VIDEO_TITLE, videoItem.title)
            }
            startActivity(intent)
        }
        searchedVideosRecyclerView.layoutManager = LinearLayoutManager(this)
        searchedVideosRecyclerView.adapter = videoAdapter
    }

    private fun observeViewModel() {
        viewModel.folders.observe(this) { folders ->
            if (viewModel.isSearchActive.value == false) {
                folderAdapter.updateFolders(folders)
            }
            updateEmptyTextViewVisibility()
        }

        viewModel.searchedVideos.observe(this) { videos ->
            if (viewModel.isSearchActive.value == true) {
                videoAdapter.updateVideos(videos)
                if (videos.isEmpty()) {
                    emptyTextView.text = "No videos found matching your search."
                    emptyTextView.visibility = View.VISIBLE
                    searchedVideosRecyclerView.visibility = View.GONE
                } else {
                    emptyTextView.visibility = View.GONE
                    searchedVideosRecyclerView.visibility = View.VISIBLE
                }
            }
        }

        viewModel.isSearchActive.observe(this) { isActive ->
            if (isActive) {
                foldersRecyclerView.visibility = View.GONE
                searchedVideosRecyclerView.visibility = View.VISIBLE
                emptyTextView.visibility = View.GONE
            } else {
                foldersRecyclerView.visibility = View.VISIBLE
                searchedVideosRecyclerView.visibility = View.GONE
                updateEmptyTextViewVisibility()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            updateEmptyTextViewVisibility()
        }

        viewModel.permissionGranted.observe(this) { isGranted ->
            updateEmptyTextViewVisibility()
            if (isGranted) {
                if (viewModel.folders.value.isNullOrEmpty() && !viewModel.isLoading.value!!) {
                    viewModel.loadFolders()
                }
            } else {
                folderAdapter.updateFolders(emptyList())
                videoAdapter.updateVideos(emptyList())
            }
        }
    }

    private fun updateEmptyTextViewVisibility() {
        val isLoading = viewModel.isLoading.value == true
        val isPermissionGranted = viewModel.permissionGranted.value == true
        val isSearching = viewModel.isSearchActive.value == true

        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE

        if (isLoading) {
            emptyTextView.visibility = View.GONE
            return
        }

        if (!isPermissionGranted) {
            emptyTextView.text = "Storage permission is required."
            emptyTextView.visibility = View.VISIBLE
            foldersRecyclerView.visibility = View.GONE
            searchedVideosRecyclerView.visibility = View.GONE
            return
        }

        if (isSearching) {
            val searchedVideosEmpty = viewModel.searchedVideos.value.isNullOrEmpty()
            if (searchView?.query?.isNotEmpty() == true && searchedVideosEmpty) {
                emptyTextView.text = "No videos found matching your search."
                emptyTextView.visibility = View.VISIBLE
            } else {
                emptyTextView.visibility = View.GONE
            }
            foldersRecyclerView.visibility = View.GONE
            searchedVideosRecyclerView.visibility = if (searchedVideosEmpty && searchView?.query?.isEmpty() == true) View.GONE else View.VISIBLE

        } else {
            val foldersEmpty = viewModel.folders.value.isNullOrEmpty()
            emptyTextView.text = "No video folders found on this device."
            emptyTextView.visibility = if (foldersEmpty) View.VISIBLE else View.GONE
            foldersRecyclerView.visibility = if (foldersEmpty) View.GONE else View.VISIBLE
            searchedVideosRecyclerView.visibility = View.GONE
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            viewModel.setPermissionGranted(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu.findItem(R.id.action_search)
        searchView = searchItem?.actionView as? SearchView

        searchView?.queryHint = "Search videos..."
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { viewModel.searchVideos(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                newText?.let { viewModel.searchVideos(it) }
                return true
            }
        })

        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                viewModel.setSearchActive(true)
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.setSearchActive(false)
                return true
            }
        })
        return true
    }

    override fun onResume() {
        super.onResume()
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (viewModel.permissionGranted.value != hasPermission || (hasPermission && viewModel.folders.value.isNullOrEmpty())) {
            viewModel.setPermissionGranted(hasPermission)
        }
    }
}
