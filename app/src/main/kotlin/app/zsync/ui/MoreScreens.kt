package app.zsync.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zsync.MainVm
import app.zsync.core.GachaBanner
import app.zsync.core.GachaItem
import app.zsync.core.GameIcons
import app.zsync.core.Game8
import app.zsync.core.Game8Upcoming
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale

@Composable
fun GachaNextScreen(vm: MainVm, onBack: () -> Unit) {
    val accounts by vm.repo.accounts.collectAsStateWithLifecycle()
    val state by vm.repo.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val extras = remember(accounts, state.extras) { accounts.flatMap { it.activeRoles() }.mapNotNull { state.extras[it.key] }.firstOrNull() }
    val loading = ("notes" in busy || "extras" in busy) && (extras == null || (extras.fetchedAt == 0L))
    val context = LocalContext.current
    val official = remember(extras) { extras?.gachaNext.orEmpty() }
    val officialByVersion = remember(official) { official.groupBy { it.version ?: "?" }.entries.toList() }
    val game8 = remember(extras) { extras?.game8?.takeIf { it.items.isNotEmpty() } }
    val game8Rows = remember(game8) { game8?.let { game8Banners(it) } }
    val openGame8 = remember(context) {
        { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Game8.PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Page("Upcoming Banners", onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)) {
            when {
                official.isNotEmpty() -> {
                    items(officialByVersion.size, key = { "v-${officialByVersion[it].key}" }) { i ->
                        val (version, banners) = officialByVersion[i]
                        Section("Version $version")
                        Panel { banners.forEach { GachaBannerRow(it, null, upcoming = true, showPity = false) } }
                    }
                    item(key = "note") {
                        Note("Announced by HoYoVerse.")
                        SourceLine("HoYoLAB", null)
                    }
                }
                game8 != null && game8Rows != null -> item(key = "game8") {
                    Section("Version ${game8.version ?: "?"}")
                    Panel { game8Rows.forEach { GachaBannerRow(it, null, upcoming = true, showPity = false, dateOnly = true) } }
                    Note("Not Official: Based on what HoYoVerse has teased so far. Banners, phases and dates can change until they are announced in the game.")
                    SourceLine("Game8", openGame8)
                }
                loading -> item(key = "skeleton") {
                    Section("Version ?")
                    Panel { repeat(2) { GachaBannerSkeleton() } }
                }
                else -> item(key = "empty") {
                    Section("Version ?")
                    Panel {
                        GachaBannerRow(placeholder("Exclusive Agent", agent = true), null, upcoming = true, showPity = false)
                        GachaBannerRow(placeholder("Exclusive W-Engine", agent = false), null, upcoming = true, showPity = false)
                    }
                    Note("No upcoming banners have been announced or teased yet.")
                    SourceLine("Game8", openGame8)
                }
            }
        }
    }
}

private fun game8Banners(g: Game8Upcoming): List<GachaBanner> {
    val phases = g.items.groupBy { it.release ?: 0L }.toSortedMap(compareBy { if (it == 0L) Long.MAX_VALUE else it })
    return phases.flatMap { (release, items) ->
        val agents = items.filterNot { it.weapon }
        val weapons = items.filter { it.weapon }
        listOfNotNull(
            agents.takeIf { it.isNotEmpty() }?.let { list ->
                GachaBanner(
                    kind = "Exclusive Agent", endAt = 0, agent = true, version = g.version, startAt = release,
                    items = list.map { GachaItem(it.name, it.wikiImage ?: it.image, specialty = it.specialty, element = it.element, rarity = it.rarity ?: "S") },
                )
            },
            GachaBanner(
                kind = "Exclusive W-Engine", endAt = 0, agent = false, version = g.version, startAt = release,
                items = weapons.map { GachaItem(it.name, it.wikiImage ?: it.image, specialty = it.specialty, element = -1, rarity = it.rarity ?: "S") }
                    .ifEmpty { List(agents.size.coerceIn(1, 3)) { GachaItem("?", null, element = -1) } },
            ),
        )
    }
}

private fun placeholder(kind: String, agent: Boolean) = GachaBanner(
    kind = kind, endAt = 0, agent = agent,
    items = List(2) { GachaItem("?", null, element = if (agent) null else -1) },
)

@Composable
private fun Note(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp, end = 4.dp),
    )
}

@Composable
private fun SourceLine(name: String, onClick: (() -> Unit)?) {
    Row(Modifier.padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Source: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        )
    }
}

