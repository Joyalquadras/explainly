package com.explainly.chat;

import com.explainly.ai.AiService;
import com.explainly.ai.LlmRequest;
import com.explainly.ai.Prompts;
import com.explainly.ai.TranscriptFormatter;
import com.explainly.common.ApiException;
import com.explainly.common.Json;
import com.explainly.common.TimeFormat;
import com.explainly.explanation.LearningMaterial;
import com.explainly.transcript.TranscriptService;
import com.explainly.video.AnalysisAccess;
import com.explainly.video.VideoAnalysis;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private static final Pattern CITATION = Pattern.compile("\\[((?:\\d{1,2}:)?\\d{1,2}:\\d{2})\\]");
    private final AnalysisAccess access;
    private final ChatMessageRepository messages;
    private final LearningMaterial material;
    private final AiService ai;
    private final TransactionTemplate tx;

    public ChatService(AnalysisAccess access, ChatMessageRepository messages, LearningMaterial material, AiService ai,
                       org.springframework.transaction.PlatformTransactionManager txManager) {
        this.access = access;
        this.messages = messages;
        this.material = material;
        this.ai = ai;
        this.tx = new TransactionTemplate(txManager);
    }

    public record MessageDto(Long id, String role, String content, List<Integer> citations, Instant createdAt) {
        static MessageDto from(ChatMessage m) {
            List<Integer> cites = m.getCitationsJson() == null ? List.of()
                    : Json.read(m.getCitationsJson(), new com.fasterxml.jackson.core.type.TypeReference<List<Integer>>() {});
            return new MessageDto(m.getId(), m.getRole(), m.getContent(), cites, m.getCreatedAt());
        }
    }

    @Transactional(readOnly = true)
    public List<MessageDto> history(Long userId, Long analysisId) {
        access.require(userId, analysisId);
        return messages.findByUserIdAndAnalysisIdOrderByCreatedAtAscIdAsc(userId, analysisId).stream().map(MessageDto::from).toList();
    }

    @Transactional
    public void clear(Long userId, Long analysisId) {
        access.require(userId, analysisId);
        messages.deleteByUserIdAndAnalysisId(userId, analysisId);
    }

    /** Answers a question grounded in the analysis. The AI call runs outside any database transaction. */
    public MessageDto ask(Long userId, Long analysisId, String question) {
        record Context(String title, String system, List<LlmRequest.Message> conversation) {}
        Context ctx = tx.execute(s -> {
            VideoAnalysis a = access.requireCompleted(userId, analysisId);
            List<ChatMessage> recent = new ArrayList<>(messages.findByUserIdAndAnalysisIdOrderByCreatedAtDescIdDesc(
                    userId, analysisId, PageRequest.of(0, 8)));
            Collections.reverse(recent);
            String retrievalQuery = question + " " + recent.stream().filter(m -> m.getRole().equals("user"))
                    .reduce((x, y) -> y).map(ChatMessage::getContent).orElse("");
            String excerpts = "";
            if (a.getTranscript() != null) {
                var blocks = TranscriptFormatter.blocks(TranscriptService.segments(a.getTranscript()));
                excerpts = TranscriptFormatter.render(TranscriptRetriever.topWindows(blocks, retrievalQuery, 8));
            }
            String system = Prompts.chatSystem(a.getVideo().getTitle(), material.overview(analysisId),
                    material.chapterList(analysisId), excerpts);
            List<LlmRequest.Message> conversation = new ArrayList<>();
            for (ChatMessage m : recent) {
                conversation.add(new LlmRequest.Message(m.getRole().equals("user") ? LlmRequest.Role.USER : LlmRequest.Role.ASSISTANT, m.getContent()));
            }
            conversation.add(new LlmRequest.Message(LlmRequest.Role.USER, question));
            return new Context(a.getVideo().getTitle(), system, conversation);
        });
        if (ctx == null) throw ApiException.notFound("Video analysis");

        String answer = ai.complete(LlmRequest.text("chat", ctx.system(), ctx.conversation(), 3000)).text().trim();
        if (answer.isEmpty()) answer = "Sorry, I couldn't come up with an answer. Please try rephrasing your question.";
        List<Integer> citations = new ArrayList<>();
        Matcher m = CITATION.matcher(answer);
        while (m.find()) {
            Integer secs = TimeFormat.parseClock(m.group(1));
            if (secs != null && !citations.contains(secs)) citations.add(secs);
        }
        String finalAnswer = answer;
        return tx.execute(s -> {
            ChatMessage userMsg = new ChatMessage();
            userMsg.setUserId(userId);
            userMsg.setAnalysisId(analysisId);
            userMsg.setRole("user");
            userMsg.setContent(question);
            messages.save(userMsg);
            ChatMessage reply = new ChatMessage();
            reply.setUserId(userId);
            reply.setAnalysisId(analysisId);
            reply.setRole("assistant");
            reply.setContent(finalAnswer);
            reply.setCitationsJson(Json.write(citations));
            reply.setCreatedAt(Instant.now().plusMillis(1));
            return MessageDto.from(messages.save(reply));
        });
    }
}
