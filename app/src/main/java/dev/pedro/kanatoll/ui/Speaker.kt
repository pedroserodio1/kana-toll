package dev.pedro.kanatoll.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred

/**
 * TTS do Android em ja-JP, para o cartão áudio → kana. Vive no AppContainer (um por processo).
 * [ready] vira `false` se não houver motor de TTS ou voz japonesa instalada; a sessão então
 * deixa os cartões de áudio de fora.
 */
class Speaker(context: Context) {
    val ready = CompletableDeferred<Boolean>()
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status ->
            val language = tts?.setLanguage(Locale.JAPANESE) ?: TextToSpeech.LANG_NOT_SUPPORTED
            ready.complete(status == TextToSpeech.SUCCESS && language >= TextToSpeech.LANG_AVAILABLE)
        }
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
    }
}
