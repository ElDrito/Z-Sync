package app.zsync.ui

import android.widget.Toast
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ImageBitmap
import android.graphics.Bitmap
import java.util.Locale
import java.time.ZoneOffset
import androidx.compose.foundation.background
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import coil.compose.AsyncImage
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import app.zsync.core.Resets
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil.imageLoader
import coil.request.ImageRequest
import androidx.compose.ui.draw.clipToBounds
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zsync.MainVm
import app.zsync.core.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainVm, onSettings: () -> Unit, onLogin: () -> Unit, onEvents: () -> Unit,
    onGachaNext: () -> Unit, onPremium: () -> Unit, onEndGame: () -> Unit = {},
) {
    val accounts by vm.repo.accounts.collectAsStateWithLifecycle()
    val settings by vm.repo.settings.collectAsStateWithLifecycle()
    val state by vm.repo.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    val nowState = produceState(System.currentTimeMillis()) { while (true) { delay(30_000); value = System.currentTimeMillis() } }
    val now: () -> Long = remember { { nowState.value } }
    val loading = "notes" in busy || "extras" in busy
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refreshOnOpen()
        onPauseOrDispose { }
    }

    val pager = rememberPagerState(initialPage = vm.homePage) { 2 }
    LaunchedEffect(pager.currentPage) { vm.homePage = pager.currentPage }
    val scrolls = List(2) { rememberScrollState() }
    Page(
        title = "Z-Sync",
        titleContent = {
            val ctx = LocalContext.current
            var taps by remember { mutableStateOf(0 to 0L) }
            Wordmark(18.dp, Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                val t = System.currentTimeMillis()
                val n = if (t - taps.second < 1500) taps.first + 1 else 1
                taps = n to t
                if (n == 5 && !settings.debugUnlocked) {
                    vm.repo.updateSettings { it.copy(debugUnlocked = true) }
                    Toast.makeText(ctx, "Debug Menu Unlocked", Toast.LENGTH_SHORT).show()
                }
            })
        },
        actions = {
            if (accounts.isNotEmpty()) {
                IconButton(onClick = { vm.refreshAll() }, enabled = "notes" !in busy) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh data") }
            }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        },
    ) { pad ->
        if (accounts.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(pad).padding(32.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No account yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Log in with your HoYoLAB account to get started.", textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onLogin) { Text("Log in") }
            }
            return@Page
        }

        val scope = rememberCoroutineScope()
        val roles = accounts.flatMap { it.activeRoles() }
        val pillTop = 6.dp
        val contentTop = pillTop + 44.dp + 10.dp
        Box(Modifier.fillMaxSize().padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding()).clipToBounds()) {
        Box(Modifier.fillMaxSize()) {
        var copiedAt by remember { mutableStateOf(0L) }
        var showCopied by remember { mutableStateOf(false) }
        LaunchedEffect(copiedAt) { if (copiedAt > 0) { showCopied = true; delay(1800); showCopied = false } }

        val shown by androidx.compose.animation.core.animateFloatAsState(if (showCopied) 1f else 0f, label = "copied")
        if (shown > 0f) Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).zIndex(1f)
                .graphicsLayer {
                    alpha = shown
                    translationY = (1f - shown) * 24.dp.toPx()
                    // Alpha, shadow and shape on one layer: a separate alpha layer clips the shadow to a rectangle.
                    shadowElevation = 6.dp.toPx(); shape = CircleShape; clip = true
                }
                .background(MaterialTheme.colorScheme.inverseSurface, CircleShape),
        ) {
            Text(
                "UID Copied", color = MaterialTheme.colorScheme.inverseOnSurface, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
        val pull = rememberPullToRefreshState()
        val refreshing = "notes" in busy
        PullToRefreshBox(
            isRefreshing = refreshing, onRefresh = { vm.refreshAll() }, state = pull, modifier = Modifier.fillMaxSize(),
            indicator = { RefreshIndicator(pull, refreshing, Modifier.align(Alignment.TopCenter).padding(top = contentTop)) },
        ) {
        val pullGuard = remember { PullGuard() }
        HorizontalPager(
            state = pager, verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); pullGuard.scrolled = false } }
                .nestedScroll(pullGuard),
        ) { page ->
        Column(Modifier.fillMaxSize().verticalScroll(scrolls[page]).padding(top = contentTop).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
          if (page == 1) {
            if (roles.none { state.extras[it.key] != null }) {
                if (loading) ExtraPanels(null, settings, true, true, onEvents, onGachaNext, onEndGame)
                else Panel {
                    Text("No resource data yet.", style = MaterialTheme.typography.bodyMedium)
                    Text("Tap the refresh button, or check what is turned on in Settings → Features.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            for (role in roles) key(role.key) { state.extras[role.key]?.let { ExtraPanels(it, settings, true, loading, onEvents, onGachaNext, onEndGame) } }
          } else {
            if (!settings.autoEnabled && settings.showActions) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("Check-in", "checkin" in busy, Modifier.weight(1f)) { vm.checkIn() }
                    ActionButton("Redeem codes", "codes" in busy, Modifier.weight(1f)) { vm.codes() }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("Update notes", "notes" in busy, Modifier.weight(1f)) { vm.notes() }
                    ActionButton("Traveling Mimo", "mimo" in busy, Modifier.weight(1f)) { vm.mimo() }
                }
            }

            if (roles.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Panel {
                    Text("No Zenless Zone Zero account was found on this HoYoLAB account.", style = MaterialTheme.typography.bodyMedium)
                    Text("Check it in Settings → Account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            for (role in roles) key(role.key) {
                val extras = state.extras[role.key]
                val firstLoad = loading && (extras?.fetchedAt ?: 0L) == 0L
                if (settings.showProfile) { Spacer(Modifier.height(12.dp)); ProfilePanel(role, extras?.proxy, onCopied = { copiedAt = System.currentTimeMillis() }) }
                if (settings.showBattery) {
                    val notes = state.notes[role.key]
                    if (notes != null) { Spacer(Modifier.height(12.dp)); BatteryPanel(notes, now) }
                    else if (loading) { Spacer(Modifier.height(12.dp)); BatterySkeleton() }
                }
                val anyStatus = with(settings) { showDaily || showBounty || showRidu || showScratch || showStore || showSurvey || showTemple }
                if (anyStatus) { Spacer(Modifier.height(12.dp)); StatusPanel(role, state.notes[role.key], settings, loading, now) }
                if (settings.showMimo) {
                    val mimo = extras?.mimo
                    if (mimo != null) { Spacer(Modifier.height(12.dp)); MimoPanel(mimo, now) }
                    else if (firstLoad) { Spacer(Modifier.height(12.dp)); MimoSkeleton() }
                }
                if (extras != null || firstLoad) ExtraPanels(extras, settings, false, loading, onEvents, onGachaNext, onEndGame)
                if (settings.showPremium) state.notes[role.key]?.let { PremiumPanel(it, settings, onPremium) }
            }
          }
        }
        }
        }
        }
        TabPill(pager, listOf("Status", "Resources"), scrolls[pager.currentPage].value, Modifier.padding(top = pillTop).zIndex(2f)) { i -> scope.launch { pager.animateScrollToPage(i) } }
        }
    }
}

