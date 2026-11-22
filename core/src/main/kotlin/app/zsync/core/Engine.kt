package app.zsync.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset

interface Storage {
    fun read(name: String): String?
    fun write(name: String, value: String)
}

fun interface Notifier {
    fun notify(tag: String, title: String, text: String)
}

class Repo(private val secure: Storage, private val plain: Storage) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _accounts = MutableStateFlow(load<List<Account>>(secure, "accounts") ?: emptyList())
    private val _settings = MutableStateFlow(load<Settings>(plain, "settings") ?: Settings())
    private val _state = MutableStateFlow(load<EngineState>(plain, "state") ?: EngineState())
    private val _log = MutableStateFlow(load<List<LogEntry>>(plain, "log") ?: emptyList())

    val accounts: StateFlow<List<Account>> = _accounts
    val settings: StateFlow<Settings> = _settings
    val state: StateFlow<EngineState> = _state
    val log: StateFlow<List<LogEntry>> = _log

    private inline fun <reified T> load(s: Storage, name: String): T? =
        runCatching { s.read(name)?.let { json.decodeFromString<T>(it) } }.getOrNull()

    @Synchronized
    fun updateAccounts(f: (List<Account>) -> List<Account>) {
        _accounts.value = f(_accounts.value)
        secure.write("accounts", json.encodeToString(_accounts.value))
    }

    fun putAccount(acc: Account) = updateAccounts { list ->
        if (list.any { it.ltuid == acc.ltuid }) list.map { if (it.ltuid == acc.ltuid) acc else it } else list + acc
    }

    @Synchronized
    fun updateSettings(f: (Settings) -> Settings) {
        _settings.value = f(_settings.value)
        plain.write("settings", json.encodeToString(_settings.value))
    }

    @Synchronized
    fun updateState(f: (EngineState) -> EngineState) {
        _state.value = f(_state.value)
        plain.write("state", json.encodeToString(_state.value))
    }

    @Synchronized
    fun log(level: Level, title: String, text: String): LogEntry {
        val e = LogEntry(System.currentTimeMillis(), level, title, text)
        _log.value = (listOf(e) + _log.value).take(300)
        plain.write("log", json.encodeToString(_log.value))
        return e
    }
}

class Engine(val repo: Repo, private val notifier: Notifier) {
    val api = Api { API_LANG }

    private fun now() = System.currentTimeMillis()
    private fun hoyoDay(): String = Instant.now().atOffset(ZoneOffset.ofHours(8)).toLocalDate().toString()

    private fun account(ltuid: String) = repo.accounts.value.firstOrNull { it.ltuid == ltuid }

    private fun name(acc: Account) = acc.label.ifBlank { acc.ltuid }
    private fun proxy(role: Role) = role.nickname.ifBlank { role.uid }
    private fun proxy(acc: Account, game: Game? = null) =
        acc.activeRoles().firstOrNull { game == null || it.game == game }?.let(::proxy) ?: name(acc)
    private fun sentence(t: String) = t.trim().let { if (it.isEmpty() || it.last() in ".!?") it else "$it." }
    private fun who(role: Role) = "${role.game.short} · ${role.nickname.ifBlank { role.uid }}"

    fun addAccount(cookies: Map<String, String>, deviceId: String = ""): Pair<Account?, String> {
        val ltuid = cookies["ltuid_v2"] ?: cookies["account_id_v2"]
        if (ltuid.isNullOrEmpty() || cookies["ltoken_v2"].isNullOrEmpty() || cookies["ltmid_v2"].isNullOrEmpty())
            return null to "ltoken_v2, ltuid_v2 or ltmid_v2 missing from the cookie"
        val prev = account(ltuid)

        val keep = repo.settings.value.keepStoken
        var acc = (prev ?: Account(ltuid, cookies)).copy(
            cookies = ((prev?.cookies ?: emptyMap()) + cookies + mapOf("ltuid_v2" to ltuid)).filterKeys { keep || it != "stoken" },
            deviceId = deviceId.ifBlank { prev?.deviceId.orEmpty() },
        )
        val (roles, res) = api.fetchRoles(acc)
        if (roles == null) return null to "Could not validate the account: ${res.reason()}"
        acc = acc.copy(roles = roles, label = acc.label.ifBlank { roles.firstOrNull()?.nickname.orEmpty() })
        repo.putAccount(acc)
        repo.log(if (roles.isEmpty()) Level.WARN else Level.OK, "Account added", if (roles.isEmpty()) "${name(acc)}: no Zenless Zone Zero account found" else roles.joinToString { "${it.nickname} · UID ${it.uid}" })
        return acc to ""
    }

