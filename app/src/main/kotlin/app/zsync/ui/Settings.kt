package app.zsync.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zsync.MainVm
import app.zsync.R
import app.zsync.core.Account
import app.zsync.core.Game
import app.zsync.core.Settings
import java.time.*

private enum class SettingsPage {
    ROOT, ACCOUNT, AUTO, FEATURES, PERSONAL, NOTIFY, UPDATES, DEBUG;

    val title: String get() = when (this) {
        ROOT -> "Settings"
        ACCOUNT -> "Account"
        AUTO -> "Z-Sync"
        FEATURES -> "Features"
        PERSONAL -> "Personalization"
        NOTIFY -> "Notifications"
        UPDATES -> "Updates"
        DEBUG -> "Debug"
    }
}

// Ko-fi page name, as in ko-fi.com/<name>. The Support button stays hidden while it is empty.
private const val KOFI_USER = "eldrito"

private val INTERVALS = listOf(30, 60, 120, 180, 240, 360, 480, 720)

@Composable
fun SettingsScreen(vm: MainVm, openAccount: Boolean = false, onAccountOpened: () -> Unit = {}, onBack: () -> Unit, onLogin: () -> Unit) {
    val accounts by vm.repo.accounts.collectAsStateWithLifecycle()
    val s by vm.repo.settings.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(SettingsPage.ROOT) }
    LaunchedEffect(openAccount) { if (openAccount) { page = SettingsPage.ACCOUNT; onAccountOpened() } }
    fun set(f: (Settings) -> Settings) = vm.repo.updateSettings(f)

    fun up() { page = SettingsPage.ROOT }
    BackHandler(page != SettingsPage.ROOT) { up() }

    var about by remember { mutableStateOf(false) }
    if (about) AboutDialog { about = false }
    Page(
        page.title, onBack = { if (page == SettingsPage.ROOT) onBack() else up() }, backdrop = false,
        actions = {
            if (page == SettingsPage.ROOT) IconButton(onClick = { about = true }) {
                Icon(Icons.Outlined.Info, contentDescription = "About")
            }
        },
    ) { pad ->
        if (page == SettingsPage.PERSONAL) {
            PersonalizationPage(s.iconKey, pad) { vm.setIcon(it) }
            return@Page
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 40.dp),
        ) {
            when (page) {
                SettingsPage.ROOT -> {
                    MenuRow("Account", icon = rememberVectorPainter(Icons.Outlined.AccountCircle)) { page = SettingsPage.ACCOUNT }
                    MenuRow("Z-Sync", icon = painterResource(R.drawable.ic_zzz)) { page = SettingsPage.AUTO }
                    MenuRow("Features", icon = painterResource(R.drawable.ic_auto_awesome)) { page = SettingsPage.FEATURES }
                    MenuRow("Personalization", icon = painterResource(R.drawable.ic_brush)) { page = SettingsPage.PERSONAL }
                    MenuRow("Notifications", icon = rememberVectorPainter(Icons.Outlined.Notifications)) { page = SettingsPage.NOTIFY }
                    MenuRow("Updates", icon = painterResource(R.drawable.ic_download)) { page = SettingsPage.UPDATES }
                    if (s.debugUnlocked) MenuRow("Debug", icon = painterResource(R.drawable.ic_bug_report)) { page = SettingsPage.DEBUG }
                }
                SettingsPage.ACCOUNT -> AccountPage(vm, accounts, s, onLogin)
                SettingsPage.AUTO -> AutoPage(vm, s, ::set)
                SettingsPage.FEATURES -> {
                    fun extra(on: Boolean, f: (Settings) -> Settings) { set(f); if (on) vm.reloadExtras() }
                    Section("Status")
                    Toggle("Profile", s.showProfile) { v -> set { it.copy(showProfile = v) } }
                    Toggle("Battery", s.showBattery) { v -> set { it.copy(showBattery = v) } }
                    Toggle("Daily", s.showDaily) { v -> set { it.copy(showDaily = v) } }
                    Toggle("Bounty Commission", s.showBounty) { v -> set { it.copy(showBounty = v) } }
                    Toggle("Ridu Weekly", s.showRidu) { v -> set { it.copy(showRidu = v) } }
                    Toggle("Scratch Card", s.showScratch) { v -> set { it.copy(showScratch = v) } }
                    Toggle("Video Store", s.showStore) { v -> set { it.copy(showStore = v) } }
                    Toggle("Investigation Points", s.showSurvey) { v -> set { it.copy(showSurvey = v) } }
                    Toggle("Suibian Temple", s.showTemple) { v -> set { it.copy(showTemple = v) } }
                    Toggle("Traveling Mimo", s.showMimo) { v -> extra(v) { it.copy(showMimo = v) } }
                    Toggle("HoYoLAB Check-In", s.showCheckInCalendar) { v -> extra(v) { it.copy(showCheckInCalendar = v) } }
                    Toggle("End-Game", s.optChallenges) { v -> extra(v) { it.copy(optChallenges = v) } }
                    Toggle("Premium", s.showPremium) { v -> set { it.copy(showPremium = v) } }
                    if (s.showPremium) Toggle("Inter-Knot Membership", s.showMember) { v -> set { it.copy(showMember = v) } }
                    Section("Resources")
                    Toggle("Currencies", s.showCurrency) { v -> extra(v) { it.copy(showCurrency = v) } }
                    Toggle("Gacha", s.showGacha) { v -> extra(v) { it.copy(showGacha = v) } }
                    Toggle("Active Events", s.showEvents) { v -> extra(v) { it.copy(showEvents = v) } }
                    Toggle("Monthly Report", s.showLedger) { v -> extra(v) { it.copy(showLedger = v) } }
                }
                SettingsPage.UPDATES -> UpdatesPage(vm)
                SettingsPage.DEBUG -> DebugPage(vm) { set { it.copy(debugUnlocked = false) }; up() }
                SettingsPage.PERSONAL -> Unit
                SettingsPage.NOTIFY -> {
                    Toggle("Check-In", "When the daily Check-In is claimed", s.notifyCheckIn) { v -> set { it.copy(notifyCheckIn = v) } }
                    Toggle("Battery Threshold", "Right when it reaches ${s.batteryThreshold}/${Game.ZZZ.maxStamina}", s.notifyStamina) { v -> set { it.copy(notifyStamina = v) } }
                    if (s.notifyStamina) Slider(
                        value = s.batteryThreshold.toFloat(), valueRange = 20f..Game.ZZZ.maxStamina.toFloat(), steps = (Game.ZZZ.maxStamina - 20) / 10 - 1,
                        onValueChange = { v -> set { it.copy(batteryThreshold = Math.round(v / 10) * 10) } },
                    )
                    Toggle("Dailies Pending", "At ${s.dailiesHour}:00, your server's time, if they are not done", s.notifyDailies) { v -> set { it.copy(notifyDailies = v) } }
                    Toggle("Weeklies Pending", "Sundays at ${s.dailiesHour}:00, your server's time", s.notifyWeeklies) { v -> set { it.copy(notifyWeeklies = v) } }
                    Toggle("Scratch Card Not Claimed", "At ${s.dailiesHour}:00, your server's time, if it is not claimed", s.notifyScratchCard) { v -> set { it.copy(notifyScratchCard = v) } }
                    Toggle("Video Store Finished", s.notifyShop) { v -> set { it.copy(notifyShop = v) } }
                }
            }
        }
    }
}