@Composable
private fun TabPill(pager: PagerState, labels: List<String>, scroll: Int, modifier: Modifier = Modifier, onTab: (Int) -> Unit) {
    val elevation by animateDpAsState(if (scroll > with(LocalDensity.current) { 4.dp.toPx() }) 6.dp else 0.dp, label = "pillElevation")
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth().height(44.dp),
        shape = CircleShape, color = cs.surfaceContainerHigh.copy(alpha = 0.9f),
        border = BorderStroke(Dp.Hairline, cs.outlineVariant), shadowElevation = elevation,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(1f / labels.size)
                    .graphicsLayer { translationX = (pager.currentPage + pager.currentPageOffsetFraction) * size.width }
                    .padding(4.dp).background(cs.primary.copy(alpha = 0.16f), CircleShape),
            )
            Row(Modifier.fillMaxSize()) {
                labels.forEachIndexed { i, label ->
                    val selected = pager.currentPage == i
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickable { onTab(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) cs.primary else cs.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshIndicator(state: PullToRefreshState, refreshing: Boolean, modifier: Modifier = Modifier) {
    val fraction = state.distanceFraction
    if (!refreshing && fraction <= 0f) return
    val bg = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier.size(40.dp).graphicsLayer {
            translationY = fraction.coerceAtMost(1f) * 72.dp.toPx() - size.height
            val scale = if (refreshing) 1f else 0.4f + 0.6f * fraction.coerceIn(0f, 1f)
            scaleX = scale; scaleY = scale
            shape = CircleShape; clip = true; shadowElevation = 4.dp.toPx()
        }.background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.primary)
        else Icon(
            Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = fraction * 360f },
        )
    }
}

// Pull-to-refresh only starts on a fresh gesture: overscroll left over from scrolling is consumed.
private class PullGuard : NestedScrollConnection {
    var scrolled = false
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (consumed.y != 0f) scrolled = true
        return if (scrolled && available.y > 0f) Offset(0f, available.y) else Offset.Zero
    }
}

@Composable
fun Panel(vertical: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = vertical), content = content)
    }
}

@Composable
fun ActionButton(label: String, working: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick, enabled = !working, modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun InfoRow(
    label: String, value: String, hint: String? = null, valueColor: Color = MaterialTheme.colorScheme.onSurface,
    progress: Float? = null,
    valueIcon: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val accent = progress?.let { progressColor(it) }

    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.bleed(8.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp) else Modifier).padding(top = 7.dp, bottom = if (progress != null) 5.dp else 7.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).alignBy(FirstBaseline).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant)
            if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        if (valueIcon != null) ItemImage(valueIcon, 22.dp, Modifier.padding(end = 6.dp))
        Text(value, Modifier.alignBy(FirstBaseline), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = accent ?: valueColor, textAlign = TextAlign.End)
    }
    if (progress != null) Meter(progress, accent!!)
}

@Composable
internal fun Meter(progress: Float, color: Color = progressColor(progress)) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) }, color = color,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
        trackColor = color.copy(alpha = 0.18f), strokeCap = StrokeCap.Round,
        drawStopIndicator = {},
    )
}

internal fun ratio(done: Int?, total: Int?): Float = if (total == null || total <= 0) 0f else (done ?: 0).toFloat() / total

