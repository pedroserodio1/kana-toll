# Avisos de terceiros

O código deste repositório é MIT (arquivo `LICENSE`). A licença MIT vale para o código, **não** para os materiais abaixo, que mantêm as licenças originais.

## Algoritmo FSRS-6

`app/src/main/java/dev/pedro/kanatoll/fsrs/Fsrs.kt` é um porte para Kotlin das fórmulas e dos pesos padrão do
[ts-fsrs](https://github.com/open-spaced-repetition/ts-fsrs) (FSRS-6), do projeto
[open-spaced-repetition](https://github.com/open-spaced-repetition).

- Licença: MIT, © 2026 Open Spaced Repetition. Texto em `third_party/licenses/ts-fsrs-MIT.txt`.

## Ordem e forma dos traços

`app/src/main/assets/kana_strokes.json` é derivado do [KanjiVG](https://kanjivg.tagaini.net): os traços dos 148
caracteres de kana usados pelo app, convertidos em polilinhas de 40 pontos.

- © Ulrich Apel e colaboradores do KanjiVG.
- Licença: [Creative Commons Atribuição-CompartilhaIgual 3.0 (CC BY-SA 3.0)](https://creativecommons.org/licenses/by-sa/3.0/).
  O arquivo derivado continua sob a mesma licença.

## Fontes

Em `app/src/main/res/font/`, do repositório [google/fonts](https://github.com/google/fonts), ambas sob a
SIL Open Font License 1.1:

| Fonte | Direitos | Texto da licença |
|---|---|---|
| Klee One | © 2020 The Klee Project Authors | `third_party/licenses/KleeOne-OFL.txt` |
| Zen Maru Gothic | © 2021 The Zen Maru Gothic Project Authors | `third_party/licenses/ZenMaruGothic-OFL.txt` |

## Bibliotecas

As dependências (AndroidX, Jetpack Compose, Room, WorkManager, Glance, Kotlin e kotlinx) são baixadas pelo
Gradle e não ficam neste repositório; usam, em geral, a licença Apache 2.0.