    fun syncRoles(ltuid: String): String {
        val acc = account(ltuid) ?: return "Account not found"
        val (roles, res) = api.fetchRoles(acc)
        if (roles == null) return res.reason()
        repo.putAccount(acc.copy(roles = roles))
        return roles.joinToString { "${it.nickname} · UID ${it.uid}" }.ifBlank { "No Zenless Zone Zero account found" }
    }

    fun setKeepStoken(keep: Boolean) {
        repo.updateSettings { it.copy(keepStoken = keep) }
        if (!keep) {
            repo.updateAccounts { l -> l.map { it.copy(cookies = it.cookies - "stoken") } }
            repo.updateState { it.copy(cookieRefreshFailed = emptySet()) }
            repo.log(Level.INFO, "Automatic renewal turned off", "The stoken saved on this phone was deleted")
        }
    }

    fun removeAccount(ltuid: String) = repo.updateAccounts { l -> l.filterNot { it.ltuid == ltuid } }

    fun refreshCookies(auto: Boolean = false, only: String? = null): Boolean {
        var any = false
        for (acc in repo.accounts.value) {
            if (only != null && acc.ltuid != only) continue
            if (!acc.hasStoken) {
                if (!auto) repo.log(Level.WARN, "Renew cookies", "${name(acc)}: no stoken, cannot renew automatically")
                continue
            }
            val (updated, error) = api.refreshTokens(acc)
            if (updated != null) {
                repo.updateAccounts { l -> l.map { if (it.ltuid == acc.ltuid) it.copy(cookies = updated.cookies) else it } }
                any = true
                if (!auto) repo.log(Level.OK, "Renew cookies", "${name(acc)}: tokens renewed")
                if (acc.ltuid in repo.state.value.cookieRefreshFailed) {
                    repo.updateState { it.copy(cookieRefreshFailed = it.cookieRefreshFailed - acc.ltuid, fired = it.fired - "expired:${acc.ltuid}") }
                    notifier.notify("cookie:${acc.ltuid}", "${proxy(acc)}: Your Tokens had been Renewed!", "You got it, Boss!")
                }
            } else {
                repo.log(Level.ERROR, "Renew cookies", "${name(acc)}: $error")
                if (acc.ltuid !in repo.state.value.cookieRefreshFailed) {
                    repo.updateState { it.copy(cookieRefreshFailed = it.cookieRefreshFailed + acc.ltuid) }
                    if (auto) notifier.notify("cookiefail:${acc.ltuid}", "${proxy(acc)}: Your Tokens couldn't be Renewed!", "Tap to Log in Again.")
                }
            }
        }
        repo.updateState { it.copy(lastCookieRefresh = now()) }
        return any
    }

    private fun recover(acc: Account): Account? {
        if (!acc.hasStoken) return null
        return if (refreshCookies(auto = true, only = acc.ltuid)) account(acc.ltuid) else null
    }

    private fun notifyExpired(acc: Account, auto: Boolean) {
        val key = "expired:${acc.ltuid}"
        if (auto && key !in repo.state.value.fired) {
            repo.updateState { it.copy(fired = it.fired + key) }
            notifier.notify(key, "${proxy(acc)}: Your Session has Expired!", "Tap to Renew your Cookies.")
        }
    }

    private fun gamesOf(acc: Account): List<Game> = acc.activeRoles().map { it.game }.distinct()