@Composable
private fun ProfilePanel(role: Role, card: ProxyCard?, onCopied: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val clipboard = LocalClipboardManager.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
        Box {
            card?.cardUrl?.let { url ->
                // The Inter-Knot card sits behind the info, lightly blurred (blur needs Android 12; older phones get a stronger veil instead).
                AsyncImage(
                    model = url, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().blur(6.dp),
                )
                val veil = if (android.os.Build.VERSION.SDK_INT >= 31) 0.45f else 0.7f
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = veil)))
            }
            // On top of the card art, text turns white with a soft dark shadow so it stays readable.
            val onArt = card?.cardUrl != null
            val shadow = if (onArt) androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 8f) else null
            val ink = if (onArt) Color.White else MaterialTheme.colorScheme.onSurface
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    card?.headIcon?.let {
                        SkeletonImage(it, "Profile icon", Modifier.padding(end = 12.dp).size(56.dp).clip(CircleShape), contentScale = ContentScale.Crop, shape = CircleShape)
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                role.nickname.ifBlank { "No name" }, style = MaterialTheme.typography.headlineSmall.copy(shadow = shadow), fontWeight = FontWeight.SemiBold,
                                color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 12.dp),
                            )
                            if (role.level > 0) Text("Lv.${role.level}", style = MaterialTheme.typography.headlineSmall.copy(shadow = shadow), fontWeight = FontWeight.SemiBold, color = ink)
                        }
                        card?.title?.let { ProxyTitle(it, card, Modifier.padding(top = 4.dp)) }
                        if (card?.medals?.isNotEmpty() == true) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            val ctx = LocalContext.current
                            card.medals.forEach {
                                val req = remember(it) { ImageRequest.Builder(ctx).data(it).allowHardware(false).transformations(TrimTransparent).build() }
                                SkeletonImage(req, "Badge", Modifier.size(28.dp), shape = CircleShape)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(role.regionName, style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow), fontWeight = FontWeight.Medium, color = ink, modifier = Modifier.weight(1f))
                    Text(
                        role.uid, style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow), fontWeight = FontWeight.Medium, color = ink,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            clipboard.setText(AnnotatedString(role.uid))
                            onCopied()
                        }.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

private fun hexColor(v: String?): Color? = v?.trim()?.removePrefix("#")?.let { h ->
    when (h.length) {
        6 -> h.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
        8 -> h.toLongOrNull(16)?.let { Color(it) }
        else -> null
    }
}

/** The title exactly as the game draws it: its own plate image with the name on top, in the game's two text colors. */
@Composable
private fun ProxyTitle(text: String, card: ProxyCard, modifier: Modifier = Modifier) {
    val top = hexColor(card.titleColor) ?: MaterialTheme.colorScheme.onSurface
    val bottom = hexColor(card.titleBgColor) ?: top
    val style = MaterialTheme.typography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold, brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, bottom)),
    )
    var ratio by remember(card.titleBgUrl) { mutableStateOf<Float?>(null) }
    Box(modifier.height(26.dp).then(ratio?.let { Modifier.widthIn(min = (26 * it).dp) } ?: Modifier), contentAlignment = Alignment.CenterStart) {
        card.titleBgUrl?.let { url ->
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.FillBounds,
                modifier = Modifier.matchParentSize(),
                onSuccess = { s -> s.painter.intrinsicSize.let { if (it.width > 0f && it.height > 0f) ratio = it.width / it.height } },
            )
        }
        // Starts where the name starts, so both line up.
        Text(text, style = style, maxLines = 1, modifier = Modifier.padding(end = 14.dp))
    }
}

@Composable
private fun BatteryPanel(notes: Notes, nowFn: () -> Long) {
    val now = nowFn()
    val cur = notes.staminaNow(now, 360)
    val max = notes.stamina.max.coerceAtLeast(1)
    val left = (notes.stamina.recoverySec - (now - notes.fetchedAt) / 1000).coerceAtLeast(0)
    val full = cur >= notes.stamina.max
    val color = batteryColor(cur.toFloat() / max)
    Panel {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GameIcons.BATTERY?.let { SkeletonImage(it, "Battery", Modifier.padding(end = 12.dp).size(56.dp), shape = RoundedCornerShape(12.dp)) }
            Text("$cur / ${notes.stamina.max}", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(if (full) "Full" else "Full in ${formatDuration(left)}", style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.End)
        }
        Meter(cur.toFloat() / max, color)
    }
}

@Composable
private fun BatterySkeleton() {
    Panel {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.padding(end = 12.dp).size(56.dp), RoundedCornerShape(12.dp))
            Skeleton(Modifier.width(110.dp).height(32.dp), RoundedCornerShape(8.dp))
            Spacer(Modifier.weight(1f))
            Skeleton(Modifier.width(90.dp).height(18.dp), RoundedCornerShape(6.dp))
        }
        Skeleton(Modifier.fillMaxWidth().padding(bottom = 8.dp).height(6.dp), RoundedCornerShape(3.dp))
    }
}

@Composable
private fun MimoSkeleton() {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.size(36.dp), RoundedCornerShape(10.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Skeleton(Modifier.width(120.dp).height(18.dp), RoundedCornerShape(6.dp))
                Spacer(Modifier.height(4.dp))
                Skeleton(Modifier.width(90.dp).height(12.dp), RoundedCornerShape(4.dp))
            }
            Skeleton(Modifier.width(36.dp).height(20.dp), RoundedCornerShape(6.dp))
        }
    }
}

