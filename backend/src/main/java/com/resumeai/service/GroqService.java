package com.resumeai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Talks to Groq's OpenAI-compatible chat API and always returns a JSON object.
 * Speed choices: one shared HTTP/2 client (connection reuse), small prompts,
 * JSON mode, low temperature, low reasoning effort.
 */
@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper;

    @Value("${groq.api.key}") private String apiKey;
    @Value("${groq.api.url}") private String apiUrl;
    @Value("${groq.model}") private String model;
    @Value("${groq.fallback-model:}") private String fallbackModel;
    @Value("${groq.reasoning-effort:low}") private String reasoningEffort;

    public GroqService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode chatJson(String systemPrompt, String userPrompt, double temperature) {
        if (apiKey == null || apiKey.isBlank() || apiKey.toUpperCase().contains("PASTE_YOUR")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No Groq API key is set yet. Put your key in application.properties (groq.api.key) or in the "
                            + "GROQ_API_KEY environment variable, then restart the backend.");
        }
        try {
            HttpResponse<String> res = call(model, systemPrompt, userPrompt, temperature);

            // If Groq retired / renamed the model, retry once with the fallback model.
            if (res.statusCode() != 200 && isModelProblem(res) && !fallbackModel.isBlank()
                    && !fallbackModel.equals(model)) {
                log.warn("Model '{}' failed ({}). Retrying with '{}'", model, errorMessage(res), fallbackModel);
                res = call(fallbackModel, systemPrompt, userPrompt, temperature);
            }

            if (res.statusCode() == 429) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "The AI is busy right now. Wait a few seconds and try again.");
            }
            if (res.statusCode() == 401) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Groq rejected the API key. Create a new key at console.groq.com/keys and paste it into groq.api.key in application.properties "
                                + "(no quotes or spaces). If you set a GROQ_API_KEY environment variable, that one is used instead.");
            }
            if (res.statusCode() != 200) {
                String msg = errorMessage(res);
                log.error("Groq error {}: {}", res.statusCode(), res.body());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "The AI service returned an error (" + res.statusCode() + "): "
                                + (msg.isBlank() ? "no details" : msg));
            }

            String content = mapper.readTree(res.body())
                    .path("choices").path(0).path("message").path("content").asText("");
            return mapper.readTree(extractJson(content));

        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The request was interrupted. Please try again.");
        } catch (Exception e) {
            log.error("Groq call failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI answered in an unexpected format. Please try again.");
        }
    }

    private HttpResponse<String> call(String modelName, String system, String user, double temperature)
            throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", modelName);
        body.put("temperature", temperature);
        body.put("seed", 42); // same input -> same output as often as possible
        // GPT-OSS models reason first; reasoning tokens count toward this limit, so keep headroom.
        body.put("max_completion_tokens", 3500);
        if (modelName.startsWith("openai/gpt-oss") && !reasoningEffort.isBlank()) {
            body.put("reasoning_effort", reasoningEffort);
        }
        body.set("response_format", mapper.createObjectNode().put("type", "json_object"));

        ArrayNode messages = body.putArray("messages");
        messages.add(mapper.createObjectNode().put("role", "system").put("content", system));
        messages.add(mapper.createObjectNode().put("role", "user").put("content", user));

        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey.strip())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        long t0 = System.currentTimeMillis();
        HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Groq [{}] responded {} in {} ms", modelName, res.statusCode(), System.currentTimeMillis() - t0);
        return res;
    }

    private boolean isModelProblem(HttpResponse<String> res) {
        int s = res.statusCode();
        return (s == 400 || s == 404 || s == 403) && errorMessage(res).toLowerCase().contains("model");
    }

    /** Groq's own error text (never contains the API key). */
    private String errorMessage(HttpResponse<String> res) {
        try {
            String m = mapper.readTree(res.body()).path("error").path("message").asText("");
            return m.length() > 240 ? m.substring(0, 240) + "…" : m;
        } catch (Exception e) {
            return "";
        }
    }

    /** Tolerate ```json fences or stray text around the JSON object. */
    private String extractJson(String content) {
        int a = content.indexOf('{');
        int b = content.lastIndexOf('}');
        return (a >= 0 && b > a) ? content.substring(a, b + 1) : content;
    }
}