    fun checkIn(auto: Boolean = false, only: Game? = null, onlyAccount: String? = null): List<LogEntry> {
        val out = ArrayList<LogEntry>()
        val day = hoyoDay()
        for (listed in repo.accounts.value) {
            if (onlyAccount != null && listed.ltuid != onlyAccount) continue
            var acc = listed
            for (game in gamesOf(acc)) {
                if (only != null && game != only) continue
                val key = "${acc.ltuid}:${game.name}"
                if (auto && repo.state.value.checkInDay[key] == day) continue

                var r = api.checkIn(acc, game)
                if (r is CheckIn.Failed && r.expired) {
                    recover(acc)?.let { acc = it; r = api.checkIn(acc, game) }
                }
                val title = "Check-in · ${game.title}"
                when (val res = r) {
                    is CheckIn.Done -> {
                        repo.updateState { st ->
                            val stale = acc.activeRoles().filter { it.game == game }.mapNotNull { r -> st.extras[r.key]?.let { r.key to it.copy(partAt = it.partAt - "checkin") } }
                            st.copy(checkInDay = st.checkInDay + (key to day), fired = st.fired - "ci-fail:$key", extras = st.extras + stale)
                        }
                        val text = if (res.already) "You already checked in today (${res.total} days)"
                        else "Reward: ${res.reward} · ${res.total} days this month"
                        if (!(auto && res.already)) out += repo.log(Level.OK, title, "${name(acc)}: $text")
                        if (auto && !res.already && repo.settings.value.notifyCheckIn)
                            notifier.notify("ci:$key", "${proxy(acc, game)}: Ka-Check-In!", "Reward: ${res.reward} (${res.total} Days in a Row!).")
                    }
                    is CheckIn.Failed -> {
                        out += repo.log(Level.ERROR, title, "${name(acc)}: ${res.reason}")
                        if (res.expired) notifyExpired(acc, auto)
                        else if (auto && "ci-fail:$key:$day" !in repo.state.value.fired) {
                            repo.updateState { s -> s.copy(fired = s.fired.filterNot { it.startsWith("ci-fail:$key") }.toSet() + "ci-fail:$key:$day") }
                            notifier.notify("ci:$key", "${proxy(acc, game)}: Check-In Failed!", sentence(res.reason))
                        }
                    }
                }
            }
        }
        if (out.isEmpty() && !auto) out += repo.log(Level.INFO, "Check-in", "No active accounts or games")
        return out
    }

    /** [maxAgeMs] > 0 reuses notes younger than that instead of calling the API. */
    fun refreshNotes(auto: Boolean = false, onlyRole: String? = null, quiet: Boolean = false, maxAgeMs: Long = 0): List<LogEntry> {
        val out = ArrayList<LogEntry>()
        for (listed in repo.accounts.value) {
            var acc = listed
            for (role in acc.activeRoles()) {
                if (!role.game.hasNotes) continue
                if (onlyRole != null && role.key != onlyRole) continue
                val cached = repo.state.value.notes[role.key]
                if (maxAgeMs > 0 && cached != null && now() - cached.fetchedAt < maxAgeMs) {
                    if (auto) evaluate(role, cached)
                    continue
                }
                var (notes, res) = api.notes(acc, role)
                if (notes == null && res.status == 200 && Errors.isExpiredLogin(res.retcode, res.message)) {
                    val renewed = recover(acc)
                    if (renewed != null) {
                        acc = renewed
                        api.notes(acc, role).let { notes = it.first; res = it.second }
                    }
                }
                val n = notes
                if (n == null) {
                    if (!auto || res.status == 200) out += repo.log(Level.ERROR, "Notes · ${who(role)}", res.reason())
                    if (res.status == 200 && Errors.isExpiredLogin(res.retcode, res.message)) notifyExpired(acc, auto)
                    continue
                }
                repo.updateState { it.copy(notes = it.notes + (role.key to n)) }
                if (!auto && !quiet) out += repo.log(
                    Level.OK, "Notes · ${who(role)}",
                    "${role.game.staminaName} ${n.stamina.current}/${n.stamina.max} · Dailies ${n.dailyDone}/${n.dailyMax}",
                )
                if (auto) evaluate(role, n)
            }
        }
        if (out.isEmpty() && !auto && !quiet) out += repo.log(Level.INFO, "Notes", "No Zenless Zone Zero account")
        return out
    }

    private fun once(key: String, cond: Boolean, enabled: Boolean, title: String, text: () -> String) {
        val fired = key in repo.state.value.fired
        if (cond && !fired) {
            repo.updateState { it.copy(fired = it.fired + key) }
            if (enabled) {
                notifier.notify(key, title, text())
                repo.log(Level.INFO, title, text())
            }
        } else if (!cond && fired) {
            repo.updateState { it.copy(fired = it.fired - key) }
        }
    }

