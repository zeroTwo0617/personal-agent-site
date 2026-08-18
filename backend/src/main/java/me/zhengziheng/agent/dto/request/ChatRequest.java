package me.zhengziheng.agent.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 问答请求体：POST /api/chat
 */
@Data
public class ChatRequest {

    /** 用户问题（必填，@NotBlank 校验） */
    @NotBlank(message = "问题不能为空")
    private String question;

    /** 召回数量，1~10，默认 5；越大召回越广但可能越杂 */
    private Integer topK;

    /** 多轮对话历史（可选）：之前的问答按序携带，后端据此提供上下文记忆（追问改写 + 提示词携带历史） */
    @Size(max = 10, message = "对话历史最多携带 10 轮")
    private List<ChatTurn> history;

    /**
     * 问答模式：normal(默认，单轮检索-生成) / agent(多步检索-反思-再检索-生成)。
     * agent 模式需要 LLM key，否则自动降级为 normal 并在检索元信息标注 fallback-no-llm。
     */
    private String mode;
}
