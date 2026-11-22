package com.explainly.chat;

import com.explainly.common.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analyses/{analysisId}/chat")
@Tag(name = "AI chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    public record AskRequest(@NotBlank @Size(max = 2000) String message) {}

    @GetMapping
    public List<ChatService.MessageDto> history(@PathVariable Long analysisId) {
        return chat.history(CurrentUser.id(), analysisId);
    }

    @PostMapping
    @Operation(summary = "Ask a question about the video; answers cite video timestamps")
    public ChatService.MessageDto ask(@PathVariable Long analysisId, @Valid @RequestBody AskRequest req) {
        return chat.ask(CurrentUser.id(), analysisId, req.message().trim());
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(@PathVariable Long analysisId) {
        chat.clear(CurrentUser.id(), analysisId);
        return ResponseEntity.noContent().build();
    }
}
