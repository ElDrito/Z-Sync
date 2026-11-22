package app.zsync.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Serializable data class EventReward(val name: String, val icon: String? = null)

@Serializable data class GameEvent(
    val name: String, val endAt: Long, val monoTotal: Int, val monoGot: Int,
    val started: Boolean = true, val startAt: Long = 0,
    val ended: Boolean = false,
    val rewardName: String? = null, val rewardIcon: String? = null,
    val bannerUrl: String? = null, val kind: String? = null, val loginDays: Int? = null,
    val extras: List<EventReward> = emptyList(),
)

@Serializable data class GachaItem(
    val name: String, val icon: String? = null,

    val specialty: Int? = null, val element: Int? = null, val subElement: Int? = null,

    val rarity: String? = null,
)

@Serializable data class GachaBanner(
    val kind: String, val names: List<String> = emptyList(), val icons: List<String> = emptyList(), val endAt: Long,
    val pullsToS: Int? = null, val items: List<GachaItem> = emptyList(),

    val agent: Boolean = false,

    val version: String? = null,

    val startAt: Long = 0,
)

@Serializable data class Challenge(
    val name: String, val endAt: Long? = null, val played: Boolean = true,
    val rating: String? = null, val score: Int? = null,

    val rooms: List<ChallengeRoom> = emptyList(),

    val rankPercent: String? = null,
)

@Serializable data class ChallengeRoom(
    val name: String, val score: Int, val rating: String? = null, val stars: Int? = null,
    val maxScore: Int? = null,
    val adversity: Boolean = false,
)
@Serializable data class LedgerLine(val name: String, val value: String, val icon: String? = null, val icon2: String? = null)
@Serializable data class Ledger(val month: String, val totals: List<LedgerLine>, val sources: List<LedgerLine>)
@Serializable data class CheckInCalendar(val signedToday: Boolean, val totalDays: Int, val missedDays: Int, val todayReward: String?, val nextReward: String?, val nextIcon: String? = null, val nextCount: Int? = null)

/** The player's in-game profile: icon, Inter-Knot card, title and shown medals. */
@Serializable data class ProxyCard(
    val headIcon: String? = null, val cardUrl: String? = null,
    val title: String? = null, val titleColor: String? = null, val titleBgColor: String? = null, val titleBgUrl: String? = null,
    val medals: List<String> = emptyList(),
)

@Serializable
data class Extras(
    val fetchedAt: Long = 0,
    val tickets: List<LedgerLine>? = null,
    val gacha: List<GachaBanner>? = null,

    val gachaNext: List<GachaBanner>? = null,

    val game8: Game8Upcoming? = null,
    val events: List<GameEvent>? = null,
    val challenges: List<Challenge>? = null,

    val mimo: MimoInfo? = null,

    val ledger: Ledger? = null,
    val checkIn: CheckInCalendar? = null,

    val itemIcons: Map<String, String> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),

    val schema: Int = 0,

    val partAt: Map<String, Long> = emptyMap(),
    val pity: Map<String, Int> = emptyMap(),
    val proxy: ProxyCard? = null,
)

object ExtrasTtl {
    private const val MIN = 60_000L
    const val CURRENCY = 10 * MIN
    const val LEDGER = 10 * MIN
    const val CHECKIN = 10 * MIN
    const val MIMO = 10 * MIN
    const val EVENTS = 30 * MIN
    const val CHALLENGES = 30 * MIN
    const val GACHA = 3 * 60 * MIN
    const val GAME8 = 6 * 60 * MIN
    const val PROXY = 30 * MIN
}

const val EXTRAS_SCHEMA = 15

