package eu.kanade.tachiyomi.extension.en.h5ai

import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class H5ai : AnimeHttpSource() {
    override val name = "h5ai (Custom)"
    override val baseUrl = "https://server5.ftpbd.net/FTP-5/Anime--Cartoon-TV-Series"
    override val lang = "en"
    override val supportsLatest = false

    override val client: OkHttpClient = network.client.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun urlWithJson(url: String): String {
        return if (url.contains("?")) "$url&json=true" else "$url?json=true"
    }

    // Popular Anime = list root folders (each folder is an "anime")
    override fun popularAnimeRequest(page: Int): Request = GET(baseUrl)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val jsonUrl = urlWithJson(baseUrl)
        val jsonResp = client.newCall(GET(jsonUrl)).execute()
        val jsonStr = jsonResp.body?.string() ?: ""
        val json = JSONObject(jsonStr)
        val children = json.optJSONArray("children")
        val animes = mutableListOf<SAnime>()

        if (children != null) {
            for (i in 0 until children.length()) {
                val item = children.getJSONObject(i)
                val name = item.optString("name")
                val type = item.optString("type")
                val href = item.optString("href")
                if (type == "dir") {
                    val anime = SAnime.create().apply {
                        title = name
                        url = href
                    }
                    animes.add(anime)
                }
            }
        }
        return AnimesPage(animes, false) // no pagination
    }

    override fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)
    override fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = popularAnimeRequest(page)
    override fun searchAnimeParse(response: Response): AnimesPage = popularAnimeParse(response)

    override fun animeDetailsParse(response: Response): SAnime {
        return SAnime.create().apply {
            title = response.request.url.encodedPath.substringAfterLast('/')
            description = "Folder from h5ai server."
        }
    }

    override fun episodeListRequest(anime: SAnime): Request {
        return GET(makeAbsolute(anime.url))
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val folderUrl = response.request.url.toString()
        val folderJsonUrl = urlWithJson(folderUrl)
        val jsonResp = client.newCall(GET(folderJsonUrl)).execute()
        val jsonStr = jsonResp.body?.string() ?: ""
        val json = JSONObject(jsonStr)
        val children = json.optJSONArray("children")
        val episodes = mutableListOf<SEpisode>()

        if (children != null) {
            for (i in 0 until children.length()) {
                val item = children.getJSONObject(i)
                val name = item.optString("name")
                val type = item.optString("type")
                val href = item.optString("href")
                if (type == "file") {
                    val ep = SEpisode.create().apply {
                        this.name = name
                        episode_number = i + 1f
                        url = href
                    }
                    episodes.add(ep)
                }
            }
        }
        return episodes
    }

    override fun videoListRequest(episode: SEpisode): Request {
        return GET(makeAbsolute(episode.url))
    }

    override fun videoListParse(response: Response): List<Video> {
        val fileUrl = response.request.url.toString()
        return listOf(Video(fileUrl, "Default", fileUrl))
    }

    override fun videoUrlParse(response: Response): String {
        return response.request.url.toString()
    }

    private fun makeAbsolute(href: String): String {
        return if (href.startsWith("http")) href
        else baseUrl.toHttpUrlOrNull()!!.newBuilder().encodedPath(href).build().toString()
    }
}
