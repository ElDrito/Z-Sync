package app.zsync

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.*
import app.zsync.core.Engine
import app.zsync.core.Http
import app.zsync.core.Notifier
import app.zsync.core.Repo
import app.zsync.core.Storage
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class App : Application(), ImageLoaderFactory {
    lateinit var repo: Repo
        private set
    lateinit var engine: Engine
        private set

    private val lock = ReentrantLock()
    fun <T> exclusive(block: () -> T): T = lock.withLock(block)

    private val widgetScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        repo = Repo(PrefStorage(securePrefs()), PrefStorage(getSharedPreferences("data", MODE_PRIVATE)))
        createChannel()
        engine = Engine(repo, AndroidNotifier(this))
        scheduleAuto(this, true, repo.settings.value.autoIntervalMin)

        AutoScheduler.sync(this)

        widgetScope.launch {
            combine(repo.state.map { it.notes to it.extras }, repo.accounts) { n, a -> n to a }
                .distinctUntilChanged().collect { BatteryWidget.updateAll(this@App) }
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(Http.client)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(100L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(150)
        .build()

    private fun securePrefs(): SharedPreferences {
        fun open() = EncryptedSharedPreferences.create(
            this, "secure",
            MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        return try {
            open()
        } catch (e: Exception) {
            deleteSharedPreferences("secure")
            open()
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Check-in, battery, codes and reminders"
            },
        )
    }

    companion object {
        const val CHANNEL = "alerts"
        private const val WORK = "auto-periodic"

        fun scheduleAuto(ctx: Context, enabled: Boolean, intervalMin: Int, replace: Boolean = false) {
            val wm = WorkManager.getInstance(ctx)
            if (!enabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val req = PeriodicWorkRequestBuilder<AutoWorker>(minOf(intervalMin.coerceIn(30, 720), 60).toLong(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(
                WORK, if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, req,
            )
        }
    }
}

class PrefStorage(private val prefs: SharedPreferences) : Storage {
    override fun read(name: String): String? = prefs.getString(name, null)
    override fun write(name: String, value: String) {
        prefs.edit().putString(name, value).apply()
    }
}

class AndroidNotifier(private val ctx: Context) : Notifier {
    override fun notify(tag: String, title: String, text: String) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val account = tag.startsWith("expired:") || tag.startsWith("cookiefail:") || tag.startsWith("debug:") && tag.endsWith("couldn't be Renewed!") || tag.startsWith("debug:") && tag.endsWith("Your Session has Expired!")
        val open = PendingIntent.getActivity(
            ctx, if (account) 1 else 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_ACCOUNT, account),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(ctx, App.CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ctx.getColor(R.color.accent))
            .setContentTitle(title)
            .setContentText(text.lineSequence().firstOrNull().orEmpty())
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        ctx.getSystemService(NotificationManager::class.java).notify(tag, 1, n)
    }
}

class AutoWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val app = applicationContext as App
        return try {
            app.exclusive {
                if (app.engine.anythingDue()) app.engine.runAuto()
            }
            AutoScheduler.sync(app)
            Result.success()
        } catch (e: Exception) {
            AutoScheduler.sync(app, 10 * 60_000L)
            Result.retry()
        }
    }
}