private fun adversity(d: JsonObject, endAt: Long?, prev: Challenge?, wiki: () -> AssaultCycle?): Challenge {
    fun norm(n: String) = n.lowercase().filter { it.isLetterOrDigit() }
    // The official lineup names this cycle's boss whether or not Adversity Mode is unlocked yet.
    val official = wiki()?.adversity
    fun current(name: String) = official == null || norm(name).let { it.contains(norm(official)) || norm(official).contains(it) }
    val room = d.arr("hard_list").firstOrNull()?.let { r ->
        val name = r.arr("boss").firstOrNull()?.str("name") ?: return@let null
        ChallengeRoom(name, r.int("score") ?: 0, stars = r.int("star"), adversity = true)
    }?.takeIf { current(it.name) }   // a record from a previous cycle is ignored
    if (room != null && room.score > 0) {
        val s = room.stars ?: 0
        val rating = when { s >= 3 -> "S"; s == 2 -> "A"; s == 1 -> "B"; else -> "X" }
        return Challenge(
            "Adversity Mode", endAt, played = true, rating = rating, score = room.score, rooms = listOf(room),
            rankPercent = d.int("hard_rank_percent")?.takeIf { it > 0 }?.let { percent(it) },
        )
    }

    val known = prev?.takeIf { it.endAt == endAt }?.rooms?.firstOrNull()?.name?.takeIf { it != "Adversity Boss" && current(it) }
    val boss = official ?: room?.name ?: known
    return Challenge(
        "Adversity Mode", endAt, played = false, rating = "X",
        rooms = listOf(ChallengeRoom(boss ?: "Adversity Boss", 0, stars = 0, adversity = true)),
    )
}

private fun percent(v: Int) = java.math.BigDecimal(v).movePointLeft(2).stripTrailingZeros().toPlainString() + "%"

object ItemIcon {
    const val POLYCHROME = "polychrome"
    const val MONOCHROME = "monochrome"
    const val ENCRYPTED_TAPE = "encrypted"
    const val MASTER_TAPE = "master"
    const val BOOPON = "boopon"
}

class ExtrasApi(private val language: () -> String) {
    private val base = "https://sg-act-nap-api.hoyolab.com/event/game_record_zzz/api/zzz"

    private fun get(acc: Account, url: String, query: Map<String, String>): Resp {
        val lang = language()
        return Http.request(
            "GET", url, query,
            Http.labHeaders(lang) + mapOf("x-rpc-lang" to lang, "Cookie" to acc.baseCookie, "DS" to Http.ds()),
        )
    }

    private fun byRole(role: Role) = mapOf("role_id" to role.uid, "server" to role.region)
    private fun byUid(role: Role) = mapOf("uid" to role.uid, "region" to role.region)

    private fun time(e: JsonElement?, role: Role): Long? = when (e) {
        is JsonPrimitive -> (e.longOrNull ?: e.content.toLongOrNull())?.takeIf { it > 0 }?.let { it * 1000 }
        is JsonObject -> runCatching {
            LocalDateTime.of(e.int("year")!!, e.int("month")!!, e.int("day")!!, e.int("hour") ?: 0, e.int("minute") ?: 0, e.int("second") ?: 0)
                .toInstant(ZoneOffset.ofHours(regionUtcOffset(role.region))).toEpochMilli()
        }.getOrNull()
        else -> null
    }

