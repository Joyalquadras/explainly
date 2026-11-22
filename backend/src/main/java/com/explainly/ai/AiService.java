package com.explainly.ai;

import com.explainly.common.ApiException;
import com.explainly.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Selects the configured LLM provider and offers text and JSON completions with one automatic retry. */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private final List<LlmClient> clients;
    private final AppProperties props;

    public AiService(List<LlmClient> clients, AppProperties props) {
        this.clients = clients;
        this.props = props;
    }

    public Optional<LlmClient> activeClient() {
        String preferred = props.ai().provider();
        return clients.stream().filter(c -> c.id().equalsIgnoreCase(preferred) && c.isConfigured()).findFirst()
                .or(() -> clients.stream().filter(LlmClient::isConfigured).findFirst());
    }

    public boolean isConfigured() {
        return activeClient().isPresent();
    }

    public LlmClient requireClient() {
        return activeClient().orElseThrow(() -> ApiException.serviceUnavailable("AI_NOT_CONFIGURED",
                "AI is not configured on this server. An administrator needs to set ANTHROPIC_API_KEY or OPENAI_API_KEY."));
    }

    public LlmResponse complete(LlmRequest request) {
        return requireClient().complete(request);
    }

    /** Requests JSON and parses it. Retries once when the output is truncated or not valid JSON. */
    public JsonNode completeJson(LlmRequest request) {
        LlmClient client = requireClient();
        LlmRequest attempt = request;
        for (int i = 0; i < 2; i++) {
            LlmResponse res = client.complete(attempt);
            try {
                if (res.truncated()) throw new IllegalArgumentException("response truncated");
                return JsonExtractor.extractObject(res.text());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid JSON from {} for {} (attempt {}): {}", client.id(), request.purpose(), i + 1, e.getMessage());
                attempt = new LlmRequest(request.system() + "\n\nIMPORTANT: Your previous answer was not valid or was cut off. "
                        + "Return ONLY one complete, valid JSON object and keep every text field concise.",
                        request.messages(), (int) Math.min(request.maxTokens() * 1.5, 64000), true, request.purpose());
            }
        }
        throw new AiProviderException("The AI returned an unexpected format. Please try again.");
    }
}
