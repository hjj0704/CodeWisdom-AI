package com.codewisdom.agent.client.llm;

import com.codewisdom.agent.client.llm.LlmModels.Request;
import com.codewisdom.agent.client.llm.LlmModels.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * DeepSeek Chat Completions（OpenAI 兼容协议）。
 *
 * <p>文档：https://api-docs.deepseek.com/ —— {@code POST /chat/completions}
 */
public class DeepSeekLlmProvider implements LlmProvider {

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DeepSeekLlmProvider(String apiKey, String baseUrl, String model) {
        this(apiKey, baseUrl, model, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build(), new ObjectMapper());
    }

    DeepSeekLlmProvider(String apiKey, String baseUrl, String model,
                        HttpClient httpClient, ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("DeepSeek API Key 未配置（环境变量 CW_DEEPSEEK_API_KEY）");
        }
        this.apiKey = apiKey.trim();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.model = model == null || model.isBlank() ? "deepseek-chat" : model;
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public String id() {
        return "deepseek";
    }

    @Override
    public Response complete(Request request) {
        Objects.requireNonNull(request, "request");
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);
            ArrayNode messages = body.putArray("messages");
            if (!request.systemPrompt().isBlank()) {
                messages.addObject().put("role", "system").put("content", request.systemPrompt());
            }
            messages.addObject().put("role", "user").put("content", request.prompt());

            String endpoint = baseUrl + "/chat/completions";
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                throw new IllegalStateException("DeepSeek HTTP " + httpResponse.statusCode()
                        + ": " + truncate(httpResponse.body(), 500));
            }

            JsonNode root = objectMapper.readTree(httpResponse.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.asText().isBlank()) {
                throw new IllegalStateException("DeepSeek 响应无 content: "
                        + truncate(httpResponse.body(), 500));
            }
            return new Response(content.asText(), id());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DeepSeek 调用被中断", ex);
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException illegal) {
                throw illegal;
            }
            throw new IllegalStateException("DeepSeek 调用失败: " + ex.getMessage(), ex);
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String url = baseUrl == null || baseUrl.isBlank()
                ? "https://api.deepseek.com" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
