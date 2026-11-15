package com.nexrtz.andload.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import coil.compose.AsyncImage
import com.nexrtz.andload.model.Song
import com.nexrtz.andload.ui.theme.CyberBlue
import com.nexrtz.andload.ui.theme.CyberDark
import com.nexrtz.andload.ui.theme.CyberGreen
import com.nexrtz.andload.ui.theme.CyberGrey
import com.nexrtz.andload.ui.theme.NeonPurple
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    viewModel: MusicViewModel,
    onRecordRequested: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingElapsedMs by viewModel.recordingElapsedMs.collectAsState()
    val recordingMessage by viewModel.recordingMessage.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val selectedSongIds by viewModel.selectedSongIds.collectAsState()
    val libraryMessage by viewModel.libraryMessage.collectAsState()

    var renameTarget by remember { mutableStateOf<Song?>(null) }
    var deleteTargets by remember { mutableStateOf<List<Song>>(emptyList()) }
    var renameText by remember { mutableStateOf("") }

    val visibleSongs = remember(songs, searchQuery) {
        songs.filter { song ->
            val query = searchQuery.trim()
            query.isEmpty() ||
                viewModel.displayTitle(song).contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true) ||
                song.album.contains(query, ignoreCase = true)
        }
    }
    val totalMinutes = songs.sumOf { it.duration } / 60_000

    if (deleteTargets.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { deleteTargets = emptyList() },
            title = { Text("Delete track(s)?") },
            text = { Text("This will permanently remove the selected audio file(s) from the device.") },
            confirmButton = {
                TextButton(onClick = {
                    if (deleteTargets.size == selectedSongIds.size && selectedSongIds.isNotEmpty()) {
                        viewModel.deleteSelectedSongs()
                    } else {
                        deleteTargets.forEach(viewModel::deleteSong)
                    }
                    deleteTargets = emptyList()
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargets = emptyList() }) { Text("Cancel") }
            }
        )
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename track") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Easy name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameTarget?.let { viewModel.renameSong(it, renameText) }
                        renameTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onRecordRequested) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = if (isRecording) "Stop recording" else "Start recording",
                            tint = if (isRecording) Color.Red else CyberBlue
                        )
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "PLAYER",
                            color = CyberGreen,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "ONLINE",
                            color = CyberBlue,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                },
                actions = {
                    if (selectedSongIds.isNotEmpty()) {
                        IconButton(onClick = {
                            deleteTargets = songs.filter { it.id in selectedSongIds }
                        }) {
                            Icon(Icons.Default.Delete, "Delete selected", tint = Color.Red)
                        }
                        IconButton(onClick = viewModel::clearSongSelection) {
                            Icon(Icons.Default.Close, "Clear selection", tint = Color.White)
                        }
                    } else {
                        IconButton(onClick = viewModel::refreshSongs) {
                            Icon(Icons.Default.Refresh, "Refresh library", tint = CyberGreen)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            currentSong?.let { song ->
                PlaybackControls(
                    song = song,
                    title = viewModel.displayTitle(song),
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    shuffleEnabled = shuffleEnabled,
                    onSeek = viewModel::seekTo,
                    onTogglePlay = viewModel::togglePlayPause,
                    onNext = viewModel::nextSong,
                    onPrevious = viewModel::previousSong,
                    onToggleShuffle = viewModel::toggleShuffle
                )
            }
        },
        containerColor = CyberDark
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            WaterGlassBackground(Modifier.fillMaxSize())
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "YOUR AUDIO MATRIX",
                            color = Color.White,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "LOCAL LIBRARY",
                            color = CyberBlue,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Filter your library") },
                        shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard("TRACKS", songs.size.toString(), Modifier.weight(1f))
                        StatCard("PLAY TIME", totalMinutes.toString() + "m", Modifier.weight(1f))
                        StatCard("MATCHES", visibleSongs.size.toString(), Modifier.weight(1f))
                    }
                    if (libraryMessage.isNotBlank()) {
                        Text(libraryMessage, color = CyberBlue, fontSize = 12.sp)
                    }
                }

                if (isRecording) {
                    item {
                        RecordingCard(
                            elapsedMs = recordingElapsedMs,
                            message = recordingMessage,
                            onStop = onRecordRequested
                        )
                    }
                }

                items(visibleSongs, key = { it.id }) { song ->
                    SongItem(
                        song = song,
                        title = viewModel.displayTitle(song),
                        isPlaying = song == currentSong,
                        isSelected = song.id in selectedSongIds,
                        onClick = { viewModel.playSong(song) },
                        onToggleSelection = { viewModel.toggleSongSelection(song) },
                        onRename = {
                            renameTarget = song
                            renameText = viewModel.displayTitle(song)
                        },
                        onDelete = { deleteTargets = listOf(song) }
                    )
                }

                if (visibleSongs.isEmpty()) {
                    item {
                        Text(
                            "No local tracks found.",
                            color = Color.LightGray,
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaterGlassBackground(modifier: Modifier) {
    Canvas(modifier.background(CyberDark)) {
        val step = 42.dp.toPx()
        for (x in 0..size.width.toInt() step step.toInt()) {
            drawLine(CyberBlue.copy(alpha = 0.055f), androidx.compose.ui.geometry.Offset(x.toFloat(), 0f), androidx.compose.ui.geometry.Offset(x.toFloat(), size.height))
        }
        for (y in 0..size.height.toInt() step step.toInt()) {
            drawLine(CyberGreen.copy(alpha = 0.055f), androidx.compose.ui.geometry.Offset(0f, y.toFloat()), androidx.compose.ui.geometry.Offset(size.width, y.toFloat()))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(11.dp)
    ) {
        Text(label, color = CyberBlue, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SongItem(
    song: Song,
    title: String,
    isPlaying: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isPlaying) CyberGrey.copy(alpha = 0.55f)
                else Color.White.copy(alpha = 0.055f)
            )
            .border(
                1.dp,
                if (isPlaying) CyberGreen.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = song.artUri,
            contentDescription = null,
            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)).background(CyberGrey),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (isPlaying) CyberGreen else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace)
            Text(song.artist.ifBlank { "Unknown artist" }, color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatDuration(song.duration), color = CyberBlue, fontSize = 12.sp)
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggleSelection() },
            colors = CheckboxDefaults.colors(checkedColor = CyberGreen)
        )
        IconButton(onClick = onRename) {
            Icon(Icons.Default.Edit, "Rename", tint = NeonPurple)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
        }
    }
}

