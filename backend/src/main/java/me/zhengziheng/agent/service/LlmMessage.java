package me.zhengziheng.agent.service;

import lombok.Data;

/**
 * LLM 对话消息（OpenAI 兼容格式）：role 取 system/user/assistant，content 为文本内容。
 * 仅用于拼装发给大模型的 messages 数组。
 */
@Data
public class LlmMessage {
    private String role;
    private String content;

    public LlmMessage() {
    }

    public LlmMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
