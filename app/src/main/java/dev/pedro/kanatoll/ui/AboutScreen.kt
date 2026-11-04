package dev.pedro.kanatoll.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Header("Sobre e fontes", onBack)
        Spacer(Modifier.height(12.dp))
        SoftCard {
            Text("Kana", style = MaterialTheme.typography.titleMedium)
            Text("App pessoal de estudo de hiragana e katakana.", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(16.dp))
        SoftCard {
            Text("Repetição espaçada", style = MaterialTheme.typography.titleMedium)
            Text(
                "FSRS-6, do projeto open-spaced-repetition. O porte para Kotlin parte do ts-fsrs " +
                    "(licença MIT, github.com/open-spaced-repetition/ts-fsrs).",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        SoftCard {
            Text("Ordem e forma dos traços", style = MaterialTheme.typography.titleMedium)
            Text(
                "KanjiVG (Ulrich Apel e colaboradores), licença CC BY-SA 3.0, kanjivg.tagaini.net. " +
                    "O arquivo de traços do app é derivado dele e fica sob a mesma licença.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        SoftCard {
            Text("Fontes", style = MaterialTheme.typography.titleMedium)
            Text(
                "Klee One e Zen Maru Gothic, licença SIL Open Font License 1.1 (Google Fonts).",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
