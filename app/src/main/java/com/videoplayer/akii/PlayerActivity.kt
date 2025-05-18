package com.videoplayer.akii // Ensure this matches your app's package

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes // Keep if you sideload specific MIME types like SubRip
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.common.TrackSelectionOverride

import androidx.media3.ui.PlayerView // Use PlayerView as in your previous code

import android.app.Dialog
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

@UnstableApi // Add annotation as PlayerView might be Unstable in recent Media3
class PlayerActivity : AppCompatActivity(), SwipeGestureLayout.GestureListener {

    private var exoPlayer: ExoPlayer? = null
    // Use PlayerView as in your previous working code
    private lateinit var playerView: PlayerView
    private var videoUri: Uri? = null
    private var videoTitle: String = "Video Player" // Default title
    private var videoList: List<Pair<Uri, String>> = emptyList() // List of video URIs and titles
    private var currentVideoIndex: Int = 0

    // Removed availableSubtitles list unless you explicitly side-load subtitles not in the stream

    private var playWhenReady = true
    private var currentItem = 0
    private var playbackPosition = 0L
    private var isFullscreen = false // Manage fullscreen state
    private var isOrientationLocked = false // Manage orientation lock state

    // lateinit properties for custom controller views found on PlayerView's inflated layout
    private lateinit var backButton: ImageButton
    private lateinit var titleTextView: TextView
    private lateinit var fullscreenButton: ImageButton
    private lateinit var orientationLockButton: ImageButton
    private lateinit var settingsButton: ImageButton
    private lateinit var subtitlesButton: ImageButton
    private lateinit var audioTracksButton: ImageButton

    private lateinit var trackSelector: DefaultTrackSelector

    private lateinit var gestureDetector: GestureDetector // For double tap seek, single tap controller toggle
    private val seekForwardMs = 10000L // 10 seconds
    private val seekBackwardMs = 10000L // 10 seconds

    private lateinit var swipeLayout: SwipeGestureLayout
    private lateinit var gestureOverlay: FrameLayout
    private lateinit var volumeIndicator: LinearLayout
    private lateinit var brightnessIndicator: LinearLayout
    private lateinit var seekIndicator: TextView
    private lateinit var volumePercent: TextView
    private lateinit var brightnessPercent: TextView
    private lateinit var trackSelectionButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var previousButton: ImageButton
    
    private lateinit var audioManager: AudioManager
    private var maxVolume: Int = 0
    private var currentVolume: Int = 0
    private var wasPlaying: Boolean = false

    // Note: We rely on the player's default exo_play_pause listener binding if using PlayerView
    // No need for a manual PlayPause button findViewById and setOnClickListener if standard IDs are used.

