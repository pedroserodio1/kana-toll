package dev.pedro.kanatoll.domain

import dev.pedro.kanatoll.data.Script
import kotlin.random.Random

/** Grupos de kana que se confundem à primeira vista. Cada letra da string é um membro. */
private val GROUPS = mapOf(
    Script.HIRAGANA to listOf("しつんそ", "ぬめねれわの", "さちき", "はほま", "るろ", "あおむ", "くへ", "いり", "まも", "こに"),
    Script.KATAKANA to listOf("シツンソ", "クケタ", "ウワラ", "ヌスメ", "ナメ", "コユ", "マム", "ノメ", "チテ", "フワ"),
)

/**
 * Kana parecidos com [glyph]. Para um kana simples, os colegas de grupo da tabela acima.
 * Para um yōon (dois caracteres), os outros yōon que dividem a primeira letra ou o ゃゅょ.
 */
fun similarKana(glyph: String, script: Script): List<String> =
    if (glyph.length == 1) {
        GROUPS.getValue(script).filter { glyph[0] in it }.flatMap { g -> g.map { it.toString() } }.filter { it != glyph }.distinct()
    } else {
        KanaTable.all.map { it.glyph(script) }
            .filter { it.length == glyph.length && it != glyph && (it[0] == glyph[0] || it.last() == glyph.last()) }
    }

/**
 * Opções de escolha (a certa mais `count - 1` distratores), embaralhadas. Prioridade dos distratores:
 * 1. [learnedConfusions]: o que você já trocou pelo kana certo;
 * 2. parecidos que você já viu ([pool]);
 * 3. quaisquer kana que você já viu;
 * 4. parecidos e, por fim, qualquer kana do script, se o [pool] ainda for pequeno.
 */
fun buildOptions(
    correct: String,
    script: Script,
    learnedConfusions: List<String>,
    pool: List<String>,
    count: Int = 5,
    random: Random = Random.Default,
): List<String> {
    val picked = LinkedHashSet<String>()
    fun add(candidates: List<String>) {
        for (k in candidates) if (picked.size < count - 1 && k != correct) picked += k
    }
    val similar = similarKana(correct, script)
    add(learnedConfusions)
    add(similar.filter { it in pool }.shuffled(random))
    add(pool.shuffled(random))
    add(similar.shuffled(random))
    add(KanaTable.all.map { it.glyph(script) }.shuffled(random))
    return (picked + correct).shuffled(random)
}
