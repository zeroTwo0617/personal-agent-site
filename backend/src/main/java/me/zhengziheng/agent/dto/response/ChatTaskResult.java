package me.zhengziheng.agent.dto.response;

import lombok.Data;

import java.util.List;
import java.util.Map;
/**
 * 问答任务结果（供前端轮询 / SSE）：status 为 pending/completed/failed，
 * 完成后携带 answer、sources（引用来源）与 retrievalMeta（检索元信息）。
 */
@Data
public class ChatTaskResult {
    /** 任务状态：pending=生成中 / completed=完成 / failed=失败 */
    private String status;
    /** 抽取式答案（拼接 Top-K 片段） */
    private String answer;
    /** 引用来源（含相似度与原文） */
    private List<ChunkSearchResult> sources;
    /** 问题是否成功向量化 */
    private boolean queryEmbedded;
    /** 检索元信息：mode(混合/纯向量)、rerank 策略、各路命中数（前端徽标/调试用） */
    private Map<String, Object> retrievalMeta;
    /** 问答记录 ID（qa_log.id）：完成后回填，前端凭它提交点赞/点踩反馈 */
    private Long qaId;
    /** Agent 模式下的完整思考轨迹（SSE 过程 + 完成后回溯展示） */
    private List<AgentStepEvent> agentTrace;
}
