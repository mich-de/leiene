package it.leiene.tv.ui

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import it.leiene.tv.data.EpisodeGroup
import it.leiene.tv.data.IeneRepository
import it.leiene.tv.data.PlaybackProgressTracker
import it.leiene.tv.data.VideoReport
import it.leiene.tv.player.PlayerScreen
import it.leiene.tv.player.formatTime
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: IeneRepository
    private lateinit var progressTracker: PlaybackProgressTracker
    var onKeyEventListener: ((KeyEvent) -> Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (onKeyEventListener?.invoke(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = IeneRepository(this)
        progressTracker = PlaybackProgressTracker(this)

        setContent {
            var episodes by remember { mutableStateOf(emptyList<EpisodeGroup>()) }
            var isLoading by remember { mutableStateOf(true) }
            var isLoadingMore by remember { mutableStateOf(false) }
            var currentPage by remember { mutableIntStateOf(0) }

            var selectedReport by remember { mutableStateOf<VideoReport?>(null) }
            var activeMpdUrl by remember { mutableStateOf<String?>(null) }
            var startPositionMs by remember { mutableLongStateOf(0L) }
            var isResolvingMpd by remember { mutableStateOf(false) }

            // Resume Prompt Dialog State
            var pendingResumeReport by remember { mutableStateOf<VideoReport?>(null) }
            var pendingMpdUrl by remember { mutableStateOf<String?>(null) }
            var pendingSavedPos by remember { mutableLongStateOf(0L) }

            fun startPlayback(report: VideoReport, mpd: String, pos: Long) {
                selectedReport = report
                activeMpdUrl = mpd
                startPositionMs = pos
            }

            fun handleReportSelection(report: VideoReport, mpd: String) {
                val savedPos = progressTracker.getProgress(report.id)
                if (savedPos > 10_000L) {
                    pendingResumeReport = report
                    pendingMpdUrl = mpd
                    pendingSavedPos = savedPos
                } else {
                    startPlayback(report, mpd, 0L)
                }
            }

            fun loadInitial() {
                isLoading = true
                currentPage = 0
                lifecycleScope.launch {
                    val list = repository.getEpisodes(0)
                    episodes = list
                    isLoading = false
                }
            }

            fun loadNextPage() {
                if (isLoadingMore) return
                isLoadingMore = true
                val nextPage = currentPage + 1
                lifecycleScope.launch {
                    val newEpisodes = repository.getEpisodes(nextPage)
                    if (newEpisodes.isNotEmpty()) {
                        episodes = episodes + newEpisodes
                        currentPage = nextPage
                    }
                    isLoadingMore = false
                }
            }

            LaunchedEffect(Unit) {
                loadInitial()
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF090A0E),
                    surface = Color(0xFF13151D),
                    primary = Color(0xFFFFE600),
                    onPrimary = Color.Black
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF090A0E))
                ) {
                    if (selectedReport != null && activeMpdUrl != null) {
                        PlayerScreen(
                            report = selectedReport!!,
                            mpdUrl = activeMpdUrl!!,
                            initialPositionMs = startPositionMs,
                            onBack = {
                                selectedReport = null
                                activeMpdUrl = null
                                startPositionMs = 0L
                            },
                            registerKeyHandler = { handler ->
                                onKeyEventListener = handler
                            }
                        )
                    } else {
                        onKeyEventListener = null
                        HomeScreen(
                            episodes = episodes,
                            isLoading = isLoading,
                            isLoadingMore = isLoadingMore,
                            onLoadMore = {
                                loadNextPage()
                            },
                            onSelectReport = { report ->
                                if (!report.mpdUrl.isNullOrBlank()) {
                                    handleReportSelection(report, report.mpdUrl)
                                } else {
                                    isResolvingMpd = true
                                    lifecycleScope.launch {
                                        val detailed = repository.resolveReportDetails(report)
                                        isResolvingMpd = false
                                        if (!detailed.mpdUrl.isNullOrBlank()) {
                                            handleReportSelection(detailed, detailed.mpdUrl)
                                        } else {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Impossibile avviare lo streaming del servizio",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            onRefresh = {
                                loadInitial()
                            }
                        )
                    }

                    // RESUME PLAYBACK DIALOG MODAL (TV + Mobile)
                    if (pendingResumeReport != null && pendingMpdUrl != null) {
                        val report = pendingResumeReport!!
                        val mpd = pendingMpdUrl!!
                        val pos = pendingSavedPos
                        val resumeFocusRequester = remember { FocusRequester() }

                        LaunchedEffect(Unit) {
                            resumeFocusRequester.requestFocus()
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.82f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF161922),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF323A4A)),
                                modifier = Modifier
                                    .width(440.dp)
                                    .padding(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        color = Color(0xFFFFE600),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "RIPRENDI VISIONE",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = report.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 2
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Ti eri fermato a ${formatTime(pos)}.\nVuoi riprendere la riproduzione da dove avevi interrotto?",
                                        color = Color(0xFFD1D5DB),
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))

                                    // Resume Button
                                    var resumeFocused by remember { mutableStateOf(false) }
                                    Button(
                                        onClick = {
                                            pendingResumeReport = null
                                            pendingMpdUrl = null
                                            startPlayback(report, mpd, pos)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFFE600),
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (resumeFocused) 3.dp else 0.dp,
                                            color = Color.White
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .focusRequester(resumeFocusRequester)
                                            .onFocusChanged { resumeFocused = it.isFocused }
                                            .focusable()
                                    ) {
                                        Text("▶ RIPRENDI DA ${formatTime(pos)}", fontWeight = FontWeight.Black, fontSize = 14.sp)
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Restart from beginning
                                    var restartFocused by remember { mutableStateOf(false) }
                                    OutlinedButton(
                                        onClick = {
                                            progressTracker.clearProgress(report.id)
                                            pendingResumeReport = null
                                            pendingMpdUrl = null
                                            startPlayback(report, mpd, 0L)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            width = if (restartFocused) 2.5.dp else 1.dp,
                                            color = if (restartFocused) Color(0xFFFFE600) else Color(0xFF3F4758)
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (restartFocused) Color(0xFF262C3A) else Color.Transparent,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .onFocusChanged { restartFocused = it.isFocused }
                                            .focusable()
                                    ) {
                                        Text("↺ RICOMINCIA DALL'INIZIO", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Resolving overlay modal
                    if (isResolvingMpd) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E222B),
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = Color(0xFFFFE600))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Caricamento stream MPEG-DASH...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
