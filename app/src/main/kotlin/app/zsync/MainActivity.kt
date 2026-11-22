package app.zsync

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zsync.ui.*

enum class Screen { HOME, SETTINGS, LOGIN, EVENTS, GACHA_NEXT, PREMIUM, ENDGAME }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            MimoStickers.advance(this)
            (application as App).repo.settings.value.iconKey.let { runCatching { AppIcons.ensure(this, it) } }
        }
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            val iconKey by (application as App).repo.settings.collectAsStateWithLifecycle().let { st -> remember { derivedStateOf { st.value.iconKey } } }
            AppTheme(iconKey) {
                val vm: MainVm = viewModel()
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                var settingsAccount by remember { mutableStateOf(false) }
                LaunchedEffect(openAccount) {
                    if (openAccount) { settingsAccount = true; screen = Screen.SETTINGS; openAccount = false }
                }

                val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                BackHandler(screen != Screen.HOME) {
                    screen = if (screen == Screen.LOGIN) Screen.SETTINGS else Screen.HOME
                }
                val splashDone by vm.splashDone.collectAsStateWithLifecycle()
                    Crossfade(targetState = splashDone, label = "splash") { ready ->
                        if (!ready) {
                            val busy by vm.busy.collectAsStateWithLifecycle()
                            val accounts by vm.repo.accounts.collectAsStateWithLifecycle()
                            SplashScreen(
                                loading = "notes" in busy, hasAccount = accounts.any { it.activeRoles().isNotEmpty() },
                                sound = vm.repo.settings.value.startupSound,
                                character = Characters.byKey(vm.repo.settings.value.iconKey),
                                onDone = { vm.splashDone.value = true },
                            )
                        } else
                    when (screen) {
                            Screen.HOME -> HomeScreen(vm, onSettings = { screen = Screen.SETTINGS },
                                onLogin = { vm.resetLogin(); screen = Screen.LOGIN }, onEvents = { screen = Screen.EVENTS },
                                onGachaNext = { screen = Screen.GACHA_NEXT }, onPremium = { screen = Screen.PREMIUM },
                                onEndGame = { screen = Screen.ENDGAME })
                            Screen.GACHA_NEXT -> GachaNextScreen(vm, onBack = { screen = Screen.HOME })
                            Screen.PREMIUM -> PremiumScreen(onBack = { screen = Screen.HOME })
                            Screen.ENDGAME -> EndGameScreen(vm, onBack = { screen = Screen.HOME })
                            Screen.EVENTS -> EventsScreen(vm, onBack = { screen = Screen.HOME })
                            Screen.SETTINGS -> SettingsScreen(vm, openAccount = settingsAccount, onAccountOpened = { settingsAccount = false }, onBack = { screen = Screen.HOME }, onLogin = { vm.resetLogin(); screen = Screen.LOGIN })
                            Screen.LOGIN -> LoginScreen(vm, onDone = { screen = Screen.HOME }, onBack = { screen = Screen.HOME })
                        }
                    }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private var openAccount by mutableStateOf(false)

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ACCOUNT, false) != true) return
        intent.removeExtra(EXTRA_ACCOUNT)
        openAccount = true
    }

    companion object {
        const val EXTRA_ACCOUNT = "open_account"
    }
}
