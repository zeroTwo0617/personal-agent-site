package me.zhengziheng.agent.service;

import java.util.List;
import java.util.function.Consumer;

/**
 * 大模型客户端接口（M2 流式生成）。
 * 与 EmbeddingClient 解耦：Embedding 负责"把文本变成向量"，本接口负责"基于上下文生成文本"。
 *
 * mode 参数用于区分思考档位（normal/agent），对应 application.yml 的 llm.thinking.* 配置；
 * 默认重载走 normal（快速兜底）。
 */
public interface LlmClient {

    /** 是否可用（通常取决于是否配置了 API key） */
    boolean available();

    /** 流式生成（normal 档） */
    default void streamGenerate(List<LlmMessage> messages, Consumer<String> onDelta) throws Exception {
        streamGenerate(messages, onDelta, null, "normal");
    }

    /** 流式生成（指定思考档位） */
    default void streamGenerate(List<LlmMessage> messages, Consumer<String> onDelta, String mode) throws Exception {
        streamGenerate(messages, onDelta, null, mode);
    }

    /**
     * 流式生成（指定思考档位 + 推理内容回调）。
     *
     * @param onDelta    答案增量（打字机效果）
     * @param onThinking 思考过程增量（如 DeepSeek 的 reasoning_content，用于前端"思考"块），可为 null
     * @param mode       思考档位（normal/agent）
     */
    void streamGenerate(List<LlmMessage> messages, Consumer<String> onDelta, Consumer<String> onThinking, String mode) throws Exception;

    /** 非流式生成（normal 档，供 Rerank 打分等场景） */
    default String generate(List<LlmMessage> messages) throws Exception {
        return generate(messages, "normal");
    }

    /** 非流式生成（指定思考档位） */
    String generate(List<LlmMessage> messages, String mode) throws Exception;
}
