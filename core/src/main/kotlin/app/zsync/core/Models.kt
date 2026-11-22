package app.zsync.core

import kotlinx.serialization.Serializable

enum class Game(
    val title: String,
    val short: String,
    val gameId: Int,
    val actId: String,
    val signGame: String?,
    val signBase: String,
    val notesUrl: String?,
    val redeemUrl: String?,
    val redeemPost: Boolean,
    val gameBiz: String?,
    val codesKey: String,
    val maxStamina: Int,
) {
    ZZZ(
        "Zenless Zone Zero", "ZZZ", 8, "e202406031448091", "zzz",
        "https://sg-public-api.hoyolab.com/event/luna/zzz/os",
        "https://sg-act-nap-api.hoyolab.com/event/game_record_zzz/api/zzz/note",
        "https://public-operation-nap.hoyoverse.com/common/apicdkey/api/webExchangeCdkey", false,
        "nap_global", "zenless", 240,
    ),
    GENSHIN(
        "Genshin Impact", "GI", 2, "e202102251931481", "hk4e",
        "https://sg-hk4e-api.hoyolab.com/event/sol",
        "https://bbs-api-os.mihoyo.com/game_record/genshin/api/dailyNote",
        "https://sg-hk4e-api.hoyoverse.com/common/apicdkey/api/webExchangeCdkey", false,
        "hk4e_global", "genshin", 200,
    ),
    STARRAIL(
        "Honkai: Star Rail", "HSR", 6, "e202303301540311", "hkrpg",
        "https://sg-public-api.hoyolab.com/event/luna/os",
        "https://bbs-api-os.hoyolab.com/game_record/hkrpg/api/note",
        "https://sg-hkrpg-api.hoyoverse.com/common/apicdkey/api/webExchangeCdkeyRisk", true,
        "hkrpg_global", "starrail", 300,
    ),
    HONKAI(
        "Honkai Impact 3rd", "HI3", 1, "e202110291205111", null,
        "https://sg-public-api.hoyolab.com/event/mani",
        null, null, false, null, "honkai", 0,
    ),
    TOT(
        "Tears of Themis", "ToT", 0, "e202202281857121", null,
        "https://sg-public-api.hoyolab.com/event/luna/os",
        null, null, false, null, "themis", 0,
    );

    val staminaName: String get() = when (this) {
        ZZZ -> "Battery"
        GENSHIN -> "Resin"
        STARRAIL -> "Trailblaze Power"
        else -> ""
    }

    val hasNotes get() = notesUrl != null
    val canRedeem get() = redeemUrl != null
}

@Serializable
data class Role(
    val game: Game,
    val uid: String,
    val nickname: String,
    val region: String,
    val level: Int = 0,
) {
    val key get() = "${game.name}:$region:$uid"
    val regionName get() = regionName(region)
}

fun regionName(region: String) = when (region) {
    "os_cht", "prod_gf_sg", "prod_official_cht" -> "TW/HK/MO"
    "os_asia", "prod_gf_jp", "prod_official_asia" -> "Asia"
    "eur01", "os_euro", "prod_gf_eu", "prod_official_eur" -> "Europe"
    "usa01", "os_usa", "prod_gf_us", "prod_official_usa" -> "America"
    else -> region
}

fun regionUtcOffset(region: String) = when (region) {
    "usa01", "os_usa", "prod_gf_us", "prod_official_usa" -> -5
    "eur01", "os_euro", "prod_gf_eu", "prod_official_eur" -> 1
    else -> 8
}

object Resets {
    private fun zone(region: String) = java.time.ZoneOffset.ofHours(regionUtcOffset(region))

    fun nextDaily(region: String, now: Long = System.currentTimeMillis()): Long {
        val t = java.time.Instant.ofEpochMilli(now).atOffset(zone(region))
        var r = t.toLocalDate().atTime(4, 0).atOffset(zone(region))
        if (!r.isAfter(t)) r = r.plusDays(1)
        return r.toInstant().toEpochMilli()
    }

    fun nextWeekly(region: String, now: Long = System.currentTimeMillis()): Long {
        val t = java.time.Instant.ofEpochMilli(now).atOffset(zone(region))
        var r = t.toLocalDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).atTime(4, 0).atOffset(zone(region))
        while (!r.isAfter(t)) r = r.plusWeeks(1)
        return r.toInstant().toEpochMilli()
    }

    fun fromApi(fetchedAt: Long, sec: Long?, fallback: Long, now: Long = System.currentTimeMillis()): Long =
        sec?.takeIf { it > 0 }?.let { fetchedAt + it * 1000 }?.takeIf { it > now } ?: fallback
}

