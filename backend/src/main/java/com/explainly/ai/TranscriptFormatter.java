package com.explainly.ai;

import com.explainly.common.TimeFormat;
import com.explainly.transcript.TranscriptSegment;

import java.util.ArrayList;
import java.util.List;

/** Turns transcript segments into compact, timestamped text blocks that models can cite. */
public final class TranscriptFormatter {
    private TranscriptFormatter() {}

    public static final int BLOCK_SECONDS = 30;

    public record Block(Integer startSeconds, String text) {
        public String render() {
            return startSeconds == null ? text : "[" + TimeFormat.clock(startSeconds) + "] " + text;
        }
    }

    public static List<Block> blocks(List<TranscriptSegment> segments) {
        List<Block> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Double blockStart = null;
        for (TranscriptSegment s : segments) {
            if (s.start() == null) {
                if (!current.isEmpty()) {
                    blocks.add(new Block(blockStart == null ? null : blockStart.intValue(), current.toString().trim()));
                    current.setLength(0);
                    blockStart = null;
                }
                blocks.add(new Block(null, s.text()));
                continue;
            }
            if (blockStart == null) blockStart = s.start();
            if (s.start() - blockStart >= BLOCK_SECONDS && !current.isEmpty()) {
                blocks.add(new Block(blockStart.intValue(), current.toString().trim()));
                current.setLength(0);
                blockStart = s.start();
            }
            current.append(s.text()).append(' ');
        }
        if (!current.isEmpty()) blocks.add(new Block(blockStart == null ? null : blockStart.intValue(), current.toString().trim()));
        return blocks;
    }

    public static String render(List<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) sb.append(b.render()).append('\n');
        return sb.toString();
    }

    /** Splits rendered blocks into chunks of at most {@code maxChars}, never splitting a block. */
    public static List<String> chunk(List<Block> blocks, int maxChars) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (Block b : blocks) {
            String line = b.render() + "\n";
            if (current.length() + line.length() > maxChars && !current.isEmpty()) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            current.append(line);
        }
        if (!current.isEmpty()) chunks.add(current.toString());
        return chunks;
    }
}
