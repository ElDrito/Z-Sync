package app.zsync.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
data class G8Item(
    val name: String,
    val page: String,
    val image: String? = null,
    val weapon: Boolean = false,
    val specialty: Int? = null,
    val element: Int? = null,

    val rarity: String? = null,

    val wikiImage: String? = null,

    val release: Long? = null,
    val checkedAt: Long = 0,
)

@Serializable
data class Game8Upcoming(val version: String? = null, val items: List<G8Item> = emptyList(), val fetchedAt: Long = 0)

object Game8 {
    const val PAGE = "https://game8.co/games/Zenless-Zone-Zero/archives/435687"

    // Game8 answers 202 with an empty body to clients that do not look like a browser.
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"
    private const val DETAIL_RETRY_MS = 12 * 3600_000L

    private fun get(url: String): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "en-US,en;q=0.9").build()
        Http.client.newCall(req).execute().use { r -> if (r.code == 200) r.body?.string() else null }
    }.getOrNull()

    private fun text(html: String) = html.replace(Regex("<[^>]+>"), " ").replace("&#39;", "'").replace("&amp;", "&")
        .replace(Regex("\\s+"), " ").trim()

    fun fetch(prev: Game8Upcoming?): Game8Upcoming? {
        val html = get(PAGE) ?: return null
        val start = html.indexOf("ZZZ Upcoming Banners</h2>").takeIf { it >= 0 } ?: return Game8Upcoming(fetchedAt = System.currentTimeMillis())
        val end = html.indexOf("<h2", start).takeIf { it > 0 } ?: html.length
        val section = html.substring(start, end)

        val header = Regex("<th[^>]*>(.*?)</th>", RegexOption.DOT_MATCHES_ALL).find(section)?.groupValues?.get(1)?.let(::text)
        val version = header?.let { Regex("(\\d+\\.\\d+)").find(it)?.groupValues?.get(1) }

        val known = prev?.items.orEmpty().associateBy { it.page }
        val now = System.currentTimeMillis()
        val items = Regex("<a class='a-link' href=([^ >]+)>(.*?)</a>", RegexOption.DOT_MATCHES_ALL).findAll(section).mapNotNull { m ->
            val page = m.groupValues[1].trim('\'', '"')
            val inner = m.groupValues[2]
            val name = Regex("alt='ZZZ - ([^']+)'").find(inner)?.groupValues?.get(1)?.replace("&#39;", "'") ?: return@mapNotNull null
            val image = Regex("data-src='([^']+)'").find(inner)?.groupValues?.get(1)
            val old = known[page]
            val complete = old != null && old.specialty != null && old.rarity != null && (old.weapon || old.element != null) && old.release != null
            if (old != null && (complete || now - old.checkedAt < DETAIL_RETRY_MS)) old.copy(name = name, image = image ?: old.image)
            else details(G8Item(name, page, image), now)
        }.distinctBy { it.page }.toList()
        return Game8Upcoming(version, withWikiImages(items), now)
    }

    private fun field(html: String, label: String): String? =
        Regex("<t[hd][^>]*>\\s*(?:<[^>]+>\\s*)*$label\\s*(?:<[^>]+>\\s*)*</t[hd]>\\s*<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)?.let(::text)?.takeIf { it.isNotBlank() }

    private fun details(item: G8Item, now: Long): G8Item {
        val html = get(item.page) ?: return item.copy(checkedAt = now)
        val title = Regex("<title>([^<]*)").find(html)?.groupValues?.get(1).orEmpty()
        val release = field(html, "Release Date")?.let { d ->
            runCatching {
                SimpleDateFormat("MMMM d, yyyy", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(d)?.time
            }.getOrNull()
        }
        return item.copy(
            weapon = title.contains("W-Engine", ignoreCase = true),
            specialty = field(html, "Specialty")?.let(::specialtyId),
            element = field(html, "Attribute")?.let(::elementId),
            rarity = Regex("Rarity</th>.{0,800}?alt='([SAB]) Icon'", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1),
            release = release,
            checkedAt = now,
        )
    }

    private fun withWikiImages(items: List<G8Item>): List<G8Item> {
        fun file(it: G8Item) = (if (it.weapon) "File:W-Engine " else "File:Agent ") + it.name + " Icon.png"
        val missing = items.filter { it.wikiImage == null }
        if (missing.isEmpty()) return items
        val titles = missing.joinToString("|") { file(it) }
        val url = "https://zenless-zone-zero.fandom.com/api.php?action=query&prop=imageinfo&iiprop=url&format=json&titles=" +
            java.net.URLEncoder.encode(titles, "UTF-8")
        val found = HashMap<String, String>()
        runCatching {
            val body = get(url) ?: return items
            val root = kotlinx.serialization.json.Json.parseToJsonElement(body).jsonObject["query"]?.jsonObject ?: return items

            val norm = root["normalized"]?.jsonArray.orEmpty().associate {
                it.jsonObject["to"]!!.jsonPrimitive.content to it.jsonObject["from"]!!.jsonPrimitive.content
            }
            root["pages"]?.jsonObject?.values?.forEach { pg ->
                val o = pg.jsonObject
                val title = o["title"]?.jsonPrimitive?.content ?: return@forEach
                val img = o["imageinfo"]?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content ?: return@forEach
                found[norm[title] ?: title] = img
                found[title] = img
            }
        }
        return items.map { if (it.wikiImage == null) found[file(it)]?.let { u -> it.copy(wikiImage = u) } ?: it else it }
    }

    private fun specialtyId(s: String): Int? = when (s.lowercase().trim()) {
        "attack" -> 1; "stun" -> 2; "anomaly" -> 3; "support" -> 4; "defense" -> 5; "rupture" -> 6; "armorer" -> 7
        else -> null
    }

    private fun elementId(s: String): Int? = when (s.lowercase().trim()) {
        "physical", "honed edge" -> 200
        "fire" -> 201
        "ice", "frost" -> 202
        "electric" -> 203
        "wind" -> 204
        "ether", "auric ink" -> 205
        "lumiflux" -> 300
        else -> null
    }
}
