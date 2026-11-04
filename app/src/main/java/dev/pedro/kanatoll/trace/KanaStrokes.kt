package dev.pedro.kanatoll.trace

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Um kana pronto para traçar: os traços em ordem, em coordenadas de canvas, e quantas "casas" ele ocupa (1, ou 2 no yōon). */
class TraceGlyph(val strokes: List<List<Pt>>, val cells: Int) {
    val width: Float get() = CELL * cells

    companion object {
        /** Lado de uma casa, a unidade do KanjiVG. */
        const val CELL = 109f
    }
}

/**
 * Traços dos kana, vindos do KanjiVG (CC BY-SA 3.0) e convertidos por um script em
 * `assets/kana_strokes.json`: cada caractere tem uma lista de traços, cada traço 40 pontos.
 */
class KanaStrokes private constructor(private val chars: Map<Char, List<List<Pt>>>) {

    /**
     * [glyph] é um kana (um caractere) ou um yōon (dois): a segunda casa recebe o ゃゅょ menor,
     * perto do canto inferior, como se escreve. `null` se faltar algum caractere nos dados.
     */
    fun layout(glyph: String): TraceGlyph? {
        val parts = glyph.map { chars[it] ?: return null }
        return when (parts.size) {
            1 -> TraceGlyph(parts[0], 1)
            2 -> TraceGlyph(parts[0] + parts[1].map { stroke -> stroke.map { Pt(TraceGlyph.CELL + 30f + it.x * 0.6f, 43f + it.y * 0.6f) } }, 2)
            else -> null
        }
    }

    companion object {
        private val serializer = MapSerializer(String.serializer(), ListSerializer(ListSerializer(ListSerializer(Float.serializer()))))

        fun parse(json: String): KanaStrokes =
            KanaStrokes(
                Json.decodeFromString(serializer, json).entries.associate { (k, strokes) ->
                    k.single() to strokes.map { stroke -> stroke.map { Pt(it[0], it[1]) } }
                },
            )

        fun load(context: Context): KanaStrokes =
            parse(context.assets.open("kana_strokes.json").bufferedReader().use { it.readText() })
    }
}
