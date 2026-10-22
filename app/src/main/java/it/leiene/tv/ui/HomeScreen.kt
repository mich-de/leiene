package it.leiene.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import it.leiene.tv.R
import it.leiene.tv.data.EpisodeGroup
import it.leiene.tv.data.PlaybackProgressTracker
import it.leiene.tv.data.VideoReport

@Composable
fun HomeScreen(
    episodes: List<EpisodeGroup>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    progressVersion: Int,
    onReportFocused: (VideoReport) -> Unit,
    onLoadMore: () -> Unit,
    onSelectReport: (VideoReport) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val progressTracker = remember { PlaybackProgressTracker(context) }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    // Search field exists only on demand: an always-present TextField took focus at launch
    // and popped the TV keyboard over the catalog.
    var searchActive by remember { mutableStateOf(false) }
    val searchFieldFocusRequester = remember { FocusRequester() }
    val searchButtonFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }

    fun closeSearch() {
        searchQuery = ""
        searchActive = false
    }

    BackHandler(enabled = searchActive) {
        closeSearch()
        runCatching { searchButtonFocusRequester.requestFocus() }
    }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            withFrameNanos { }
            runCatching { searchFieldFocusRequester.requestFocus() }
        }
    }

    // Land on the first card at launch, not on a control
    LaunchedEffect(episodes.isNotEmpty()) {
        if (episodes.isNotEmpty() && !initialFocusDone) {
            repeat(10) {
                withFrameNanos { }
                if (runCatching { firstCardFocusRequester.requestFocus() }.isSuccess) {
                    initialFocusDone = true
                    return@LaunchedEffect
                }
            }
        }
    }

    val allReports = remember(episodes) {
        episodes.flatMap { it.reports }.distinctBy { it.id }
    }

    val searchResults = remember(searchQuery, allReports) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.trim().lowercase()
            allReports.filter {
                it.title.lowercase().contains(q) ||
                it.inviato.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.date.lowercase().contains(q)
            }
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            if (searchQuery.isNotBlank()) false
            else {
                val totalItems = listState.layoutInfo.totalItemsCount
                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleItem >= totalItems - 2
            }
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoading && !isLoadingMore) {
            onLoadMore()
        }
    }

    Scaffold(
        containerColor = Color(0xFF090A0E)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // TOP HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFE600), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "LE IENE",
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            letterSpacing = 1.2.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "ARCHIVIO INCHIESTE & PUNTATE",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Streaming DASH HD in tempo reale",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    var searchFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = { searchActive = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (searchFocused || searchActive) Color(0xFFFFE600) else Color(0xFF1E222D),
                            contentColor = if (searchFocused || searchActive) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (searchFocused) 2.dp else 1.dp,
                            color = if (searchFocused) Color.White else Color(0xFF323846)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .focusRequester(searchButtonFocusRequester)
                            .onFocusChanged { searchFocused = it.isFocused }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = null,
                            tint = if (searchFocused || searchActive) Color.Black else Color(0xFFFFE600),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CERCA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
    
                    var refreshFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (refreshFocused) Color(0xFFFFE600) else Color(0xFF1E222D),
                            contentColor = if (refreshFocused) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (refreshFocused) 2.dp else 1.dp,
                            color = if (refreshFocused) Color.White else Color(0xFF323846)
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .onFocusChanged { refreshFocused = it.isFocused }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_refresh),
                            contentDescription = "Aggiorna",
                            tint = if (refreshFocused) Color.Black else Color(0xFFFFE600),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AGGIORNA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // SEARCH BAR (on demand)
            if (searchActive) OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Cerca per titolo, inviato (Pelazza, Golia, Rei...), argomento o data...",
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = "Cerca",
                        tint = Color(0xFFFFE600),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = {
                        closeSearch()
                        runCatching { searchButtonFocusRequester.requestFocus() }
                    }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_clear),
                                contentDescription = "Cancella ricerca",
                                tint = Color(0xFFFFE600),
                                modifier = Modifier.size(18.dp)
                            )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                // Leave the field and jump into the results with the D-pad ready
                keyboardActions = KeyboardActions(onSearch = { focusManager.moveFocus(FocusDirection.Down) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF181B24),
                    unfocusedContainerColor = Color(0xFF111319),
                    focusedBorderColor = Color(0xFFFFE600),
                    unfocusedBorderColor = Color(0xFF262B37),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .focusRequester(searchFieldFocusRequester)
            )

            // CONTENT
            if (searchQuery.isNotBlank()) {
                Text(
                    text = "Risultati della ricerca per \"$searchQuery\" (${searchResults.size} servizi trovati)",
                    color = Color(0xFFFFE600),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Nessun servizio corrisponde ai criteri di ricerca",
                            color = Color(0xFF9CA3AF),
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 230.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(searchResults, key = { it.id }) { report ->
                            UniformReportCard(
                                report = report,
                                savedProgress = remember(report.id, progressVersion) { progressTracker.getProgress(report.id) },
                                onFocused = { onReportFocused(report) },
                                onClick = { onSelectReport(report) }
                            )
                        }
                    }
                }
            } else {
                if (isLoading && episodes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFFE600))
                    }
                } else if (episodes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nessuna puntata disponibile al momento", color = Color.White)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(episodes, key = { _, episode -> episode.date }) { index, episode ->
                            EpisodeRowSection(
                                episode = episode,
                                progressTracker = progressTracker,
                                progressVersion = progressVersion,
                                firstCardFocusRequester = if (index == 0) firstCardFocusRequester else null,
                                onReportFocused = onReportFocused,
                                onSelectReport = onSelectReport
                            )
                        }

                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            color = Color(0xFFFFE600),
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Caricamento altre puntate precedenti...",
                                            color = Color(0xFFD1D5DB),
                                            fontSize = 13.sp
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
}

