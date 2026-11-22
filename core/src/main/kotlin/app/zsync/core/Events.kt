package app.zsync.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant

data class EventReport(
    val success: Boolean,
    val message: String,
    val lines: List<String>,
    val activity: Boolean,
    val expired: Boolean,
)

@kotlinx.serialization.Serializable
data class MimoInfo(val points: Int, val restockAt: Long? = null)

data class MimoOptions(val lottery: Boolean = false, val reservePoints: Int = 0, val redeem: Boolean = true)

private const val MIMO_BASE = "https://sg-public-api.hoyolab.com/event/e2023mimotravel"

private fun jsonBody(vararg p: Pair<String, Any>): String =
    JsonObject(p.associate { (k, v) -> k to (if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString())) }).toString()

private class EventClient(private val acc: Account, private val lang: String) {
    var expired = false

    fun call(method: String, url: String, query: Map<String, String> = emptyMap(), body: String? = null): Resp {
        val headers = Http.labHeaders(lang) + mapOf("Cookie" to acc.fullCookie)
        var res = Http.request(method, url, query, headers, body)
        for (attempt in 1..9) {
            if (!(res.retcode == -500004 || res.status == 429)) break
            val cap = minOf(500L * (1L shl attempt), 30000L)
            Thread.sleep((500 + Math.random() * (cap - 500)).toLong())
            res = Http.request(method, url, query, headers, body)
        }
        if (!res.ok && res.status == 200 && Errors.isExpiredLogin(res.retcode, res.message)) expired = true
        return res
    }
}

private fun normalizeTime(p: JsonPrimitive?): Long? {
    val s = p?.contentOrNull?.trim() ?: return null
    s.toDoubleOrNull()?.let { return if (it > 1e12) it.toLong() else (it * 1000).toLong() }
    return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
}

private class Task(val id: Long, val name: String, val point: Int, var status: Int, val type: Int)

private fun parseTasks(res: Resp): List<Task> =
    res.data?.arr("task_list")?.mapNotNull { t ->
        Task(
            t.long("task_id") ?: return@mapNotNull null,
            t.str("task_name") ?: "?",
            t.int("point") ?: 0,
            t.int("status") ?: 0,
            t.int("task_type") ?: 0,
        )
    } ?: emptyList()

private class ShopItem(val id: Long, val name: String, val cost: Int, var status: Int, val refreshSec: Long)

private fun parseShop(res: Resp): List<ShopItem> =
    res.data?.arr("exchange_award_list")?.mapNotNull { i ->
        ShopItem(
            i.long("award_id") ?: return@mapNotNull null,
            i.str("name") ?: "?",
            i.int("cost") ?: 0,
            i.int("status") ?: 0,
            i.long("next_refresh_time") ?: 0,
        )
    } ?: emptyList()

private fun restockOf(items: List<ShopItem>, keyword: String): Long? =
    items.firstOrNull { it.refreshSec > 0 && it.name.contains(keyword, ignoreCase = true) }?.refreshSec

private val pointsSuffix = Regex("[×x]\\s*(\\d+)$", RegexOption.IGNORE_CASE)
private val pointsPrefixed = Regex("^Points\\s*[×x]\\s*(\\d+)$", RegexOption.IGNORE_CASE)

