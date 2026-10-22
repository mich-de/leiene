package it.leiene.tv.player

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import it.leiene.tv.R
import it.leiene.tv.data.PlaybackProgressTracker
import it.leiene.tv.data.VideoReport
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    report: VideoReport,
    mpdUrl: String,
    initialPositionMs: Long = 0L,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val progressTracker = remember { PlaybackProgressTracker(context) }

    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(initialPositionMs) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(true) }

    val playPauseFocusRequester = remember { FocusRequester() }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.Builder()
                .setUri(mpdUrl)
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .build()
            setMediaItem(mediaItem)
            prepare()
            if (initialPositionMs > 0) {
                seekTo(initialPositionMs)
            }
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            progressTracker.saveProgress(report.id, exoPlayer.currentPosition, exoPlayer.duration)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Auto update progress and periodic saving
    LaunchedEffect(isPlaying) {
        var tick = 0
        while (true) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            tick++
            if (tick % 10 == 0) {
                progressTracker.saveProgress(report.id, currentPosition, totalDuration)
            }
            delay(500)
        }
    }

    // Controls timeout
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(5000)
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        playPauseFocusRequester.requestFocus()
    }

    BackHandler {
        progressTracker.saveProgress(report.id, exoPlayer.currentPosition, exoPlayer.duration)
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    showControls = true
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            progressTracker.saveProgress(report.id, exoPlayer.currentPosition, exoPlayer.duration)
                            onBack()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        // Fullscreen ExoPlayer
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Sleek Buffering Ring
        if (isBuffering) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFFFE600),
                    strokeWidth = 3.5.dp,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        // Modern Cinematic Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // TOP BAR with smooth gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.90f),
                                    Color.Black.copy(alpha = 0.60f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var backFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                progressTracker.saveProgress(report.id, exoPlayer.currentPosition, exoPlayer.duration)
                                onBack()
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    if (backFocused) Color(0xFFFFE600) else Color(0xFF1E222D).copy(alpha = 0.8f),
                                    CircleShape
                                )
                                .border(
                                    width = if (backFocused) 2.5.dp else 1.dp,
                                    color = if (backFocused) Color.White else Color(0xFF333A48),
                                    shape = CircleShape
                                )
                                .onFocusChanged { backFocused = it.isFocused }
                                .focusable()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrow_back),
                                contentDescription = "Indietro",
                                tint = if (backFocused) Color.Black else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(18.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFFFFE600),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "LE IENE • ${report.inviato.uppercase()}",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = report.date,
                                    color = Color(0xFFE5E7EB),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = report.title,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Quality badge
                        Surface(
                            color = Color(0xFF161922).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF374151))
                        ) {
                            Text(
                                text = "1080p DASH",
                                color = Color(0xFFFFE600),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // BOTTOM CONTROLS DOCK with smooth gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.95f)
                                )
                            )
                        )
                        .padding(horizontal = 36.dp, vertical = 28.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Sleek Progress Timeline
                        val progress = if (totalDuration > 0) currentPosition.toFloat() / totalDuration.toFloat() else 0f
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                color = Color(0xFFFFE600),
                                trackColor = Color(0xFF2E3440),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = formatTime(totalDuration),
                                color = Color(0xFF9CA3AF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Controls Row using exact user icons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(36.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Replay 10s (Row 6, col 4 icon)
                            var rewFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(newPos)
                                    currentPosition = newPos
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(
                                        if (rewFocused) Color(0xFFFFE600) else Color(0xFF1E222D).copy(alpha = 0.85f),
                                        CircleShape
                                    )
                                    .border(
                                        width = if (rewFocused) 2.5.dp else 1.dp,
                                        color = if (rewFocused) Color.White else Color(0xFF333A48),
                                        shape = CircleShape
                                    )
                                    .onFocusChanged { rewFocused = it.isFocused }
                                    .focusable()
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_player_replay_10),
                                    contentDescription = "Riavvolgi 10s",
                                    tint = if (rewFocused) Color.Black else Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Big Center Play / Pause (Row 1/2 circle filled icon)
                            var playFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                },
                                modifier = Modifier
                                    .size(72.dp)
                                    .focusRequester(playPauseFocusRequester)
                                    .border(
                                        width = if (playFocused) 4.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .onFocusChanged { playFocused = it.isFocused }
                                    .focusable()
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play
                                    ),
                                    contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(72.dp)
                                )
                            }

                            // Forward 10s (Row 6, col 5 icon)
                            var ffFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
                                    exoPlayer.seekTo(newPos)
                                    currentPosition = newPos
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(
                                        if (ffFocused) Color(0xFFFFE600) else Color(0xFF1E222D).copy(alpha = 0.85f),
                                        CircleShape
                                    )
                                    .border(
                                        width = if (ffFocused) 2.5.dp else 1.dp,
                                        color = if (ffFocused) Color.White else Color(0xFF333A48),
                                        shape = CircleShape
                                    )
                                    .onFocusChanged { ffFocused = it.isFocused }
                                    .focusable()
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_player_forward_10),
                                    contentDescription = "Avanza 10s",
                                    tint = if (ffFocused) Color.Black else Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