    private fun evaluate(role: Role, n: Notes) {
        val s = repo.settings.value
        val k = role.key
        val threshold = minOf(s.batteryThreshold, n.stamina.max)

        once("stam:$k", n.stamina.max > 0 && n.stamina.current >= threshold, s.notifyStamina, "${proxy(role)}: Battery Almost Full!") {
            "${n.stamina.current}/${n.stamina.max}, Time to Farm!"
        }
        once("shop:$k", n.shopState == "Finished", s.notifyShop, "${proxy(role)}: Clocking Out!") { "Video Store is now Closed!" }

        val server = Instant.now().atOffset(ZoneOffset.ofHours(regionUtcOffset(role.region)))
        val gameDay = server.minusHours(4).toLocalDate().toString()
        val hourOfGameDay = server.minusHours(4).hour + 4
        val late = hourOfGameDay >= s.dailiesHour
        once("daily:$k:$gameDay", late && n.dailyMax > 0 && n.dailyDone < n.dailyMax, s.notifyDailies, "${proxy(role)}: Hey! You haven't done your Dailies yet!") {
            "Daily Activity ${n.dailyDone}/${n.dailyMax}."
        }
        once("card:$k:$gameDay", late && n.scratchCardDone == false, s.notifyScratchCard, "${proxy(role)}: Howl is Sad :(") {
            "Pending Scratch Card."
        }
        val sunday = server.dayOfWeek == java.time.DayOfWeek.SUNDAY && server.hour >= s.dailiesHour
        val weeklies = listOfNotNull(n.riduMax?.takeIf { (n.riduDone ?: 0) < it }?.let { "Ridu Weekly ${n.riduDone ?: 0}/$it" }) + n.pendingWeeklies()
        once("weekly:$k:${server.toLocalDate()}", sunday && weeklies.isNotEmpty(), s.notifyWeeklies, "${proxy(role)}: Week's ending, Get on it!") {
            weeklies.joinToString("\n") { "$it." }
        }

        repo.updateState { st ->
            st.copy(fired = st.fired.filterNot { ((it.startsWith("daily:$k:") || it.startsWith("card:$k:")) && !it.endsWith(":$gameDay")) ||
                (it.startsWith("weekly:$k:") && !it.endsWith(":${server.toLocalDate()}")) }.toSet())
        }
    }

    fun redeemCodes(auto: Boolean = false, manualCode: String? = null, onlyRole: String? = null): List<LogEntry> {
        val out = ArrayList<LogEntry>()
        val published = HashMap<Game, List<GiftCode>>()
        var tried = 0
        accounts@ for (listed in repo.accounts.value) {
            var acc = listed
            for (role in acc.activeRoles()) {
                if (!role.game.canRedeem) continue
                if (onlyRole != null && role.key != onlyRole) continue
                val codes = if (manualCode != null) listOf(GiftCode(manualCode.trim().uppercase()))
                else published.getOrPut(role.game) { api.fetchCodes(role.game) }
                val done = repo.state.value.doneCodes[role.key].orEmpty()
                val pending = codes.filter { manualCode != null || it.code !in done }
                if (pending.isEmpty()) continue
                if (!acc.canRedeem) {
                    val renewed = recover(acc)
                    if (renewed == null) {
                        out += repo.log(Level.WARN, "Codes · ${who(role)}", "cookie_token_v2 missing; log in again to redeem")
                        notifyExpired(acc, auto)
                        continue@accounts
                    }
                    acc = renewed
                }
                for (code in pending) {
                    tried++
                    var r = api.redeem(acc, role, code.code)
                    if (r.expired) {
                        val renewed = recover(acc)
                        if (renewed != null) { acc = renewed; r = api.redeem(acc, role, code.code) }
                    }
                    val title = "Code ${code.code} · ${who(role)}"
                    if (r.expired) {
                        out += repo.log(Level.ERROR, title, "Session expired; pending codes will be retried once the cookie is renewed")
                        notifyExpired(acc, auto)
                        continue@accounts
                    }
                    if (r.success || r.final) {
                        repo.updateState { st -> st.copy(doneCodes = st.doneCodes + (role.key to (st.doneCodes[role.key].orEmpty() + code.code))) }
                    }
                    if (r.success) {
                        val rewards = code.rewards.joinToString(", ").ifBlank { "Check the in-game mail" }
                        out += repo.log(Level.OK, title, "Redeemed · $rewards")
                        if (auto) notifier.notify("code:${role.key}:${code.code}", "${proxy(role)}: Here's Something for You.", "Code Redeemed: ${code.code} · $rewards.")
                    } else {
                        val quiet = auto && r.retcode in setOf(-2017, -2018)
                        if (!quiet) out += repo.log(if (r.final) Level.WARN else Level.ERROR, title, r.reason)
                    }
                }
            }
        }
        if (!auto && tried == 0) out += repo.log(Level.INFO, "Codes", "No new codes to redeem")
        return out
    }

