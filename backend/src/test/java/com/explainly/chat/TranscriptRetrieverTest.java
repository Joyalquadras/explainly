package com.explainly.chat;

import com.explainly.ai.TranscriptFormatter.Block;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptRetrieverTest {
    @Test
    void findsTheRelevantWindow() {
        List<Block> blocks = List.of(
                new Block(0, "welcome to the channel"), new Block(30, "today we cover cooking pasta"),
                new Block(60, "boil water and add salt"), new Block(90, "drain the noodles"),
                new Block(120, "now garbage collection in java frees memory"), new Block(150, "the heap is divided into generations"),
                new Block(180, "thanks for watching"), new Block(210, "subscribe"));
        List<Block> top = TranscriptRetriever.topWindows(blocks, "How does garbage collection work?", 1);
        assertThat(top).hasSize(1);
        assertThat(top.getFirst().startSeconds()).isEqualTo(120);
        assertThat(top.getFirst().text()).contains("heap");
    }
}