@Composable
private fun StatusPanel(role: Role, notes: Notes?, s: Settings, loading: Boolean, nowFn: () -> Long) {
    Panel {
        if (notes == null) {
            if (loading) {
                val rows = listOf(s.showDaily, s.showBounty, s.showRidu, s.showScratch, s.showStore, s.showSurvey, s.showTemple).count { it }
                SkeletonInfoRows(rows.coerceAtLeast(1), meters = minOf(rows, 3))
                return@Panel
            }
            Text("No data yet. Pull down or tap refresh to load it.", style = MaterialTheme.typography.bodyMedium)
            return@Panel
        }
        val now = nowFn()
        val ok = OkColor
        val pending = StatusOrange
        fun resetsIn(at: Long) = "Resets in ${formatDuration(((at - now) / 1000).coerceAtLeast(0))}"
        val weekly = Resets.nextWeekly(role.region, now)
        // Cached or stale API values are zeroed once their reset has passed.
        val weekPassed = Resets.nextWeekly(role.region, notes.fetchedAt) <= now
        val dayPassed = Resets.nextDaily(role.region, notes.fetchedAt) <= now
        fun reset(sec: Long?) = weekPassed || (sec != null && (sec <= 0 || notes.fetchedAt + sec * 1000 <= now))
        val dailyDoneNow = if (dayPassed) 0 else notes.dailyDone
        val bountyNow = if (reset(notes.bountyResetSec)) 0 else notes.bountyDone ?: 0
        val riduNow = if (reset(notes.riduResetSec)) 0 else notes.riduDone ?: 0
        val surveyNow = if (weekPassed) 0 else notes.surveyDone ?: 0
        val templeNow = if (reset(notes.templeResetSec)) 0 else notes.templeCurrency ?: 0

        if (s.showDaily) InfoRow("Daily", "$dailyDoneNow / ${notes.dailyMax}", hint = resetsIn(Resets.nextDaily(role.region, now)), progress = ratio(dailyDoneNow, notes.dailyMax))
        if (s.showBounty) notes.bountyTotal?.let { total ->
            InfoRow("Bounty Commission", "$bountyNow / $total", hint = resetsIn(Resets.fromApi(notes.fetchedAt, notes.bountyResetSec, weekly, now)), progress = ratio(bountyNow, total))
        }
        if (s.showRidu) notes.riduMax?.let { max ->
            InfoRow(
                "Ridu Weekly", "$riduNow / $max",
                hint = resetsIn(Resets.fromApi(notes.fetchedAt, notes.riduResetSec, weekly, now)),
                progress = ratio(riduNow, max),
            )
        }
        if (s.showScratch) notes.scratchCardDone?.let { claimed -> claimed && !dayPassed }?.let {
            InfoRow("Scratch Card", if (it) "Claimed" else "Pending", valueColor = if (it) ok else pending)
        }
        if (s.showStore) notes.shopState?.let {
            InfoRow(
                "Video Store", it,
                valueColor = when (it) { "Open" -> ok; "Finished" -> pending; else -> MaterialTheme.colorScheme.onSurface },
            )
        }
        if (s.showSurvey) notes.surveyTotal?.let { total ->
            InfoRow("Investigation Points", "$surveyNow / $total", progress = ratio(surveyNow, total))
        }
        if (s.showTemple && notes.templeLevel != null) {
            notes.templeCurrencyMax?.let { max ->
                InfoRow("Suibian Temple", "$templeNow / $max", hint = resetsIn(Resets.fromApi(notes.fetchedAt, notes.templeResetSec, weekly, now)), progress = ratio(templeNow, max))
            }
            val free = listOfNotNull(
                notes.templeBench?.let { "Bench: ${it.lowercase()}" },
                notes.templeShelf?.let { "Shelf: ${it.lowercase()}" },
                notes.templeExpedition?.let { "Expedition: ${it.lowercase()}" },
            )
            if (free.isNotEmpty()) {
                val attention = listOf(notes.templeBench, notes.templeShelf, notes.templeExpedition).any { it == "Free" || it == "Finished" || it == "Sold out" }
                InfoRow("Temple tasks", if (attention) "Something to do" else "All running", hint = free.joinToString(" · "), valueColor = if (attention) pending else ok)
            }
        }
    }
}

internal fun endsIn(endAt: Long): String {
    if (endAt <= 0) return "no end date"
    val sec = (endAt - System.currentTimeMillis()) / 1000
    return if (sec <= 0) "ended" else "ends in ${formatDuration(sec)}"
}

@Composable
internal fun PanelTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
}

@Composable
internal fun rememberDominantColor(url: String?): Color? {
    val context = LocalContext.current
    val color by produceState<Color?>(null, url) {
        value = null
        if (url == null) return@produceState
        value = runCatching {
            val res = context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).build())
            val bmp = (res as? SuccessResult)?.drawable?.toBitmap() ?: return@runCatching null
            withContext(Dispatchers.Default) {
                val p = Palette.from(bmp).maximumColorCount(16).generate()
                (p.vibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch)?.rgb?.let { Color(it) }
            }
        }.getOrNull()
    }
    return color
}

private val EmptyExtras = Extras()

@Composable
internal fun EventRowSkeleton() {
    Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 5.dp), verticalAlignment = Alignment.Top) {
        Skeleton(Modifier.padding(end = 12.dp).size(width = 78.dp, height = 44.dp), RoundedCornerShape(10.dp))
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Skeleton(Modifier.fillMaxWidth(0.7f).height(18.dp), RoundedCornerShape(6.dp))
            Spacer(Modifier.height(4.dp))
            Skeleton(Modifier.width(90.dp).height(12.dp), RoundedCornerShape(4.dp))
        }
        Skeleton(Modifier.width(64.dp).height(22.dp), RoundedCornerShape(6.dp))
    }
    Skeleton(Modifier.fillMaxWidth().padding(bottom = 8.dp).height(6.dp), RoundedCornerShape(3.dp))
}