    private val mimo = Mimo { API_LANG }

    private fun redeemFor(acc: Account, role: Role): (String) -> RedeemResult = { code ->
        val r = api.redeem(account(acc.ltuid) ?: acc, role, code)
        if (r.success || r.final) repo.updateState { st -> st.copy(doneCodes = st.doneCodes + (role.key to (st.doneCodes[role.key].orEmpty() + code.uppercase()))) }
        r
    }

    fun runMimo(auto: Boolean = false): List<LogEntry> {
        val out = ArrayList<LogEntry>()
        val s = repo.settings.value
        for (listed in repo.accounts.value) {
            for (role in listed.activeRoles()) {
                val title = "Traveling Mimo · ${who(role)}"
                fun run(acc: Account) = mimo.run(acc, role, MimoOptions(s.mimoLottery, s.mimoReservePoints, true), redeemFor(acc, role))

                var acc = account(listed.ltuid) ?: listed
                var rep = run(acc)
                if (rep.expired) recover(acc)?.let { acc = it; rep = run(acc) }
                if (!rep.success || rep.expired) {
                    if (!auto || rep.expired) out += repo.log(Level.ERROR, title, if (rep.expired) "The session expired or the cookie is not valid" else rep.message)
                    if (rep.expired) notifyExpired(acc, auto)
                    continue
                }
                val text = rep.lines.joinToString("\n")
                if (!auto || rep.activity) out += repo.log(if (rep.activity) Level.OK else Level.INFO, title, text.ifBlank { "Nothing new" })
                if (auto && rep.activity) notifier.notify("event:${role.key}", "${proxy(role)}: Traveling Mimo Stopped By!", rep.lines.joinToString("\n") { sentence(it) })
            }
        }
        if (out.isEmpty() && !auto) out += repo.log(Level.INFO, "Traveling Mimo", "No Zenless Zone Zero account")
        return out
    }

    val extrasApi = ExtrasApi { API_LANG }

    /** Each extras part has its own TTL (see [ExtrasTtl]); [force] refetches everything. */
    fun refreshExtras(force: Boolean = false, logErrors: Boolean = false) {
        val s = repo.settings.value
        for (listed in repo.accounts.value) {
            var acc = listed
            for (role in acc.activeRoles()) {
                val prev = repo.state.value.extras[role.key]
                var (x, expired) = extrasApi.fetch(acc, role, s, prev, force)
                if (expired) recover(acc)?.let { acc = it; x = extrasApi.fetch(acc, role, s, x).first }
                if (x != prev) repo.updateState { it.copy(extras = it.extras + (role.key to x)) }
                if (logErrors && x.errors.isNotEmpty())
                    repo.log(Level.WARN, "Extra info · ${who(role)}", x.errors.entries.joinToString("\n") { "${it.key}: ${it.value}" })
            }
        }
    }

    private val hour = 3600_000L

    companion object {
        const val NOTES_REUSE_MS = 2 * 60_000L
    }
    private val mimoEvery = 6 * hour
    private val codesEvery = hour

    private val checkInRetry = 30 * 60_000L
    private val notesRetry = 15 * 60_000L

