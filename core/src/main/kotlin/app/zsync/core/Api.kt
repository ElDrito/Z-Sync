package app.zsync.core

import kotlinx.serialization.json.*

sealed class CheckIn {
    abstract val game: Game
    data class Done(override val game: Game, val already: Boolean, val total: Int, val reward: String) : CheckIn()
    data class Failed(override val game: Game, val reason: String, val expired: Boolean = false, val noAccount: Boolean = false) : CheckIn()
}

data class RedeemResult(val success: Boolean, val retcode: Int?, val reason: String, val expired: Boolean, val final: Boolean)

class Api(private val language: () -> String = { API_LANG }) {
    private fun lab(extra: Map<String, String> = emptyMap(), lang: String = language()) = Http.labHeaders(lang) + extra

    fun fetchRoles(acc: Account): Pair<List<Role>?, Resp> {
        val res = Http.request(
            "GET", "https://bbs-api-os.hoyolab.com/game_record/card/wapi/getGameRecordCard",
            query = mapOf("uid" to acc.ltuid),
            headers = lab(mapOf("Cookie" to acc.baseCookie)),
        )
        if (!res.ok) return null to res
        val list = res.data?.arr("list") ?: return null to res
        val roles = list.mapNotNull { e ->
            if (e.int("game_id") != Game.ZZZ.gameId) return@mapNotNull null
            Role(
                game = Game.ZZZ,
                uid = e.str("game_role_id") ?: return@mapNotNull null,
                nickname = e.str("nickname").orEmpty(),
                region = e.str("region").orEmpty(),
                level = e.int("level") ?: 0,
            )
        }
        return roles to res
    }

    fun checkIn(acc: Account, game: Game): CheckIn {
        val headers = lab(buildMap {
            put("Cookie", acc.fullCookie)
            game.signGame?.let { put("x-rpc-signgame", it) }
        })
        val q = mapOf("act_id" to game.actId)

        val info = Http.request("GET", "${game.signBase}/info", q, headers)
        if (!info.ok) return fail(game, info)
        val d = info.data ?: return CheckIn.Failed(game, "Response without data")
        val total = d.int("total_sign_day") ?: 0
        val signed = d.bool("is_sign") ?: false

        val home = Http.request("GET", "${game.signBase}/home", q, headers)
        val awards = home.data?.arr("awards").orEmpty()
        fun reward(i: Int) = awards.getOrNull(i)?.let { "${it.str("name")} x${it.int("cnt")}" } ?: "?"

        if (signed) return CheckIn.Done(game, true, total, reward(total - 1))

        val sign = Http.request("POST", "${game.signBase}/sign", q, headers)
        if (sign.status == 200 && sign.retcode == -5003) return CheckIn.Done(game, true, total, reward(total - 1))
        if (!sign.ok) return fail(game, sign)

        val risk = sign.data?.obj("gt_result")?.let { (it.bool("is_risk") ?: false) || (it.int("risk_code") ?: 0) == 5001 } ?: false
        if (risk) return CheckIn.Failed(game, "HoYoLAB asked for a captcha; check in once from the website")
        return CheckIn.Done(game, false, total + 1, reward(total))
    }

    private fun fail(game: Game, r: Resp) = CheckIn.Failed(
        game, r.reason(),
        expired = r.status == 200 && Errors.isExpiredLogin(r.retcode, r.message),
        noAccount = r.retcode == -10002,
    )

    fun notes(acc: Account, role: Role): Pair<Notes?, Resp> {
        val url = role.game.notesUrl ?: error("no notes")
        val res = Http.request(
            "GET", url,
            query = mapOf("server" to role.region, "role_id" to role.uid),
            headers = lab(mapOf("Cookie" to acc.baseCookie, "DS" to Http.ds())),
        )
        if (!res.ok) return null to res
        val d = res.data ?: return null to res
        val now = System.currentTimeMillis()
        val notes = when (role.game) {
            Game.ZZZ -> {
                val energy = d.obj("energy")
                val prog = energy?.obj("progress")
                val vit = d.obj("vitality")

                val shop = when (d.obj("vhs_sale")?.str("sale_state")) {
                    "SaleStateNo" -> "Closed"; "SaleStateDoing" -> "Open"; "SaleStateDone" -> "Finished"; else -> null
                }

                val bounty = d.obj("bounty_commission") ?: d.obj("hollow_zero")?.obj("bounty_commission")
                val survey = d.obj("survey_points") ?: d.obj("hollow_zero")?.obj("survey_points")
                val ridu = d.obj("weekly_task")
                val member = d.obj("member_card")
                val temple = d.obj("temple_running")
                val card = d.str("card_sign")?.let { it == "CardSignDone" }
                Notes(
                    fetchedAt = now,
                    stamina = Stamina(prog?.int("current") ?: 0, prog?.int("max") ?: 0, energy?.long("restore") ?: 0),
                    dailyDone = vit?.int("current") ?: 0,
                    dailyMax = vit?.int("max") ?: 0,
                    scratchCardDone = card,
                    shopState = shop,
                    bountyDone = bounty?.int("num"), bountyTotal = bounty?.int("total"),
                    surveyDone = survey?.int("num"), surveyTotal = survey?.int("total"),
                    riduDone = ridu?.int("cur_point"), riduMax = ridu?.int("max_point"), riduResetSec = ridu?.long("refresh_time"),
                    bountyResetSec = bounty?.long("refresh_time"), templeResetSec = temple?.long("currency_next_refresh_ts"),
                    memberActive = member?.str("member_card_state")?.let { it == "MemberCardStateACK" },
                    memberLeftSec = member?.long("exp_time"),
                    templeLevel = temple?.int("level"),
                    templeCurrency = temple?.int("current_currency"), templeCurrencyMax = temple?.int("weekly_currency_max"),
                    templeBench = when (temple?.str("bench_state")) {
                        "BenchStateCanProduce" -> "Free"; "BenchStateProducing" -> "Producing"; else -> null
                    },
                    templeShelf = when (temple?.str("shelve_state")) {
                        "ShelveStateCanSell" -> "Free"; "ShelveStateSelling" -> "Selling"; "ShelveStateSoldOut" -> "Sold out"; else -> null
                    },
                    templeExpedition = when (temple?.str("expedition_state")) {
                        "ExpeditionStateInCanSend" -> "Free"; "ExpeditionStateInProgress" -> "In progress"; "ExpeditionStateEnd" -> "Finished"; else -> null
                    },
                )
            }
            else -> return null to res
        }
        return notes to res
    }

