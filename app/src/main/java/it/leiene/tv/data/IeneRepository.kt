package it.leiene.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class IeneRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val guidPattern = Pattern.compile("programGuid=(F[0-9A-Z]{15})")
    private val mpdPattern = Pattern.compile("src=[\"'](https?://[^\\s\"']+\\.mpd)[\"']")
    private val descPattern = Pattern.compile("<meta\\s+name=[\"']description[\"']\\s+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

    suspend fun getEpisodes(page: Int = 0): List<EpisodeGroup> = withContext(Dispatchers.IO) {
        if (page == 0) {
            val cached = loadArchiveFromAssets()
            try {
                val live = fetchLiveEpisodesPage(0)
                if (live.isNotEmpty()) {
                    return@withContext live
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext cached
        } else {
            try {
                return@withContext fetchLiveEpisodesPage(page)
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext emptyList()
            }
        }
    }

    fun loadArchiveFromAssets(): List<EpisodeGroup> {
        val groups = mutableListOf<EpisodeGroup>()
        try {
            val jsonString = context.assets.open("servizi_iene_archive.json").bufferedReader().use { it.readText() }
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val epObj = array.getJSONObject(i)
                val epDate = epObj.optString("date", "Puntata")
                val vArray = epObj.getJSONArray("videos")
                val reports = mutableListOf<VideoReport>()

                for (j in 0 until vArray.length()) {
                    val obj = vArray.getJSONObject(j)
                    reports.add(
                        VideoReport(
                            id = obj.optString("id"),
                            title = obj.optString("title"),
                            description = obj.optString("description", ""),
                            inviato = obj.optString("inviato", "Le Iene"),
                            date = obj.optString("date"),
                            durationMin = obj.optInt("duration_min", 0),
                            imageUrl = obj.optString("image"),
                            pageUrl = obj.optString("url"),
                            guid = obj.optString("guid").takeIf { it.isNotBlank() },
                            mpdUrl = obj.optString("mpd").takeIf { it.isNotBlank() }
                        )
                    )
                }
                groups.add(EpisodeGroup(date = epDate, reports = reports))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return groups
    }

    private suspend fun fetchLiveEpisodesPage(page: Int): List<EpisodeGroup> {
        val url = if (page == 0) {
            "https://www.iene.mediaset.it/data/iene/episode/all.json"
        } else {
            "https://www.iene.mediaset.it/data/iene/episode/all.json?page=$page"
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return emptyList()

        val body = response.body?.string() ?: return emptyList()
        val root = JSONObject(body)
        val contentArray = root.optJSONArray("content") ?: return emptyList()

        val groups = mutableListOf<EpisodeGroup>()

        for (i in 0 until contentArray.length()) {
            val section = contentArray.getJSONObject(i)
            val videoArray = section.optJSONArray("videoList") ?: continue
            if (videoArray.length() == 0) continue

            val reports = mutableListOf<VideoReport>()
            val firstDate = videoArray.getJSONObject(0).optString("date", "Puntata")

            for (j in 0 until videoArray.length()) {
                val v = videoArray.getJSONObject(j)
                val characters = v.optJSONArray("characters")
                var inviato = "Le Iene"
                if (characters != null && characters.length() > 0) {
                    val lastName = characters.getJSONObject(0).optString("last_name")
                    if (lastName.isNotBlank()) {
                        inviato = lastName.replaceFirstChar { it.uppercase() }
                    }
                }

                reports.add(
                    VideoReport(
                        id = v.optString("id"),
                        title = v.optString("title"),
                        description = "",
                        inviato = inviato,
                        date = v.optString("date"),
                        durationMin = v.optInt("duration", 0),
                        imageUrl = v.optString("image"),
                        pageUrl = v.optString("url"),
                        guid = null,
                        mpdUrl = null
                    )
                )
            }

            if (reports.isNotEmpty()) {
                groups.add(EpisodeGroup(date = firstDate, reports = reports))
            }
        }
        return groups
    }

    suspend fun resolveReportDetails(report: VideoReport): VideoReport = withContext(Dispatchers.IO) {
        var guid = report.guid
        var description = report.description
        var mpdUrl = report.mpdUrl

        if (description.isBlank() || guid.isNullOrBlank()) {
            try {
                val pageReq = Request.Builder()
                    .url(report.pageUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    .build()
                val pageResp = client.newCall(pageReq).execute()
                val pageBody = pageResp.body?.string() ?: ""

                if (guid.isNullOrBlank()) {
                    val matcher = guidPattern.matcher(pageBody)
                    if (matcher.find()) {
                        guid = matcher.group(1)
                    }
                }

                if (description.isBlank()) {
                    val descMatcher = descPattern.matcher(pageBody)
                    if (descMatcher.find()) {
                        description = android.text.Html.fromHtml(descMatcher.group(1), android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (mpdUrl.isNullOrBlank() && !guid.isNullOrBlank()) {
            try {
                val smilUrl = "http://link.theplatform.eu/s/PR1GhC/media/guid/2702976343/$guid?mbr=true&formats=MPEG-DASH&assetTypes=geoNo:HD,browser,geoIT|geoNo:HD,geoIT|geoNo:SD,browser,geoIT|geoNo:SD,geoIT|geoNo|HD|SD&format=SMIL"
                val smilReq = Request.Builder()
                    .url(smilUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val smilResp = client.newCall(smilReq).execute()
                val smilBody = smilResp.body?.string() ?: ""

                val mpdMatcher = mpdPattern.matcher(smilBody)
                val candidates = mutableListOf<String>()
                while (mpdMatcher.find()) {
                    candidates.add(mpdMatcher.group(1))
                }

                for (c in candidates) {
                    if (c.contains("hr_no_mpl.mpd")) {
                        mpdUrl = c
                        break
                    }
                }
                if (mpdUrl == null && candidates.isNotEmpty()) {
                    mpdUrl = candidates.first()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext report.copy(
            guid = guid,
            description = description,
            mpdUrl = mpdUrl
        )
    }
}