    private fun endAt(o: JsonObject, role: Role): Long =
        o.long("left_end_ts")?.takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 1000 } ?: time(o["end_ts"], role) ?: 0

    private fun startAt(o: JsonObject, role: Role): Long =
        o.long("left_start_ts")?.takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 1000 } ?: time(o["start_ts"], role) ?: 0

    private val htmlTag = Regex("<[^>]+>")
    private val skippedNotice = Regex("signal search|channel|banner", RegexOption.IGNORE_CASE)
    private val quoted = Regex("\"([^\"]+)\"")
    private val noticeTags = setOf("活动公告", "福利活动")

    /** Events from the official in-game announcements (no login needed). Gacha banners are left out. */
    private fun announcedEvents(role: Role, icons: Map<String, String> = emptyMap()): List<GameEvent> {
        val q = mapOf(
            "game" to "nap", "game_biz" to "nap_global", "lang" to "en", "bundle_id" to "nap_global",
            "platform" to "pc", "region" to role.region, "level" to "60", "uid" to role.uid,
        )
        val api = "https://sg-announcement-static.hoyoverse.com/common/nap_global/announcement/api"
        val r = Http.request("GET", "$api/getAnnList", q)
        val d = r.data?.takeIf { r.ok } ?: return emptyList()
        val zone = ZoneOffset.ofHours(d.int("timezone") ?: regionUtcOffset(role.region))
        fun at(t: String?) = runCatching { LocalDateTime.parse(t!!.replace(' ', 'T')).toInstant(zone).toEpochMilli() }.getOrNull()
        val notices = d.arr("list").flatMap { g -> g.arr("list").filter { it.str("tag_label") in noticeTags || g.int("type_id") == 4 } }
        val now = System.currentTimeMillis()
        val bodies = runCatching {
            Http.request("GET", "$api/getAnnContent", q).data?.arr("list")?.associate { (it.int("ann_id") ?: 0) to (it.str("content") ?: "") }
        }.getOrNull().orEmpty()
        val wikiStart = System.currentTimeMillis()
        return notices.mapNotNull { a ->
            val title = (a.str("title") ?: return@mapNotNull null).replace(htmlTag, "")
                .replace("&quot;", "\"").replace("&amp;", "&").replace("&#39;", "'").trim()
            if (skippedNotice.containsMatchIn(title)) return@mapNotNull null
            val name = quoted.find(title)?.groupValues?.get(1)
                ?: title.removeSuffix(" Event Details").removeSuffix(" Details").trim()
            val start = at(a.str("start_time")) ?: return@mapNotNull null
            val end = at(a.str("end_time")) ?: return@mapNotNull null
            if (end - start > 120L * 24 * 3600 * 1000) return@mapNotNull null
            val body = bodies[a.int("ann_id") ?: 0]
            val info = body?.let { EventText.parse(title, it) }
            val banner = a.str("banner")?.takeIf { it.startsWith("http") }
            val icon = info?.reward?.let { rewardIcon(it, icons, wikiStart) }
            GameEvent(
                name, end, 0, 0, started = start <= now, startAt = start, ended = end <= now,
                rewardName = info?.reward, rewardIcon = icon, bannerUrl = banner, kind = info?.kind, loginDays = info?.loginDays,
                extras = info?.extras.orEmpty().map { EventReward(it, rewardIcon(it, icons, wikiStart)) },
            )
        }.distinctBy { it.name.lowercase() }
    }

    /** Titles and cover images of ZZZ's official HoYoLAB posts (notices and web events). */
    private fun newsCovers(): List<Pair<String, String>> = listOf("1", "2").flatMap { type ->
        val r = Http.request(
            "GET", "https://bbs-api-os.hoyolab.com/community/post/wapi/getNewsList",
            mapOf("gids" to "8", "page_size" to "30", "type" to type), mapOf("x-rpc-language" to "en-us"),
        )
        r.data?.takeIf { r.ok }?.arr("list").orEmpty().mapNotNull { p ->
            val subject = p.obj("post")?.str("subject") ?: return@mapNotNull null
            val cover = p.arr("cover_list").firstOrNull()?.str("url")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            subject to cover
        }
    }

    private fun rewardIcon(reward: String, icons: Map<String, String>, since: Long): String? {
        EventText.iconKey(reward)?.let { k -> icons[k]?.let { return it } }
        if (System.currentTimeMillis() - since > 10_000) return null
        val base = EventText.baseName(reward)
        return wikiIcon(base) ?: wikiIcon(base.removeSuffix("s")) ?: FandomWiki.fileUrl("File:Item $base.png")
    }

    private val ticketNames = linkedMapOf(
        "GACHA_TICKET_TYPE_POLYCHROME" to "Polychromes",
        "GACHA_TICKET_TYPE_RECHARGE_MONOCHROME" to "Monochromes",
        "GACHA_TICKET_TYPE_ENCRYPTED_MASTER_TAPE" to "Encrypted Master Tapes",
        "GACHA_TICKET_TYPE_MASTER_TAPE" to "Master Tapes",
        "GACHA_TICKET_TYPE_BOOPON" to "Boopons",
    )

    private val ticketEnglish = mapOf(
        "GACHA_TICKET_TYPE_POLYCHROME" to "Polychrome",
        "GACHA_TICKET_TYPE_RECHARGE_MONOCHROME" to "Monochrome",
        "GACHA_TICKET_TYPE_ENCRYPTED_MASTER_TAPE" to "Encrypted Master Tape",
        "GACHA_TICKET_TYPE_MASTER_TAPE" to "Master Tape",
        "GACHA_TICKET_TYPE_BOOPON" to "Boopon",
    )
    private val ticketIconKey = mapOf(
        "GACHA_TICKET_TYPE_POLYCHROME" to ItemIcon.POLYCHROME,
        "GACHA_TICKET_TYPE_RECHARGE_MONOCHROME" to ItemIcon.MONOCHROME,
        "GACHA_TICKET_TYPE_ENCRYPTED_MASTER_TAPE" to ItemIcon.ENCRYPTED_TAPE,
        "GACHA_TICKET_TYPE_MASTER_TAPE" to ItemIcon.MASTER_TAPE,
        "GACHA_TICKET_TYPE_BOOPON" to ItemIcon.BOOPON,
    )

    private fun resolveIcons(role: Role, prev: Map<String, String>): Map<String, String> {
        val out = HashMap(prev)
        out[ItemIcon.MONOCHROME] = "res:monochrome"
        val missing = ticketIconKey.filterValues { it !in out }
        if (missing.isEmpty()) return out
        val awards = awardIcons(role)
        val start = System.currentTimeMillis()
        for ((type, key) in missing) {
            val english = ticketEnglish[type] ?: continue
            val url = awards[norm(english)] ?: if (System.currentTimeMillis() - start < 10_000) wikiIcon(english) else null
            if (url != null) out[key] = url
        }
        return out
    }

    private fun norm(n: String) = n.trim().lowercase().removeSuffix("s")

    private val awardsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<JsonObject>>>()
    private val wikiIcons = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun signAwards(g: Game): List<JsonObject> {
        val hit = awardsCache[g.name]
        if (hit != null && System.currentTimeMillis() - hit.first < 30 * 60_000L) return hit.second
        val r = Http.request(
            "GET", "${g.signBase}/home", mapOf("act_id" to g.actId, "lang" to "en-us"),
            Http.labHeaders("en-us") + mapOf("x-rpc-signgame" to (g.signGame ?: "")),
        )
        val list = r.data?.arr("awards").orEmpty()
        if (list.isNotEmpty()) awardsCache[g.name] = System.currentTimeMillis() to list
        return list
    }

    private fun awardIcons(role: Role): Map<String, String> = runCatching {
        signAwards(role.game).mapNotNull { a ->
            val n = a.str("name") ?: return@mapNotNull null
            val i = a.str("icon")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            norm(n) to i
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun wikiIcon(name: String): String? = wikiIcons[name] ?: runCatching {
        val r = Http.request(
            "GET", "https://sg-wiki-api.hoyolab.com/hoyowiki/zzz/wapi/search", mapOf("keyword" to name),
            Http.labHeaders("en-us") + mapOf("x-rpc-wiki_app" to "zzz", "x-rpc-language" to "en-us", "Referer" to "https://wiki.hoyolab.com/"),
        )
        r.data?.arr("list").orEmpty().firstOrNull { it.str("name").equals(name, ignoreCase = true) }
            ?.str("icon_url")?.takeIf { it.startsWith("http") }
    }.getOrNull()?.also { wikiIcons[name] = it }

    private val bannerNames = mapOf(
        "GACHA_TYPE_CHARACTER_UP" to "Exclusive Agent",
        "GACHA_TYPE_WEAPON_UP" to "Exclusive W-Engine",
        "GACHA_TYPE_PERMANENT" to "Standard",
        "GACHA_TYPE_BANGBOO" to "Bangboo",
        "GACHA_TYPE_CHARACTER_RETURN" to "Agent Rerun",
        "GACHA_TYPE_WEAPON_RETURN" to "W-Engine Rerun",
    )
    private val promoTypes = setOf("GACHA_TYPE_CHARACTER_UP", "GACHA_TYPE_CHARACTER_RETURN", "GACHA_TYPE_WEAPON_UP", "GACHA_TYPE_WEAPON_RETURN")
    private val incomeNames = mapOf(
        "daily_activity_rewards" to "Daily Activity", "growth_rewards" to "Progress",
        "shiyu_rewards" to "Shiyu Defense", "mail_rewards" to "Mail",
        "hollow_rewards" to "Hollows", "event_rewards" to "Events", "other_rewards" to "Other",
    )

    /** Refetches only the parts older than their TTL (all of them when [force]); the rest is carried over from [prev]. */
    fun fetch(acc: Account, role: Role, s: Settings, prev: Extras?, force: Boolean = false): Pair<Extras, Boolean> {
        val start = System.currentTimeMillis()
        var x = prev ?: Extras()
        val errors = HashMap(x.errors)
        val times = HashMap(x.partAt)
        var expired = false
        var fetched = false

        fun fresh(key: String, ttl: Long) = !force && key !in errors && start - (times[key] ?: 0L) < ttl

        fun <T> part(key: String, enabled: Boolean, ttl: Long, res: () -> Resp, parse: (JsonObject) -> T?, store: (T?) -> Unit) {
            if (!enabled) { store(null); times.remove(key); errors.remove(key); return }
            if (ttl > 0 && fresh(key, ttl)) return
            fetched = true
            errors.remove(key)
            val r = runCatching(res).getOrElse { Resp(-1, null, emptyMap()) }
            if (r.status == 200 && Errors.isExpiredLogin(r.retcode, r.message)) expired = true
            val d = r.data
            if (!r.ok || d == null) { errors[key] = r.reason(); return }
            val v = runCatching { parse(d) }.getOrNull()
            if (v == null) errors[key] = "Unexpected response" else { store(v); times[key] = start }
        }

        val icons = runCatching { resolveIcons(role, x.itemIcons) }.getOrElse { x.itemIcons }
        x = x.copy(itemIcons = icons)

        part("proxy", s.showProfile, ExtrasTtl.PROXY, { get(acc, "$base/index", byRole(role)) }, { d ->
            val g = d.obj("game_data_show")
            fun url(v: String?) = v?.takeIf { it.startsWith("http") }
            ProxyCard(
                headIcon = url(d.str("cur_head_icon_url")), cardUrl = url(g?.str("card_url")),
                title = g?.str("personal_title")?.takeIf { it.isNotBlank() },
                titleColor = g?.str("title_main_color"), titleBgColor = g?.str("title_bottom_color"), titleBgUrl = url(g?.str("title_bg_url")),
                // The badges the player picked to show (up to 3); medal_list is the fallback for older responses.
                medals = g?.arr("all_medal_list").orEmpty().filter { it.bool("is_show") == true }.mapNotNull { url(it.str("medal_icon")) }
                    .ifEmpty { (g?.get("medal_list") as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content?.let(::url) } }
                    .take(3),
            )
        }) { x = x.copy(proxy = it) }

        var pity: Map<String, Int> = x.pity
        part("currency", s.showCurrency || s.showGacha, ExtrasTtl.CURRENCY, { get(acc, "$base/cur_gacha_detail", byUid(role)) }, { d ->
            val t = d.arr("tickets").associate { (it.str("ticket_type") ?: "") to (it.int("ticket_cnt") ?: 0) }
            val tickets = ticketNames.mapNotNull { (k, name) -> t[k]?.let { LedgerLine(name, it.toString(), icons[ticketIconKey[k]]) } }
            tickets to d.arr("gacha_info_list").mapNotNull { g -> (g.str("gacha_type") ?: return@mapNotNull null) to (g.int("more_s_need_cnt") ?: return@mapNotNull null) }.toMap()
        }) { v ->
            x = x.copy(tickets = v?.first?.takeIf { s.showCurrency })
            pity = v?.second.orEmpty()
        }
        if (!s.showCurrency) x = x.copy(tickets = null)

        part("events", s.showEvents, ExtrasTtl.EVENTS, { get(acc, "$base/activity_calendar", byUid(role)) }, { d ->
            val calendar = d.arr("activity_list").mapNotNull {
                val state = it.str("state")
                if (state !in setOf("STATE_IN_PROGRESS", "STATE_NOT_START", "STATE_COMPLETED")) return@mapNotNull null
                GameEvent(
                    it.str("name").orEmpty(), endAt(it, role), it.int("monochrome_cnt") ?: 0, it.int("monochrome_got_cnt") ?: 0,
                    started = state != "STATE_NOT_START", startAt = startAt(it, role), ended = state == "STATE_COMPLETED",
                )
            }
            fun key(n: String) = n.lowercase().filter { it.isLetterOrDigit() }
            val known = calendar.map { key(it.name) }
            val announced = announcedEvents(role, icons)
            fun same(a: String, b: String) = a.isNotEmpty() && b.isNotEmpty() && (a in b || b in a)
            val merged = calendar.map { c ->
                val a = announced.firstOrNull { same(key(c.name), key(it.name)) } ?: return@map c
                c.copy(rewardName = a.rewardName, rewardIcon = a.rewardIcon, bannerUrl = a.bannerUrl, kind = a.kind, loginDays = a.loginDays, extras = a.extras)
            }
            val extra = announced.filter { a -> known.none { k -> same(k, key(a.name)) } }
            // Web and prize events have no in-game notice: HoYoLAB's official posts carry their artwork.
            val posts by lazy { newsCovers() }
            fun withCover(e: GameEvent) = if (e.bannerUrl != null || e.ended) e
                else posts.firstOrNull { (t, _) -> same(key(e.name), key(t)) }?.let { e.copy(bannerUrl = it.second) } ?: e
            // Ended events keep only their text: no images are kept around once an event is over.
            (merged + extra).map(::withCover).map { if (it.ended) it.copy(rewardIcon = null, bannerUrl = null, extras = it.extras.map { r -> r.copy(icon = null) }) else it }.sortedWith(compareBy({ if (it.ended) 2 else if (it.started) 1 else 0 }, { if (!it.started) it.startAt else if (!it.ended) it.endAt else -it.endAt }))
        }) { x = x.copy(events = it) }

        part("gacha", s.showGacha, ExtrasTtl.GACHA, { get(acc, "$base/gacha_calendar", byUid(role)) }, { d ->

            val list = (d.arr("avatar_gacha_schedule_list") + d.arr("weapon_gacha_schedule_list"))
                .filter { it.str("gacha_state") == "GACHA_STATE_IN_PROGRESS" || it.str("gacha_state") == "GACHA_STATE_NOT_START" }
                .mapNotNull { b ->
                    val type = b.str("gacha_type").orEmpty()
                    if (type !in promoTypes) return@mapNotNull null
                    val all = b.arr("avatar_list").ifEmpty { b.arr("weapon_list") }
                    val items = all.filter { it.str("rarity") == "S" }.ifEmpty { all }
                    GachaBanner(
                        kind = bannerNames[type] ?: "Banner",
                        agent = type.startsWith("GACHA_TYPE_CHARACTER"),
                        items = items.mapNotNull { i ->
                            val n = i.str("avatar_name") ?: i.str("weapon_name") ?: i.str("name") ?: i.str("talent_title") ?: return@mapNotNull null
                            GachaItem(
                                n, i.str("icon")?.takeIf { u -> u.startsWith("http") },
                                specialty = i.int("avatar_profession") ?: i.int("profession"),
                                element = i.int("avatar_element_type"), subElement = i.int("avatar_sub_element_type"),
                            )
                        },
                        endAt = endAt(b, role),
                        version = b.str("version")?.takeIf { v -> v.isNotBlank() },
                        startAt = startAt(b, role),
                        pullsToS = pity[type] ?: pity[type.replace("_RETURN", "_UP")],
                    )
                        .let { g -> g to (b.str("gacha_state") != "GACHA_STATE_NOT_START") }
                }
            val current = list.filter { it.second }.map { it.first }.sortedBy { if (it.agent) 0 else 1 }
            val next = list.filterNot { it.second }.map { it.first }.sortedWith(compareBy({ it.startAt }, { if (it.agent) 0 else 1 }))
            current to next
        }) { x = x.copy(gacha = it?.first, gachaNext = it?.second) }

        if (!s.optChallenges) { x = x.copy(challenges = null); times.remove("challenges") }
        else if (!fresh("challenges", ExtrasTtl.CHALLENGES)) {
            val list = ArrayList<Challenge>()
            part("shiyu", true, 0, { get(acc, "$base/hadal_info_v2", byRole(role) + ("schedule_type" to "1")) }, { d ->
                val v2 = d.obj("hadal_info_v2")
                val v1 = d.obj("hadal_info_v1")
                when {
                    v2 != null -> {
                        // "fitfh" is the API's own typo. Only Node 5 counts; its rating is the lowest of its three rooms.
                        val rooms = (v2.obj("fitfh_layer_detail") ?: v2.obj("fifth_layer_detail"))?.arr("layer_challenge_info_list").orEmpty()
                        val order = listOf("B", "A", "S")
                        val rating = rooms.mapNotNull { it.str("rating")?.takeIf { r -> r in order } }.minByOrNull { order.indexOf(it) }
                        val score = rooms.sumOf { it.int("score") ?: 0 }
                        val played = rooms.isNotEmpty() && score > 0
                        Challenge(
                            "Shiyu Defense",
                            time(v2["hadal_end_time"], role), played = played,
                            rating = if (played) rating else "X", score = score.takeIf { played },
                            rooms = (0 until maxOf(3, rooms.size)).map { i ->
                                val r = rooms.getOrNull(i)
                                ChallengeRoom(
                                    "Room ${i + 1}", r?.int("score") ?: 0, rating = r?.str("rating"),
                                    maxScore = r?.int("max_score"),
                                )
                            },
                            rankPercent = v2.obj("brief")?.int("rank_percent")?.takeIf { it > 0 }?.let { percent(it) },
                        )
                    }
                    v1 != null -> {
                        val has = v1.bool("has_data") == true
                        Challenge(
                            "Shiyu Defense",
                            time(v1["hadal_end_time"], role), played = has, rating = if (has) null else "X",
                            rooms = (1..3).map { ChallengeRoom("Room $it", 0) },
                        )
                    }
                    else -> null
                }
            }) { it?.let(list::add) }
            part("assault", true, 0, { get(acc, "$base/hadal_mem_detail_v2", byUid(role) + ("schedule_type" to "1")) }, { d ->
                val stars = d.int("total_star") ?: 0
                val score = d.int("total_score") ?: 0
                val played = d.bool("has_data") == true && stars > 0

                val rating = when { stars >= 9 -> "S+"; stars >= 7 -> "S"; stars >= 4 -> "A"; stars >= 1 -> "B"; else -> null }
                val endAt = time(d["end_time"], role)
                // Cycles last two weeks. Unfought bosses come from the official update notice, then the wiki, for this cycle only.
                val startAt = time(d["start_time"], role) ?: endAt?.minus(14L * 24 * 3600 * 1000)
                val cycleStart = startAt?.let { java.time.Instant.ofEpochMilli(it).atOffset(ZoneOffset.ofHours(8)).toLocalDate() }
                val wiki by lazy { AssaultLineup.forCycle(role, cycleStart) ?: FandomWiki.assaultCycle(cycleStart) }
                // Rooms come from what the player fought, matched by boss name; bosses not fought yet are added after.
                fun norm(n: String) = n.lowercase().filter { it.isLetterOrDigit() }
                val fought = d.arr("list").mapNotNull { r ->
                    val name = r.arr("boss").firstOrNull()?.str("name") ?: return@mapNotNull null
                    ChallengeRoom(name, r.int("score") ?: 0, stars = r.int("star") ?: 0, maxScore = d.int("room_max_score")?.takeIf { it > 0 })
                }
                val pending = if (fought.size >= 3) emptyList() else wiki?.bosses.orEmpty()
                    .filter { b -> fought.none { f -> norm(f.name).let { it.contains(norm(b)) || norm(b).contains(it) } } }
                    .map { ChallengeRoom(it, 0, stars = 0, maxScore = d.int("room_max_score")?.takeIf { m -> m > 0 }) }
                val order = if (fought.size >= 3) emptyList() else wiki?.bosses.orEmpty().map(::norm)
                fun rank(r: ChallengeRoom) = order.indexOfFirst { o -> norm(r.name).let { it.contains(o) || o.contains(it) } }.let { if (it < 0) 99 else it }
                val rooms = (fought + pending).sortedBy(::rank).take(3).let { r ->
                    r + (r.size until 3).map { ChallengeRoom("Unknown Boss", 0, stars = 0) }
                }
                listOf(Challenge(
                    "Deadly Assault",
                    endAt, played = played,
                    rating = if (played) rating else "X", score = score.takeIf { played },
                    rooms = rooms,
                    rankPercent = d.int("rank_percent")?.takeIf { it > 0 }?.let { percent(it) },
                ), adversity(d, endAt, prev?.challenges?.firstOrNull { it.name == "Adversity Mode" }) { wiki })
            }) { it?.let(list::addAll) }
            if (list.isNotEmpty()) x = x.copy(challenges = list)
            if ("shiyu" !in errors && "assault" !in errors) times["challenges"] = start
        }

        if (!s.showMimo) { x = x.copy(mimo = null); times.remove("mimo") }
        else if (!fresh("mimo", ExtrasTtl.MIMO)) {
            fetched = true
            val m = runCatching { Mimo { API_LANG }.status(acc, role) }.getOrNull()
            x = x.copy(mimo = m ?: x.mimo)
            if (m != null) times["mimo"] = start
        }

        part("ledger", s.showLedger, ExtrasTtl.LEDGER, {
            get(acc, "https://sg-public-api.hoyolab.com/event/nap_ledger/month_info", byUid(role) + ("month" to ""))
        }, { d ->
            val md = d.obj("month_data") ?: return@part null
            Ledger(
                month = d.str("data_month") ?: d.str("current_month").orEmpty(),
                totals = md.arr("list").map {
                    val (i1, i2) = when (it.str("data_type")) {
                        "PolychromesData" -> icons[ItemIcon.POLYCHROME] to null
                        "MatserTapeData" -> icons[ItemIcon.ENCRYPTED_TAPE] to icons[ItemIcon.MASTER_TAPE]
                        "BooponsData" -> icons[ItemIcon.BOOPON] to null
                        else -> null to null
                    }
                    LedgerLine(it.str("data_name") ?: it.str("data_type").orEmpty(), (it.int("count") ?: 0).toString(), i1, i2)
                },
                sources = md.arr("income_components").filter { (it.int("num") ?: 0) > 0 && it.str("action") != "mail_rewards" }.sortedByDescending { it.int("num") ?: 0 }.map {
                    LedgerLine(incomeNames[it.str("action")] ?: it.str("action").orEmpty(), "${it.int("num")} (${it.int("percent") ?: 0}%)")
                },
            )
        }) { x = x.copy(ledger = it) }

        if (!s.showCheckInCalendar) { x = x.copy(checkIn = null); times.remove("checkin"); errors.remove("checkin") }
        else if (!fresh("checkin", ExtrasTtl.CHECKIN)) {
            fetched = true
            errors.remove("checkin")
            val g = role.game
            val headers = Http.labHeaders(language()) + mapOf("Cookie" to acc.fullCookie, "x-rpc-signgame" to (g.signGame ?: ""))
            val info = Http.request("GET", "${g.signBase}/info", mapOf("act_id" to g.actId), headers)
            val d = info.data
            if (!info.ok || d == null) {
                errors["checkin"] = info.reason()
                if (info.status == 200 && Errors.isExpiredLogin(info.retcode, info.message)) expired = true
            } else {
                val total = d.int("total_sign_day") ?: 0
                val signed = d.bool("is_sign") ?: false
                val awards = signAwards(g)
                fun reward(i: Int) = awards.getOrNull(i)?.let { "${it.str("name")} x${it.int("cnt")}" }
                val day = Instant.now().atOffset(ZoneOffset.ofHours(8)).dayOfMonth
                val next = if (signed) total else total + 1
                x = x.copy(checkIn = CheckInCalendar(
                    signedToday = signed, totalDays = total,
                    missedDays = (day - total - if (signed) 0 else 1).coerceAtLeast(0),
                    todayReward = reward(if (signed) total - 1 else total),
                    nextReward = reward(next),
                    nextIcon = awards.getOrNull(next)?.str("icon"),
                    nextCount = awards.getOrNull(next)?.int("cnt"),
                ))
                times["checkin"] = start
            }
        }

        if (!s.showGacha) times.remove("game8")
        else if (!fresh("game8", ExtrasTtl.GAME8)) {
            fetched = true
            errors.remove("game8")
            val g8 = Game8.fetch(x.game8)
            if (g8 != null) { x = x.copy(game8 = g8); times["game8"] = start }
            else if (x.game8 == null) errors["game8"] = "Game8 is unavailable"
        }

        if (pity.isNotEmpty()) {
            val types = bannerNames.entries.associate { it.value to it.key }
            x = x.copy(gacha = x.gacha?.map { b ->
                val t = types[b.kind] ?: return@map b
                b.copy(pullsToS = pity[t] ?: pity[t.replace("_RETURN", "_UP")] ?: b.pullsToS)
            })
        }
        return x.copy(fetchedAt = if (fetched) start else x.fetchedAt, errors = errors, schema = EXTRAS_SCHEMA, partAt = times, pity = pity) to expired
    }
}