    private fun nextCheckInReset(): Long =
        Instant.now().atOffset(ZoneOffset.ofHours(8)).toLocalDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.ofHours(8)).toEpochMilli() + 2 * 60_000L

    private fun checkInPending(): Boolean {
        val day = hoyoDay()
        return repo.accounts.value.any { acc -> gamesOf(acc).any { repo.state.value.checkInDay["${acc.ltuid}:${it.name}"] != day } }
    }

    private fun alertTimes(): List<Long> {
        val s = repo.settings.value
        val st = repo.state.value
        val out = ArrayList<Long>()
        for (role in repo.accounts.value.flatMap { it.activeRoles() }) {
            val n = st.notes[role.key] ?: continue
            val k = role.key
            if (s.notifyStamina && "stam:$k" !in st.fired && n.stamina.max > 0) {
                val threshold = minOf(s.batteryThreshold, n.stamina.max)
                val missing = (threshold - n.stamina.current).coerceAtLeast(0)

                out += n.fetchedAt + missing * 360_000L + 60_000L
            }
            if (s.notifyShop && n.shopState == "Open") out += n.fetchedAt + 30 * 60_000L
            if (s.notifyDailies || s.notifyScratchCard || s.notifyWeeklies) {
                val offset = ZoneOffset.ofHours(regionUtcOffset(role.region))
                val server = Instant.now().atOffset(offset)
                val gameDay = server.minusHours(4).toLocalDate()
                val at = gameDay.atTime(s.dailiesHour.coerceIn(4, 23), 0).toInstant(offset).toEpochMilli() + 60_000L
                val pendingToday = (s.notifyDailies && "daily:$k:$gameDay" !in st.fired && n.dailyDone < n.dailyMax) ||
                    (s.notifyScratchCard && "card:$k:$gameDay" !in st.fired && n.scratchCardDone != true) ||
                    (s.notifyWeeklies && server.dayOfWeek == java.time.DayOfWeek.SUNDAY && "weekly:$k:${server.toLocalDate()}" !in st.fired)

                if (now() < at) out += at
                else if (pendingToday && n.fetchedAt < at) out += at
            }
        }
        return out
    }

    private class Due(val full: Boolean, val checkIn: Boolean, val mimo: Boolean, val codes: Boolean, val notes: Boolean) {
        val any get() = full || checkIn || mimo || codes || notes
    }

    private val notesEvery = 30 * 60_000L
    private val extrasEvery = 6 * hour

    private fun todayAt(min: Int, t: Long = now()): Long {
        val zone = java.time.ZoneId.systemDefault()
        return Instant.ofEpochMilli(t).atZone(zone).toLocalDate().atStartOfDay(zone).plusMinutes(min.toLong()).toInstant().toEpochMilli()
    }

    private fun nextDailyAt(min: Int, last: Long, t: Long = now()): Long {
        val today = todayAt(min, t)
        return if (last < today) today else today + 24 * hour
    }

    private fun lastMimoReset(t: Long = now()): Long {
        var d = Instant.ofEpochMilli(t).atOffset(ZoneOffset.UTC).toLocalDate()
        while (d.dayOfWeek != java.time.DayOfWeek.WEDNESDAY) d = d.minusDays(1)
        var at = d.atTime(11, 0, 1).toInstant(ZoneOffset.UTC).toEpochMilli()
        if (at > t) at -= 7 * 24 * hour
        return at
    }

    private fun manualCheckInDue(s: Settings, t: Long): Boolean {
        if (!checkInPending()) return false
        val at = todayAt(s.manualCheckInMin, t)
        if (t < at) return false
        val last = repo.state.value.lastCheckInAttempt
        if (last < at) return true
        val sameHoyoDay = Instant.ofEpochMilli(last).atOffset(ZoneOffset.ofHours(8)).toLocalDate().toString() == hoyoDay()
        return sameHoyoDay && t - last >= checkInRetry - 60_000L
    }

    // Z-Sync Mode follows the hoyolab-auto cron schedule; otherwise each task runs at the user's chosen time.
    private fun due(): Due {
        val s = repo.settings.value
        val st = repo.state.value
        val t = now()
        val margin = 60_000L
        if (!s.autoEnabled) {
            val full = s.autoNotes && t - st.lastAutoRun >= s.autoIntervalMin.coerceIn(30, 720) * 60_000L - margin
            return Due(
                full = full,
                checkIn = s.autoCheckIn && manualCheckInDue(s, t),
                mimo = s.autoMimo && t >= todayAt(s.manualMimoMin, t) && st.lastMimoRun < todayAt(s.manualMimoMin, t),
                codes = s.autoCodes && t >= todayAt(s.manualCodesMin, t) && st.lastCodesCheck < todayAt(s.manualCodesMin, t),
                notes = full,
            )
        }

        val full = t - st.lastAutoRun >= extrasEvery - margin
        return Due(
            full = full,
            checkIn = checkInPending() && t - st.lastCheckInAttempt >= checkInRetry - margin,
            mimo = (t - st.lastMimoRun >= mimoEvery - margin || st.lastMimoRun < lastMimoReset(t)),
            codes = t - st.lastCodesCheck >= codesEvery - margin,
            notes = full || t - st.lastNotesAttempt >= notesEvery - margin ||
                (alertTimes().any { it <= t } && t - st.lastNotesAttempt >= notesRetry - margin),
        )
    }

    fun anythingDue(): Boolean = repo.accounts.value.isNotEmpty() && (due().any || cookiesDue())

    private fun cookiesDue() = repo.settings.value.keepStoken && now() - repo.state.value.lastCookieRefresh >= 2 * hour - 5 * 60_000L

    fun nextWakeAt(): Long {
        val s = repo.settings.value
        val st = repo.state.value
        val times = ArrayList<Long>()
        if (!s.autoEnabled) {
            if (s.autoNotes) times += st.lastAutoRun + s.autoIntervalMin.coerceIn(30, 720) * 60_000L
            if (s.autoCheckIn) times += if (manualCheckInDue(s, now())) st.lastCheckInAttempt + checkInRetry
                else nextDailyAt(s.manualCheckInMin, st.lastCheckInAttempt)
            if (s.autoMimo) times += nextDailyAt(s.manualMimoMin, st.lastMimoRun)
            if (s.autoCodes) times += nextDailyAt(s.manualCodesMin, st.lastCodesCheck)

            if (s.keepStoken) times += st.lastCookieRefresh + 2 * hour
            return times.min()
        }
        times += st.lastAutoRun + extrasEvery
        times += st.lastNotesAttempt + notesEvery
        times += if (checkInPending()) st.lastCheckInAttempt + checkInRetry else nextCheckInReset()
        times += minOf(st.lastMimoRun + mimoEvery, lastMimoReset() + 7 * 24 * hour)
        times += st.lastCodesCheck + codesEvery
        times += alertTimes().map { maxOf(it, st.lastNotesAttempt + notesRetry) }
        return times.min()
    }

    fun runAuto(force: Boolean = false) {
        val s = repo.settings.value
        if (repo.accounts.value.isEmpty()) return
        val d = if (force) Due(true, true, true, true, true) else due()
        if (cookiesDue()) refreshCookies(auto = true)
        if (!d.any) return
        if (d.checkIn) repo.updateState { it.copy(lastCheckInAttempt = now()) }
        if (d.notes) repo.updateState { it.copy(lastNotesAttempt = now()) }
        if (d.checkIn) runCatching { checkIn(auto = true) }.onFailure { repo.log(Level.ERROR, "Automatic check-in", it.toString()) }
        if (d.notes) runCatching { refreshNotes(auto = true, maxAgeMs = if (force) 0 else NOTES_REUSE_MS) }.onFailure { repo.log(Level.ERROR, "Automatic notes", it.toString()) }
        if (d.codes) {
            runCatching { redeemCodes(auto = true) }.onFailure { repo.log(Level.ERROR, "Automatic codes", it.toString()) }
            repo.updateState { it.copy(lastCodesCheck = now()) }
        }
        if (d.mimo) {
            runCatching { runMimo(auto = true) }.onFailure { repo.log(Level.ERROR, "Automatic Mimo", it.toString()) }
            repo.updateState { it.copy(lastMimoRun = now()) }
        }
        if (d.full) {
            runCatching { refreshExtras() }
            repo.updateState { it.copy(lastAutoRun = now()) }
        }
    }
}

fun formatDuration(sec: Long): String {
    if (sec <= 0) return "0 min"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return when {
        h >= 24 -> "${h / 24} d ${h % 24} h"
        h > 0 -> "$h h $m min"
        else -> "$m min"
    }
}
