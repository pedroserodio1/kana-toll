package dev.pedro.kanatoll.domain

import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.data.Direction
import dev.pedro.kanatoll.data.Script

enum class KanaGroup { BASIC, DAKUTEN, YOON }

/**
 * Variantes de rōmaji aceitas ao digitar, indexadas pelo rōmaji canônico (o que aparece no
 * cartão rōmaji → kana). O canônico de ぢ/づ/ぢゃ é di/du/dya (como num IME), para que cada
 * prompt tenha uma única resposta em kana; ji/zu/ja continuam aceitos quando se digita.
 */
private val VARIANTS: Map<String, List<String>> = mapOf(
    "shi" to listOf("si"), "chi" to listOf("ti"), "tsu" to listOf("tu"), "fu" to listOf("hu"),
    "ji" to listOf("zi"), "wo" to listOf("o"), "n" to listOf("nn", "n'"),
    "sha" to listOf("sya", "shya"), "shu" to listOf("syu", "shyu"), "sho" to listOf("syo", "shyo"),
    "cha" to listOf("tya", "cya"), "chu" to listOf("tyu", "cyu"), "cho" to listOf("tyo", "cyo"),
    "ja" to listOf("zya", "jya"), "ju" to listOf("zyu", "jyu"), "jo" to listOf("zyo", "jyo"),
    "di" to listOf("ji", "zi"), "du" to listOf("zu"),
    "dya" to listOf("ja", "zya", "jya"), "dyu" to listOf("ju", "zyu", "jyu"), "dyo" to listOf("jo", "zyo", "jyo"),
)

/** [hira] é sempre hiragana; o katakana é derivado (mesmo bloco Unicode, deslocado em 0x60). */
data class Kana(val hira: String, val romaji: String, val group: KanaGroup) {
    val accepted: Set<String> = setOf(romaji) + (VARIANTS[romaji] ?: emptyList())

    fun glyph(script: Script): String =
        if (script == Script.HIRAGANA) hira else hira.map { it + 0x60 }.joinToString("")

    fun accepts(input: String): Boolean = input.trim().lowercase() in accepted
}

object KanaTable {
    private fun rows(group: KanaGroup, vararg rows: String): List<Kana> =
        rows.flatMap { it.split(" ") }.chunked(2).map { (k, r) -> Kana(k, r, group) }

    /** 107 kana na ordem de introdução: básicos por fileira → dakuten/handakuten → yōon. */
    val all: List<Kana> =
        rows(
            KanaGroup.BASIC,
            "あ a い i う u え e お o",
            "か ka き ki く ku け ke こ ko",
            "さ sa し shi す su せ se そ so",
            "た ta ち chi つ tsu て te と to",
            "な na に ni ぬ nu ね ne の no",
            "は ha ひ hi ふ fu へ he ほ ho",
            "ま ma み mi む mu め me も mo",
            "や ya ゆ yu よ yo",
            "ら ra り ri る ru れ re ろ ro",
            "わ wa を wo",
            "ん n",
        ) + rows(
            KanaGroup.DAKUTEN,
            "が ga ぎ gi ぐ gu げ ge ご go",
            "ざ za じ ji ず zu ぜ ze ぞ zo",
            "だ da ぢ di づ du で de ど do",
            "ば ba び bi ぶ bu べ be ぼ bo",
            "ぱ pa ぴ pi ぷ pu ぺ pe ぽ po",
        ) + rows(
            KanaGroup.YOON,
            "きゃ kya きゅ kyu きょ kyo",
            "しゃ sha しゅ shu しょ sho",
            "ちゃ cha ちゅ chu ちょ cho",
            "にゃ nya にゅ nyu にょ nyo",
            "ひゃ hya ひゅ hyu ひょ hyo",
            "みゃ mya みゅ myu みょ myo",
            "りゃ rya りゅ ryu りょ ryo",
            "ぎゃ gya ぎゅ gyu ぎょ gyo",
            "じゃ ja じゅ ju じょ jo",
            "ぢゃ dya ぢゅ dyu ぢょ dyo",
            "びゃ bya びゅ byu びょ byo",
            "ぴゃ pya ぴゅ pyu ぴょ pyo",
        )

    fun find(script: Script, glyph: String): Kana = all.first { it.glyph(script) == glyph }

    /** Os 321 cartões do script (3 direções por kana), na ordem de introdução, para semear o banco. */
    fun cards(script: Script): List<Card> =
        all.flatMap { k -> Direction.entries.map { Card(script = script, kana = k.glyph(script), direction = it) } }
}