@Composable
private fun RecordingCard(elapsedMs: Long, message: String, onStop: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "recording")
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "recordingPulse"
    )
    Row(
        Modifier.fillMaxWidth()
            .background(Color.Red.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
            .border(1.dp, Color.Red.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RecordingVisualizer(pulse)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("RECORDING NOW", color = Color.Red, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(message, color = Color.White)
            Text(formatDuration(elapsedMs), color = Color.LightGray, fontFamily = FontFamily.Monospace)
        }
        Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
            Text("STOP")
        }
    }
}

@Composable
private fun RecordingVisualizer(pulse: Float) {
    Canvas(Modifier.size(width = 70.dp, height = 42.dp)) {
        repeat(7) { index ->
            val height = size.height * (0.35f + pulse * ((index % 3 + 1) / 4f))
            val x = size.width * (index + 1) / 8f
            drawLine(
                Color.Red,
                androidx.compose.ui.geometry.Offset(x, size.height / 2f - height / 2f),
                androidx.compose.ui.geometry.Offset(x, size.height / 2f + height / 2f),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    song: Song,
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text("NOW BROADCASTING", color = CyberBlue, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Text(title, color = CyberGreen, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, color = Color.LightGray, fontSize = 12.sp)
        Slider(
            value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)).toFloat(),
            onValueChange = { onSeek(it.roundToInt().toLong()) },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            colors = SliderDefaults.colors(thumbColor = CyberGreen, activeTrackColor = CyberGreen)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(positionMs), color = Color.LightGray, fontSize = 11.sp)
            Text(formatDuration(durationMs), color = Color.LightGray, fontSize = 11.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, "Previous", tint = CyberBlue) }
            IconButton(onClick = onToggleShuffle) {
                Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffleEnabled) CyberGreen else Color.LightGray)
            }
            FilledIconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(54.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = CyberGreen, contentColor = CyberDark)
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Next", tint = CyberBlue) }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
