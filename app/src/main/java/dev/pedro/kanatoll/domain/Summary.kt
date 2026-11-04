package dev.pedro.kanatoll.domain

import kotlin.math.roundToInt

/** Texto do resumo do dia: o que foi respondido, a taxa de acerto e a situação da meta ([goal] 0 = sem meta). */
fun summaryText(answers: Int, correct: Int, goal: Int): String {
    val today = if (answers == 0) {
        "Hoje você ainda não estudou."
    } else {
        val percent = (100f * correct / answers).roundToInt()
        "Hoje: $answers ${if (answers == 1) "resposta" else "respostas"}, $percent% de acerto."
    }
    val target = when {
        goal <= 0 -> ""
        answers >= goal -> " Meta de $goal batida."
        else -> " Faltam ${goal - answers} para a meta de $goal."
    }
    return today + target
}