    private val codeCache = java.util.concurrent.ConcurrentHashMap<Game, Pair<Long, List<GiftCode>>>()

    fun fetchCodes(game: Game): List<GiftCode> {
        codeCache[game]?.let { (at, list) -> if (System.currentTimeMillis() - at < 5 * 60_000L) return list }
        return loadCodes(game).also { if (it.isNotEmpty()) codeCache[game] = System.currentTimeMillis() to it }
    }

    private fun loadCodes(game: Game): List<GiftCode> {
        val res = Http.request(
            "GET", "https://api.ennead.cc/mihoyo/${game.codesKey}/codes",
            headers = mapOf("User-Agent" to "HoyoLabAuto"),
        )
        fun JsonObject.code() = str("code")?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        val first = if (res.status != 200) emptyList() else res.body?.arr("active").orEmpty().mapNotNull { c ->
            val code = c.code() ?: return@mapNotNull null
            GiftCode(code, (c["rewards"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty())
        }

        val second = runCatching {
            val r = Http.request("GET", "https://hoyo-codes.seria.moe/codes", mapOf("game" to "nap"), mapOf("User-Agent" to "Z-Sync"))
            if (r.status != 200) emptyList() else r.body?.arr("codes").orEmpty().mapNotNull { c ->
                if (c.str("status")?.uppercase() != "OK") return@mapNotNull null
                val code = c.code() ?: return@mapNotNull null
                GiftCode(code, listOfNotNull(c.str("rewards")?.takeIf { it.isNotBlank() }))
            }
        }.getOrDefault(emptyList())
        return (first + second).distinctBy { it.code }
    }

    fun redeem(acc: Account, role: Role, code: String): RedeemResult {
        val url = role.game.redeemUrl ?: return RedeemResult(false, null, "This game only allows redeeming in-game", false, true)
        var res: Resp? = null
        for (attempt in 0 until 3) {
            RedeemThrottle.await(role.key)
            res = try {
                Http.request(
                    if (role.game.redeemPost) "POST" else "GET", url,
                    query = mapOf(
                        "uid" to role.uid, "region" to role.region, "lang" to "en", "cdkey" to code,
                        "game_biz" to role.game.gameBiz!!, "t" to System.currentTimeMillis().toString(),
                    ),
                    headers = lab(mapOf("Cookie" to acc.redeemCookie), lang = "en-us"),
                )
            } finally {
                RedeemThrottle.mark(role.key, 6000)
            }
            if (res.status != 200 || res.retcode != -2016) break
            val secs = cooldown.find(res.message)?.groupValues?.get(1)?.toDoubleOrNull()
            RedeemThrottle.mark(role.key, maxOf(6000L, ((secs ?: 5.0) * 1000 + 1000).toLong()))
        }
        val r = res!!
        if (r.ok) return RedeemResult(true, 0, "Redeemed", false, true)
        val expired = r.status == 200 && Errors.isExpiredLogin(r.retcode, r.message)
        return RedeemResult(
            false, r.retcode, r.reason(), expired,
            final = r.status == 200 && r.retcode in FINAL_ERRORS,
        )
    }

    fun refreshTokens(acc: Account): Pair<Account?, String> {
        val stoken = acc.cookies["stoken"]
        val mid = acc.cookies["ltmid_v2"]
        if (stoken.isNullOrEmpty() || mid.isNullOrEmpty()) return null to "No stoken"
        val (tokens, error) = Auth.tokensFromStoken(stoken, mid)
        if (error != null) return null to error
        val merged = acc.cookies + tokens + mapOf(
            "account_id_v2" to acc.ltuid,
            "account_mid_v2" to mid,
        )
        return acc.copy(cookies = merged) to ""
    }

    companion object {
        val FINAL_ERRORS = setOf(-2001, -2003, -2004, -2006, -2017, -2018)
        private val cooldown = Regex("(\\d+(?:\\.\\d+)?)\\s*second", RegexOption.IGNORE_CASE)
    }
}

object RedeemThrottle {
    private val next = HashMap<String, Long>()
    fun await(key: String) {
        val wait = synchronized(next) { (next[key] ?: 0) - System.currentTimeMillis() }
        if (wait > 0) Thread.sleep(wait)
    }
    fun mark(key: String, ms: Long) = synchronized(next) { next[key] = System.currentTimeMillis() + ms }
}