@Composable
internal fun GachaBannerSkeleton() {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Skeleton(Modifier.width(110.dp).height(18.dp), RoundedCornerShape(6.dp))
            Spacer(Modifier.height(4.dp))
            repeat(2) {
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Skeleton(Modifier.size(42.dp), RoundedCornerShape(10.dp))
                    Skeleton(Modifier.padding(start = 8.dp).width(120.dp).height(20.dp), RoundedCornerShape(6.dp))
                }
            }
            Skeleton(Modifier.padding(top = 2.dp).width(80.dp).height(12.dp), RoundedCornerShape(4.dp))
        }
        Skeleton(Modifier.width(48.dp).height(36.dp), RoundedCornerShape(6.dp))
    }
}

private val cutouts = android.util.LruCache<String, Pair<ImageBitmap, Color?>>(24)

@Composable
private fun rememberGame8Cutout(url: String): Pair<ImageBitmap, Color?>? {
    val context = LocalContext.current
    val result by produceState(cutouts.get(url), url) {
        if (value != null) return@produceState
        value = runCatching {
            val res = context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).build())
            val bmp = (res as? SuccessResult)?.drawable?.toBitmap() ?: return@runCatching null
            withContext(Dispatchers.Default) { game8Cutout(bmp) }
        }.getOrNull()?.also { cutouts.put(url, it) }
    }
    return result
}

// Game8 thumbnails bake rank/attribute badges into the right column: keep the left square and drop the gray background.
private fun game8Cutout(src: Bitmap): Pair<ImageBitmap, Color?> {
    val k = src.width / 437f
    val x0 = (11 * k).toInt(); val y0 = (8 * k).toInt()
    val side = (298 * k).toInt().coerceAtMost(minOf(src.width - x0, src.height - y0)).coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(src, x0, y0, side, side).copy(Bitmap.Config.ARGB_8888, true)
    val w = bmp.width
    val h = bmp.height
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    fun isBackground(c: Int): Boolean {
        val a = c ushr 24
        val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b)
        return a < 200 || (mx - mn <= 14 && mx <= 80)
    }
    val seen = BooleanArray(w * h)
    val stack = IntArray(w * h)
    var sp = 0
    fun push(i: Int) { if (!seen[i] && isBackground(px[i])) { seen[i] = true; stack[sp++] = i } }
    for (x in 0 until w) push(x)
    for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
    while (sp > 0) {
        val i = stack[--sp]; val x = i % w
        if (x > 0) push(i - 1)
        if (x < w - 1) push(i + 1)
        if (i >= w) push(i - w)
        if (i < w * (h - 1)) push(i + w)
    }
    for (i in px.indices) if (seen[i]) px[i] = 0
    bmp.setPixels(px, 0, w, 0, 0, w, h)

    val p = Palette.from(bmp).maximumColorCount(16).generate()
    val color = (p.vibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch)?.rgb?.let { Color(it) }
    return bmp.asImageBitmap() to color
}

