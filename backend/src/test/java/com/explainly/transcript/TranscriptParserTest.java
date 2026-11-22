package com.explainly.transcript;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptParserTest {

    @Test
    void parsesWebVttAndStripsTags() {
        String vtt = """
                WEBVTT
                Kind: captions

                00:00:01.000 --> 00:00:04.000
                <c>Hello</c> and welcome &amp; enjoy

                00:00:04.000 --> 00:00:08.500
                today we learn Java
                """;
        List<TranscriptSegment> s = TranscriptParser.parse(vtt);
        assertThat(s).hasSize(2);
        assertThat(s.get(0).text()).isEqualTo("Hello and welcome & enjoy");
        assertThat(s.get(1).start()).isEqualTo(4.0);
        assertThat(s.get(1).end()).isEqualTo(8.5);
        assertThat(TranscriptParser.isTimed(s)).isTrue();
    }

    @Test
    void parsesSrt() {
        String srt = "1\r\n00:00:01,500 --> 00:00:03,000\r\nFirst line\r\nsecond line\r\n\r\n2\r\n00:01:02,000 --> 00:01:05,000\r\nNext\r\n";
        List<TranscriptSegment> s = TranscriptParser.parse(srt);
        assertThat(s).extracting(TranscriptSegment::text).containsExactly("First line second line", "Next");
        assertThat(s.get(1).start()).isEqualTo(62.0);
    }

    @Test
    void mergesRollingAutoCaptions() {
        String vtt = """
                WEBVTT

                00:00:01.000 --> 00:00:02.000
                so today

                00:00:02.000 --> 00:00:03.000
                so today we will

                00:00:03.000 --> 00:00:04.000
                so today we will
                """;
        List<TranscriptSegment> s = TranscriptParser.parse(vtt);
        assertThat(s).hasSize(1);
        assertThat(s.getFirst().text()).isEqualTo("so today we will");
        assertThat(s.getFirst().end()).isEqualTo(4.0);
    }

    @Test
    void parsesCopiedYouTubeTranscriptWithTimestampLines() {
        String text = "0:00\nwelcome to the course\n0:12\nlet's talk about loops\nand arrays\n1:02:03\nthe end";
        List<TranscriptSegment> s = TranscriptParser.parse(text);
        assertThat(s).hasSize(3);
        assertThat(s.get(1).text()).isEqualTo("let's talk about loops and arrays");
        assertThat(s.get(2).start()).isEqualTo(3723.0);
        assertThat(s.get(0).end()).isEqualTo(12.0);
    }

    @Test
    void parsesInlineBracketStamps() {
        List<TranscriptSegment> s = TranscriptParser.parse("[00:05] hello there\n[00:10] second part");
        assertThat(s).hasSize(2);
        assertThat(s.get(0).start()).isEqualTo(5.0);
    }

    @Test
    void plainTextIsUntimed() {
        String text = "This is a paragraph about photosynthesis.\n\nPlants use light. At 12:30 we eat lunch.";
        List<TranscriptSegment> s = TranscriptParser.parse(text);
        assertThat(s).hasSize(2);
        assertThat(TranscriptParser.isTimed(s)).isFalse();
        assertThat(TranscriptParser.plainText(s)).contains("photosynthesis").contains("lunch");
    }
}
