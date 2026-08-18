package me.zhengziheng.agent.service;

/**
 * Embedding 抽象：把文本转为定长向量。
 * 两个实现：OpenAI 兼容 API（远程，语义质量高）与本地 hash 兜底（离线，无需 key）。
 */
public interface EmbeddingClient {

    /** 文本 -> 向量（维度见配置 embedding.dim，默认 1536） */
    float[] embed(String text);

    /** 实现名称（用于日志/调试） */
    String name();
}
