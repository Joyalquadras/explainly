package com.explainly.transcript;

import com.explainly.common.ApiException;
import com.explainly.common.Hashing;
import com.explainly.common.Json;
import com.explainly.config.AppProperties;
import com.explainly.user.User;
import com.explainly.video.Video;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TranscriptService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "srt", "vtt");
    private final TranscriptRepository transcripts;
    private final AppProperties props;

    public TranscriptService(TranscriptRepository transcripts, AppProperties props) {
        this.transcripts = transcripts;
        this.props = props;
    }

    /** Stores a transcript, reusing an identical one for the same video so its analysis can be reused. */
    @Transactional
    public Transcript store(Video video, TranscriptSource source, String language, List<TranscriptSegment> segments, User submittedBy) {
        if (segments.isEmpty()) {
            throw ApiException.unprocessable("EMPTY_TRANSCRIPT", "We couldn't find any transcript text in what you provided.");
        }
        String content = TranscriptParser.plainText(segments);
        if (content.length() < 200) {
            throw ApiException.unprocessable("TRANSCRIPT_TOO_SHORT", "That transcript is too short to explain. Please provide the full transcript.");
        }
        if (content.length() > props.limits().maxTranscriptChars()) {
            throw ApiException.unprocessable("TRANSCRIPT_TOO_LONG", "That transcript is longer than we can process.");
        }
        String hash = Hashing.sha256Hex(content);
        return transcripts.findFirstByVideoIdAndContentHash(video.getId(), hash).orElseGet(() -> {
            Transcript t = new Transcript();
            t.setVideo(video);
            t.setSource(source);
            t.setLanguage(language);
            t.setContent(content);
            t.setSegmentsJson(Json.write(segments));
            t.setContentHash(hash);
            t.setSubmittedBy(submittedBy);
            return transcripts.save(t);
        });
    }

    public List<TranscriptSegment> parseText(String text) {
        if (text == null || text.isBlank()) {
            throw ApiException.badRequest("EMPTY_TRANSCRIPT", "Please paste the transcript text.");
        }
        if (text.length() > props.limits().maxTranscriptChars()) {
            throw ApiException.unprocessable("TRANSCRIPT_TOO_LONG", "That transcript is longer than we can process.");
        }
        return TranscriptParser.parse(text);
    }

    /** Validates and decodes an uploaded transcript file (.txt, .srt, .vtt; UTF-8 text only). */
    public List<TranscriptSegment> parseUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "Please choose a transcript file.");
        if (file.getSize() > props.limits().maxTranscriptUploadBytes()) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "Transcript files must be smaller than "
                    + (props.limits().maxTranscriptUploadBytes() / 1024 / 1024) + " MB.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE", "Please upload a .txt, .srt or .vtt file.");
        }
        try {
            byte[] bytes = file.getBytes();
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (text.indexOf('\0') >= 0) throw new CharacterCodingException();
            return parseText(text);
        } catch (CharacterCodingException e) {
            throw ApiException.badRequest("INVALID_TEXT_FILE", "That file isn't readable UTF-8 text.");
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("UPLOAD_FAILED", "We couldn't read that file. Please try again.");
        }
    }

    public static List<TranscriptSegment> segments(Transcript t) {
        return Json.read(t.getSegmentsJson(), new TypeReference<List<TranscriptSegment>>() {});
    }
}
