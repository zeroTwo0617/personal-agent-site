package me.zhengziheng.agent.dto.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 问答结果：最终答案 + 完整引用来源（收集到的全部片段）+ 思考轨迹 + 元信息。
 */
@Data
public class AgentResult {

    /** 最终答案（LLM 生成，用 [N] 标注引用） */
    private String answer;

    /** 完整引用来源（按 [N] 编号顺序，去重后的全部命中片段） */
    private List<ChunkSearchResult> sources;

    /** 实际执行步数（工具调用次数） */
    private int steps;

    /** 思考轨迹（前端还原过程动效） */
    private List<AgentStepEvent> trace;

    /** 检索元信息（与普通模式一致：mode / rerank / 命中数） */
    private Map<String, Object> retrievalMeta;

    /** 降级说明：如 LLM 不可用时回退单轮检索 */
    private String fallbackReason;
}
