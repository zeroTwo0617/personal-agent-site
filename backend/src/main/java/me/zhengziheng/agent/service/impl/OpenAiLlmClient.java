package me.zhengziheng.agent.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengziheng.agent.service.LlmClient;
import me.zhengziheng.agent.service.LlmMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OpenAI 兼容的大模型客户端（M2）。
 * 使用 JDK 自带的 java.net.http.HttpClient 调用 /chat/completions（stream=true），
 * 逐行读取 SSE 流（data: {...}），解析 choices[0].delta.content 增量并回调 onDelta。
 * 选用 JDK HttpClient 而非 RestTemplate，是因为 RestTemplate 不便于按块读取流式响应体。
 *
 * 支持：max-tokens（单次回答上限）与 thinking（DeepSeek V4 默认开思考，需显式传参控制）。
 */
@Service
public class OpenAiLlmClient implements LlmClient {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${llm.api.base-url}")
    private String baseUrl;

    @Value("${llm.api.key}")
    private String apiKey;

    @Value("${llm.api.model}")
    private String model;

    @Value("${llm.api.max-tokens:800}")
    private int maxTokens;

    @Value("${llm.thinking.normal:disabled}")
    private String normalThinking;

    @Value("${llm.thinking.agent:low}")
    private String agentThinking;

    @Override
    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public void streamGenerate(List<LlmMessage> messages, Consumer<String> onDelta, String mode) throws Exception {
        if (!available()) {
            throw new IllegalStateException("LLM API key 未配置，无法调用远程大模型");
        }
        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        Map<String, Object> body = buildBody(messages, true, mode);
        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<java.io.InputStream> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            String errBody = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("LLM 接口返回 " + resp.statusCode() + "：" + errBody);
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    continue;
                }
                try {
                    JsonNode node = objectMapper.readTree(data);
                    JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                    if (delta.isTextual() && !delta.asText().isEmpty()) {
                        onDelta.accept(delta.asText());
                    }
                } catch (Exception e) {
                    // 个别控制行（如注释、心跳）解析失败则跳过，不影响主流程
                }
            }
        }
    }

    @Override
    public String generate(List<LlmMessage> messages, String mode) throws Exception {
        if (!available()) {
            throw new IllegalStateException("LLM API key 未配置，无法调用远程大模型");
        }
        String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
        Map<String, Object> body = buildBody(messages, false, mode);
        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("LLM 接口返回 " + resp.statusCode() + "：" + resp.body());
        }

        JsonNode node = objectMapper.readTree(resp.body());
        return node.path("choices").path(0).path("message").path("content").asText("");
    }

    /** 组装请求体：max-tokens + 按 mode 传 thinking/reasoning_effort */
    private Map<String, Object> buildBody(List<LlmMessage> messages, boolean stream, String mode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", stream);
        body.put("max_tokens", maxTokens);
        String thinking = thinkingFor(mode);
        if (thinking != null) {
            if ("disabled".equalsIgnoreCase(thinking)) {
                body.put("thinking", Map.of("type", "disabled"));
            } else {
                // enabled：low/medium/high
                body.put("thinking", Map.of("type", "enabled"));
                body.put("reasoning_effort", thinking.toLowerCase());
            }
        }
        if (!stream) {
            body.put("temperature", 0.0);
        }
        return body;
    }

    private String thinkingFor(String mode) {
        if ("agent".equalsIgnoreCase(mode)) {
            return agentThinking;
        }
        return normalThinking;
    }
}
