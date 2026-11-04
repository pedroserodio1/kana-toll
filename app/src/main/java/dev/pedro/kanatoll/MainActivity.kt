package dev.pedro.kanatoll

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pedro.kanatoll.ui.AboutScreen
import dev.pedro.kanatoll.ui.HomeScreen
import dev.pedro.kanatoll.ui.KanaTheme
import dev.pedro.kanatoll.ui.AheadSessionViewModel
import dev.pedro.kanatoll.ui.DrillViewModel
import dev.pedro.kanatoll.ui.SessionScreen
import dev.pedro.kanatoll.ui.SessionViewModel
import dev.pedro.kanatoll.rhythm.RhythmViewModel
import dev.pedro.kanatoll.ui.RhythmScreen
import dev.pedro.kanatoll.ui.SettingsScreen
import dev.pedro.kanatoll.ui.StatsScreen
import dev.pedro.kanatoll.ui.TraceScreen
import dev.pedro.kanatoll.ui.TollSettingsScreen
import dev.pedro.kanatoll.ui.useDarkTheme

private enum class Screen { HOME, SESSION, AHEAD, SETTINGS, ABOUT, TOLL, TRACE, RHYTHM, STATS, DRILL }

class MainActivity : ComponentActivity() {
    /** Destino pedido por um atalho do ícone ou por um widget (ver [Destinations]); a tela consome e zera. */
    private var requested by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requested = intent.getStringExtra(Destinations.EXTRA_SCREEN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Só na criação de verdade: ao girar a tela o mesmo intent voltaria a navegar.
        if (savedInstanceState == null) requested = intent?.getStringExtra(Destinations.EXTRA_SCREEN)
        enableEdgeToEdge() // o fundo vai até as bordas; o conteúdo respeita barras e teclado via safeDrawingPadding
        setContent {
            val container = (application as KanaApp).container
            val themeMode by container.themeMode.collectAsState()
            val dark = useDarkTheme(themeMode)
            // Ícones das barras do sistema legíveis no tema escolhido, que pode ser diferente do do aparelho.
            SideEffect {
                val transparent = android.graphics.Color.TRANSPARENT
                val bars = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            KanaTheme(themeMode) {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding()) {
                        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                        // Cada sessão tem a sua chave, para não reaproveitar o ViewModel da anterior.
                        var sessionKey by rememberSaveable { mutableIntStateOf(0) }
                        var aheadKey by rememberSaveable { mutableIntStateOf(0) }
                        var rhythmKey by rememberSaveable { mutableIntStateOf(0) }
                        var drillKey by rememberSaveable { mutableIntStateOf(0) }
                        val more = { aheadKey++; screen = Screen.AHEAD }
                        // Atalho do ícone ou widget: abre direto na tela pedida.
                        LaunchedEffect(requested) {
                            when (requested) {
                                Destinations.SESSION -> { sessionKey++; screen = Screen.SESSION }
                                Destinations.TRACE -> screen = Screen.TRACE
                                Destinations.RHYTHM -> { rhythmKey++; screen = Screen.RHYTHM }
                                Destinations.STATS -> screen = Screen.STATS
                                else -> return@LaunchedEffect
                            }
                            requested = null
                        }
                        val home = { screen = Screen.HOME }
                        val settings = { screen = Screen.SETTINGS }
                        val stats = { screen = Screen.STATS }
                        // O Pedágio e o Sobre vivem dentro de Configurações, e o treino das confusões dentro das
                        // Estatísticas: Voltar sai deles para lá.
                        if (screen != Screen.HOME) {
                            BackHandler(
                                onBack = when (screen) {
                                    Screen.TOLL, Screen.ABOUT -> settings
                                    Screen.DRILL -> stats
                                    else -> home
                                },
                            )
                        }
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                onStudy = { sessionKey++; screen = Screen.SESSION },
                                onAhead = more,
                                onTrace = { screen = Screen.TRACE },
                                onRhythm = { rhythmKey++; screen = Screen.RHYTHM },
                                onStats = { screen = Screen.STATS },
                                onSettings = { screen = Screen.SETTINGS },
                            )
                            Screen.SESSION -> SessionScreen(viewModel<SessionViewModel>(key = "session$sessionKey"), home, onMore = more)
                            Screen.AHEAD -> SessionScreen(viewModel<AheadSessionViewModel>(key = "ahead$aheadKey"), home, onMore = more)
                            Screen.SETTINGS -> SettingsScreen(home, onToll = { screen = Screen.TOLL }, onAbout = { screen = Screen.ABOUT })
                            Screen.ABOUT -> AboutScreen(settings)
                            Screen.STATS -> StatsScreen(home, onDrill = { drillKey++; screen = Screen.DRILL })
                            Screen.DRILL -> SessionScreen(
                                viewModel<DrillViewModel>(key = "drill$drillKey"),
                                onExit = stats,
                                onMore = { drillKey++ },
                                moreLabel = "Treinar de novo",
                                emptyText = "Sem trocas registradas ainda.",
                                emptyHint = "Elas aparecem conforme você erra nas perguntas de escolha e de digitação.",
                            )
                            Screen.TOLL -> TollSettingsScreen(settings)
                            Screen.TRACE -> TraceScreen(home)
                            Screen.RHYTHM -> RhythmScreen(viewModel<RhythmViewModel>(key = "rhythm$rhythmKey"), home)
                        }
                    }
                }
            }
        }
    }
}
