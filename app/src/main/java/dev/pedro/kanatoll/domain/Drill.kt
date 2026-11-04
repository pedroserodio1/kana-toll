package dev.pedro.kanatoll.domain

import dev.pedro.kanatoll.data.Confusion
import dev.pedro.kanatoll.data.Script

/** Uma pergunta do treino: o rōmaji de [target] aparece e você escolhe o kana; [other] é o que você costuma trocar por ele. */
data class DrillItem(val script: Script, val target: String, val other: String)

fun scriptOf(glyph: String): Script = if (glyph.first() in 'ぁ'..'ゖ') Script.HIRAGANA else Script.KATAKANA

private fun isKnownGlyph(glyph: String) =
    glyph.isNotEmpty() && KanaTable.all.any { it.glyph(Script.HIRAGANA) == glyph || it.glyph(Script.KATAKANA) == glyph }

/**
 * Monta o treino com as 10 trocas mais frequentes. Cada par vira perguntas dos dois lados (ora um
 * é o alvo, ora o outro), até [questions] no máximo e no máximo 4 por par, embaralhadas.
 * Sem nenhuma troca registrada, devolve uma lista vazia.
 */
fun buildDrill(confusions: List<Confusion>, questions: Int = 12): List<DrillItem> {
    val pairs = confusions
        .filter { it.count > 0 && isKnownGlyph(it.expected) && isKnownGlyph(it.given) && it.expected != it.given }
        .sortedByDescending { it.count }
        .take(10)
    if (pairs.isEmpty()) return emptyList()
    return (0 until minOf(questions, pairs.size * 4)).map { i ->
        val pair = pairs[i % pairs.size]
        val script = scriptOf(pair.expected)
        // as voltas pares perguntam o kana certo; as ímpares, o kana que você deu no lugar
        if ((i / pairs.size) % 2 == 0) DrillItem(script, pair.expected, pair.given) else DrillItem(script, pair.given, pair.expected)
    }.shuffled()
}
