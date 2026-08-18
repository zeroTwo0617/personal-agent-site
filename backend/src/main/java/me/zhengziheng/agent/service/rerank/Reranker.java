package me.zhengziheng.agent.service.rerank;

import me.zhengziheng.agent.dto.response.ChunkSearchResult;

import java.util.List;

/**
 * 重排器接口：对「融合后的候选片段」做精排，取 Top-N 送入 LLM 生成。
 *
 * 为什么抽象成接口 + 多实现：
 *  - 混合检索（向量+关键词 → RRF 融合）给出的是"候选集"，重排决定最终进 Prompt 的 Top-N；
 *  - 重排策略可插拔：启发式（零成本、可解释）或 LLM 打分（更贴近语义相关性）；
 *  - 与配置 rag.rerank.strategy 对应，上层按策略名选择实现，无 key 时自动回退启发式。
 */
public interface Reranker {

    /** 策略名，对应配置 rag.rerank.strategy（heuristic / llm） */
    String strategy();

    /** 是否可用（如 LLM 策略在无 key 时为 false，触发上层回退） */
    boolean available();

    /**
     * 对候选精排。
     *
     * @param query      原始问题
     * @param terms      关键词召回用的 term 列表（启发式可借此算"词面重合度"）
     * @param candidates 融合后的候选（已按 fusedScore 降序）
     * @param topN       最终保留数量
     * @return 重排后的 Top-N 片段
     */
    List<ChunkSearchResult> rerank(String query,
                                   List<String> terms,
                                   List<ChunkSearchResult> candidates,
                                   int topN);
}
