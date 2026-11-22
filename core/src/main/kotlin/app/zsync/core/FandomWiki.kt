package app.zsync.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.net.URLEncoder

object FandomWiki {
    private const val API = "https://zenless-zone-zero.fandom.com/api.php"

    private fun get(params: String): String? = runCatching {
        val req = Request.Builder().url("$API?format=json&$params").header("User-Agent", "Z-Sync").build()
        Http.client.newCall(req).execute().use { r -> if (r.code == 200) r.body?.string() else null }
    }.getOrNull()

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private val files = java.util.concurrent.ConcurrentHashMap<String, String>()
    private const val BOSS_TTL = 6 * 3600_000L
    private const val MISS_TTL = 30 * 60_000L
    private var cycle: Triple<Long, Long, AssaultCycle?>? = null

    fun fileUrl(title: String): String? = files[title] ?: lookupFile(title)?.also { files[title] = it }

    private fun lookupFile(title: String): String? {
        val body = get("action=query&prop=imageinfo&iiprop=url&titles=${enc(title)}") ?: return null
        return runCatching {
            Json.parseToJsonElement(body).jsonObject["query"]!!.jsonObject["pages"]!!.jsonObject.values.firstOrNull()
                ?.jsonObject?.get("imageinfo")?.jsonArray?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
        }.getOrNull()
    }

    // HoYoLAB hides the cycle's bosses until they are played; the wiki lists them per cycle.
    @Synchronized
    /** Bosses of the cycle that started on [start] (UTC+8 date). A wiki page for an older cycle is never used. */
    fun assaultCycle(start: java.time.LocalDate?, now: Long = System.currentTimeMillis()): AssaultCycle? {
        if (start == null) return null
        cycle?.let { (at, ttl, v) -> if (now - at < ttl && v?.start == start) return v }
        return lookupCycle(start).also { cycle = Triple(now, if (it != null) BOSS_TTL else MISS_TTL, it) }
    }

    private fun lookupCycle(start: java.time.LocalDate): AssaultCycle? {
        val list = get("action=query&list=allpages&apprefix=${enc("Deadly Assault/20")}&aplimit=500") ?: return null
        // The wiki dates its pages by the cycle's start; allow a day either way for time zones.
        val page = runCatching {
            Json.parseToJsonElement(list).jsonObject["query"]!!.jsonObject["allpages"]!!.jsonArray
                .map { it.jsonObject["title"]!!.jsonPrimitive.content }
                .firstOrNull { t ->
                    val d = Regex("Deadly Assault/(\\d{4}-\\d{2}-\\d{2})").matchEntire(t)?.groupValues?.get(1)
                        ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return@firstOrNull false
                    kotlin.math.abs(d.toEpochDay() - start.toEpochDay()) <= 1
                }
        }.getOrNull() ?: return null
        val wt = get("action=parse&prop=wikitext&page=${enc(page)}") ?: return null
        val text = runCatching {
            Json.parseToJsonElement(wt).jsonObject["parse"]!!.jsonObject["wikitext"]!!.jsonObject["*"]!!.jsonPrimitive.content
        }.getOrNull() ?: return null
        val enemy = Regex("\\|\\s*enemy\\d*\\s*=\\s*([^\\n|}]+)")
        fun names(t: String) = enemy.findAll(t).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        val marker = Regex("=+\\s*Adversity Mode\\s*=+").find(text)
        val bosses = names(if (marker != null) text.substring(0, marker.range.first) else text).take(3)
        val adversity = marker?.let { names(text.substring(it.range.last + 1)).firstOrNull() }
        if (bosses.isEmpty() && adversity == null) return null
        return AssaultCycle(start, bosses, adversity)
    }
}

data class AssaultCycle(val start: java.time.LocalDate, val bosses: List<String>, val adversity: String?)
