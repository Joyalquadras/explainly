package com.explainly.ai;

import com.explainly.config.AppProperties;
import com.explainly.transcript.Transcript;
import com.explainly.transcript.TranscriptParser;
import com.explainly.transcript.TranscriptSegment;
import com.explainly.transcript.TranscriptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Produces the transcript text sent to the model. Transcripts that fit the configured budget are sent
 * in full; longer ones are condensed section by section (map step) while preserving timestamps, so
 * nothing is silently truncated.
 */
@Component
public class TranscriptPreparer {

    private static final Logger log = LoggerFactory.getLogger(TranscriptPreparer.class);
    private final AiService ai;
    private final AppProperties props;

    public TranscriptPreparer(AiService ai, AppProperties props) {
        this.ai = ai;
        this.props = props;
    }

    public record Prepared(String text, boolean timed, boolean condensed, List<TranscriptFormatter.Block> blocks) {}

    public Prepared prepare(Transcript transcript) {
        List<TranscriptSegment> segments = TranscriptService.segments(transcript);
        List<TranscriptFormatter.Block> blocks = TranscriptFormatter.blocks(segments);
        String full = TranscriptFormatter.render(blocks);
        boolean timed = TranscriptParser.isTimed(segments);
        int budget = props.ai().maxDirectTranscriptChars();
        if (full.length() <= budget) return new Prepared(full, timed, false, blocks);

        List<String> chunks = TranscriptFormatter.chunk(blocks, Math.max(20_000, budget / 4));
        log.info("Condensing long transcript {} ({} chars) in {} sections", transcript.getId(), full.length(), chunks.size());
        StringBuilder condensed = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            LlmResponse res = ai.complete(LlmRequest.text("condense", Prompts.CONDENSE,
                    List.of(new LlmRequest.Message(LlmRequest.Role.USER,
                            "Section " + (i + 1) + " of " + chunks.size() + ":\n<transcript_section>\n" + chunks.get(i) + "</transcript_section>")),
                    8000));
            condensed.append("--- Section ").append(i + 1).append(" ---\n").append(res.text().trim()).append("\n\n");
        }
        return new Prepared(condensed.toString(), timed, true, blocks);
    }
}
