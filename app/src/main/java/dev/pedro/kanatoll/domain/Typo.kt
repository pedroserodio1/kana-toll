package dev.pedro.kanatoll.domain

/** Dois textos a no máximo uma edição um do outro: um caractere trocado, a mais, a menos, ou duas letras vizinhas trocadas. */
fun withinOneEdit(a: String, b: String): Boolean {
    if (a == b) return true
    if (kotlin.math.abs(a.length - b.length) > 1) return false
    if (a.length == b.length) {
        val diff = a.indices.filter { a[it] != b[it] }
        return diff.size == 1 || (diff.size == 2 && diff[1] == diff[0] + 1 && a[diff[0]] == b[diff[1]] && a[diff[1]] == b[diff[0]])
    }
    val (short, long) = if (a.length < b.length) a to b else b to a
    val i = short.indices.firstOrNull { short[it] != long[it] } ?: short.length
    return short.substring(i) == long.substring(i + 1)
}

/**
 * [input] parece um erro de digitação da resposta de [kana]: está a uma edição de uma resposta
 * aceita, mas não é, ela própria, o rōmaji de nenhum kana (então "ka" para き é errar o kana, não
 * errar a tecla).
 */
fun isNearMiss(kana: Kana, input: String): Boolean {
    val typed = input.trim().lowercase()
    if (typed.isEmpty() || typed in kana.accepted) return false
    if (KanaTable.all.any { it.accepts(typed) }) return false
    return kana.accepted.any { it.length >= 2 && withinOneEdit(typed, it) }
}
