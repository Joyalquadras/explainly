package com.explainly.ai;

import com.explainly.transcript.TranscriptSegment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptFormatterTest {
    @Test
    void groupsIntoThirtySecondBlocksAndChunksWithoutSplitting() {
        List<TranscriptSegment> segs = new ArrayList<>();
        for (int i = 0; i < 20; i++) segs.add(new TranscriptSegment(i * 5.0, i * 5.0 + 5, "sentence " + i));
        var blocks = TranscriptFormatter.blocks(segs);
        assertThat(blocks).hasSize(4);
        assertThat(blocks.get(1).render()).startsWith("[0:30] sentence 6");
        var chunks = TranscriptFormatter.chunk(blocks, 120);
        assertThat(String.join("", chunks)).isEqualTo(TranscriptFormatter.render(blocks));
        assertThat(chunks.size()).isGreaterThan(1);
    }
}
