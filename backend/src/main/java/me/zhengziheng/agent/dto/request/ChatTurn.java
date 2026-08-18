package me.zhengziheng.agent.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多轮对话历史中的一轮：role 取 user / assistant（与 LLM messages 对齐），content 为该轮文本。
 * 前端把之前的问答按序携带进来，后端据此提供「上下文记忆」：检索追问改写 + 提示词携带历史。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatTurn {

    /** 角色：user=用户提问 / assistant=助手回答 */
    private String role;

    /** 该轮文本内容 */
    private String content;
}
