package me.zhengziheng.agent.service;

import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.mapper.ChunkMapper;
import me.zhengziheng.agent.service.rerank.Reranker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 混合检索服务（M2 检索增强核心）：
 *
 *   1) 向量召回：整句 embedding → PGVector 余弦 Top-K*multiplier（语义层面）
 *   2) 关键词召回：QueryTerms 分词 → ILIKE 命中 Top-K*multiplier（字面精确层面）
 *   3) 融合：RRF 倒数排名融合（各路 1/(k+rank) 求和），互补语义与字面召回盲区
 *   4) 重排：选中的 Reranker（启发式 / LLM）对融合候选精排，取 Top-N
 *
 * 返回最终片段列表，供 M2 流式生成或 M1 抽取式拼接使用；并在每个片段上附检索元信息，
 * 便于前端展示「检索：混合 · Rerank：启发式/LLM」徽标与调试。
 */
@Service
public class HybridRetrievalService {

    private final RetrievalService retrievalService;   // 向量召回原语
    private final ChunkMapper chunkMapper;             // 关键词召回
    private final List<Reranker> rerankers;            // 所有 Reranker 实现（按策略名选择）
    private final String rerankStrategy;

    private final int candidateMultiplier;
    private final double rrfK;
    private final int topN;

    public HybridRetrievalService(RetrievalService retrievalService,
                                  ChunkMapper chunkMapper,
                                  List<Reranker> rerankers,
                                  @Value("${rag.rerank.strategy:heuristic}") String rerankStrategy,
                                  @Value("${rag.retrieval.candidate-multiplier:3}") int candidateMultiplier,
                                  @Value("${rag.rrf.k:60}") double rrfK,
                                  @Value("${rag.rerank.top-n:6}") int topN) {
        this.retrievalService = retrievalService;
        this.chunkMapper = chunkMapper;
        this.rerankers = rerankers;
        this.rerankStrategy = rerankStrategy;
        this.candidateMultiplier = candidateMultiplier;
        this.rrfK = rrfK;
        this.topN = topN;
    }

    /**
     * 默认走混合检索（兼容既有调用，如 M2 流式问答）。
     */
    public List<ChunkSearchResult> retrieve(String question, int topK) {
        return retrieve(question, topK, "hybrid");
    }

    /**
     * 带模式的检索：mode=hybrid（默认，向量+关键词+RRF融合+Rerank）或 mode=vector（纯向量基线）。
     * 两者共用同一 Rerank 管线，唯一差异是「是否引入关键词召回与 RRF 融合」，
     * 便于评测闭环做 before/after 对照，把 Recall@K 提升干净归因到关键词+RRF。
     */
    public List<ChunkSearchResult> retrieve(String question, int topK, String mode) {
        boolean hybrid = !"vector".equalsIgnoreCase(mode);
        int pool = Math.max(topK, 1) * candidateMultiplier;
        Reranker reranker = pickReranker();

        // 1) 向量召回（语义层，两种模式都执行）
        List<ChunkSearchResult> vectorHits = retrievalService.retrieve(question, pool);

        List<ChunkSearchResult> finalResults;
        List<String> terms;
        int keywordHitCount;
        String modeName;

        if (hybrid) {
            // 2) 关键词召回（字面层）
            terms = QueryTerms.extract(question);
            List<ChunkSearchResult> keywordHits = terms.isEmpty()
                    ? List.of()
                    : chunkMapper.keywordSearch(terms, pool);

            // 关键词路按"命中 term 数"降序，作为融合排名依据
            List<ChunkSearchResult> kwOrdered = new ArrayList<>(keywordHits);
            kwOrdered.sort((a, b) -> Integer.compare(
                    matchedTermCount(terms, b.getContent()),
                    matchedTermCount(terms, a.getContent())));

            // 3) RRF 倒数排名融合
            Map<String, ChunkSearchResult> byKey = new LinkedHashMap<>();
            Map<String, Double> rrf = new HashMap<>();
            int vi = 0;
            for (ChunkSearchResult c : vectorHits) {
                String key = c.getDocId() + "#" + c.getChunkIndex();
                ChunkSearchResult ref = byKey.computeIfAbsent(key, k -> c);
                rrf.merge(key, 1.0 / (rrfK + (vi + 1)), Double::sum);
                ref.setFusedScore(rrf.get(key));
                ref.setVectorRank(vi + 1);
                vi++;
            }
            int ki = 0;
            for (ChunkSearchResult c : kwOrdered) {
                String key = c.getDocId() + "#" + c.getChunkIndex();
                ChunkSearchResult ref = byKey.computeIfAbsent(key, k -> c);
                rrf.merge(key, 1.0 / (rrfK + (ki + 1)), Double::sum);
                ref.setFusedScore(rrf.get(key));
                ref.setKeywordRank(ki + 1);
                ki++;
            }

            List<ChunkSearchResult> fused = new ArrayList<>(byKey.values());
            fused.sort((a, b) -> Double.compare(
                    b.getFusedScore() == null ? 0 : b.getFusedScore(),
                    a.getFusedScore() == null ? 0 : a.getFusedScore()));

            // 4) 重排精排
            if (reranker == null) {
                int n = Math.min(topN, fused.size());
                finalResults = new ArrayList<>(fused.subList(0, n));
            } else {
                finalResults = reranker.rerank(question, terms, fused, topN);
            }
            keywordHitCount = keywordHits.size();
            modeName = keywordHits.isEmpty() ? "vector-only" : "hybrid";
        } else {
            // 向量基线：纯向量召回，按距离取 Top-N，同样过 Rerank（公平对照）
            terms = List.of();
            if (reranker == null) {
                int n = Math.min(topN, vectorHits.size());
                finalResults = new ArrayList<>(vectorHits.subList(0, n));
            } else {
                finalResults = reranker.rerank(question, terms, vectorHits, topN);
            }
            keywordHitCount = 0;
            modeName = "vector";
        }

        // 附检索元信息（前端徽标 / 调试用）
        String rerankName = reranker == null ? "none" : reranker.strategy();
        for (ChunkSearchResult c : finalResults) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("mode", modeName);
            meta.put("rerank", rerankName);
            meta.put("vectorHits", vectorHits.size());
            meta.put("keywordHits", keywordHitCount);
            c.setRetrievalMeta(meta);
        }
        return finalResults;
    }

    /** 按配置选择重排器；所选策略不可用（如 llm 无 key）时回退启发式 */
    private Reranker pickReranker() {
        for (Reranker r : rerankers) {
            if (rerankStrategy.equalsIgnoreCase(r.strategy()) && r.available()) {
                return r;
            }
        }
        for (Reranker r : rerankers) {
            if ("heuristic".equalsIgnoreCase(r.strategy())) {
                return r;
            }
        }
        return rerankers.isEmpty() ? null : rerankers.get(0);
    }

    private int matchedTermCount(List<String> terms, String content) {
        if (terms == null || terms.isEmpty() || content == null) {
            return 0;
        }
        String lower = content.toLowerCase();
        int hit = 0;
        for (String t : terms) {
            if (lower.contains(t.toLowerCase())) {
                hit++;
            }
        }
        return hit;
    }
}
