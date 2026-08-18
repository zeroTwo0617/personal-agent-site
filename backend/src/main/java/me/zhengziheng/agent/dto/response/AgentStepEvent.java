package me.zhengziheng.agent.dto.response;

import lombok.Data;

import java.util.Map;

/**
 * Agent 步骤事件（SSE 推送 + 结果回溯共用）：
 * 前端据此渲染"正在检索… / 检索到 N 个片段"的思考过程动效；
 * 完成后随 done 事件整体回传，刷新/轮询也能还原完整轨迹。
 */
@Data
public class AgentStepEvent {

    /** 事件类型（前端 SSE 解析器按 type 分发）：固定 agent_step */
    private String type = "agent_step";

    /** 第几步（1 起） */
    private int step;

    /** 调用的工具名：retrieve / retrieve_keyword / list_documents / get_document */
    private String tool;

    /** 工具参数（LLM 给出的原样参数） */
    private Map<String, Object> args;

    /** running=开始执行 / done=执行完成 / error=执行出错 */
    private String status;

    /** 命中数量（done 时） */
    private Integer hits;

    /** 一句话摘要（done 时，如"语义检索到 3 个片段"） */
    private String summary;
}