class Mimo(private val language: () -> String) {
    fun run(acc: Account, role: Role, opts: MimoOptions, redeem: (String) -> RedeemResult): EventReport {
        val gameId = role.game.gameId
        val keyword = when (role.game) {
            Game.STARRAIL -> "stellar jade"
            Game.ZZZ -> "polychrome"
            else -> return fail("Traveling Mimo is only available for Star Rail and Zenless", false)
        }
        val lang = language()
        val c = EventClient(acc, lang)
        fun fail(msg: String) = fail(msg, c.expired)

        fun index(): Pair<Resp, JsonObject?> {
            val r = c.call("GET", "$MIMO_BASE/index", mapOf("lang" to lang))
            val g = if (r.ok) r.data?.arr("list")?.firstOrNull { it.int("game_id") == gameId } else null
            return r to g
        }

        val (idxRes, game) = index()
        if (!idxRes.ok) return fail(idxRes.reason())
        if (game == null) return fail("The Mimo event is not active for ${role.game.title}")
        val versionId = game.long("version_id") ?: return fail("The Mimo event is not active for ${role.game.title}")
        val durationEnd = normalizeTime(game["end_time"] as? JsonPrimitive)
        if (durationEnd != null && durationEnd <= System.currentTimeMillis()) return fail("The Mimo event has ended")
        var points = game.int("point") ?: 0

        val tasksClaimed = mutableListOf<Pair<String, Int>>()
        val itemsExchanged = mutableListOf<Triple<String, Int, String?>>()
        val codesRedeemed = mutableListOf<String>()
        val codesObtained = mutableListOf<String>()
        val lotteryDraws = mutableListOf<String>()
        val errors = mutableListOf<String>()

        fun handleCode(code: String, label: String) {
            if (opts.redeem) {
                val r = redeem(code)
                if (r.success) codesRedeemed += code else errors += "Could not redeem $label $code: ${r.reason}"
            } else codesObtained += code
        }

        val taskRes = c.call("GET", "$MIMO_BASE/task-list", mapOf("lang" to lang, "game_id" to "$gameId", "version_id" to "$versionId"))
        if (taskRes.ok) {
            for (t in parseTasks(taskRes)) {
                if (t.status == 2 && t.type in listOf(1, 2)) {
                    val r = c.call("POST", "$MIMO_BASE/finish-task", body = jsonBody("task_id" to t.id, "game_id" to gameId, "lang" to lang, "version_id" to versionId))
                    if (r.ok) t.status = 1
                    Thread.sleep(1000)
                }
                if (t.status == 1) {
                    val r = c.call("GET", "$MIMO_BASE/receive-point", mapOf("task_id" to "${t.id}", "game_id" to "$gameId", "lang" to lang, "version_id" to "$versionId"))
                    if (r.ok) { tasksClaimed += t.name to t.point; points += t.point }
                    Thread.sleep(1000)
                }
            }
        }

        index().second?.int("point")?.let { points = it }

        var lotteryAllowed = false
        val shopRes = c.call("GET", "$MIMO_BASE/exchange-list", mapOf("lang" to "en-us", "game_id" to "$gameId", "version_id" to "$versionId"))
        if (shopRes.ok) {
            val items = parseShop(shopRes)
            val premium = items.filter { i -> i.name.contains(keyword, ignoreCase = true) }
            for (it in premium.sortedByDescending { i -> i.cost }) {
                if (it.status != 1 || points < it.cost) continue
                val r = c.call("POST", "$MIMO_BASE/exchange", body = jsonBody("award_id" to it.id, "game_id" to gameId, "lang" to lang, "version_id" to versionId))
                if (r.ok) {
                    val code = r.data?.str("exchange_code")?.takeIf { s -> s.isNotBlank() }
                    itemsExchanged += Triple(it.name, it.cost, code)
                    points -= it.cost
                    it.status = 3
                    if (code != null) handleCode(code, "the code")
                }
                Thread.sleep(1500)
            }

            // Lottery only spends leftovers: every weekly premium item redeemed (status 3) and not enough points for another.
            lotteryAllowed = premium.isNotEmpty() && premium.all { i -> i.status == 3 } && points < premium.minOf { i -> i.cost }
        }

        if (opts.lottery && lotteryAllowed) {
            val threshold = maxOf(0, opts.reservePoints)
            val infoQuery = mapOf("lang" to lang, "game_id" to "$gameId", "version_id" to "$versionId")
            val info = c.call("GET", "$MIMO_BASE/lottery-info", infoQuery)
            if (info.ok && info.data != null) {
                val d = info.data!!
                val cost = d.int("cost") ?: 0
                val limit = d.int("limit_count") ?: 0
                var remaining = maxOf(0, limit - (d.int("count") ?: 0))
                d.int("point")?.let { points = it }
                val awardTypes = d.arr("award_list").associate { a -> a.long("award_id") to a.int("type") }
                while (remaining > 0 && cost > 0 && points - threshold >= cost) {
                    val r = c.call("POST", "$MIMO_BASE/lottery", body = jsonBody("game_id" to gameId, "lang" to lang, "version_id" to versionId))
                    if (!r.ok) break
                    val rd = r.data
                    val name = rd?.str("name") ?: "?"
                    val code = rd?.str("exchange_code")?.takeIf { s -> s.isNotBlank() }
                    lotteryDraws += name
                    points -= cost
                    val isPoints = rd?.int("type") == 3 || awardTypes[rd?.long("award_id")] == 3
                    (if (isPoints) pointsSuffix else pointsPrefixed).find(name)?.groupValues?.get(1)?.toIntOrNull()?.let { points += it }
                    remaining--
                    if (code != null) handleCode(code, "the lottery code")
                    Thread.sleep(1500)
                    val again = c.call("GET", "$MIMO_BASE/lottery-info", infoQuery)
                    if (again.ok && again.data != null) {
                        again.data!!.int("point")?.let { points = it }
                        remaining = minOf(remaining, maxOf(0, (again.data!!.int("limit_count") ?: limit) - (again.data!!.int("count") ?: 0)))
                    }
                }
            }
        }

        if (itemsExchanged.isNotEmpty() || lotteryDraws.isNotEmpty()) {
            val (_, g) = index()
            if (g != null && g.long("version_id") == versionId) g.int("point")?.let { points = it }
        }

        val lines = buildList {
            if (tasksClaimed.isNotEmpty()) {
                add("Tasks claimed: ${tasksClaimed.size} (+${tasksClaimed.sumOf { it.second }} pts)")
                tasksClaimed.forEach { add("- ${it.first} (+${it.second})") }
            }
            itemsExchanged.forEach { add("Redeemed: ${it.first} (-${it.second} pts)") }
            if (codesRedeemed.isNotEmpty()) add("Codes redeemed: ${codesRedeemed.joinToString(", ")}")
            codesObtained.forEach { add("Code obtained (not redeemed): $it") }
            lotteryDraws.forEach { add("Lottery: $it") }
            errors.forEach { add("Error: $it") }
            add("Current points: $points")
        }
        val activity = tasksClaimed.isNotEmpty() || itemsExchanged.isNotEmpty() || codesRedeemed.isNotEmpty() ||
            codesObtained.isNotEmpty() || lotteryDraws.isNotEmpty() || errors.isNotEmpty()
        return EventReport(true, "", lines, activity, c.expired)
    }

    fun status(acc: Account, role: Role): MimoInfo? {
        val gameId = role.game.gameId
        val c = EventClient(acc, language())
        val r = c.call("GET", "$MIMO_BASE/index", mapOf("lang" to language()))
        val game = if (r.ok) r.data?.arr("list")?.firstOrNull { it.int("game_id") == gameId } else null
        game ?: return null
        val versionId = game.long("version_id") ?: return null
        val shop = c.call("GET", "$MIMO_BASE/exchange-list", mapOf("lang" to "en-us", "game_id" to "$gameId", "version_id" to "$versionId"))

        val sec = if (shop.ok) restockOf(parseShop(shop), "polychrome") else null
        return MimoInfo(game.int("point") ?: 0, sec?.let { System.currentTimeMillis() + it * 1000 })
    }

    private fun fail(msg: String, expired: Boolean) = EventReport(false, msg, emptyList(), false, expired)
}
