package dev.pedro.kanatoll.rhythm

import org.junit.Assert.assertEquals
import org.junit.Test

class RhythmRulesTest {
    @Test fun `acerto sobe 4 e erro desce 8`() {
        assertEquals(64, RhythmRules.nextBpm(60, hit = true))
        assertEquals(52, RhythmRules.nextBpm(60, hit = false))
    }

    @Test fun `o BPM respeita o piso e o teto`() {
        assertEquals(RhythmRules.MAX_BPM, RhythmRules.nextBpm(RhythmRules.MAX_BPM, hit = true))
        assertEquals(RhythmRules.MAX_BPM, RhythmRules.nextBpm(RhythmRules.MAX_BPM - 1, hit = true))
        assertEquals(RhythmRules.MIN_BPM, RhythmRules.nextBpm(RhythmRules.MIN_BPM, hit = false))
        assertEquals(RhythmRules.MIN_BPM, RhythmRules.nextBpm(RhythmRules.MIN_BPM + 3, hit = false))
    }

    @Test fun `duracao da batida em quadros de audio`() {
        assertEquals(48_000L, RhythmRules.beatFrames(60, 48_000))
        assertEquals(24_000L, RhythmRules.beatFrames(120, 48_000))
    }

    @Test fun `de 60 a 160 BPM sao 25 acertos seguidos`() {
        var bpm = RhythmRules.START_BPM
        var hits = 0
        while (bpm < RhythmRules.MAX_BPM) { bpm = RhythmRules.nextBpm(bpm, true); hits++ }
        assertEquals(25, hits)
    }
}
