package me.zhengziheng.agent.service.impl;

import me.zhengziheng.agent.service.EmbeddingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Embedding 接口实现。
 * 需配置 embedding.api.key；无 key 时 available()=false，由 EmbeddingService 回退本地。
 * 注意：RestTemplate 显式设置连接/读取超时，避免远程不可达时请求无限挂起（会卡死调用线程）。
 */
@Service
public class OpenAiEmbeddingClient implements EmbeddingClient {

    private final RestTemplate restTemplate;

    public OpenAiEmbeddingClient() {
        this.restTemplate = new RestTemplate();
        // 连接 5s、读取 30s 超时；远程不可达会快速失败而非永久挂起
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restTemplate.setRequestFactory(factory);
    }

    @Value("${embedding.api.base-url}")
    private String baseUrl;

    @Value("${embedding.api.key}")
    private String apiKey;

    @Value("${embedding.api.model}")
    private String model;

    @Value("${embedding.dim}")
    private int dim;

    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public float[] embed(String text) {
        if (!available()) {
            throw new IllegalStateException("Embedding API key 未配置，无法调用远程 Embedding");
        }
        String url = baseUrl.endsWith("/") ? baseUrl + "embeddings" : baseUrl + "/embeddings";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = Map.of("model", model, "input", text);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Map<String, Object> resp = restTemplate.postForObject(url, entity, Map.class);
        if (resp == null || !resp.containsKey("data")) {
            throw new IllegalStateException("Embedding 接口返回异常: " + resp);
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
        List<Number> embedding = (List<Number>) data.get(0).get("embedding");

        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = embedding.get(i).floatValue();
        }
        return vector;
    }

    @Override
    public String name() {
        return "openai:" + model;
    }
}
