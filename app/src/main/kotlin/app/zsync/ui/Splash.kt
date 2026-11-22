package app.zsync.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.media.*
import app.zsync.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Bit(val angle: Float, val speed: Float, val size: Float, val color: Color, val spin: Float)

@Composable
fun SplashScreen(loading: Boolean, hasAccount: Boolean, sound: Boolean = true, character: app.zsync.Character = app.zsync.Characters.default, onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isLoading by rememberUpdatedState(loading)
    val done by rememberUpdatedState(onDone)
    val rise = remember { Animatable(1f) }
    val scale = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    val nameAlpha = remember { Animatable(0f) }
    val loaderAlpha = remember { Animatable(0f) }
    val bits = remember {
        val rnd = Random(7)
        List(34) {
            Bit(
                angle = rnd.nextFloat() * 6.2832f,
                speed = 0.55f + rnd.nextFloat() * 0.75f,
                size = 5f + rnd.nextFloat() * 7f,

                color = Color(character.bits[rnd.nextInt(character.bits.size)]),
                spin = (rnd.nextFloat() - 0.5f) * 720f,
            )
        }
    }

    LaunchedEffect(Unit) {
        // The sound is timed to this animation, so both start together.
        if (sound) playStartupSound(context)

        rise.animateTo(0f, tween(1500, easing = CubicBezierEasing(0.22f, 1.25f, 0.36f, 1f)))

        scale.animateTo(0.78f, tween(220))

        launch { burst.animateTo(1f, tween(1700, easing = LinearEasing)) }
        scale.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow))
        nameAlpha.animateTo(1f, tween(500))
        if (hasAccount) {
            loaderAlpha.animateTo(1f, tween(300))
            delay(700)

            withTimeoutOrNull(8000) { snapshotFlow { isLoading }.first { !it } }
        } else delay(600)
        done()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        val travel = maxHeight
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(148.dp)) {
                    val p = burst.value
                    if (p <= 0f || p >= 1f) return@Canvas
                    val seconds = p * 1.7f
                    val reach = size.width * 1.9f
                    val gravity = size.width * 4.2f
                    val fade = if (p < 0.65f) 1f else (1f - p) / 0.35f
                    for (b in bits) {
                        val x = center.x + cos(b.angle) * b.speed * reach * seconds
                        val y = center.y + sin(b.angle) * b.speed * reach * seconds + 0.5f * gravity * seconds * seconds
                        val side = b.size.dp.toPx() * 0.5f
                        rotate(b.spin * seconds, pivot = Offset(x, y)) {
                            drawRect(b.color.copy(alpha = fade), topLeft = Offset(x - side / 2, y - side / 2), size = Size(side, side))
                        }
                    }
                }
                Image(
                    painter = painterResource(character.image), contentDescription = null,
                    modifier = Modifier
                        .graphicsLayer {
                            translationY = rise.value * travel.toPx()
                            scaleX = scale.value; scaleY = scale.value
                        }
                        .size(148.dp)
                        .shadow(12.dp, RoundedCornerShape(34.dp))
                        .clip(RoundedCornerShape(34.dp)),
                )
            }
            Spacer(Modifier.height(24.dp))

            Wordmark(
                30.dp,
                Modifier.alpha(nameAlpha.value).graphicsLayer { translationY = (1f - nameAlpha.value) * 24f },
            )
            // Keeps the logo block where it was before the loader moved to the corner.
            Spacer(Modifier.height(64.dp))
        }
        NowLoading(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 20.dp)
                .width(190.dp).alpha(loaderAlpha.value),
        )
    }
}

/** The game's "Now Loading" Bangboo, inked in the theme's text color (black on light, white on dark). */
@Composable
private fun NowLoading(modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ink = MaterialTheme.colorScheme.onBackground.toArgb()
    val drawable = remember {
        runCatching {
            android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(context.resources, R.raw.loading))
        }.getOrNull()
    } ?: return
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { android.widget.ImageView(it).apply { adjustViewBounds = true; setImageDrawable(drawable) } },
        update = { v ->
            v.setColorFilter(ink, android.graphics.PorterDuff.Mode.SRC_IN)
            (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
        },
        modifier = modifier,
    )
}

// Held here so the player is not garbage-collected mid-sound, which cut it off or kept it silent.
private var startupPlayer: MediaPlayer? = null

private fun playStartupSound(context: android.content.Context) {
    val audio = context.getSystemService(AudioManager::class.java)
    if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
    runCatching {
        startupPlayer?.release()
        val player = MediaPlayer()
        startupPlayer = player
        player.setAudioAttributes(
            AudioAttributes.Builder()
                // Media usage follows the phone's media volume slider.
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(),
        )
        context.resources.openRawResourceFd(R.raw.splash).use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        player.setOnCompletionListener { it.release(); if (startupPlayer === it) startupPlayer = null }
        // Synchronous prepare: the file is tiny and the sound must start with the animation.
        player.prepare()
        player.start()
    }
}
