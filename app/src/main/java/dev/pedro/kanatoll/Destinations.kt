package dev.pedro.kanatoll

import android.content.Context
import android.content.Intent

/**
 * Telas que dá para abrir direto de fora do app: os atalhos do ícone e os widgets mandam o nome
 * no extra [EXTRA_SCREEN] e a [MainActivity] navega para lá. Os nomes também estão em `res/xml/shortcuts.xml`.
 */
object Destinations {
    const val EXTRA_SCREEN = "screen"
    const val SESSION = "session"
    const val TRACE = "trace"
    const val RHYTHM = "rhythm"
    const val STATS = "stats"

    /**
     * Intent que abre o app em [destination]. Cada destino tem a sua `action`: o Android
     * considera iguais, para efeito de PendingIntent, intents que só diferem nos extras, e os
     * botões de um mesmo widget acabariam abrindo todos a mesma tela.
     */
    fun intent(context: Context, destination: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction("dev.pedro.kanatoll.OPEN_$destination")
            .putExtra(EXTRA_SCREEN, destination)
}
