package app.zsync.ui

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    backdrop: Boolean = true,
    topBarColor: Color? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (backdrop) Backdrop()
        Scaffold(
            containerColor = if (backdrop) Color.Transparent else MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { titleContent?.invoke() ?: Text(title, fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        if (onBack != null) IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = topBarColor ?: if (backdrop) Color.Transparent else MaterialTheme.colorScheme.background,
                    ),
                )
            },
            content = content,
        )
    }
}

/** The app's art behind the cards, blurred like the Proxy card (blur needs Android 12; older phones get a stronger veil). */
@Composable
private fun Backdrop() {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    androidx.compose.foundation.Image(
        painterResource(app.zsync.R.drawable.app_backdrop), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().blur(6.dp),
    )
    val veil = when {
        !dark -> 0.82f
        android.os.Build.VERSION.SDK_INT >= 31 -> 0.3f
        else -> 0.5f
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = veil)))
}

fun ago(ms: Long): String {
    if (ms <= 0) return "never"
    val min = (System.currentTimeMillis() - ms) / 60000
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        min < 1440 -> "${min / 60} h ago"
        else -> "${min / 1440} d ago"
    }
}

@Composable
fun Section(title: String) {
    Text(
        title.uppercase(), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

fun intervalLabel(min: Int): String = when {
    min < 60 -> "$min min"
    min == 60 -> "1 hour"
    else -> "${min / 60} hours"
}

@Composable
fun Wordmark(height: Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(app.zsync.R.drawable.wordmark),
        contentDescription = "Z-Sync",
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
        modifier = modifier.height(height).aspectRatio(333f / 52f),
    )
}

@Composable
fun Skeleton(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val pulse = rememberInfiniteTransition(label = "skeleton").animateFloat(
        0.5f, 1f, infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "alpha",
    )
    Box(modifier.graphicsLayer { alpha = pulse.value }.background(MaterialTheme.colorScheme.surfaceContainerHighest, shape))
}

@Composable
fun SkeletonImage(
    model: Any?, contentDescription: String?, modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit, alignment: Alignment = Alignment.Center,
    colorFilter: ColorFilter? = null, shape: Shape = RoundedCornerShape(8.dp),
) {
    var pending by remember(model) { mutableStateOf(true) }
    Box(modifier) {
        AsyncImage(
            model = model, contentDescription = contentDescription, modifier = Modifier.fillMaxSize(),
            contentScale = contentScale, alignment = alignment, colorFilter = colorFilter,
            onState = { pending = it is AsyncImagePainter.State.Loading || it is AsyncImagePainter.State.Empty },
        )
        if (pending) Skeleton(Modifier.fillMaxSize(), shape)
    }
}

@Composable
internal fun SkeletonInfoRows(count: Int, meters: Int = 0) {
    repeat(count) { i ->
        Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = if (i < meters) 5.dp else 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.weight(1f).padding(end = 12.dp).height(20.dp), RoundedCornerShape(6.dp))
            Skeleton(Modifier.width(64.dp).height(22.dp), RoundedCornerShape(6.dp))
        }
        if (i < meters) Skeleton(Modifier.fillMaxWidth().padding(bottom = 8.dp).height(6.dp), RoundedCornerShape(3.dp))
    }
}

@Composable
internal fun SkeletonIconRows(count: Int, icon: Dp = 28.dp) {
    repeat(count) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.padding(end = 10.dp).size(icon), RoundedCornerShape(6.dp))
            Skeleton(Modifier.weight(1f).padding(end = 12.dp).height(18.dp), RoundedCornerShape(6.dp))
            Skeleton(Modifier.width(48.dp).height(20.dp), RoundedCornerShape(6.dp))
        }
    }
}

/** Crops fully transparent margins so icons with uneven padding line up at the same visual size. */
internal object TrimTransparent : coil.transform.Transformation {
    override val cacheKey = "trimTransparent"
    override suspend fun transform(input: android.graphics.Bitmap, size: coil.size.Size): android.graphics.Bitmap {
        val w = input.width; val h = input.height
        val px = IntArray(w * h)
        input.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 8) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
        }
        if (r < l || b < t) return input
        return android.graphics.Bitmap.createBitmap(input, l, t, r - l + 1, b - t + 1)
    }
}
