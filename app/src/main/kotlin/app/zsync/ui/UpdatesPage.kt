package app.zsync.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zsync.MainVm
import app.zsync.UpdateState
import java.io.File

internal fun prettyVersion(v: String) = v

@Composable
internal fun UpdatesPage(vm: MainVm) {
    val ctx = LocalContext.current
    val state by vm.update.collectAsStateWithLifecycle()
    var needsPermission by remember { mutableStateOf(false) }

    fun install(apk: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            needsPermission = true
            runCatching {
                ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
            }
            return
        }
        needsPermission = false
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text("Current Version", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(prettyVersion(vm.appVersion()), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(8.dp))

    val note: String? = when (val s = state) {
        UpdateState.Idle -> null
        UpdateState.Checking -> "Checking GitHub…"
        UpdateState.UpToDate -> "You're on the latest version."
        UpdateState.Failed -> "Couldn't reach GitHub. Check your connection and try again."
        is UpdateState.Available -> "Version ${prettyVersion(s.release.version)} is available."
        is UpdateState.Downloading -> "Downloading ${prettyVersion(s.release.version)}… ${(s.progress * 100).toInt()}%"
        is UpdateState.Ready -> "Version ${prettyVersion(s.release.version)} is ready to install."
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    (state as? UpdateState.Downloading)?.let {
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { it.progress }, modifier = Modifier.fillMaxWidth())
    }
    if (needsPermission) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Allow Z-Sync to install apps, then tap Install again.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(16.dp))

    when (val s = state) {
        is UpdateState.Available -> Button(onClick = { vm.downloadUpdate(s.release) }, modifier = Modifier.fillMaxWidth()) {
            Text("Download and Install")
        }
        is UpdateState.Ready -> Button(onClick = { install(s.apk) }, modifier = Modifier.fillMaxWidth()) { Text("Install") }
        else -> Button(
            onClick = { vm.checkUpdate() },
            enabled = s !is UpdateState.Checking && s !is UpdateState.Downloading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Check for Updates") }
    }
}
