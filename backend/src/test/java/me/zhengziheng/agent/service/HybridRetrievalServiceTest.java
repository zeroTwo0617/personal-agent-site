package me.zhengziheng.agent.service;

import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.mapper.ChunkMapper;
import me.zhengziheng.agent.service.rerank.HeuristicReranker;
import me.zhengziheng.agent.service.rerank.Reranker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HybridRetrievalService 单测：向量+关键词双路召回 → RRF 融合 → 启发式重排。
 * RetrievalService / ChunkMapper 用 Mockito 打桩（纯逻辑链路验证，不连数据库）。
 */
@ExtendWith(MockitoExtension.class)
class HybridRetrievalServiceTest {

    @Mock
    private RetrievalService retrievalService;
    @Mock
    private ChunkMapper chunkMapper;

    private final HeuristicReranker heuristic = new HeuristicReranker();

    private ChunkSearchResult chunk(String docId, int idx, String content) {
        ChunkSearchResult c = new ChunkSearchResult();
        c.setDocId(docId);
        c.setChunkIndex(idx);
        c.setContent(content);
        c.setDistance(0.5d); // 占位，融合阶段不读它
        return c;
    }

    private HybridRetrievalService service(String strategy, int multiplier, double k, int topN, List<Reranker> rerankers) {
        return new HybridRetrievalService(retrievalService, chunkMapper, rerankers, strategy, multiplier, k, topN);
    }

    @Test
    void hybridMode_rrfFusionAndHeuristicRerank() {
        ChunkSearchResult c1 = chunk("d1", 0, "Vue3 响应式基于 Proxy");
        ChunkSearchResult c2 = chunk("d1", 1, "ref 与 reactive 区别");
        ChunkSearchResult c3 = chunk("d2", 0, "关键词内容示例");
        // 关键词路仅命中 c2/c3（c1 若也命中会与向量路 rank 拉平，无法体现双路加成）
        when(retrievalService.retrieve("Vue3 ref reactive 区别", 6)).thenReturn(List.of(c1, c2));
        when(chunkMapper.keywordSearch(any(), eq(6))).thenReturn(List.of(c2, c3));

        HybridRetrievalService svc = service("heuristic", 3, 60, 2, List.of(heuristic));
        List<ChunkSearchResult> out = svc.retrieve("Vue3 ref reactive 区别", 2);

        // c2 同时命中向量 + 关键词且词面重合最高 → 应排第一
        assertEquals("d1", out.get(0).getDocId());
        assertEquals(1, out.get(0).getChunkIndex());
        assertEquals(2, out.size());
        // 融合分（RRF: 1/(k+rank)，k=60）：
        //   c2 = 1/62(向量rank2) + 1/61(关键词rank1) ≈ 0.03252
        //   c1 = 1/61(向量rank1)                     ≈ 0.01639
        assertEquals(1.0 / 62.0 + 1.0 / 61.0, c2.getFusedScore(), 1e-9);
        assertEquals(1.0 / 61.0, c1.getFusedScore(), 1e-9);
        assertTrue(c2.getFusedScore() > c1.getFusedScore(), "双路命中的块融合分应更高");
        // 元信息：混合模式 + 启发式重排
        Map<String, Object> meta = out.get(0).getRetrievalMeta();
        assertEquals("hybrid", meta.get("mode"));
        assertEquals("heuristic", meta.get("rerank"));
    }

    @Test
    void vectorMode_skipsKeywordPath() {
        ChunkSearchResult c1 = chunk("d1", 0, "内容一");
        ChunkSearchResult c2 = chunk("d1", 1, "内容二");
        when(retrievalService.retrieve("问题", 6)).thenReturn(List.of(c1, c2));

        HybridRetrievalService svc = service("heuristic", 3, 60, 2, List.of(heuristic));
        List<ChunkSearchResult> out = svc.retrieve("问题", 2, "vector");

        verify(chunkMapper, never()).keywordSearch(any(), anyInt());
        assertEquals("vector", out.get(0).getRetrievalMeta().get("mode"));
        assertEquals(2, out.size());
    }

    @Test
    void emptyTerms_skipsKeywordCallAndFallsBackVectorOnly() {
        ChunkSearchResult c1 = chunk("d1", 0, "内容");
        when(retrievalService.retrieve("的", 6)).thenReturn(List.of(c1));

        HybridRetrievalService svc = service("heuristic", 3, 60, 2, List.of(heuristic));
        List<ChunkSearchResult> out = svc.retrieve("的", 2);

        verify(chunkMapper, never()).keywordSearch(any(), anyInt());
        assertEquals("vector-only", out.get(0).getRetrievalMeta().get("mode"));
    }

    @Test
    void noReranker_truncatesFusedByTopN() {
        ChunkSearchResult c1 = chunk("d1", 0, "x");
        ChunkSearchResult c2 = chunk("d1", 1, "y");
        ChunkSearchResult c3 = chunk("d2", 0, "z");
        when(retrievalService.retrieve("vue", 9)).thenReturn(List.of(c1, c2));
        when(chunkMapper.keywordSearch(any(), eq(9))).thenReturn(List.of(c3));

        HybridRetrievalService svc = service("heuristic", 3, 60, 2, List.of());
        List<ChunkSearchResult> out = svc.retrieve("vue", 3);

        assertEquals(2, out.size(), "无 Reranker 时按融合分截断 Top-N");
        assertEquals("none", out.get(0).getRetrievalMeta().get("rerank"));
    }

    @Test
    void rrfFormula_rank1GetsOneOverKPlusRank() {
        ChunkSearchResult c1 = chunk("d1", 0, "a");
        ChunkSearchResult c2 = chunk("d1", 1, "b");
        when(retrievalService.retrieve("的", 6)).thenReturn(List.of(c1, c2));

        HybridRetrievalService svc = service("heuristic", 3, 60, 10, List.of());
        svc.retrieve("的", 2);

        assertEquals(1.0 / 61.0, c1.getFusedScore(), 1e-9, "rank1 → 1/(60+1)");
        assertEquals(1.0 / 62.0, c2.getFusedScore(), 1e-9, "rank2 → 1/(60+2)");
    }

    @Test
    void unavailableStrategy_fallsBackToHeuristic() {
        Reranker llm = new Reranker() {
            @Override
            public String strategy() {
                return "llm";
            }

            @Override
            public boolean available() {
                return false;
            }

            @Override
            public List<ChunkSearchResult> rerank(String query, List<String> terms,
                                                  List<ChunkSearchResult> candidates, int topN) {
                return List.of();
            }
        };
        ChunkSearchResult c1 = chunk("d1", 0, "内容");
        when(retrievalService.retrieve("q", 6)).thenReturn(List.of(c1));

        HybridRetrievalService svc = service("llm", 3, 60, 2, List.of(llm, heuristic));
        List<ChunkSearchResult> out = svc.retrieve("q", 2);

        assertEquals("heuristic", out.get(0).getRetrievalMeta().get("rerank"), "LLM 不可用应回退启发式");
        assertNotNull(out.get(0).getRerankScore());
    }
}
