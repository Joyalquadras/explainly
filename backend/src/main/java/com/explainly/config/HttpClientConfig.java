package com.explainly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;

/** Shared outbound HTTP client. Honours JVM proxy settings and enforces timeouts. */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient jdkHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(ProxySelector.getDefault())
                .build();
    }

    @Bean
    public RestClient.Builder externalRestClientBuilder(HttpClient jdkHttpClient) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkHttpClient);
        factory.setReadTimeout(Duration.ofMinutes(5));
        return RestClient.builder().requestFactory(factory);
    }
}
