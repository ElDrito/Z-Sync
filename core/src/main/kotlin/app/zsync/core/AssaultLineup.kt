package app.zsync.core

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Deadly Assault bosses from the official version update notice, which lists every phase of the version:
 * "Phase II (Trial): A, B, C" and "Phase II (Adversity): D". Phase I starts with the first reset after the update.
 */
object AssaultLineup {
    private const val API = "https://sg-announcement-static.hoyoverse.com/common/nap_global/announcement/api"
    private const val TTL = 6 * 3600_000L
    private val phaseLine = Regex("""Phase\s+(I{1,3}|IV|V|\d)\s*\((Trial|Adversity)\)\s*:\s*([^\n]+)""", RegexOption.IGNORE_CASE)
    private val tag = Regex("<[^>]+>")

    private class Notice(val release: LocalDate, val trial: Map<Int, List<String>>, val adversity: Map<Int, String>)
    private var cache: Triple<Long, String, Notice?>? = null

    /** Bosses for the cycle that started on [start] (UTC+8 date), or null when the notice doesn't cover it. */
    @Synchronized
    fun forCycle(role: Role, start: LocalDate?, now: Long = System.currentTimeMillis()): AssaultCycle? {
        if (start == null) return null
        val notice = cache?.takeIf { now - it.first < TTL && it.second == role.region }?.third
            ?: fetch(role).also { cache = Triple(now, role.region, it) }
            ?: return null
        val days = start.toEpochDay() - notice.release.toEpochDay()
        if (days < 0) return null
        val phase = (days / 14).toInt() + 1
        val bosses = notice.trial[phase] ?: return null
        return AssaultCycle(start, bosses, notice.adversity[phase])
    }

    private fun fetch(role: Role): Notice? = runCatching {
        val q = mapOf(
            "game" to "nap", "game_biz" to "nap_global", "lang" to "en", "bundle_id" to "nap_global",
            "platform" to "pc", "region" to role.region, "level" to "60", "uid" to role.uid,
        )
        val list = Http.request("GET", "$API/getAnnList", q).data ?: return null
        val update = list.arr("list").flatMap { it.arr("list") }
            .filter { Regex("""Version\s+[\d.]+.*Update Announcement""").containsMatchIn(it.str("title")?.replace(tag, "").orEmpty()) }
            .maxByOrNull { it.str("start_time").orEmpty() } ?: return null
        val release = LocalDateTime.parse(update.str("start_time")!!.replace(' ', 'T')).toLocalDate()
        val body = Http.request("GET", "$API/getAnnContent", q).data?.arr("list")
            ?.firstOrNull { it.int("ann_id") == update.int("ann_id") }?.str("content") ?: return null
        val text = body.replace(Regex("<br\\s*/?>|</p>|</li>|</div>", RegexOption.IGNORE_CASE), "\n").replace(tag, "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"")
        val trial = HashMap<Int, List<String>>()
        val adversity = HashMap<Int, String>()
        for (m in phaseLine.findAll(text)) {
            val n = roman(m.groupValues[1]) ?: continue
            val names = m.groupValues[3].split(',').map { it.trim().trimEnd('.', ';') }.filter { it.isNotEmpty() }
            if (m.groupValues[2].equals("Trial", true)) trial[n] = names else adversity[n] = names.joinToString(", ")
        }
        if (trial.isEmpty()) null else Notice(release, trial, adversity)
    }.getOrNull()

    private fun roman(s: String) = when (s.uppercase()) {
        "I", "1" -> 1; "II", "2" -> 2; "III", "3" -> 3; "IV", "4" -> 4; "V", "5" -> 5; else -> null
    }
}
