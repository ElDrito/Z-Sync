package app.zsync.ui

import android.app.NotificationManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zsync.BatteryWidget
import app.zsync.MainVm
import app.zsync.core.Level
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TEST_NOTIFICATIONS = listOf(
    "Check-In" to ("Ka-Check-In!" to "Reward: Dennies x4,000 (14 Days in a Row!)."),
    "Battery Threshold" to ("Battery Almost Full!" to "220/240, Time to Farm!"),
    "Dailies Pending" to ("Hey! You haven't done your Dailies yet!" to "Daily Activity 200/400."),
    "Weeklies Pending" to ("Week's ending, Get on it!" to "Ridu Weekly 1500/2100."),
    "Scratch Card Not Claimed" to ("Howl is Sad :(" to "Pending Scratch Card."),
    "Video Store Finished" to ("Clocking Out!" to "Video Store is now Closed!"),
    "Code Redeemed" to ("Here's Something for You." to "Code Redeemed: ZZZFREE · Polychrome x60."),
    "Tokens Renewed" to ("Your Tokens had been Renewed!" to "You got it, Boss!"),
    "Tokens Not Renewed" to ("Your Tokens couldn't be Renewed!" to "Tap to Log in Again."),
    "Check-In Failed" to ("Check-In Failed!" to "Already checked in today."),
    "Traveling Mimo" to ("Traveling Mimo Stopped By!" to "Tasks claimed: 2 (+15 pts).\n- Visit the Zenless Zone Zero community today (+5).\n- Check-in once in Zenless Zone Zero (+10).\nCurrent points: 875."),
    "Session Expired" to ("Your Session has Expired!" to "Tap to Renew your Cookies."),
)

@Composable
internal fun DebugPage(vm: MainVm, onHide: () -> Unit) {
    val ctx = LocalContext.current
    val log by vm.repo.log.collectAsStateWithLifecycle()
    val accounts by vm.repo.accounts.collectAsStateWithLifecycle()
    val proxy = accounts.flatMap { it.activeRoles() }.firstOrNull()?.let { it.nickname.ifBlank { it.uid } } ?: "Proxy"
    fun toast(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()

    Section("Test Notifications")
    TEST_NOTIFICATIONS.forEach { (label, n) ->
        MenuRow(label) {
            if (!ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) toast("Notifications Are Off for Z-Sync")
            else vm.testNotification("$proxy: ${n.first}", n.second)
        }
    }

    Section("Run Now")
    MenuRow("Check-In") { vm.checkIn() }
    MenuRow("Refresh Status") { vm.notes() }
    MenuRow("Redeem Codes") { vm.codes() }
    MenuRow("Traveling Mimo") { vm.mimo() }
    MenuRow("Renew Cookies") { vm.refreshCookies() }
    MenuRow("Z-Sync Run", "Runs every task that is due, like the background service") { vm.runAutoNow(); toast("Queued") }
    MenuRow("Update Widget") { BatteryWidget.updateAll(ctx); toast("Widget Updated") }
    MenuRow("Clear Sent Alerts", "Lets alerts that already fired today fire again") { vm.clearFiredAlerts(); toast("Cleared") }

    Section("Log")
    val fmt = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }
    if (log.isEmpty()) Text("Empty", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    log.take(40).forEach { e ->
        val color = when (e.level) {
            Level.ERROR -> MaterialTheme.colorScheme.error
            Level.WARN -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("${fmt.format(Date(e.time))}  ${e.level}  ${e.title}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = color)
            Text(e.text, style = MaterialTheme.typography.bodySmall)
        }
    }

    Spacer(Modifier.height(16.dp))
    MenuRow("Hide Debug Menu", "Tap the Z-Sync logo 5 times to bring it back", onHide)
}