@Composable
fun EpisodeRowSection(
    episode: EpisodeGroup,
    progressTracker: PlaybackProgressTracker,
    progressVersion: Int,
    firstCardFocusRequester: FocusRequester?,
    onReportFocused: (VideoReport) -> Unit,
    onSelectReport: (VideoReport) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .background(Color(0xFFFFE600), RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Puntata del ${episode.date}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "• ${episode.reports.size} servizi",
                color = Color(0xFFFFE600),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(episode.reports, key = { _, report -> report.id }) { index, report ->
                UniformReportCard(
                    report = report,
                    savedProgress = remember(report.id, progressVersion) { progressTracker.getProgress(report.id) },
                    onFocused = { onReportFocused(report) },
                    onClick = { onSelectReport(report) },
                    modifier = if (index == 0 && firstCardFocusRequester != null) {
                        Modifier.focusRequester(firstCardFocusRequester)
                    } else Modifier
                )
            }
        }
    }
}

/**
 * 100% Uniform Dimension Card (Pixel-Perfect Fixed Size: 230dp width × 235dp height)
 */
@Composable
fun UniformReportCard(
    report: VideoReport,
    savedProgress: Long,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "uniformCardScale"
    )

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF1E222E) else Color(0xFF13151D)
        ),
        modifier = modifier
            .width(230.dp)
            .height(235.dp) // Fixed height ensures 100% equal dimensions
            .scale(scale)
            .onFocusChanged {
                if (it.isFocused && !isFocused) onFocused()
                isFocused = it.isFocused
            }
            .border(
                width = if (isFocused) 3.5.dp else 1.dp,
                color = if (isFocused) Color(0xFFFFE600) else Color(0xFF232733),
                shape = RoundedCornerShape(10.dp)
            )
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // FIXED THUMBNAIL (125dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(125.dp)
            ) {
                AsyncImage(
                    model = report.imageUrl,
                    contentDescription = report.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark Bottom Vignette
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                )

                // Focus Play Icon (Overlay)
                if (isFocused) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFFFFE600), CircleShape)
                            .align(Alignment.Center)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_play),
                            contentDescription = "Riproduci",
                            tint = Color.Black,
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                // Inviato Tag TOP LEFT
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = report.inviato.uppercase(),
                        color = Color(0xFFFFE600),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Duration Badge BOTTOM RIGHT
                if (report.durationMin > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.5.dp)
                            .align(Alignment.BottomEnd)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_clock),
                            contentDescription = "Durata",
                            tint = Color(0xFFD1D5DB),
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${report.durationMin} MIN",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Resume progress indicator line along thumbnail bottom (Netflix style)
                if (savedProgress > 10_000L && report.durationMin > 0) {
                    val totalMs = report.durationMin * 60 * 1000L
                    val progressRatio = (savedProgress.toFloat() / totalMs.toFloat()).coerceIn(0.05f, 0.95f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(Color(0xFF374151))
                            .align(Alignment.BottomCenter)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressRatio)
                                .fillMaxHeight()
                                .background(Color(0xFFFFE600))
                        )
                    }
                }
            }

            // FIXED CONTENT BODY (110dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Release Date
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_calendar),
                            contentDescription = "Data uscita",
                            tint = Color(0xFFFFE600),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = report.date,
                            color = Color(0xFFFFE600),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Title: Exactly 2 lines
                    Text(
                        text = report.title,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Description: Exactly 2 lines
                Text(
                    text = if (report.description.isNotBlank()) report.description else "Servizio completo di ${report.inviato}",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    lineHeight = 14.5.sp,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