    private lateinit var playlistButton: ImageButton
    private lateinit var playlistIndicator: TextView
    private var isPlaylistDialogShowing = false

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> {
                    Log.d(TAG, "Playback state: IDLE")
                }
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "Playback state: BUFFERING")
                }
                Player.STATE_READY -> {
                    Log.d(TAG, "Playback state: READY")
                    updatePlayPauseButton()
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "Playback state: ENDED")
                    updatePlayPauseButton()
                    // Auto-play next video when current video ends
                    if (currentVideoIndex < videoList.size - 1) {
                        playNextVideo()
                    }
                }
            }
        }

        // Called when tracks (audio, video, text) change
        // This is a good place to log track info, but selection happens in dialogs
        override fun onTracksChanged(tracks: Tracks) {
            // The `tracks` object contains detailed info about the current tracks
            Log.d(TAG, "onTracksChanged called.")
            // Example logging tracks:
            /*
            for (trackGroup in tracks.groups) {
                Log.d(TAG, "Renderer type: ${trackGroup.rendererType}")
                Log.d(TAG, "Group: ${trackGroup.mediaTrackGroup.length} tracks")
                for(i in 0 until trackGroup.mediaTrackGroup.length) {
                    val format = trackGroup.mediaTrackGroup.getFormat(i)
                    Log.d(TAG, "  Track ${i}: Mime=${format.sampleMimeType}, Lang=${format.language}, Label=${format.label}")
                }
            }
            */
        }

        override fun onPlayerError(error: PlaybackException) {
            // Log and show error messages
            Log.e(TAG, "Playback error: ${error.errorCodeName}", error)
            Toast.makeText(this@PlayerActivity, "Playback error: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "Is playing changed to: $isPlaying")
            updatePlayPauseButton()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            // Handle video size changes, useful for adapting UI or fullscreen
            // For instance, might update initial fullscreen state based on orientation/video size relationship
            if (!isFullscreen && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT && videoSize.width > videoSize.height) {
                // If portrait and video is landscape, perhaps suggest or auto-enter fullscreen
                // Optional logic here
                Log.d(TAG, "Video is landscape, orientation is portrait. Recommend fullscreen?")
            }
        }
    }

    companion object {
        // Consistent intent keys, used by calling activities like FolderVideosActivity
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_VIDEO_LIST = "extra_video_list" // New extra for video list
        const val EXTRA_VIDEO_INDEX = "extra_video_index" // New extra for current index
        private const val TAG = "PlayerActivity"
        private const val REQUEST_SELECT_SUBTITLE = 1001
        private const val REQUEST_WRITE_SETTINGS = 1002
    }

    // Use UnstableApi annotation for the whole class as recommended for PlayerView
    @SuppressLint("ClickableViewAccessibility", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Initialize PlayerView
        playerView = findViewById(R.id.playerView)
        playerView.useController = true // Ensure controller is enabled
        playerView.controllerAutoShow = true
        playerView.controllerShowTimeoutMs = 3000
        
        // Set controller visibility listener with explicit type
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            if (visibility == View.VISIBLE) {
                updateNavigationButtons()
            }
        })
        
        // Get video list and current index from intent
        val videoListExtra = intent.getSerializableExtra(EXTRA_VIDEO_LIST) as? ArrayList<Pair<String, String>>
        currentVideoIndex = intent.getIntExtra(EXTRA_VIDEO_INDEX, 0)
        
        if (videoListExtra != null) {
            videoList = videoListExtra.map { (uriString, title) -> 
                Pair(Uri.parse(uriString), title)
            }
            Log.d(TAG, "Video list size: ${videoList.size}")
            Log.d(TAG, "Current index: $currentVideoIndex")
        }

        // Initialize next and previous buttons
        nextButton = playerView.findViewById(R.id.custom_next)
        previousButton = playerView.findViewById(R.id.custom_prev)

        // Set click listeners for next and previous buttons
        nextButton.setOnClickListener {
            Log.d(TAG, "Next button clicked")
            playNextVideo()
        }

        previousButton.setOnClickListener {
            Log.d(TAG, "Previous button clicked")
            playPreviousVideo()
        }

        // Force initial button visibility update
        playerView.post {
            updateNavigationButtons()
        }

        // Get current video URI and title
        val videoUriString = intent.getStringExtra(EXTRA_VIDEO_URI)
        videoUri = try {
            videoUriString?.let { Uri.parse(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing video URI: ${e.message}", e)
            null
        }
        
        if (videoUri == null) {
            Log.e(TAG, "No valid video URI provided")
            Toast.makeText(this, "Error: No valid video selected", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Video Player"
        Log.d(TAG, "Video title: $videoTitle")

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setPreferredAudioLanguage("en").setPreferredTextLanguage("en"))
        }

        // Initialize system services
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        // Initialize views
        initializeViews()
        
        // Initialize player and setup controls
        initializePlayer()
        
        // Wait for the PlayerView's controller to be inflated before setting up controls
        playerView.post {
            try {
                setupCustomControls()
                initializePlayerGestureDetector()
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up controls: ${e.message}", e)
                Toast.makeText(this, "Error setting up video controls", Toast.LENGTH_SHORT).show()
            }
        }

        setupTrackSelection()
        hideSystemUI()

        // Initialize playlist button and indicator
        playlistButton = findViewById(R.id.playlistButton)
        playlistIndicator = findViewById(R.id.playlistIndicator)

        playlistButton.setOnClickListener {
            showPlaylistDialog()
        }

        // Update playlist indicator
        updatePlaylistIndicator()
    }

    // Setup gesture detection for double tap seek and single tap controller toggle
    @SuppressLint("ClickableViewAccessibility") // Suppress accessibility warning for onTouchListener on PlayerView
    private fun initializePlayerGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                // Prevent playerView from handling double tap on progress bar etc.
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Custom double-tap logic for seeking
                exoPlayer?.let { player ->
                    // Only seek if duration is available and greater than 0
                    if (player.duration > 0 && !player.isCurrentMediaItemDynamic && !player.isCurrentMediaItemLive) {
                        val viewWidth = playerView.width
                        val newPosition = if (e.x < viewWidth / 2) {
                            // Left side double tap - seek backward
                            (player.currentPosition - seekBackwardMs).coerceAtLeast(0L)
                        } else {
                            // Right side double tap - seek forward
                            (player.currentPosition + seekForwardMs).coerceAtMost(player.duration)
                        }
                        player.seekTo(newPosition)
                        // Ensure controller is visible after seeking
                        playerView.showController()
                        Log.d(TAG, "Double tap seek to $newPosition")
                    } else {
                        Log.d(TAG, "Double tap seek ignored - live or dynamic content")
                    }
                }
                return true // Consume the event
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Toggle controller visibility on single tap (if not a double tap)
                // PlayerView handles this automatically by default.
                // If you override this and return true, you prevent PlayerView's default toggle.
                // Return false here so PlayerView's default single-tap-to-toggle still works,
                // unless your custom layout explicitly disabled PlayerView's touch handling.
                // playerView.showController() vs hideController() logic from old code.
                // The StyledPlayerView manages show/hide, you typically just need to *call* showController
                // after custom actions if needed. Returning false here is safer.
                return false // Allow PlayerView to handle single tap for controller toggle
            }

            // If you wanted tap and hold:
            // override fun onLongPress(e: MotionEvent) { ... }
            // If you wanted scroll:
            // override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean { ... }

        })

        // Set the touch listener on the playerView.
        // This allows our GestureDetector to process touch events.
        playerView.setOnTouchListener { _, event ->
            // Pass events to gestureDetector. If it consumes the event (e.g., double tap returns true),
            // the onTouchListener should return true. Otherwise, return false to allow default view handling (like single tap toggle).
            // However, GestureDetector.onTouchEvent handles this logic internally; it will return true if
            // any gesture listener method returned true.
            gestureDetector.onTouchEvent(event) || super.onTouchEvent(event) // Pass unhandled events further if needed
        }
    }


    // Find and setup listeners for custom controls from the controller layout
    private fun setupCustomControls() {
        try {
            // Find all custom control views
            backButton = playerView.findViewById(R.id.exo_back_button)
            titleTextView = playerView.findViewById(R.id.exo_custom_title)
            fullscreenButton = playerView.findViewById(R.id.exo_fullscreen_button)
            settingsButton = playerView.findViewById(R.id.exo_settings_button)
            subtitlesButton = playerView.findViewById(R.id.exo_subtitles_button)
            audioTracksButton = playerView.findViewById(R.id.exo_audio_tracks_button)

            // Set video title
            titleTextView.text = videoTitle

            // Set click listeners
            backButton.setOnClickListener {
                onBackPressedDispatcher.onBackPressed()
            }

            fullscreenButton.setOnClickListener {
                toggleFullscreen()
            }

            settingsButton.setOnClickListener {
                showSettingsDialog()
            }

            subtitlesButton.setOnClickListener {
                showSubtitlesDialog()
            }

            audioTracksButton.setOnClickListener {
                showAudioTracksDialog()
            }

            // Set up play/pause buttons
            val playButton = playerView.findViewById<ImageButton>(R.id.exo_play)
            val pauseButton = playerView.findViewById<ImageButton>(R.id.exo_pause)

            playButton?.setOnClickListener {
                exoPlayer?.play()
                updatePlayPauseButton()
            }

            pauseButton?.setOnClickListener {
                exoPlayer?.pause()
                updatePlayPauseButton()
            }

            // Update initial button states
            updateFullscreenButton()
            updatePlayPauseButton()

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up custom controls: ${e.message}", e)
            Toast.makeText(this, "Error loading custom controls: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Show main settings dialog with options like Playback Speed
    private fun showSettingsDialog() {
        val settingsItems = mutableListOf("Playback Speed")
        // Add more settings here later (e.g., Video Quality)

        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(settingsItems.toTypedArray()) { dialog, which ->
                when (which) {
                    0 -> showPlaybackSpeedDialog() // Index 0 is Playback Speed
                    // Add more cases for other settings
                }
                dialog.dismiss() // Close this dialog after selection
            }
            .setNegativeButton("Cancel", null) // Simple cancel button
            .show()
    }

    // Show playback speed selection dialog
    private fun showPlaybackSpeedDialog() {
        val speeds = arrayOf("0.25x", "0.5x", "0.75x", "Normal", "1.25x", "1.5x", "2x")
        val speedValues = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        // Get current speed and find its index to pre-select in the dialog
        val currentSpeed = exoPlayer?.playbackParameters?.speed ?: 1.0f
        val checkedItem = speedValues.indexOfFirst { it == currentSpeed }.takeIf { it != -1 } ?: 3 // Default to Normal (index 3)

        AlertDialog.Builder(this)
            .setTitle("Playback Speed")
            .setSingleChoiceItems(speeds, checkedItem) { dialog, which ->
                // Apply the selected speed to the player
                exoPlayer?.setPlaybackParameters(PlaybackParameters(speedValues[which]))
                dialog.dismiss() // Close the dialog
            }
            .setNegativeButton("Cancel", null) // Simple cancel button
            .show()
    }


    // Initialize the ExoPlayer instance
    private fun initializePlayer() {
        if (exoPlayer != null) {
            Log.d(TAG, "Player already initialized.")
            exoPlayer?.playWhenReady = playWhenReady
            exoPlayer?.seekTo(currentItem, playbackPosition)
            return
        }

        try {
            Log.d(TAG, "Initializing player with URI: $videoUri")
            
            // Create the player instance
            exoPlayer = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .build()
                .also { player ->
                    // Set up the player with the PlayerView
                    playerView.player = player
                    
                    // Set default playback properties
                    player.playWhenReady = playWhenReady
                    player.seekTo(currentItem, playbackPosition)
                    
                    // Add listener for player events
                    player.addListener(playerListener)
                    
                    // Prepare the media
                    videoUri?.let { uri ->
                        try {
                            val mediaItem = MediaItem.fromUri(uri)
                            player.setMediaItem(mediaItem)
                            player.prepare()
                            Log.d(TAG, "Media item set and preparing: $uri")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error setting media item: ${e.message}", e)
                            Toast.makeText(this, "Error loading video: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } ?: run {
                        Log.e(TAG, "Video URI is null")
                        Toast.makeText(this, "Error: No video selected", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }

            Log.d(TAG, "Player initialized and preparing media.")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing player: ${e.message}", e)
            Toast.makeText(this, "Error initializing player: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Add method to load external subtitles
    private fun loadExternalSubtitles(subtitleUri: Uri) {
        try {
            val subtitleMediaItem = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                .setMimeType(MimeTypes.APPLICATION_SUBRIP) // for .srt files
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            // Get current media item
            val currentMediaItem = exoPlayer?.currentMediaItem ?: return
            
            // Create new media item with subtitles
            val newMediaItem = MediaItem.Builder()
                .setUri(currentMediaItem.localConfiguration?.uri ?: return)
                .setSubtitleConfigurations(listOf(subtitleMediaItem))
                .build()

            // Set the new media item
            exoPlayer?.let { player ->
                val currentPosition = player.currentPosition
                val wasPlaying = player.isPlaying
                
                player.setMediaItem(newMediaItem)
                player.prepare()
                player.seekTo(currentPosition)
                if (wasPlaying) player.play()
                
                Toast.makeText(this, "Subtitles loaded successfully", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading subtitles: ${e.message}", e)
            Toast.makeText(this, "Error loading subtitles: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Update subtitle button click handler
    private fun showSubtitlesDialog() {
        val options = arrayOf("Select from tracks", "Load external subtitles", "No subtitles")
        
        AlertDialog.Builder(this)
            .setTitle("Subtitles")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showSubtitleTracksDialog() // Show built-in subtitle tracks
                    1 -> selectSubtitleFile() // Launch file picker for external subtitles
                    2 -> disableSubtitles() // Disable subtitles
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSubtitleTracksDialog() {
        val trackGroups = exoPlayer?.currentTracks?.groups ?: run {
            Toast.makeText(this, "Track info not available", Toast.LENGTH_SHORT).show()
            return
        }

        // Find the groups specific to the Text renderer (subtitles)
        val textRendererGroups = trackGroups.filter { it.type == C.TRACK_TYPE_TEXT }

        if (textRendererGroups.isEmpty()) {
            Toast.makeText(this, "No built-in subtitles available", Toast.LENGTH_SHORT).show()
            return
        }

        // List to display in the dialog ("None" + subtitle labels)
        val subtitleOptions = mutableListOf("None")
        // Store necessary info for selection
        val subtitleTrackInfo = mutableListOf<Pair<Tracks.Group, Int>>()

        textRendererGroups.forEach { group ->
            for (trackIndex in 0 until group.length) {
                val trackFormat = group.getTrackFormat(trackIndex)
                val label = trackFormat.label ?: trackFormat.language ?: "Subtitle ${subtitleTrackInfo.size + 1}"
                subtitleOptions.add(label)
                subtitleTrackInfo.add(Pair(group, trackIndex))
            }
        }

        // Find currently selected track
        var currentlySelectedDialogIndex = 0
        for ((index, group) in textRendererGroups.withIndex()) {
            if (group.isSelected) {
                currentlySelectedDialogIndex = index + 1 // +1 for "None" option
                break
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Select Built-in Subtitle")
            .setSingleChoiceItems(subtitleOptions.toTypedArray(), currentlySelectedDialogIndex) { dialog, which ->
                val parametersBuilder = trackSelector.parameters.buildUpon()

                if (which == 0) {
                    // Disable text tracks
                    parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    // Enable selected track
                    val (selectedGroup, selectedTrackIndex) = subtitleTrackInfo[which - 1]
                    parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    parametersBuilder.setOverrideForType(
                        TrackSelectionOverride(
                            selectedGroup.mediaTrackGroup,
                            listOf(selectedTrackIndex)
                        )
                    )
                }

                // Apply the new selection parameters
                trackSelector.parameters = parametersBuilder.build()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun selectSubtitleFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*" // You might want to restrict to specific subtitle file types
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(
            Intent.createChooser(intent, "Select Subtitle File"),
            REQUEST_SELECT_SUBTITLE
        )
    }

    private fun disableSubtitles() {
        val parametersBuilder = trackSelector.parameters.buildUpon()
        parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        trackSelector.parameters = parametersBuilder.build()
        Toast.makeText(this, "Subtitles disabled", Toast.LENGTH_SHORT).show()
    }

    // Release player resources and save state
    private fun releasePlayer() {
        exoPlayer?.let { player ->
            // Save current position and play state before releasing
            playbackPosition = player.currentPosition
            currentItem = player.currentMediaItemIndex // Save current item index if playlist is used
            playWhenReady = player.playWhenReady // Save play state

            // Remove the listener
            player.removeListener(playerListener)
            // Release the player instance
            player.release()
            exoPlayer = null // Nullify the reference
            playerView.player = null // Remove player from view
        }
        Log.d(TAG, "Player released.")
    }

    // Fullscreen toggle logic
    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen // Toggle state
        if (isFullscreen) {
            enterFullscreen()
        } else {
            exitFullscreen()
        }
        updateFullscreenButton() // Update button icon
    }

    // Enter fullscreen mode
    @SuppressLint("SourceLockedOrientationActivity")
    private fun enterFullscreen() {
        hideSystemUI() // Hide status bar and navigation bar

        // Set screen orientation to landscape sensor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // Exit fullscreen mode
    private fun exitFullscreen() {
        showSystemUI() // Show status bar and navigation bar

        // Set screen orientation back to unspecified
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Update the fullscreen button icon
    private fun updateFullscreenButton() {
        if (::fullscreenButton.isInitialized) { // Check if the button was successfully found
            // Use the appropriate drawable resource ID
            fullscreenButton.setImageResource(
                if (isFullscreen) R.drawable.ic_fullscreen_exit // Assumes you have ic_fullscreen_exit drawable
                else R.drawable.ic_fullscreen_enter // Assumes you have ic_fullscreen_enter drawable
            )
        } else {
            Log.w(TAG, "updateFullscreenButton: fullscreenButton not initialized.")
        }
    }


    // Orientation lock toggle logic
    @SuppressLint("SourceLockedOrientationActivity") // Suppress lint warning about requestedOrientation
    private fun toggleOrientationLock() {
        isOrientationLocked = !isOrientationLocked // Toggle state
        val currentOrientation = resources.configuration.orientation // Get current actual orientation

        requestedOrientation = if (isOrientationLocked) {
            // Lock to the current orientation (either portrait sensor or landscape sensor)
            if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            // Unlock: Allow device's sensor to determine orientation
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        updateOrientationLockButton() // Update button icon
    }

    // Update the orientation lock button icon
    private fun updateOrientationLockButton() {
        if (::orientationLockButton.isInitialized) { // Check if button is initialized
            orientationLockButton.setImageResource(
                // Use the appropriate drawable resource ID
                if (isOrientationLocked) R.drawable.ic_screen_lock_rotation // Assumes ic_screen_lock_rotation drawable
                else R.drawable.ic_screen_rotation // Assumes ic_screen_rotation drawable
            )
        } else {
            Log.w(TAG, "updateOrientationLockButton: orientationLockButton not initialized.")
        }
    }


    // Hide system UI (status bar, navigation bar)
    private fun hideSystemUI() {
        // Use WindowCompat and WindowInsetsControllerCompat for modern edge-to-edge fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, playerView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars()) // Hide both status and navigation bars
            // immersive_sticky means swipe reveals bars transiently, and they hide again
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // Show system UI (status bar, navigation bar)
    private fun showSystemUI() {
        // Restore default system window fits
        WindowCompat.setDecorFitsSystemWindows(window, true)
        // Show system bars again
        WindowInsetsControllerCompat(window, playerView).show(WindowInsetsCompat.Type.systemBars())
    }


    // --- Lifecycle Callbacks ---
    // Player management across lifecycle state changes.
    // On SDK >= 24 (Nougat and up), activity resumes in multi-window or splitscreen. Player state should be preserved.
    // On SDK < 24, pause activity fully releases resources, so re-initialize on resume.

    override fun onStart() {
        super.onStart()
        // Initialize player when the activity is becoming visible
        // This is crucial for API 24+ when resuming from multi-window
        if (Util.SDK_INT >= 24) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        // Initialize player here as well, for cases like resuming from lock screen or minimizing (on older SDKs)
        // For API < 24, releasePlayer is called in onPause, so player will be null and re-initialized here.
        // For API >= 24, initializePlayer() onStart would have handled it, but calling again checks for null gracefully.
        if (Util.SDK_INT < 24 || exoPlayer == null) {
            initializePlayer()
            // In this case (resuming player was previously null), you likely want to start playing again.
            // If initializePlayer already sets playWhenReady based on saved state, this isn't strictly needed.
            // exoPlayer?.play() // Optional: explicitly resume play
        }

        // Hide system UI to create an immersive video experience
        hideSystemUI() // Re-apply immersive mode when resuming

        // Update play/pause button state
        updatePlayPauseButton()
    }

    override fun onPause() {
        super.onPause()
        // Pause the player when the activity is no longer the primary focus,
        // even if it's still partially visible (API 24+). This saves battery/resources.
        exoPlayer?.pause()

        // Release the player resources for SDK < 24 when pausing completely.
        if (Util.SDK_INT < 24) {
            releasePlayer()
        }
        // For SDK >= 24, the player is typically kept alive across simple pauses and foreground multi-window usage.
        // Release only happens in onStop.
    }

    override fun onStop() {
        super.onStop()
        // Release the player when the activity is no longer visible.
        // This is important for API 24+ when activity goes to the background.
        if (Util.SDK_INT >= 24) {
            releasePlayer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Final check to release the player resources just before the activity is destroyed.
        // In most cases, onStop handles this for API 24+, but this is a safeguard.
        // No need to release again if exoPlayer is already null (handled in releasePlayer).
        // This can be removed if onStop release is reliable for your minSdk.
        if (exoPlayer != null) {
            releasePlayer()
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Log.d(TAG, "Activity onDestroy")
    }

    // --- Track Selection Dialogs ---

    // Audio track selection dialog - using currentTracks.groups API
    private fun showAudioTracksDialog() {
        val trackGroups = exoPlayer?.currentTracks?.groups ?: run {
            Toast.makeText(this, "Track info not available", Toast.LENGTH_SHORT).show()
            return
        }

        // Find groups specific to the Audio renderer
        val audioRendererGroups = trackGroups.filter { it.type == C.TRACK_TYPE_AUDIO }

        if (audioRendererGroups.isEmpty()) {
            Toast.makeText(this, "No audio tracks available", Toast.LENGTH_SHORT).show()
            return
        }

        val audioTrackInfo = mutableListOf<Pair<Tracks.Group, Int>>()
        val trackNames = mutableListOf<String>()

        audioRendererGroups.forEach { group ->
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val label = format.label ?: format.language ?: "Audio ${trackNames.size + 1}"
                trackNames.add(label)
                audioTrackInfo.add(Pair(group, trackIndex))
            }
        }

        // Find currently selected audio track
        var currentlySelectedDialogIndex = 0
        for ((index, group) in audioRendererGroups.withIndex()) {
            if (group.isSelected) {
                currentlySelectedDialogIndex = index
                break
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Select Audio Track")
            .setSingleChoiceItems(trackNames.toTypedArray(), currentlySelectedDialogIndex) { dialog, which ->
                val (selectedGroup, selectedTrackIndex) = audioTrackInfo[which]
                
                val parametersBuilder = trackSelector.parameters.buildUpon()
                parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                parametersBuilder.setOverrideForType(
                    TrackSelectionOverride(
                        selectedGroup.mediaTrackGroup,
                        listOf(selectedTrackIndex)
                    )
                )

                // Apply the new selection parameters
                trackSelector.parameters = parametersBuilder.build()

                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePlayPauseButton() {
        try {
            val playButton = playerView.findViewById<ImageButton>(R.id.exo_play)
            val pauseButton = playerView.findViewById<ImageButton>(R.id.exo_pause)
            
            exoPlayer?.let { player ->
                when {
                    player.isPlaying -> {
                        playButton?.visibility = View.GONE
                        pauseButton?.visibility = View.VISIBLE
                    }
                    player.playbackState == Player.STATE_ENDED -> {
                        playButton?.visibility = View.VISIBLE
                        pauseButton?.visibility = View.GONE
                    }
                    else -> {
                        playButton?.visibility = View.VISIBLE
                        pauseButton?.visibility = View.GONE
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating play/pause button: ${e.message}", e)
        }
    }

    private fun initializeViews() {
        swipeLayout = findViewById(R.id.swipeLayout)
        gestureOverlay = findViewById(R.id.gestureOverlay)
        volumeIndicator = findViewById(R.id.volumeIndicator)
        brightnessIndicator = findViewById(R.id.brightnessIndicator)
        seekIndicator = findViewById(R.id.seekIndicator)
        volumePercent = findViewById(R.id.volumePercent)
        brightnessPercent = findViewById(R.id.brightnessPercent)
        trackSelectionButton = findViewById(R.id.exo_audio_tracks_button)

        // Initialize current values
        val currentVolumePercent = currentVolume.toFloat() / maxVolume
        val currentBrightness = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (e: Exception) {
            0.5f // Default to 50% if can't get current brightness
        }
        val currentSeekPercent = exoPlayer?.let { player ->
            if (player.duration > 0) player.currentPosition.toFloat() / player.duration else 0f
        } ?: 0f

        swipeLayout.setCurrentValues(currentVolumePercent, currentBrightness, currentSeekPercent)
        swipeLayout.setGestureListener(this)
    }

    private fun setupTrackSelection() {
        trackSelectionButton.setOnClickListener {
            showTrackSelectionDialog()
        }

        exoPlayer?.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                updateTrackSelectionButton(tracks)
            }
        })
    }

    private fun updateTrackSelectionButton(tracks: Tracks) {
        val hasMultipleTracks = tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_AUDIO || group.type == C.TRACK_TYPE_TEXT
        }
        trackSelectionButton.visibility = if (hasMultipleTracks) View.VISIBLE else View.GONE
    }

    private fun showTrackSelectionDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_track_selection)

        val audioGroup = dialog.findViewById<RadioGroup>(R.id.audioTrackGroup)
        val subtitleGroup = dialog.findViewById<RadioGroup>(R.id.subtitleTrackGroup)

        exoPlayer?.currentTracks?.groups?.forEach { group ->
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val button = RadioButton(this)
                        val format = group.getTrackFormat(i)
                        button.text = format.label ?: format.language ?: "Audio Track ${i + 1}"
                        button.id = View.generateViewId()
                        button.isChecked = group.isTrackSelected(i)
                        button.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                val parameters = exoPlayer?.trackSelectionParameters
                                    ?.buildUpon()
                                    ?.setOverrideForType(
                                        TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                                    )
                                    ?.build()
                                parameters?.let { exoPlayer?.trackSelectionParameters = it }
                            }
                        }
                        audioGroup.addView(button)
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    // Add "None" option for subtitles
                    val noneButton = RadioButton(this)
                    noneButton.text = "None"
                    noneButton.id = View.generateViewId()
                    noneButton.isChecked = !group.isSelected
                    subtitleGroup.addView(noneButton)

                    for (i in 0 until group.length) {
                        val button = RadioButton(this)
                        val format = group.getTrackFormat(i)
                        button.text = format.label ?: format.language ?: "Subtitle Track ${i + 1}"
                        button.id = View.generateViewId()
                        button.isChecked = group.isTrackSelected(i)
                        button.setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) {
                                val parameters = exoPlayer?.trackSelectionParameters
                                    ?.buildUpon()
                                    ?.setOverrideForType(
                                        TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                                    )
                                    ?.build()
                                parameters?.let { exoPlayer?.trackSelectionParameters = it }
                            }
                        }
                        subtitleGroup.addView(button)
                    }
                }
            }
        }

        dialog.show()
    }

    // Implementation of SwipeGestureLayout.GestureListener interface
    override fun onVolumeChange(percent: Float) {
        val newVolume = (maxVolume * percent).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        currentVolume = newVolume
        
        // Update volume indicator
        volumeIndicator.visibility = View.VISIBLE
        volumePercent.text = "${(percent * 100).toInt()}%"
        
        // Hide indicator after a delay
        volumeIndicator.postDelayed({
            volumeIndicator.visibility = View.GONE
        }, 1000)
    }

    override fun onBrightnessChange(percent: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                // Request permission if not granted
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_WRITE_SETTINGS)
                return
            }
        }

        try {
            // Convert percentage to brightness value (0-255)
            val brightness = (percent * 255).toInt()
            
            // Set the brightness
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightness)
            
            // Update the window brightness
        val window = window
            val layoutParams = window.attributes
            layoutParams.screenBrightness = percent
            window.attributes = layoutParams
            
            // Update brightness indicator
        brightnessIndicator.visibility = View.VISIBLE
            brightnessPercent.text = "${(percent * 100).toInt()}%"
            
            // Hide indicator after a delay
            brightnessIndicator.postDelayed({
                brightnessIndicator.visibility = View.GONE
            }, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting brightness: ${e.message}")
            Toast.makeText(this, "Cannot change brightness: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSeekChange(percent: Float) {
        exoPlayer?.let { player ->
            if (player.duration > 0) {
                val newPosition = (player.duration * percent).toLong()
                player.seekTo(newPosition)
                
                // Update seek indicator
                seekIndicator.visibility = View.VISIBLE
                val minutes = newPosition / 60000
                val seconds = (newPosition % 60000) / 1000
                seekIndicator.text = String.format("%02d:%02d", minutes, seconds)
                
                // Hide indicator after a delay
                seekIndicator.postDelayed({
                    seekIndicator.visibility = View.GONE
                }, 1000)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_SELECT_SUBTITLE -> {
                if (resultCode == RESULT_OK) {
                    data?.data?.let { uri ->
                        loadExternalSubtitles(uri)
                    }
                }
            }
            REQUEST_WRITE_SETTINGS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (Settings.System.canWrite(this)) {
                        Toast.makeText(this, "Brightness control enabled", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Permission denied for brightness control", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun playNextVideo() {
        Log.d(TAG, "playNextVideo called. Current index: $currentVideoIndex, List size: ${videoList.size}")
        if (videoList.isNotEmpty() && currentVideoIndex < videoList.size - 1) {
            currentVideoIndex++
            val (nextUri, nextTitle) = videoList[currentVideoIndex]
            Log.d(TAG, "Playing next video: $nextTitle at index $currentVideoIndex")
            playVideo(nextUri, nextTitle)
            updateNavigationButtons()
        } else {
            Log.d(TAG, "No more videos to play")
            Toast.makeText(this, "No more videos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playPreviousVideo() {
        if (videoList.isNotEmpty() && currentVideoIndex > 0) {
            currentVideoIndex--
            val (prevUri, prevTitle) = videoList[currentVideoIndex]
            playVideo(prevUri, prevTitle)
            updateNavigationButtons()
        } else {
            Toast.makeText(this, "No previous videos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playVideo(uri: Uri, title: String) {
        videoUri = uri
        videoTitle = title
        titleTextView.text = title
        
        exoPlayer?.let { player ->
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
        
        updatePlaylistIndicator()
        updateNavigationButtons()
        
        // Force show controls briefly to ensure buttons are visible
        playerView.showController()
    }

    private fun updateNavigationButtons() {
        if (videoList.isEmpty()) {
            nextButton.visibility = View.GONE
            previousButton.visibility = View.GONE
            return
        }

        // Show next button if there are more videos after current
        nextButton.visibility = if (currentVideoIndex < videoList.size - 1) View.VISIBLE else View.GONE
        
        // Show previous button if we're not at the first video
        previousButton.visibility = if (currentVideoIndex > 0) View.VISIBLE else View.GONE

        Log.d(TAG, "Updated navigation buttons - Current index: $currentVideoIndex, List size: ${videoList.size}")
        Log.d(TAG, "Next button visibility: ${nextButton.visibility}, Previous button visibility: ${previousButton.visibility}")
    }

    private fun updatePlaylistIndicator() {
        if (videoList.isNotEmpty()) {
            playlistIndicator.text = "${currentVideoIndex + 1}/${videoList.size}"
            playlistIndicator.visibility = View.VISIBLE
        } else {
            playlistIndicator.visibility = View.GONE
        }
    }

    private fun showPlaylistDialog() {
        if (isPlaylistDialogShowing) return
        isPlaylistDialogShowing = true

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_playlist, null)
        
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.playlistRecyclerView)
        val closeButton = dialogView.findViewById<ImageButton>(R.id.closeButton)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = PlaylistAdapter(videoList, currentVideoIndex) { index ->
            currentVideoIndex = index
            val (uri, title) = videoList[index]
            playVideo(uri, title)
            updatePlaylistIndicator()
            dialog.dismiss()
        }
        recyclerView.adapter = adapter

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            isPlaylistDialogShowing = false
        }

        dialog.setContentView(dialogView)
        dialog.show()
    }
}