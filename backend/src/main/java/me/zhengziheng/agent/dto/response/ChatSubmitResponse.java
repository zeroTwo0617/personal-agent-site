package me.zhengziheng.agent.dto.response;

import lombok.Data;

/**
 * 提交问答的返回：仅携带任务 ID，前端据此轮询 {@code GET /chat/result} 拉取答案。
 * 把"提交"与"取结果"拆开，前端发完消息即可立即上屏 AI 占位气泡并异步刷新，
 * 不必干等整段生成（也为 M2 流式生成预留同一通道）。
 */
@Data
public class ChatSubmitResponse {
    /** 问答任务 ID */
    private String taskId;
}
