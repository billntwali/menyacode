package com.repolens.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.repolens.exception.OpenAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiClient {

    private static final String SYSTEM_PROMPT =
            "You are a code analysis assistant. Provide a concise, accurate summary of the given file. " +
            "Describe its primary purpose, key components, and any notable patterns or dependencies. " +
            "Respond in plain prose, no markdown headers. Keep the summary under 200 words.";

    private final RestClient  restClient;
    private final ObjectMapper objectMapper;
    private final String       model;
    private final int          maxTokens;
    private final String       apiKey;

    public OpenAiClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model,
            @Value("${openai.max-tokens:500}") int maxTokens) {

        this.objectMapper = objectMapper;
        this.apiKey       = apiKey;
        this.model        = model;
        this.maxTokens    = maxTokens;

        this.restClient = builder
                .baseUrl("https://api.openai.com")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public String generateSummary(String filePath, String content) {
        if (apiKey.isBlank()) {
            throw new OpenAiException(
                    "OpenAI API key not configured. Set the OPENAI_API_KEY environment variable.");
        }

        String userPrompt = "Summarize this file:\n\nFile: " + filePath + "\n\n```\n" + content + "\n```";

        // Build the request body as a plain Map so Jackson serialises it cleanly with
        // the global SNAKE_CASE strategy (max_tokens, etc.).
        Map<String, Object> body = Map.of(
                "model",      model,
                "max_tokens", maxTokens,
                "messages",   List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user",   "content", userPrompt)
                )
        );

        String rawResponse;
        try {
            rawResponse = restClient.post()
                    .uri("/v1/chat/completions")
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.value() == 401, (req, res) -> {
                        throw new OpenAiException(
                                "OpenAI authentication failed. Check your OPENAI_API_KEY.");
                    })
                    .onStatus(status -> status.value() == 429, (req, res) -> {
                        throw new OpenAiException(
                                "OpenAI rate limit reached. Please try again shortly.");
                    })
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {
                        throw new OpenAiException(
                                "OpenAI API request failed (" + res.getStatusCode().value() + ").");
                    })
                    .body(String.class);
        } catch (ResourceAccessException e) {
            throw new OpenAiException("Could not connect to OpenAI API. Please try again shortly.");
        } catch (RestClientResponseException e) {
            throw new OpenAiException("OpenAI API request failed (" + e.getStatusCode().value() + ").");
        }

        if (rawResponse == null || rawResponse.isBlank()) {
            throw new OpenAiException("OpenAI returned an empty response.");
        }

        return extractSummary(rawResponse);
    }

    private String extractSummary(String rawJson) {
        try {
            JsonNode root    = objectMapper.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty()) {
                throw new OpenAiException("OpenAI returned no choices in the response.");
            }
            JsonNode content = choices.get(0).path("message").path("content");
            String text = content.asText("").strip();
            if (text.isEmpty()) {
                throw new OpenAiException("OpenAI returned an empty message content.");
            }
            return text;
        } catch (OpenAiException e) {
            throw e;
        } catch (Exception e) {
            throw new OpenAiException("Could not parse OpenAI response.");
        }
    }
}