@Composable
private fun AccountPage(vm: MainVm, accounts: List<Account>, s: Settings, onLogin: () -> Unit) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf<Account?>(null) }
    var confirmDisable by remember { mutableStateOf(false) }
    var needLogin by remember { mutableStateOf(false) }

    if (accounts.isEmpty()) Note("You haven't logged in yet.")
    for (acc in accounts) {
        Panel {
            val role = acc.activeRoles().firstOrNull()
            Text(role?.nickname?.ifBlank { null } ?: acc.label.ifBlank { "Account" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Field("HoYoLAB Account", acc.ltuid)
            if (role != null) {
                Field("Zenless Zone Zero UID", role.uid)
                Field("Server", role.regionName)
                Field("Inter-Knot Level", if (role.level > 0) role.level.toString() else "—")
            } else Field("Zenless Zone Zero", "No game account found", warn = true)
            Row(Modifier.offset(x = (-12).dp)) {
                TextButton(onClick = { vm.syncRoles(acc.ltuid) }, enabled = "sync" !in busy) { Text("Refresh Data") }
                TextButton(onClick = { confirmRemove = acc }) { Text("Remove Account", color = MaterialTheme.colorScheme.error) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    OutlinedButton(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
        Text(if (accounts.isEmpty()) "Log in" else "Add Another Account")
    }

    Section("Security")
    Toggle(
        "Automatic Cookie Renewal",
        "Saves the Stoken (encrypted, only on this phone) to renew the cookies every 2 hours without you doing anything.",
        s.keepStoken,
    ) { on ->
        if (!on) confirmDisable = true
        else {
            vm.setKeepStoken(true)
            if (accounts.any { !it.hasStoken }) needLogin = true
        }
    }

    if (confirmDisable) AlertDialog(
        onDismissRequest = { confirmDisable = false },
        title = { Text("Turn off Automatic Renewal?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("The Stoken saved on this phone is deleted and will not be saved again. Before you do it, keep in mind:")
                Text("• When the Cookies expire you will have to renew them by hand: log in again or paste a new Cookie.")
                Text("• Until you do, Check-In, Code Redemption, the game status and Mimo stop working. The app alerts you when the session expires, but it cannot fix it on its own.")
                Text("• Code Redemption is the first thing to fail, because its Cookie is the shortest-lived.")
                Text("• To turn it back on you have to log in again with email and password.")
            }
        },
        confirmButton = { TextButton(onClick = { vm.setKeepStoken(false); confirmDisable = false }) { Text("Turn off", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text("Keep on") } },
    )

    if (needLogin) AlertDialog(
        onDismissRequest = { needLogin = false },
        title = { Text("Login needed") },
        text = { Text("Automatic renewal is on, but the stoken had been deleted. Log in again with email and password to get it.") },
        confirmButton = { TextButton(onClick = { needLogin = false; onLogin() }) { Text("Log in") } },
        dismissButton = { TextButton(onClick = { needLogin = false }) { Text("Later") } },
    )

    confirmRemove?.let { acc ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove the account?") },
            text = { Text("The saved cookies of ${acc.label.ifBlank { acc.ltuid }} will be deleted from this phone.") },
            confirmButton = { TextButton(onClick = { vm.removeAccount(acc.ltuid); confirmRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutoPage(vm: MainVm, s: Settings, set: ((Settings) -> Settings) -> Unit) {
    val state by vm.repo.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    Toggle("Z-Sync Mode", checked = s.autoEnabled, onChange = vm::setAuto)

    if (s.autoEnabled) {
        Section("Schedule")
        val reset = LocalDate.now(ZoneOffset.ofHours(8)).atStartOfDay(ZoneOffset.ofHours(8))
            .withZoneSameInstant(ZoneId.systemDefault()).toLocalTime()
        ScheduleRow("Check-In", "Daily at ${reset.toString().take(5)} (HoYoLAB Reset)")
        ScheduleRow("Game Status & Alerts", "Every 30 minutes")
        ScheduleRow("Redeem Codes", "Every hour")
        ScheduleRow("Traveling Mimo", "Every 6 hours")
        ScheduleRow("Home Screen Information", "Every 6 hours")
        ScheduleRow("Cookie Renewal", if (s.keepStoken) "Every 2 hours" else "Off")
        Spacer(Modifier.height(6.dp))
        MimoLottery(s, vm)
    } else {
        Section("Your schedule")
        Note("Choose when each task runs, or turn off the ones you don't want.")
        TimedTask("Check-In", s.autoCheckIn, s.manualCheckInMin,
            onToggle = { v -> vm.setSchedule { it.copy(autoCheckIn = v) } }, onTime = { m -> vm.setSchedule { it.copy(manualCheckInMin = m) } })
        TimedTask("Redeem Codes", s.autoCodes, s.manualCodesMin,
            onToggle = { v -> vm.setSchedule { it.copy(autoCodes = v) } }, onTime = { m -> vm.setSchedule { it.copy(manualCodesMin = m) } })
        TimedTask("Traveling Mimo", s.autoMimo, s.manualMimoMin,
            onToggle = { v -> vm.setSchedule { it.copy(autoMimo = v) } }, onTime = { m -> vm.setSchedule { it.copy(manualMimoMin = m) } })
        if (s.autoMimo) MimoLottery(s, vm)

        Toggle("Game Status & Alerts", checked = s.autoNotes) { v -> vm.setSchedule { it.copy(autoNotes = v) } }
        if (s.autoNotes) {
            var pickInterval by rememberSaveable { mutableStateOf(false) }
            OutlinedButton(onClick = { pickInterval = !pickInterval }) {
                Text("Every ${intervalLabel(s.autoIntervalMin)}")
                Icon(if (pickInterval) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.padding(start = 6.dp))
            }
            if (pickInterval) FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                for (m in INTERVALS) FilterChip(
                    selected = s.autoIntervalMin == m,
                    onClick = { vm.setInterval(m); pickInterval = false }, label = { Text(intervalLabel(m)) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Toggle("Add Buttons to the Home Screen", "Check-In, Codes, Notes and Mimo by hand", s.showActions) { v -> set { it.copy(showActions = v) } }
    }

    Section("Battery")
    Note("The app works in the background without a permanent notification. While the phone is idle, Android may delay it by some minutes; disabling battery optimization for this app makes it more reliable.")
    val pm = ctx.getSystemService(PowerManager::class.java)

    var unrestricted by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        unrestricted = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        onPauseOrDispose {}
    }
    if (unrestricted) {
        Text("Battery optimization is disabled for this app", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
    } else {
        Text(
            "Disable Battery Optimization for this App", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable {
                runCatching {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                }.onFailure {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                }
            }.padding(vertical = 8.dp),
        )
    }
}

@Composable
internal fun MenuRow(title: String, subtitle: String? = null, onClick: () -> Unit) = MenuRow(title, subtitle, null, onClick)

@Composable
internal fun MenuRow(title: String, subtitle: String? = null, icon: Painter?, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 14.dp).size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Field(label: String, value: String, warn: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = if (warn) WarnColor else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
internal fun Toggle(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) = Toggle(title, null, checked, onChange)

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Wordmark(22.dp) },
        text = {
            Column {
                Text(
                    "Version ${prettyVersion(version)}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                )
                Text(
                    "Inspired by hoyolab-auto by torikushiii.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Disclaimer: Z-Sync is a free fan project, distributed only on GitHub. None of the names, images, " +
                        "icons, stickers, sounds or other game content shown in the app belong to me: they belong to their " +
                        "respective owners, and all credit goes to them. That includes HoYoVerse (Zenless Zone Zero and " +
                        "HoYoLAB), torikushiii (hoyolab-auto), zzze.ro (sticker collection), Game8 and the Zenless Zone Zero " +
                        "Fandom wiki. Z-Sync is not affiliated with, endorsed by or connected to HoYoVerse in any way.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Z-Sync is free and always will be. If you enjoy it, you can support the project with any amount.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/$KOFI_USER")))
                }) { Text("Support on Ko-fi") }
                Spacer(Modifier.height(16.dp))
                Text("Made with \u2764\uFE0F from Argentina.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        },
    )
}

@Composable
private fun ScheduleRow(task: String, time: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(task, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            time, style = MaterialTheme.typography.bodyMedium,
            color = if (time == "Off") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TimedTask(title: String, on: Boolean, minutes: Int, onToggle: (Boolean) -> Unit, onTime: (Int) -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (on) TextButton(onClick = {
            android.app.TimePickerDialog(
                ctx, { _, h, m -> onTime(h * 60 + m) }, minutes / 60, minutes % 60,
                android.text.format.DateFormat.is24HourFormat(ctx),
            ).show()
        }) { Text("Daily at %02d:%02d".format(minutes / 60, minutes % 60)) }
        Switch(checked = on, onCheckedChange = onToggle, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun MimoLottery(s: Settings, vm: MainVm) {
    Toggle("Mimo Lottery", "Only with points left over after the weekly Polychromes", s.mimoLottery) { v -> vm.setSchedule { it.copy(mimoLottery = v) } }
}

@Composable
private fun PersonalizationPage(selected: String, pad: PaddingValues, onPick: (String) -> Unit) {
    var picked by remember(selected) { mutableStateOf(selected) }
    val shape = remember { RoundedCornerShape(16.dp) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4), modifier = Modifier.fillMaxSize().padding(pad),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title", span = { GridItemSpan(maxLineSpan) }) { Section("App Icon") }
        items(app.zsync.Characters.all, key = { it.key }) { c ->
            val on = c.key == picked
            Column(
                Modifier.clip(RoundedCornerShape(14.dp)).clickable { picked = c.key; onPick(c.key) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                coil.compose.AsyncImage(
                    model = c.image, contentDescription = c.name,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                        .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
                )
            }
        }
    }
}
