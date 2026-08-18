package me.zhengziheng.agent.dto.response;

import lombok.Data;

/**
 * 单条评测结果。
 *
 * - recallAtK：期望要点(expectedPoints)在 Top-K 召回片段中的覆盖率（0~1），衡量检索是否拉到了相关材料；
 * - docRecall：应命中文档(expectedDocs)在 Top-K 中的命中率（0~1），文档名对齐时为严格指标，未配置则为 null；
 * - refusalDetected：reject 类问题是否触发拒答（答案含"未找到/未涵盖/没有相关信息"等）；
 * - faithfulness：LLM-as-judge 判定的忠实度（0~1，回答是否全基于上下文）；无 LLM key 时为 null；
 * - answer / latencyMs：生成的答案与耗时（无 key 时 answer 为 null）。
 */
@Data
public class EvalItemResult {
    private String id;
    private String question;
    private String type;            // fact / cross / reject

    private Double recallAtK;       // 期望要点覆盖率
    private Double docRecall;       // 应命中文档命中率（可为 null）
    private Boolean refusalDetected;// 仅 reject 类有意义
    private Double faithfulness;    // LLM-as-judge（可为 null）

    private Long latencyMs;
    private String answer;          // 生成的答案（无 key 为 null）
    private String note;            // 补充说明（如忠实度跳过原因）
}
