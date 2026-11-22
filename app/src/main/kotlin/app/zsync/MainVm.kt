package app.zsync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zsync.core.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data object Failed : UpdateState()
    data class Available(val release: AppRelease) : UpdateState()
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState()
    data class Ready(val release: AppRelease, val apk: File) : UpdateState()
}

sealed class LoginStep {
    data object Form : LoginStep()
    data object Working : LoginStep()

    data class Captcha(val captcha: Auth.Captcha, val forEmail: EmailCode? = null) : LoginStep()
    data class EmailCode(val verifyHeader: String, val ticket: String, val sent: Boolean = true) : LoginStep()
    data object Done : LoginStep()
}

class MainVm(app: Application) : AndroidViewModel(app) {
    private val application = app as App
    val repo: Repo = application.repo
    private val engine: Engine = application.engine

    var homePage = 0

    val splashDone = MutableStateFlow(false)

    val busy = MutableStateFlow<Set<String>>(emptySet())

    private fun run(tag: String, block: () -> List<LogEntry>) {
        if (tag in busy.value) return
        busy.update { it + tag }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { application.exclusive(block) }
                    .getOrElse { listOf(repo.log(Level.ERROR, "Error", it.message ?: it.toString())) }
            }
            busy.update { it - tag }
        }
    }

    fun checkIn() = run("checkin") { engine.checkIn() }
    fun notes() = run("notes") { engine.refreshNotes() }
    fun codes() = run("codes") { engine.redeemCodes() }
    fun mimo() = run("mimo") { engine.runMimo() }

    /** Manual refresh (button, pull-to-refresh) forces everything; refreshAll(force = false) only fetches stale data. */
    fun refreshAll(force: Boolean = true) = run("notes") {
        val before = repo.log.value.firstOrNull()
        engine.refreshNotes(maxAgeMs = if (force) 0 else Engine.NOTES_REUSE_MS, quiet = !force)
        engine.refreshExtras(force = force, logErrors = force)
        repo.log.value.takeWhile { it !== before }
    }

    /** Call when the app opens: cached data is already on screen, this only refreshes what is stale. */
    fun refreshOnOpen() {
        if (repo.accounts.value.isNotEmpty()) refreshAll(force = false)
    }

    fun reloadExtras() {
        if ("extras" in busy.value || "notes" in busy.value) return
        busy.update { it + "extras" }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { application.exclusive { engine.refreshExtras() } } }
            busy.update { it - "extras" }
        }
    }

    private fun logged(empty: String, block: () -> Unit): List<LogEntry> {
        val before = repo.log.value.firstOrNull()
        block()
        return repo.log.value.takeWhile { it !== before }.ifEmpty { listOf(repo.log(Level.INFO, empty, "Nothing new")) }
    }

    fun testNotification(title: String, text: String) =
        AndroidNotifier(application).notify("debug:$title", title, text)

    fun runAutoNow() = AutoScheduler.enqueueRun(application)

    fun clearFiredAlerts() = repo.updateState { it.copy(fired = emptySet()) }

    init {
        // Banners and reward art of events that just ended are dropped from the image cache.
        viewModelScope.launch {
            var seen: Set<String>? = null
            repo.state.collect { st ->
                val live = st.extras.values.flatMap { x ->
                    x.events.orEmpty().filter { !it.ended }.flatMap { e -> listOfNotNull(e.bannerUrl, e.rewardIcon) + e.extras.mapNotNull { it.icon } } -
                        x.itemIcons.values.toSet()
                }.toSet()
                val gone = seen.orEmpty() - live
                if (gone.isNotEmpty()) withContext(Dispatchers.IO) {
                    val loader = coil.Coil.imageLoader(application)
                    gone.forEach { url ->
                        loader.diskCache?.remove(url)
                        loader.memoryCache?.keys?.filter { it.key == url }?.forEach { loader.memoryCache?.remove(it) }
                    }
                }
                seen = live
            }
        }
    }

    val update = MutableStateFlow<UpdateState>(UpdateState.Idle)

    fun appVersion(): String = runCatching {
        application.packageManager.getPackageInfo(application.packageName, 0).versionName
    }.getOrNull().orEmpty()

    fun checkUpdate() {
        if (update.value is UpdateState.Checking || update.value is UpdateState.Downloading) return
        update.value = UpdateState.Checking
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { Updates.latest() }
            update.value = when {
                latest == null -> UpdateState.Failed
                Updates.isNewer(latest.version, appVersion()) -> UpdateState.Available(latest)
                else -> UpdateState.UpToDate
            }
        }
    }

    fun downloadUpdate(release: AppRelease) {
        if (update.value is UpdateState.Downloading) return
        update.value = UpdateState.Downloading(release, 0f)
        viewModelScope.launch {
            val file = File(application.cacheDir, "updates/Z-Sync-${release.version}.apk")
            val ok = withContext(Dispatchers.IO) {
                Updates.download(release.apkUrl, file) { p -> update.value = UpdateState.Downloading(release, p) }
            }
            update.value = if (ok) UpdateState.Ready(release, file) else UpdateState.Failed
        }
    }

    fun refreshCookies() = run("cookies") { logged("Renew cookies") { engine.refreshCookies() } }
    fun syncRoles(ltuid: String) = run("sync") { listOf(repo.log(Level.INFO, "Game account", engine.syncRoles(ltuid))) }

    fun removeAccount(ltuid: String) = engine.removeAccount(ltuid)

    fun setAuto(enabled: Boolean) {
        repo.updateSettings { it.copy(autoEnabled = enabled) }
        App.scheduleAuto(application, true, repo.settings.value.autoIntervalMin, replace = true)
        AutoScheduler.sync(application)
    }

    fun setInterval(minutes: Int) {
        repo.updateSettings { it.copy(autoIntervalMin = minutes) }
        App.scheduleAuto(application, true, minutes, replace = true)
        AutoScheduler.sync(application)
    }

    fun setSchedule(f: (Settings) -> Settings) {
        repo.updateSettings(f)
        AutoScheduler.sync(application)
    }

    fun setIcon(key: String) {
        repo.updateSettings { it.copy(iconKey = key) }
        runCatching { AppIcons.apply(application, key) }
    }

    fun setKeepStoken(keep: Boolean) = application.exclusive { engine.setKeepStoken(keep) }

    val loginStep = MutableStateFlow<LoginStep>(LoginStep.Form)
    val loginError = MutableStateFlow<String?>(null)

    private var email = ""
    private var password = ""
    private var deviceId = ""

    fun resetLogin() {
        email = ""; password = ""
        loginStep.value = LoginStep.Form
        loginError.value = null
    }

    fun login(user: String, pass: String) {
        email = user.trim(); password = pass

        deviceId = repo.settings.value.deviceId.ifEmpty {
            (repo.accounts.value.firstOrNull { it.deviceId.isNotEmpty() }?.deviceId ?: Auth.newDeviceId())
                .also { id -> repo.updateSettings { it.copy(deviceId = id) } }
        }
        attempt()
    }

    private fun attempt(aigis: String? = null, verifyHeader: String? = null) {
        loginStep.value = LoginStep.Working
        loginError.value = null
        viewModelScope.launch {
            val model = android.os.Build.MODEL ?: "Android"
            val r = withContext(Dispatchers.IO) {
                runCatching { Auth.login(email, password, deviceId, aigis, verifyHeader, "Z-Sync", model) }
                    .getOrElse { Auth.LoginResult.Error(it.message ?: "Unexpected error") }
            }
            when (r) {
                is Auth.LoginResult.NeedCaptcha -> loginStep.value = LoginStep.Captcha(r.captcha)
                is Auth.LoginResult.NeedEmailCode -> sendCode(LoginStep.EmailCode(r.verifyHeader, r.ticket))
                is Auth.LoginResult.Error -> fail(r.message)
                is Auth.LoginResult.Success -> finish(r.cookies)
            }
        }
    }

    private fun fail(msg: String) {
        loginError.value = msg
        loginStep.value = LoginStep.Form
    }

    private suspend fun finish(cookies: Map<String, String>) {
        val (acc, err) = withContext(Dispatchers.IO) { application.exclusive { engine.addAccount(cookies, deviceId) } }
        if (acc == null) fail(err) else {
            email = ""; password = ""
            AutoScheduler.sync(application)
            loginStep.value = LoginStep.Done
        }
    }

    fun sendCode(step: LoginStep.EmailCode, aigis: String? = null) {
        loginStep.value = LoginStep.Working
        loginError.value = null
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { Auth.sendEmailCode(step.ticket, aigis) }.getOrElse { Auth.SendResult.Error(it.message ?: "Unexpected error") }
            }
            when (r) {
                is Auth.SendResult.Sent -> loginStep.value = step.copy(sent = true)
                is Auth.SendResult.NeedCaptcha -> loginStep.value = LoginStep.Captcha(r.captcha, forEmail = step)
                is Auth.SendResult.Error -> {
                    loginError.value = "Could not request the code: ${r.message}. If you already received one by email, enter it anyway."
                    loginStep.value = step.copy(sent = false)
                }
            }
        }
    }

    fun captchaSolved(step: LoginStep.Captcha, validateJson: String) {
        val aigis = Auth.aigisHeader(step.captcha.sessionId, validateJson)
        if (step.forEmail != null) sendCode(step.forEmail, aigis) else attempt(aigis = aigis)
    }

    fun captchaCancelled(step: LoginStep.Captcha, reason: String?) {
        if (step.forEmail != null) {
            loginError.value = reason ?: "Captcha cancelled"
            loginStep.value = step.forEmail.copy(sent = false)
        } else fail(reason ?: "Captcha cancelled")
    }

    fun submitEmailCode(step: LoginStep.EmailCode, code: String) {
        loginStep.value = LoginStep.Working
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) { Auth.verifyEmailCode(step.ticket, code.trim()) }
            if (err != null) {
                loginError.value = err
                loginStep.value = step
            } else attempt(verifyHeader = step.verifyHeader)
        }
    }

    fun loginWithCookie(raw: String) {
        loginStep.value = LoginStep.Working
        loginError.value = null
        viewModelScope.launch { finish(parseCookieString(raw)) }
    }
}