@Serializable
data class Account(
    val ltuid: String,
    val cookies: Map<String, String>,
    val deviceId: String = "",
    val label: String = "",
    val roles: List<Role> = emptyList(),
) {
    val hasStoken get() = !cookies["stoken"].isNullOrEmpty()
    val canRedeem get() = !cookies["cookie_token_v2"].isNullOrEmpty()

    fun cookie(vararg keys: String) = keys.mapNotNull { k -> cookies[k]?.let { "$k=$it" } }.joinToString("; ")
    val baseCookie get() = cookie("ltoken_v2", "ltuid_v2", "ltmid_v2")
    val fullCookie get() = cookie("ltoken_v2", "ltuid_v2", "ltmid_v2", "cookie_token_v2", "account_mid_v2", "account_id_v2")
    val redeemCookie get() = cookie("cookie_token_v2", "account_mid_v2", "account_id_v2", "cookie_token", "account_id")

    fun activeRoles() = roles.filter { it.game == Game.ZZZ }
}

fun parseCookieString(raw: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (pair in raw.split(';')) {
        val p = pair.trim()
        val i = p.indexOf('=')
        if (i > 0) out[p.substring(0, i).trim()] = p.substring(i + 1).trim()
    }
    return out
}

@Serializable
data class Stamina(val current: Int, val max: Int, val recoverySec: Long)

@Serializable
data class Notes(
    val fetchedAt: Long,
    val stamina: Stamina,
    val dailyDone: Int,
    val dailyMax: Int,

    val extras: List<String> = emptyList(),
    val scratchCardDone: Boolean? = null,
    val shopState: String? = null,
    val bountyDone: Int? = null,
    val bountyTotal: Int? = null,
    val surveyDone: Int? = null,
    val surveyTotal: Int? = null,

    val riduDone: Int? = null,
    val riduMax: Int? = null,
    val riduResetSec: Long? = null,

    val bountyResetSec: Long? = null,
    val templeResetSec: Long? = null,

    val memberActive: Boolean? = null,
    val memberLeftSec: Long? = null,

    val templeLevel: Int? = null,
    val templeCurrency: Int? = null,
    val templeCurrencyMax: Int? = null,
    val templeBench: String? = null,
    val templeShelf: String? = null,
    val templeExpedition: String? = null,

) {
    fun pendingWeeklies(): List<String> = listOfNotNull(
        bountyTotal?.takeIf { bountyDone != it }?.let { "Bounty Commission ${bountyDone ?: 0}/$it" },
        surveyTotal?.takeIf { surveyDone != it }?.let { "Investigation Points ${surveyDone ?: 0}/$it" },
    )

    fun staminaNow(now: Long, regenSec: Int): Int {
        if (stamina.current >= stamina.max) return stamina.current
        val gained = ((now - fetchedAt) / 1000 / regenSec).toInt()
        return minOf(stamina.max, stamina.current + gained)
    }
}

@Serializable
data class GiftCode(val code: String, val rewards: List<String> = emptyList())

enum class Level { OK, INFO, WARN, ERROR }

@Serializable
data class LogEntry(val time: Long, val level: Level, val title: String, val text: String)

@Serializable
data class Settings(
    val autoEnabled: Boolean = true,

    val manualCheckInMin: Int = 9 * 60,
    val manualCodesMin: Int = 10 * 60,
    val manualMimoMin: Int = 12 * 60,
    val autoCheckIn: Boolean = true,
    val autoCodes: Boolean = true,
    val autoNotes: Boolean = true,
    val notifyCheckIn: Boolean = true,
    val notifyStamina: Boolean = true,
    val notifyDailies: Boolean = true,
    val notifyScratchCard: Boolean = true,
    val notifyShop: Boolean = false,
    val notifyWeeklies: Boolean = true,
    val autoMimo: Boolean = false,
    val mimoLottery: Boolean = false,
    val mimoReservePoints: Int = 0,

    val showActions: Boolean = false,

    val batteryThreshold: Int = 220,
    val dailiesHour: Int = 21,

    val showProfile: Boolean = true,
    val showBattery: Boolean = true,
    val showDaily: Boolean = true,
    val showBounty: Boolean = true,
    val showScratch: Boolean = true,
    val showStore: Boolean = false,
    val showSurvey: Boolean = true,
    val showPremium: Boolean = false,
    val showMimo: Boolean = true,
    val showRidu: Boolean = true,
    val showTemple: Boolean = false,
    val showMember: Boolean = false,
    val showCheckInCalendar: Boolean = true,
    val showCurrency: Boolean = true,
    val showEvents: Boolean = true,

    val optChallenges: Boolean = false,
    val showLedger: Boolean = false,

    val showGacha: Boolean = true,

    val startupSound: Boolean = true,

    val iconKey: String = "anby",

    val keepStoken: Boolean = true,

    val autoIntervalMin: Int = 720,

    val deviceId: String = "",

    val debugUnlocked: Boolean = false,
)

@Serializable
data class EngineState(
    val checkInDay: Map<String, String> = emptyMap(),

    val doneCodes: Map<String, Set<String>> = emptyMap(),

    val fired: Set<String> = emptySet(),
    val notes: Map<String, Notes> = emptyMap(),
    val extras: Map<String, Extras> = emptyMap(),
    val lastCookieRefresh: Long = 0,
    val lastAutoRun: Long = 0,
    val lastMimoRun: Long = 0,
    val lastCodesCheck: Long = 0,

    val lastCheckInAttempt: Long = 0,
    val lastNotesAttempt: Long = 0,
    val cookieRefreshFailed: Set<String> = emptySet(),
)
