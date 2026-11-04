# Decisões: app de japonês (Pedágio de kana)

Registro das decisões de produto e técnica tomadas antes de escrever código.
Base: [pesquisa prévia (nov/2026)](https://claude.ai/artifact/UVotqwTLyGFyPYQww7MZwZ).

Status: 🟢 decidido · 🟡 proposta, aguardando confirmação · ⚪ aberto

| # | Ramo | Status |
|---|---|---|
| 1 | Escopo do v1 | 🟢 |
| 2 | Pedágio | 🟢 (spike pendente) |
| 3 | Portão por domínio | 🟢 |
| 4 | Conteúdo | 🟢 |
| 5 | Cartões e FSRS | 🟢 (retenção 🟡) |
| 6 | Superfícies secundárias | 🟢 (ritmo×FSRS 🟡) |
| 7 | Stack e aparelho | 🟢 |
| 8 | Persistência e backup | 🟢 |

## Resumo em uma tela

Um app Android **só seu** (sideload), em Kotlin + Compose + Room, para um Samsung com One UI 9. O centro é o **FSRS com portão por domínio**: os 107 hiragana em três direções, e o katakana só libera quando todos os cartões de leitura e de produção tiverem stability ≥ 7 dias. O v1 sai em 5 marcos: **núcleo → notificação/widget → Pedágio → traçado → ritmo**. O Pedágio usa AccessibilityService e cobra 3 cartões devidos por abertura, com carência de 30 min e um "agora não" sempre visível. O teclado-professor, as palavras e os kanji ficam fora do v1.

---

## 1. Escopo do v1 🟡

**Razão de ser do app:** o núcleo FSRS + portão por domínio. Se só isso funcionar, o app já vale. O Pedágio é um meio de trazer você até o núcleo, não o fim.

**Entra no v1:** núcleo FSRS + hiragana, Pedágio, quiz na notificação, widget, ritmo de kana, traçado.
**Fora do v1:** teclado-professor (IME).

**Ressalva:** são cinco superfícies além do núcleo, e ritmo e traçado são caros. Por isso o v1 é entregue em marcos, e cada marco precisa ser usável sozinho (ver ordem abaixo).

**Ordem dos marcos** 🟢

1. **M1 Núcleo:** FSRS, cartões de hiragana, pares confundíveis, portão.
2. **M2 Superfícies baratas:** quiz na notificação, widget.
3. **M3 Pedágio.**
4. **M4 Traçado.**
5. **M5 Ritmo de kana.**

A ordem segue o critério: primeiro o que dá valor, depois o barato, depois o arriscado, por último o caro.

## 2. Pedágio (M3) 🟢

- **Quais apps:** uma lista que você escolhe numa tela com os apps instalados.
- **Quando:** a cada abertura de um app da lista, com carência de **30 min** (configurável) por app depois de pagar.
- **Rodada:** **3 cartões devidos** do FSRS, que contam como revisão de verdade. Se nada estiver devido, entram os cartões de menor stability.
- **Saída:** o botão "agora não" fica **sempre visível, sem limite**. O app só registra quantos pulos houve.
- **Detecção:** **AccessibilityService**, pelo evento de mudança de janela (`TYPE_WINDOW_STATE_CHANGED`).
- **Exibição:** um overlay de acessibilidade (`TYPE_ACCESSIBILITY_OVERLAY`), que não exige SYSTEM_ALERT_WINDOW. ⚠ Verificar no spike: o campo de digitar rōmaji recebe foco e teclado dentro do overlay? Se não receber, abrir uma Activity própria.
- **Riscos (One UI):** o sistema pode desligar o serviço de acessibilidade sozinho. O app precisa detectar quando o serviço está desligado e avisar.

## 3. Portão por domínio 🟢

- **Critério:** o próximo script libera quando **todo** cartão do script atual tem stability ≥ 7 dias **e** a última resposta foi correta.
- **Escopo:** os 107 kana do script: 46 básicos, 25 com dakuten/handakuten e 36 yōon.
- **Regressão:** depois que o portão abre, ele não fecha mais. Um kana que falhar volta para a fila do FSRS normalmente.
- **Dependência:** o número de cartões depende das direções de teste (ramo 5). Com duas direções são 214 cartões, e o portão exige as duas.

## 4. Conteúdo 🟢

- **v1:** só kana. Hiragana (107) → portão → katakana (107).
- **Palavras:** não entram no v1. Vêm depois do portão do katakana.
- **Kanji (pós-v1):** por nível JLPT, N5 → N4 → … até o joyo (2.136). Cada nível tem o seu portão. As listas de JLPT não são oficiais e servem de aproximação.

## 5. Cartões e FSRS 🟢

- **Algoritmo:** porte do FSRS v6 para Kotlin, partindo da implementação em Java ou TS. Parâmetros padrão, sem otimização até haver histórico seu. Retenção desejada padrão de 90% (🟡, ajustável depois).
- **Teste ativo sempre:** nenhum cartão do tipo "mostrar e confirmar".
- **Direções:** cada uma é um cartão separado, 107 × 3 = 321 cartões por script.

| Direção | Resposta no M1 | Conta para o portão |
|---|---|---|
| kana → rōmaji | digitar rōmaji, com variantes aceitas (shi/si, tsu/tu, fu/hu…) | sim |
| rōmaji → kana | escolha entre 4 a 6 opções sorteadas de pares confundíveis | sim |
| áudio → kana | escolha entre confundíveis; áudio pelo TTS do Android (ja-JP) | não (treino) |

- **Novos por dia:** 10 kana, que entram nas 3 direções de uma vez (30 cartões).
- **Confusões:** registrar "errou X respondendo Y" e reforçar esse par.
- **Risco aceito:** a carga de revisão no pico deve passar de 100 por dia. Se pesar, baixe para 5 kana por dia.
- **Pendência técnica:** confirmar que a voz ja-JP do Google TTS está instalada no Samsung. Avaliar a pronúncia de kana isolado, sobretudo yōon e っ.
- **No M4:** o traçado substitui a escolha no cartão rōmaji → kana? _decidir no M4_

## 6. Superfícies secundárias 🟢

### Quiz na notificação (M2)
- **Quando:** só quando houver cartão devido. No máximo 1 por hora, numa janela do dia (padrão 9h–22h, configurável).
- **Resposta:** digitada via RemoteInput, sem abrir o app. Por isso a notificação só puxa cartões **kana → rōmaji**.
- **Permissões:** POST_NOTIFICATIONS. Agendamento pelo WorkManager (mínimo de 15 min, suficiente para "1 por hora").

### Widget (M2)
- Com Glance. Mostra quantos cartões estão devidos e o progresso do portão (ex.: 142/214). Tocar abre a sessão.

### Traçado (M4)
- Um modo opcional à parte, com a ordem dos traços do KanjiVG. Não substitui a escolha no rōmaji → kana e não conta para o portão.
- ⚠ Verificar se o KanjiVG cobre todos os kana, inclusive os pequenos (ゃ, っ).

### Ritmo de kana (M5)
- **Formato:** leitura sob batida. Os kana aparecem no ritmo de um metrônomo, você responde antes da próxima batida, e o BPM sobe com os acertos. Áudio de baixa latência com Oboe.
- **Relação com o FSRS** 🟡 (sem preferência sua, então fica o padrão): as respostas **não** alimentam o FSRS. O ritmo usa só kana com stability ≥ 7 e registra as estatísticas à parte.

### Teclado-professor (IME)
- Fora do v1.

## 7. Stack e aparelho 🟢

- **Stack:** Kotlin + Jetpack Compose + Room. Glance para o widget.
- **Aparelho:** Samsung com One UI 9, que deve ser o Android 17. Confirmar o número da API na Fase 0.
- **Distribuição:** só seu, por sideload, sem Play Store. O app pode usar PACKAGE_USAGE_STATS, acessibilidade e overlay sem revisão de loja.
- **minSdk:** a versão do seu aparelho. Não há suporte a versões antigas.
- **Risco Samsung:** a One UI mata processos em segundo plano. O app precisa pedir isenção de otimização de bateria e incluir instruções para "Apps que nunca suspendem".

## 8. Persistência e backup 🟢

- O histórico do FSRS é o ativo do app: perdê-lo significa recomeçar.
- **Auto Backup do Android** (Google Drive) ligado para o banco Room.
- **Exportar e importar um JSON** manualmente, com um botão nas configurações.
- **Sem sync** entre aparelhos.
- ⚠ Confirmar se o Auto Backup funciona com um APK instalado por sideload.

---

## Verificações da Fase 0 (no seu aparelho, antes do M1)

- [x] O número da API do Android na One UI 9: **API 37** (Android 17, Galaxy SM-S936B, confirmado pelo Android Studio em 2026-11-04). O `minSdk` fica em 34 por enquanto: sobe para 37 só junto com um AGP/compileSdk que o suporte, sem ganho para um app só seu.
- [ ] A voz ja-JP do Google TTS: está instalada, e como soam os kana isolados, yōon e っ.
- [ ] Auto Backup com o APK de sideload.

## Spike do M3 (antes de construir o Pedágio)

- [x] O AccessibilityService detecta a abertura dos apps da lista de forma confiável. **Sim** (2026-11-04, Instagram, pelo launcher e pelos recentes). Cuidados para o M3: o mesmo app emite vários eventos por abertura (telas e diálogos internos), e há ruído de `systemui`, `honeyboard` (teclado), `spay` e `launcher`. A carência por app absorve os repetidos; só reagir a pacotes da lista.
- [x] O overlay de acessibilidade aceita foco e teclado (para digitar rōmaji). **Sim**: `TYPE_ACCESSIBILITY_OVERLAY` focável recebeu o teclado Samsung e o texto digitado. Não precisa de Activity própria.
- [ ] A One UI desliga o serviço depois de horas ou de um reboot? Como detectar isso e avisar.

## Riscos aceitos

- O v1 tem 5 marcos. O ritmo (M5) e o traçado (M4) são caros e podem escorregar.
- Com 30 cartões novos por dia, a carga de revisão no pico deve passar de 100 por dia.
- O portão de 7 dias é uma escolha de design, sem estudo direto que o sustente.

---

## Fontes e licenças (já decidido pela pesquisa)

- KanjiVG (CC BY-SA 3.0), KANJIDIC2 (CC BY-SA 4.0), Tatoeba: atribuição numa tela "Sobre/Fontes".
- Livros servem como roteiro de ordem, não como fonte de conteúdo.
