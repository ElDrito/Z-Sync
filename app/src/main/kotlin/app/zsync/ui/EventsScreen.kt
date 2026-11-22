package app.zsync.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zsync.MainVm
import app.zsync.core.Extras
import app.zsync.core.GameEvent
import app.zsync.core.ItemIcon

private enum class EventKind { UPCOMING, RUNNING, ENDED }

@Composable
fun EventsScreen(vm: MainVm, onBack: () -> Unit) {
    val accounts by vm.repo.accounts.collectAsStateWithLifecycle()
    val state by vm.repo.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val loading = "notes" in busy || "extras" in busy
    val extras = remember(accounts, state.extras) { accounts.flatMap { it.activeRoles() }.mapNotNull { r -> state.extras[r.key]?.let { r.key to it } } }

    Page("Events", onBack) { pad ->
        val none = extras.all { it.second.events == null }
        if (none && !loading) {
            Text("No event data yet.", Modifier.padding(pad).padding(horizontal = 16.dp).padding(top = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Page
        }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)) {
            if (none) {
                item(key = "skeleton") {
                    Section("In Progress")
                    repeat(3) { Panel(vertical = 8.dp) { EventRowSkeleton() }; Spacer(Modifier.height(10.dp)) }
                }
                return@LazyColumn
            }
            for ((roleKey, x) in extras) eventItems(roleKey, x)
        }
    }
}

private fun LazyListScope.eventItems(roleKey: String, x: Extras) {
    val icon = x.itemIcons[ItemIcon.POLYCHROME]
    val all = x.events.orEmpty()
    val upcoming = all.filter { !it.started }
    val running = all.filter { it.started && !it.ended }
    val ended = all.filter { it.ended }

    if (upcoming.isNotEmpty()) {
        item(key = "$roleKey-h-up") { Section("Coming Soon") }
        eventList(roleKey, EventKind.UPCOMING, upcoming, icon)
    }
    item(key = "$roleKey-h-run") { Section("In Progress") }
    if (running.isEmpty()) item(key = "$roleKey-none") { Text("No events in progress.", style = MaterialTheme.typography.bodyMedium) }
    eventList(roleKey, EventKind.RUNNING, running, icon)
    if (ended.isNotEmpty()) {
        item(key = "$roleKey-h-end") { Section("Ended") }
        eventList(roleKey, EventKind.ENDED, ended, icon)
    }
}

private fun LazyListScope.eventList(roleKey: String, kind: EventKind, list: List<GameEvent>, icon: String?) {
    items(list.size, key = { "$roleKey-$kind-${list[it].name}-${list[it].startAt}-$it" }) { i ->
        val e = list[i]
        when (kind) {
            EventKind.UPCOMING, EventKind.ENDED -> Surface(
                color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B0B0C) else Color(0xFFD9D9D6),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    if (kind == EventKind.UPCOMING) EventArt(e, dim = true)
                    Row(Modifier.padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 12.dp).size(18.dp))
                        Column(Modifier.weight(1f)) { EventRow(e, icon, locked = kind == EventKind.UPCOMING, ended = kind == EventKind.ENDED) }
                    }
                }
            }
            EventKind.RUNNING -> Surface(
                color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    EventArt(e)
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { EventRow(e, icon) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
