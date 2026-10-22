package it.leiene.tv.data

data class VideoReport(
    val id: String,
    val title: String,
    val description: String = "",
    val inviato: String,
    val date: String,
    val durationMin: Int,
    val imageUrl: String,
    val pageUrl: String,
    val guid: String? = null,
    val mpdUrl: String? = null
)

data class EpisodeGroup(
    val date: String,
    val reports: List<VideoReport>
)
