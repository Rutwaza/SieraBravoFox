package com.nexrtz.andload.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.nexrtz.andload.model.Song
import com.nexrtz.andload.repository.AudioRecorder
import com.nexrtz.andload.repository.MusicRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    private val recorder = AudioRecorder(application)
    private val aliases = application.getSharedPreferences("song_aliases", 0)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingElapsedMs = MutableStateFlow(0L)
    val recordingElapsedMs: StateFlow<Long> = _recordingElapsedMs.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _recordingMessage = MutableStateFlow("")
    val recordingMessage: StateFlow<String> = _recordingMessage.asStateFlow()

    private val _selectedSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedSongIds: StateFlow<Set<Long>> = _selectedSongIds.asStateFlow()

    private val _libraryMessage = MutableStateFlow("")
    val libraryMessage: StateFlow<String> = _libraryMessage.asStateFlow()

    private var recordingStartedAt = 0L

    private val player: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _currentSong.value = _songs.value.find {
                    it.contentUri == mediaItem?.localConfiguration?.uri
                }
                updatePlaybackState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlaybackState()
            }
        })
    }

    init {
        loadSongs()
        viewModelScope.launch {
            while (true) {
                updatePlaybackState()
                if (_isRecording.value) {
                    _recordingElapsedMs.value = System.currentTimeMillis() - recordingStartedAt
                }
                delay(500)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshSongs() {
        loadSongs()
    }

    fun displayTitle(song: Song): String {
        return aliases.getString(song.contentUri.toString(), song.title)
            ?.takeIf { it.isNotBlank() }
            ?: song.title
    }

    fun renameSong(song: Song, name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        aliases.edit().putString(song.contentUri.toString(), cleanName).apply()
    }

    fun startRecording(): Boolean {
        if (_isRecording.value) return false
        val started = recorder.start()
        if (started) {
            recordingStartedAt = System.currentTimeMillis()
            _recordingElapsedMs.value = 0L
            _recordingMessage.value = "Recording live"
            _isRecording.value = true
        } else {
            _recordingMessage.value = "Microphone could not start"
        }
        return started
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        val file = recorder.stop()
        _isRecording.value = false
        _recordingElapsedMs.value = 0L
        _recordingMessage.value = if (file != null) {
            "Recording saved"
        } else {
            "Recording was not saved"
        }
        viewModelScope.launch {
            delay(1_000)
            loadSongs()
        }
    }

    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
    }

    fun toggleSongSelection(song: Song) {
        _selectedSongIds.value = _selectedSongIds.value.toMutableSet().apply {
            if (!add(song.id)) remove(song.id)
        }
    }

    fun clearSongSelection() {
        _selectedSongIds.value = emptySet()
    }

    fun deleteSong(song: Song) {
        deleteSongs(listOf(song))
    }

    fun deleteSelectedSongs() {
        val selected = _songs.value.filter { it.id in _selectedSongIds.value }
        deleteSongs(selected)
    }

    private fun deleteSongs(songsToDelete: List<Song>) {
        if (songsToDelete.isEmpty()) return
        viewModelScope.launch {
            var deletedCount = 0
            songsToDelete.forEach { song ->
                if (runCatching { repository.deleteSong(song) }.getOrDefault(false)) {
                    deletedCount++
                    aliases.edit().remove(song.contentUri.toString()).apply()
                    if (_currentSong.value?.id == song.id) {
                        player.stop()
                        _currentSong.value = null
                    }
                }
            }
            _selectedSongIds.value = emptySet()
            _libraryMessage.value = if (deletedCount == songsToDelete.size) {
                "Deleted " + deletedCount + " track(s)"
            } else {
                "Deleted " + deletedCount + " track(s); some files need system permission"
            }
            loadSongs()
        }
    }

    private fun loadSongs() {
        viewModelScope.launch {
            _songs.value = repository.fetchLocalSongs()
        }
    }

    fun playSong(song: Song) {
        player.setMediaItem(MediaItem.fromUri(song.contentUri))
        player.prepare()
        player.play()
        _currentSong.value = song
        updatePlaybackState()
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
        updatePlaybackState()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceIn(0L, player.duration.coerceAtLeast(0L)))
        updatePlaybackState()
    }

    fun nextSong() {
        if (_songs.value.isEmpty()) return
        val currentIndex = _songs.value.indexOf(_currentSong.value)
        val nextIndex = if (_shuffleEnabled.value) {
            (0 until _songs.value.size).filter { it != currentIndex }.randomOrNull() ?: 0
        } else {
            max(0, (currentIndex + 1) % _songs.value.size)
        }
        playSong(_songs.value[nextIndex])
    }

    fun previousSong() {
        val currentIndex = _songs.value.indexOf(_currentSong.value)
        if (currentIndex > 0) {
            playSong(_songs.value[currentIndex - 1])
        } else {
            seekTo(0L)
        }
    }

    private fun updatePlaybackState() {
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        _durationMs.value = player.duration.takeIf { it > 0L } ?: _currentSong.value?.duration ?: 0L
    }

    override fun onCleared() {
        recorder.release()
        player.release()
        super.onCleared()
    }
}
