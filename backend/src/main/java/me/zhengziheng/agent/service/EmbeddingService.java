package me.zhengziheng.agent.service;

import me.zhengziheng.agent.service.impl.LocalHashEmbeddingClient;
import me.zhengziheng.agent.service.impl.OpenAiEmbeddingClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Embedding 统一入口：按 provider 配置选择实现。
 * - provider=api   且 配置了 key：用远程 API
 * - provider=local：强制本地兜底
 * - provider=auto（默认）：有 key 用 API，否则本地兜底
 */
@Service
public class EmbeddingService {

    private final OpenAiEmbeddingClient openAi;
    private final LocalHashEmbeddingClient local;
    private final String provider;

    public EmbeddingService(OpenAiEmbeddingClient openAi,
                            LocalHashEmbeddingClient local,
                            @Value("${embedding.provider:auto}") String provider) {
        this.openAi = openAi;
        this.local = local;
        this.provider = provider;
    }

    public float[] embed(String text) {
        if ("api".equals(provider)) {
            return openAi.embed(text);
        }
        if ("local".equals(provider)) {
            return local.embed(text);
        }
        // auto
        if (openAi.available()) {
            return openAi.embed(text);
        }
        return local.embed(text);
    }

    /** 是否在使用本地兜底（前端可据此提示向量质量） */
    public boolean usingLocal() {
        if ("local".equals(provider)) {
            return true;
        }
        return !openAi.available();
    }

    public String activeProvider() {
        return usingLocal() ? local.name() : openAi.name();
    }
}
