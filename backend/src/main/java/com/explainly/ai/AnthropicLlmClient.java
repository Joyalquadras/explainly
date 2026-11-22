package com.explainly.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.explainly.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** Claude via the official Anthropic Java SDK. Streams responses to avoid HTTP timeouts on long outputs. */
@Component
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);
    private final AppProperties.Ai.Anthropic config;
    private volatile AnthropicClient client;

    public AnthropicLlmClient(AppProperties props) {
        this.config = props.ai().anthropic();
    }

    @Override
    public String id() { return "anthropic"; }

    @Override
    public String model() { return config.model(); }

    @Override
    public boolean isConfigured() { return AppProperties.notBlank(config.apiKey()); }

    @Override
    public LlmResponse complete(LlmRequest request) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(request.maxTokens())
                .system(request.system());
        for (LlmRequest.Message m : request.messages()) {
            if (m.role() == LlmRequest.Role.USER) builder.addUserMessage(m.content());
            else builder.addAssistantMessage(m.content());
        }
        if (AppProperties.notBlank(config.effort())) {
            builder.outputConfig(OutputConfig.builder()
                    .effort(OutputConfig.Effort.of(config.effort().toLowerCase(Locale.ROOT))).build());
        }
        if (config.serverFallbacks()) {
            // Server-side refusal fallbacks: routes a refused request to a suitable fallback model.
            builder.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01");
            builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }

        StringBuilder text = new StringBuilder();
        AtomicReference<String> stopReason = new AtomicReference<>();
        long started = System.currentTimeMillis();
        try (StreamResponse<RawMessageStreamEvent> stream = client().messages().createStreaming(builder.build())) {
            stream.stream().forEach(event -> {
                event.contentBlockDelta().flatMap(d -> d.delta().text()).ifPresent(t -> text.append(t.text()));
                event.messageDelta().flatMap(d -> d.delta().stopReason()).ifPresent(r -> stopReason.set(r.toString()));
            });
        } catch (RateLimitException e) {
            throw new AiProviderException("The AI service is busy right now. Please try again in a minute.", e);
        } catch (AnthropicServiceException e) {
            log.warn("Anthropic API error ({}) for {}: {}", e.statusCode(), request.purpose(), e.getMessage());
            throw new AiProviderException("The AI service returned an error. Please try again.", e);
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Anthropic call failed for {}: {}", request.purpose(), e.toString());
            throw new AiProviderException("We couldn't reach the AI service. Please try again.", e);
        }
        String stop = stopReason.get();
        if ("refusal".equalsIgnoreCase(stop)) {
            throw new AiProviderException("The AI declined to process this content.");
        }
        log.info("Claude {} completed in {} ms (stop={}, chars={})", request.purpose(),
                System.currentTimeMillis() - started, stop, text.length());
        return new LlmResponse(text.toString(), id(), config.model(), "max_tokens".equalsIgnoreCase(stop));
    }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.builder()
                            .apiKey(config.apiKey())
                            .baseUrl(config.baseUrl())
                            .timeout(Duration.ofMinutes(15))
                            .maxRetries(3)
                            .build();
                }
            }
        }
        return client;
    }
}
