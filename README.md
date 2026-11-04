# Kana

App Android pessoal para aprender **hiragana e katakana** com repetição espaçada.
O centro é o **FSRS** com um **portão por domínio**: o katakana só libera quando você domina o hiragana. Para trazer você até os cartões, existe o **Pedágio**: antes de abrir um app da sua lista, algumas revisões de kana.

[![Release](https://img.shields.io/github/v/release/pedroserodio1/kana-toll?include_prereleases)](https://github.com/pedroserodio1/kana-toll/releases)
[![Licença: MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-green)](LICENSE)

> Feito para uso próprio, instalado por APK (sem Play Store). O app **não pede permissão de internet**: o histórico fica no aparelho.

## O que ele faz

**Estudo**
- **Cartões** dos 107 kana de cada script (46 básicos, 25 com dakuten/handakuten e 36 yōon), em três direções: *kana → rōmaji* (digitando, com variantes como `shi`/`si`), *rōmaji → kana* (escolhendo entre parecidos) e *áudio → kana* (voz japonesa do Android).
- **FSRS-6** agenda cada cartão. São 10 kana novos por dia (3 cartões cada) e a retenção desejada é ajustável.
- **Portão por domínio:** o katakana libera quando *todo* cartão de leitura e produção do hiragana tem estabilidade de 7 dias ou mais e a última resposta correta. Depois de aberto, não fecha.
- **Confusões:** cada erro registra "errou X respondendo Y" e reforça esse par nas opções. Há um **treino das confusões** só com os seus pares mais trocados.
- **Foi erro de digitação:** um erro que parece só de digitação (`shii` para し) fica pendente até você decidir se vale como certo.
- **Estudar à frente:** quando não há nada devido, estude os próximos 10 cartões.

**Fora da sessão**
- **Pedágio:** um serviço de acessibilidade detecta a abertura dos apps que você escolheu e mostra 3 cartões por cima. "Agora não" fica sempre visível; pagar dá uma carência (30 min por padrão). Conta como revisão de verdade.
- **Quiz na notificação**, respondido digitando na própria notificação, no máximo um por hora numa janela do dia.
- **Widgets:** revisões e portão, **meta do dia** com dias seguidos, e **atalhos** para cada modo.
- **Menu do ícone** (segurar o ícone): Estudar, Traçado, Ritmo e Estatísticas.

**Treinos à parte** (não contam para o portão nem para o FSRS)
- **Traçado:** desenhe o kana traço a traço; cada traço é comparado com a ordem e a forma do [KanjiVG](https://kanjivg.tagaini.net).
- **Ritmo:** leitura sob batida; o BPM sobe com os acertos.

**Acompanhamento e dados**
- **Estatísticas:** acerto, últimos 14 dias, progresso, **mapa de kana**, carga dos próximos dias, onde você erra.
- **Meta diária** e resumo de fim de dia (notificação opcional).
- **Backup** do histórico em JSON, manual ou automático e semanal numa pasta que você escolhe (pode ser uma pasta sincronizada pelo Drive).
- Tema **claro, escuro ou do sistema**.

## Instalar

1. Em [Releases](https://github.com/pedroserodio1/kana-toll/releases), baixe o `kana-toll-vX.Y.Z.apk` da versão mais recente.
2. Abra o APK no celular e permita a instalação de fontes desconhecidas, se o Android pedir.
3. Requer **Android 14 ou superior**.

Permissões, todas opcionais e pedidas dentro do app:
- **Notificações:** quiz e resumo do dia.
- **Acessibilidade:** só para o Pedágio. O serviço escuta apenas a troca de janela (qual app abriu) e **não lê o conteúdo das telas**. Em aparelhos que bloqueiam serviços de apps instalados por fora, libere em *Informações do app → ⋮ → Permitir configurações restritas*.
- **Isenção de bateria:** ajuda o quiz e os widgets a rodar em segundo plano. Na One UI, adicione também o app em *Bateria → Limites de uso em segundo plano → Apps que nunca suspendem*.

> Reinstalar o app desliga o serviço de acessibilidade; religue em *Configurações → Pedágio*.
> Antes de desinstalar, exporte o histórico em *Configurações → Backup*.

## Compilar

Requisitos: **JDK 17 a 21** e o Android SDK (plataforma 35). O Android Studio mais recente traz um JDK que o Gradle 8.9 do projeto ainda não aceita; aponte o *Gradle JDK* para o 21.

```bash
./gradlew assembleDebug          # gera app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # instala no aparelho conectado
./gradlew testDebugUnitTest      # testes unitários (JVM)
./gradlew connectedDebugAndroidTest   # testes com banco de verdade, em aparelho conectado
```

## Publicar uma versão

O pipeline em [`.github/workflows/release.yml`](.github/workflows/release.yml) roda ao criar uma tag `vX.Y.Z`: executa os testes, gera o APK assinado e cria uma **Release** com o APK anexado.

```bash
git tag v1.0.0
git push origin v1.0.0
```

A versão vem da tag (`v1.2.3` vira `versionName 1.2.3` e `versionCode 10203`). A assinatura usa quatro segredos do repositório (*Settings → Secrets and variables → Actions*):

| Segredo | Conteúdo |
|---|---|
| `KEYSTORE_BASE64` | o keystore em base64 (`base64 -w0 meu.keystore`) |
| `KEYSTORE_PASSWORD` | senha do keystore |
| `KEY_ALIAS` | alias da chave |
| `KEY_PASSWORD` | senha da chave |

Use **sempre o mesmo keystore**: o Android só atualiza um app por cima de outro se a assinatura for a mesma.

## Estrutura

```
app/src/main/java/dev/pedro/kanatoll/
├── fsrs/      FSRS-6 (porte do ts-fsrs) e o dia de estudo, que vira às 04:00
├── domain/    tabela de kana, opções, fila da sessão, portão, estatísticas, treino, resumo
├── data/      Room (cartões, respostas, confusões, portão), repositório e preferências
├── ui/        telas em Jetpack Compose e o tema
├── toll/      Pedágio: serviço de acessibilidade e a rodada de cartões
├── notif/     quiz na notificação, resumo do dia e o worker de 15 minutos
├── widget/    widgets em Glance
├── trace/     comparação de traços com os dados do KanjiVG
├── rhythm/    metrônomo (AudioTrack de baixa latência) e regras do ritmo
└── backup/    exportar e importar em JSON, e o backup automático
```

Kotlin, Jetpack Compose, Room, WorkManager e Glance. `minSdk 34`.

## Decisões de projeto

As decisões de produto e técnicas, o que mudou durante a implementação e o que ainda falta verificar estão em [`docs/decisoes.md`](docs/decisoes.md).

## Créditos e licenças

O código é **MIT** ([`LICENSE`](LICENSE)). Os materiais de terceiros mantêm as licenças deles; os detalhes estão em [`NOTICE.md`](NOTICE.md):

- **FSRS-6:** porte do [ts-fsrs](https://github.com/open-spaced-repetition/ts-fsrs) (MIT).
- **Traços dos kana:** derivados do [KanjiVG](https://kanjivg.tagaini.net) (CC BY-SA 3.0).
- **Fontes:** Klee One e Zen Maru Gothic (SIL Open Font License 1.1).