@Composable
internal fun GachaItemRow(item: GachaItem) {
    val game8 = item.icon?.startsWith("https://img.game8.co") == true
    val cut = if (game8) rememberGame8Cutout(item.icon!!) else null
    val dominant = if (game8) cut?.second else rememberDominantColor(item.icon)
    val shape = RoundedCornerShape(10.dp)
    val bg = if (dominant != null) Brush.linearGradient(listOf(dominant.copy(alpha = 0.85f), dominant.copy(alpha = 0.25f)))
    else SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest)
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(shape).background(bg, shape), contentAlignment = Alignment.Center) {
            when {
                item.icon == null -> UnknownMark()
                game8 -> {
                    if (cut != null) Image(cut.first, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Skeleton(Modifier.fillMaxSize(), shape)
                }
                else -> SkeletonImage(item.icon, null, Modifier.fillMaxSize(), ContentScale.Crop, Alignment.TopCenter, shape = shape)
            }
        }
        GameIcons.rankBadge(item.rarity)?.let {
            SkeletonImage(it, "${item.rarity} rank", Modifier.padding(start = 6.dp).size(30.dp), shape = RoundedCornerShape(6.dp))
        }
        Spacer(Modifier.width(8.dp))

        val unknown = item.name == "?"
        GameIcons.specialty(item.specialty)?.let { TagIcon(it) } ?: run { if (unknown) UnknownTag() }
        GameIcons.element(item.element, item.subElement)?.let { TagIcon(it) } ?: run { if (unknown && item.element != -1) UnknownTag() }
        Text(
            item.name, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ExtraPanels(extras: Extras?, s: Settings, resources: Boolean, loading: Boolean, onEvents: () -> Unit, onGachaNext: () -> Unit, onEndGame: () -> Unit) {
    val x = extras ?: EmptyExtras
    val firstLoad = loading && x.fetchedAt == 0L
    val ok = OkColor
    val pending = WarnColor
    var first = resources
    @Composable fun gap() { if (first) first = false else Spacer(Modifier.height(12.dp)) }
    @Composable fun unavailable(key: String) {
        x.errors[key]?.let { Text("Unavailable: $it", style = MaterialTheme.typography.bodySmall, color = pending) }
    }

    if (!resources && s.showCheckInCalendar && (x.checkIn != null || "checkin" in x.errors || firstLoad)) {
        gap()
        Panel {
            PanelTitle("HoYoLAB Check-In")
            if (x.checkIn == null && firstLoad) SkeletonInfoRows(3) else x.checkIn?.let { c ->
                InfoRow("Today", if (c.signedToday) "Done" else "Pending", hint = c.todayReward?.let { "Reward: $it" }, valueColor = if (c.signedToday) ok else pending)
                InfoRow("Days This Month", c.totalDays.toString(), hint = if (c.missedDays > 0) "You missed ${c.missedDays}" else "No missed days")
                if (c.nextIcon != null) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Next Reward", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    c.nextCount?.let { Text("x$it", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 8.dp)) }
                    SkeletonImage(c.nextIcon, c.nextReward, Modifier.size(30.dp), shape = RoundedCornerShape(6.dp))
                } else c.nextReward?.let { InfoRow("Next Reward", it) }
            }
            unavailable("checkin")
        }
    }
    if (resources && s.showCurrency && (x.tickets != null || "currency" in x.errors || firstLoad)) {
        gap()
        Panel {
            if (x.tickets == null && firstLoad) SkeletonIconRows(3)
            x.tickets?.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemImage(t.icon, 28.dp, Modifier.padding(end = 10.dp))
                    Text(t.name, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(t.value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
                }
            }
            unavailable("currency")
        }
    }
    if (resources && s.showGacha && (x.gacha != null || "gacha" in x.errors || firstLoad)) {
        gap()
        NavPanel(onClick = onGachaNext, description = "Upcoming banners", title = {
            Text("Gacha", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(
                "  ·  Version ${x.gacha.orEmpty().firstNotNullOfOrNull { it.version } ?: "?"}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline,
            )
        }) {
            if (x.gacha == null && firstLoad) GachaBannerSkeleton()
            if (x.gacha?.isEmpty() == true) Text("No promotional banners in progress.", style = MaterialTheme.typography.bodyMedium)
            x.gacha?.forEach { b -> GachaBannerRow(b, x.itemIcons[ItemIcon.ENCRYPTED_TAPE]) }
            unavailable("gacha")
        }
    }
    if (resources && s.showEvents && (x.events != null || "events" in x.errors || firstLoad)) {
        gap()
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onEvents),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active Events", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "See all events", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                val active = remember(x.events) { x.events.orEmpty().filter { it.started && !it.ended }.sortedBy { it.endAt } }
                if (x.events == null && firstLoad) repeat(2) { EventRowSkeleton() }
                if (x.events != null && active.isEmpty()) Text("No events in progress.", style = MaterialTheme.typography.bodyMedium)
                active.forEachIndexed { i, e ->
                    Spacer(Modifier.height(if (i == 0) 4.dp else 10.dp))
                    Box(Modifier.clip(RoundedCornerShape(10.dp))) { EventArt(e) }
                    EventRow(e, x.itemIcons[ItemIcon.POLYCHROME])
                }
                unavailable("events")
            }
        }
    }
    if (!resources && s.optChallenges && (x.challenges != null || "shiyu" in x.errors || "assault" in x.errors || firstLoad)) {
        gap()

        NavPanel(onClick = onEndGame, description = "End-Game details", title = {
            Text("End-Game", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }) {
            if (x.challenges == null && firstLoad) SkeletonInfoRows(2)
            val shown = remember(x.challenges) { x.challenges?.filter { it.name != "Adversity Mode" } }
            shown?.forEach { ChallengeRow(it) }
            unavailable("shiyu"); unavailable("assault")
        }
    }
    if (resources && s.showLedger && (x.ledger != null || "ledger" in x.errors || firstLoad)) {
        gap()
        var open by rememberSaveable { mutableStateOf(false) }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { open = !open },
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Monthly Summary" + (x.ledger?.month?.takeIf { it.length == 6 }?.let { " · ${it.substring(4)}/${it.substring(0, 4)}" } ?: ""),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    )
                    Icon(
                        if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (open) "Collapse" else "Expand", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (x.ledger == null && firstLoad) {
                    Spacer(Modifier.height(6.dp))
                    SkeletonIconRows(2)
                } else if (open) {
                    Spacer(Modifier.height(6.dp))
                    x.ledger?.let { l ->
                        l.totals.forEach { t ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                                ItemImage(t.icon, 28.dp, Modifier.padding(end = if (t.icon2 != null) 4.dp else 10.dp))
                                if (t.icon2 != null) ItemImage(t.icon2, 28.dp, Modifier.padding(end = 10.dp))
                                Text(t.name, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(t.value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (l.sources.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("From:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            l.sources.forEach { InfoRow(it.name, it.value) }
                        }
                    }
                }
                unavailable("ledger")
            }
        }
    }
    if (resources && x.fetchedAt > 0) Text(
        "Updated ${ago(x.fetchedAt)}", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun PremiumPanel(notes: Notes, s: Settings, onPremium: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    NavPanel(onClick = onPremium, description = "Official Top-Up", title = {
        Text("Premium", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }) {
        val active = notes.memberActive.takeIf { s.showMember }
        if (active == null) Text("Official Top-Up", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            val days = notes.memberLeftSec?.takeIf { it > 0 }?.let { (it + 86399) / 86400 }
            InfoRow(
                "Inter-Knot Membership", if (active) "Active" else "Inactive",
                hint = if (active) days?.let { if (it == 1L) "1 day left" else "$it days left" } else null,
                valueColor = if (active) OkColor else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
internal fun ItemImage(icon: String?, size: Dp, modifier: Modifier = Modifier) {
    val model: Any? = when (icon) {
        null -> null
        "res:monochrome" -> app.zsync.R.drawable.item_monochrome
        else -> icon
    }
    if (model == null) Spacer(modifier.size(size))
    else SkeletonImage(model, null, modifier.size(size), shape = RoundedCornerShape(6.dp))
}

@Composable
private fun TagIcon(tag: GameIcons.Tag) {
    val icon = tag.icon ?: return
    TagBox { SkeletonImage(icon, tag.name, Modifier.size(18.dp), shape = RoundedCornerShape(4.dp)) }
}

@Composable
private fun TagBox(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.padding(end = 4.dp).size(24.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF1F1F23)), contentAlignment = Alignment.Center, content = content)
}

/** Full-width event banner for the top of an event card; keeps the image's own proportions. */
@Composable
internal fun EventBanner(url: String, dim: Boolean) {
    var ratio by remember(url) { mutableStateOf<Float?>(null) }
    Box(Modifier.fillMaxWidth().aspectRatio(ratio ?: 5f).alpha(if (dim) 0.4f else 1f)) {
        coil.compose.AsyncImage(
            model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            onSuccess = { s -> s.painter.intrinsicSize.let { if (it.width > 0f && it.height > 0f) ratio = it.width / it.height } },
        )
        if (ratio == null) Skeleton(Modifier.fillMaxSize(), androidx.compose.ui.graphics.RectangleShape)
    }
}

private val rewardQty = Regex("""[×x]\s*([\d,]+)""")

@Composable
internal fun EventRow(e: GameEvent, rewardIcon: String?, locked: Boolean = false, ended: Boolean = false) {
    val dim = MaterialTheme.colorScheme.outline
    val whenText = if (locked) {
        val sec = (e.startAt - System.currentTimeMillis()) / 1000
        if (e.startAt > 0 && sec > 0) "Starts in ${formatDuration(sec)}" else "Coming soon"
    } else if (ended) "Ended" else endsIn(e.endAt).replaceFirstChar { it.uppercase() }
    val progress = e.monoTotal > 0
    // The main reward goes on the right; with Polychrome progress it is already the Polychrome counter.
    val main = e.rewardName?.takeIf { !progress || !it.contains("Polychrome", true) }
    val mainShown = main != null && e.rewardIcon != null && !ended
    val extras = if (ended) emptyList() else e.extras.filter { it.icon != null && (!progress || !it.name.contains("Polychrome", true)) }
    val subText = if (ended) "" else listOfNotNull(e.loginDays?.let { "$it-day login" }, main?.takeIf { !mainShown }).joinToString(" · ")
    val fade = Modifier.alpha(if (locked) 0.4f else 1f)
    Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(e.name, style = MaterialTheme.typography.bodyMedium, color = if (locked || ended) dim else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(whenText, style = MaterialTheme.typography.labelSmall, color = dim)
            if (subText.isNotEmpty()) Text(subText, style = MaterialTheme.typography.labelMedium, color = dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        extras.forEach { ItemImage(it.icon, 18.dp, fade.padding(end = 4.dp)) }
        when {
            ended -> Unit
            progress -> {
                if (!ended) ItemImage(rewardIcon, 22.dp, fade.padding(end = 6.dp))
                Text(
                    if (locked) "${e.monoTotal}" else "${e.monoGot} / ${e.monoTotal}",
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = if (locked || ended) dim else progressColor(ratio(e.monoGot, e.monoTotal)),
                )
            }
            mainShown -> {
                if (extras.isNotEmpty()) Spacer(Modifier.width(2.dp))
                ItemImage(e.rewardIcon, 22.dp, fade.padding(end = 6.dp))
                rewardQty.find(main!!)?.let {
                    Text("×${it.groupValues[1]}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = if (locked) dim else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
    if (progress && !locked && !ended) Meter(ratio(e.monoGot, e.monoTotal))
}

@Composable
internal fun NavPanel(onClick: () -> Unit, description: String, title: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = title)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
internal fun GachaBannerRow(b: GachaBanner, tapeIcon: String?, upcoming: Boolean = false, showPity: Boolean = true, dateOnly: Boolean = false) {
    val items = b.items.ifEmpty { b.names.mapIndexed { i, n -> GachaItem(n, b.icons.takeIf { it.size == b.names.size }?.getOrNull(i)) } }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(b.kind, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            items.take(3).forEach { GachaItemRow(it) }
            val whenText = if (upcoming) {
                val sec = (b.startAt - System.currentTimeMillis()) / 1000
                when {
                    b.startAt <= 0 -> "Starts in ?"

                    dateOnly -> "Starts " + java.text.SimpleDateFormat("MMM d, yyyy", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date(b.startAt)) +
                        if (sec > 86_400) " · in ~${sec / 86_400} d" else ""
                    sec > 0 -> "Starts in ${formatDuration(sec)}"
                    else -> "Started"
                }
            } else if (b.endAt > 0) endsIn(b.endAt).replaceFirstChar { it.uppercase() } else "Ends in ?"
            Text(whenText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 2.dp))
        }
        if (showPity) Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.pullsToS?.toString() ?: "?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                tapeIcon?.let { icon -> ItemImage(icon, 22.dp, Modifier.padding(start = 4.dp)) }
            }
            Text("Guaranteed S", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun UnknownMark() {
    Text("?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun UnknownTag() {
    TagBox { Text("?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFBDBDC4)) }
}

private fun Modifier.bleed(x: Dp) = layout { m, c ->
    val extra = x.roundToPx()
    val p = m.measure(c.copy(minWidth = c.minWidth + 2 * extra, maxWidth = c.maxWidth + 2 * extra))
    layout(p.width - 2 * extra, p.height) { p.place(-extra, 0) }
}

internal fun rankIcon(rating: String?): Int? = when (rating) {
    "S+" -> app.zsync.R.drawable.rank_splus
    "S" -> app.zsync.R.drawable.rank_s
    "A" -> app.zsync.R.drawable.rank_a
    "B" -> app.zsync.R.drawable.rank_b
    "X" -> app.zsync.R.drawable.rank_x
    else -> null
}

@Composable
internal fun rankTint(rating: String?): Color = when (rating) {
    "S+" -> Color(0xFF9FE3FF)
    "S" -> Color(0xFFFF9A2E)
    "A" -> Color(0xFFC86BFF)
    "B" -> Color(0xFF4FA8FF)
    else -> MaterialTheme.colorScheme.outline
}

@Composable
internal fun RankImage(rating: String?, height: Dp, modifier: Modifier = Modifier) {
    val none = rating == null
    Image(
        painterResource(rankIcon(rating) ?: app.zsync.R.drawable.rank_x), contentDescription = "Rank ${rating ?: "none"}",
        modifier = modifier.height(height).then(if (none) Modifier.alpha(0.35f) else Modifier), contentScale = ContentScale.FillHeight,
        colorFilter = if (none) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
    )
}

@Composable
private fun ChallengeRow(c: Challenge) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankImage(c.rating.takeIf { c.played }, 30.dp, Modifier.padding(end = 6.dp))
                Text(c.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            c.endAt?.let { Text("Cycle ${endsIn(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
        }
        RankNumber(c.score, c.rating, played = c.played, size = 20.sp)
    }
}

@Composable
internal fun RankNumber(score: Int?, rating: String?, played: Boolean = true, size: androidx.compose.ui.unit.TextUnit = 20.sp) {
    val text = if (!played || score == null) "No Data" else "%,d".format(Locale.US, score)
    val key = if (!played || score == null) "NONE" else rating
    val (top, bottom, outline) = when (key) {
        "S" -> Triple(Color(0xFFFFB02E), Color(0xFFF2600C), Color(0xFF3A1604))
        "A" -> Triple(Color(0xFFE067FF), Color(0xFF9B23E0), Color(0xFF2A0838))
        "B" -> Triple(Color(0xFF4FD0FF), Color(0xFF2D78EE), Color(0xFF08243F))
        "X" -> Triple(Color(0xFFB8B8B8), Color(0xFF6A6A6A), Color(0xFF1A1A1A))
        "S+" -> Triple(Color(0xFF9FE3FF), Color(0xFFB9A4FF), Color(0xFF1E2A33))
        "NONE" -> Triple(Color(0xFFFF6B5E), Color(0xFFD32F2F), Color(0xFF3A0A0A))
        else -> Triple(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurface, Color.Transparent)
    }
    val base = androidx.compose.ui.text.TextStyle(
        fontSize = size, fontWeight = FontWeight.Black, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        letterSpacing = 0.5.sp,
    )
    val brush: Brush = if (key == "S+") {
        val shift by rememberInfiniteTransition(label = "splus").animateFloat(
            0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "shine",
        )
        val colors = listOf(Color(0xFF7FD6FF), Color(0xFFE9F7FF), Color(0xFFB9A4FF), Color(0xFF7FE3E0), Color(0xFF7FD6FF))
        val span = 300f
        Brush.linearGradient(colors, start = Offset(shift * span - span, 0f), end = Offset(shift * span, span / 3), tileMode = androidx.compose.ui.graphics.TileMode.Repeated)
    } else Brush.verticalGradient(listOf(top, bottom))
    Box {
        Text(text, style = base.copy(color = outline, drawStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = with(LocalDensity.current) { 2.5.dp.toPx() }, join = androidx.compose.ui.graphics.StrokeJoin.Round)))
        Text(text, style = base.copy(brush = brush))
    }
}

@Composable
private fun MimoPanel(m: MimoInfo, nowFn: () -> Long) {
    val now = nowFn()
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painterResource(app.zsync.MimoStickers.current), contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Traveling Mimo", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)

                val at = m.restockAt?.takeIf { it > now } ?: run {
                    var d = java.time.Instant.ofEpochMilli(now).atOffset(ZoneOffset.UTC).toLocalDate()
                    while (d.dayOfWeek != java.time.DayOfWeek.WEDNESDAY) d = d.plusDays(1)
                    var t = d.atTime(11, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
                    if (t <= now) t += 7 * 24 * 3600_000L
                    t
                }
                Text("Restocks in ${formatDuration((at - now) / 1000)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Text("${m.points}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

private object Stickers {
    @Volatile private var names: List<String>? = null
    fun all(context: android.content.Context): List<String> =
        names ?: (context.assets.list("stickers")?.sorted().orEmpty()).also { names = it }
}

/** The event's own banner or, until one turns up, the stand-in banner with a sticker of the day. */
@Composable
internal fun EventArt(e: GameEvent, dim: Boolean = false) {
    e.bannerUrl?.let { EventBanner(it, dim); return }
    val context = LocalContext.current
    val day = java.time.LocalDate.now().toEpochDay()
    val sticker = remember(day, e.name) {
        Stickers.all(context).let { if (it.isEmpty()) null else it[Math.floorMod(day * 31 + e.name.hashCode(), it.size.toLong()).toInt()] }
    }
    // Laid out on the 2032×444 artwork: the sticker sits in a fixed 344 px square on the left of the picture area.
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(2032f / 444f).alpha(if (dim) 0.4f else 1f)) {
        val k = maxWidth / 2032f
        Image(painterResource(app.zsync.R.drawable.no_banner), contentDescription = "No banner", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        sticker?.let {
            AsyncImage(
                model = "file:///android_asset/stickers/$it", contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.offset(x = k * 56f, y = k * 28f).size(k * 344f),
            )
        }
    }
}