@Composable
fun PremiumScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Page("Premium", onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 32.dp)) {
            Section("Top-Up")
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GameIcons.TOPUP_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    SkeletonImage(
                        GameIcons.TOPUP_ICON, "Zenless Zone Zero",
                        Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)), shape = RoundedCornerShape(14.dp),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text("Zenless Zone Zero Top-Up Center", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Official HoYoVerse Page", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("sdk.hoyoverse.com", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open in browser", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Disclaimer: Third-party top-up sites and resellers are not affiliated with HoYoVerse. Buying through them may break " +
                    "HoYoVerse's Terms of Service, and there is always a risk of your account being suspended or banned, even when the " +
                    "purchase goes through. That's why this app only links to official HoYoVerse pages.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

@Composable
fun EndGameScreen(vm: MainVm, onBack: () -> Unit) {
    val accounts by vm.repo.accounts.collectAsStateWithLifecycle()
    val state by vm.repo.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val loading = "notes" in busy || "extras" in busy
    val challenges = remember(accounts, state.extras) { accounts.flatMap { it.activeRoles() }.mapNotNull { state.extras[it.key]?.challenges }.firstOrNull().orEmpty() }

    Page("End-Game", onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp)) {
            if (challenges.isEmpty()) item(key = "empty") {
                if (loading) repeat(2) { i ->
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Skeleton(Modifier.padding(end = 8.dp).size(width = 34.dp, height = 34.dp), RoundedCornerShape(6.dp))
                            Skeleton(Modifier.weight(1f).padding(end = 12.dp).height(22.dp), RoundedCornerShape(6.dp))
                            Skeleton(Modifier.width(80.dp).height(26.dp), RoundedCornerShape(6.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Skeleton(Modifier.width(160.dp).height(14.dp), RoundedCornerShape(4.dp))
                        Spacer(Modifier.height(8.dp))
                        repeat(3) { RoomSkeleton() }
                    }
                } else Panel { Text("No data yet. Pull down on the home screen to load it.", style = MaterialTheme.typography.bodyMedium) }
            }
            itemsIndexed(challenges, key = { i, c -> "${c.name}#$i" }) { i, c ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RankImage(c.rating.takeIf { c.played }, 34.dp, Modifier.padding(end = 8.dp))
                        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        RankNumber(c.score, c.rating, played = c.played, size = 26.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    val sub = listOfNotNull(
                        if (c.name == "Shiyu Defense") "Node 5" else null,

                        c.endAt?.let { "Cycle ${endsIn(it)}" },
                        c.rankPercent?.let { "Top $it" },
                    )
                    Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.rooms.isNotEmpty()) Spacer(Modifier.height(8.dp))
                    c.rooms.forEach { r -> RoomRow(r) }
                }
            }
        }
    }
}

@Composable
private fun RoomSkeleton() {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Skeleton(Modifier.size(44.dp), RoundedCornerShape(10.dp))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Skeleton(Modifier.fillMaxWidth(0.6f).height(18.dp), RoundedCornerShape(6.dp))
            Spacer(Modifier.height(4.dp))
            Skeleton(Modifier.width(60.dp).height(14.dp), RoundedCornerShape(4.dp))
        }
        Skeleton(Modifier.width(64.dp).height(20.dp), RoundedCornerShape(6.dp))
    }
}

@Composable
private fun RoomRow(r: app.zsync.core.ChallengeRoom) {
    val rating = if (r.score > 0) r.rating ?: r.stars?.let { when { it >= 3 -> "S"; it == 2 -> "A"; it == 1 -> "B"; else -> "X" } } else null
    val tint = rankTint(rating)
    val shape = RoundedCornerShape(10.dp)
    val sticker = when (rating) {
        "S+", "S" -> app.zsync.R.drawable.sticker_rank_s
        "A" -> app.zsync.R.drawable.sticker_rank_a
        "B" -> app.zsync.R.drawable.sticker_rank_b
        else -> app.zsync.R.drawable.sticker_rank_x
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(shape).background(tint.copy(alpha = 0.16f), shape), contentAlignment = Alignment.Center) {
            Image(
                painterResource(sticker), contentDescription = null, modifier = Modifier.size(46.dp)
                    .then(if (rating == null) Modifier.alpha(0.5f) else Modifier),
                colorFilter = if (rating == null) androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }) else null,
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                r.name, style = MaterialTheme.typography.bodyLarge,
                maxLines = if (r.adversity || r.stars != null) 2 else 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                RankImage(rating, 24.dp)
                r.stars?.let { stars ->
                    Text(
                        "★".repeat(stars.coerceIn(0, 3)) + "☆".repeat((3 - stars).coerceIn(0, 3)),
                        Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color(0xFFF2B531),
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            RankNumber(r.score, rating, played = r.score > 0, size = 17.sp)
            r.maxScore?.let { Text("of ${"%,d".format(java.util.Locale.US, it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
        }
    }
}
