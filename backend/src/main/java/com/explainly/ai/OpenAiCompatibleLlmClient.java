package com.explainly.ai;

import com.explainly.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions client. Works with OpenAI and any OpenAI-compatible endpoint
 * (Azure OpenAI proxies, Ollama, Groq, Together, vLLM...) by changing OPENAI_BASE_URL.
 */
@Component
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private final AppProperties.Ai.OpenAi config;
    private final RestClient http;

    public OpenAiCompatibleLlmClient(AppProperties props, RestClient.Builder builder) {
        this.config = props.ai().openai();
        this.http = builder.build();
    }

    @Override
    public String id() { return "openai"; }

    @Override
    public String model() { return config.model(); }

    @Override
    public boolean isConfigured() { return AppProperties.notBlank(config.apiKey()); }

    @Override
    public LlmResponse complete(LlmRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.system()));
        for (LlmRequest.Message m : request.messages()) {
            messages.add(Map.of("role", m.role() == LlmRequest.Role.USER ? "user" : "assistant", "content", m.content()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("messages", messages);
        body.put(config.baseUrl().contains("api.openai.com") ? "max_completion_tokens" : "max_tokens", request.maxTokens());
        if (request.jsonOutput() && config.jsonMode()) body.put("response_format", Map.of("type", "json_object"));
        try {
            JsonNode res = http.post().uri(config.baseUrl().replaceAll("/+$", "") + "/chat/completions")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
            JsonNode choice = res == null ? null : res.path("choices").path(0);
            if (choice == null || choice.isMissingNode()) throw new AiProviderException("The AI service returned an empty response.");
            String finish = choice.path("finish_reason").asText("");
            if ("content_filter".equals(finish)) throw new AiProviderException("The AI declined to process this content.");
            return new LlmResponse(choice.path("message").path("content").asText(""), id(),
                    res.path("model").asText(config.model()), "length".equals(finish));
        } catch (HttpStatusCodeException e) {
            log.warn("OpenAI-compatible API error {} for {}", e.getStatusCode(), request.purpose());
            if (e.getStatusCode().value() == 429) {
                throw new AiProviderException("The AI service is busy right now. Please try again in a minute.", e);
            }
            throw new AiProviderException("The AI service returned an error. Please try again.", e);
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OpenAI-compatible call failed for {}: {}", request.purpose(), e.toString());
            throw new AiProviderException("We couldn't reach the AI service. Please try again.", e);
        }
    }
}
