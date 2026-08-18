package me.zhengziheng.agent.dto.response;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 评测报告（M3 质量闭环核心产出）。
 *
 * 指标口径：
 *  - recallAtK：非 reject 类问题的 mean(recallAtK)，即"检索是否拉到相关材料"的整体水平；
 *  - recallByType：按 fact/cross/reject 分类型的 recallAtK 均值；
 *  - docRecall：mean(docRecall)，严格文档命中率（评测集配了 expectedDocs 才有意义）；
 *  - faithfulness：mean(faithfulness)，LLM-as-judge 忠实度；全部跳过时为 null；
 *  - faithfulnessSkipped：是否因无 LLM key 而未计算忠实度；
 *  - avgLatencyMs：平均单题耗时（检索+生成）。
 */
@Data
public class EvalReport {
    private String runAt;                       // 运行时间(ISO)
    private String mode;                        // 本次检索模式: hybrid / vector
    private int total;                          // 评测条数
    private Double recallAtK;                   // 整体召回率(期望要点覆盖)
    private Map<String, Double> recallByType;   // 分类型召回率
    private Double docRecall;                   // 严格文档命中率(可为 null)
    private Double faithfulness;                // 忠实度(可为 null)
    private Long avgLatencyMs;                  // 平均耗时
    private boolean faithfulnessSkipped;        // 是否跳过忠实度(无 key)
    private List<EvalItemResult> perItem;       // 逐条明细
}
