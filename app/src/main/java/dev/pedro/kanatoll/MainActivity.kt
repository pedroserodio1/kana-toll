package dev.pedro.kanatoll

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
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
import dev.pedro.kanatoll.ui.SessionScreen
import dev.pedro.kanatoll.ui.SessionViewModel
import dev.pedro.kanatoll.rhythm.RhythmViewModel
import dev.pedro.kanatoll.ui.RhythmScreen
import dev.pedro.kanatoll.ui.SettingsScreen
import dev.pedro.kanatoll.ui.StatsScreen
import dev.pedro.kanatoll.ui.TraceScreen
import dev.pedro.kanatoll.ui.TollSettingsScreen

private enum class Screen { HOME, SESSION, SETTINGS, ABOUT, TOLL, TRACE, RHYTHM, STATS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // o fundo vai até as bordas; o conteúdo respeita barras e teclado via safeDrawingPadding
        setContent {
            KanaTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding()) {
                        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                        // Cada sessão tem a sua chave, para não reaproveitar o ViewModel da anterior.
                        var sessionKey by rememberSaveable { mutableIntStateOf(0) }
                        var rhythmKey by rememberSaveable { mutableIntStateOf(0) }
                        val home = { screen = Screen.HOME }
                        val settings = { screen = Screen.SETTINGS }
                        // O Pedágio e o Sobre vivem dentro de Configurações: Voltar sai deles para lá.
                        if (screen != Screen.HOME) BackHandler(onBack = if (screen == Screen.TOLL || screen == Screen.ABOUT) settings else home)
                        when (screen) {
                            Screen.HOME -> HomeScreen(
                                onStudy = { sessionKey++; screen = Screen.SESSION },
                                onTrace = { screen = Screen.TRACE },
                                onRhythm = { rhythmKey++; screen = Screen.RHYTHM },
                                onStats = { screen = Screen.STATS },
                                onSettings = { screen = Screen.SETTINGS },
                            )
                            Screen.SESSION -> SessionScreen(viewModel<SessionViewModel>(key = "session$sessionKey"), home)
                            Screen.SETTINGS -> SettingsScreen(home, onToll = { screen = Screen.TOLL }, onAbout = { screen = Screen.ABOUT })
                            Screen.ABOUT -> AboutScreen(settings)
                            Screen.STATS -> StatsScreen(home)
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
