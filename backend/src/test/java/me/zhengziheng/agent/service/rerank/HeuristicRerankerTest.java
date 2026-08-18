package me.zhengziheng.agent.service.rerank;

import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HeuristicReranker 纯逻辑单测：score = 0.6 * 归一化RRF + 0.4 * 词面重合度。
 */
class HeuristicRerankerTest {

    private final HeuristicReranker reranker = new HeuristicReranker();

    private ChunkSearchResult chunk(String docId, int idx, String content, Double fusedScore) {
        ChunkSearchResult c = new ChunkSearchResult();
        c.setDocId(docId);
        c.setChunkIndex(idx);
        c.setContent(content);
        c.setFusedScore(fusedScore);
        return c;
    }

    @Test
    void emptyOrNullCandidates_returnsEmpty() {
        assertEquals(0, reranker.rerank("q", List.of(), null, 5).size());
        assertEquals(0, reranker.rerank("q", List.of(), List.of(), 5).size());
    }

    @Test
    void higherFusedScore_ranksFirst_whenOverlapEqual() {
        List<ChunkSearchResult> out = reranker.rerank(
                "vue3",
                List.of("vue3"),
                List.of(chunk("a", 0, "无关内容", 1.0), chunk("b", 0, "无关内容", 0.5)),
                5);
        assertEquals("a", out.get(0).getDocId());
        assertEquals("b", out.get(1).getDocId());
    }

    @Test
    void termOverlap_boostsLowerFusedScore() {
        // B 的 RRF 更低，但命中 2/2 个 term，词面重合度 1.0 → 总分反超 A
        List<ChunkSearchResult> out = reranker.rerank(
                "vue 响应式",
                List.of("vue", "响应式"),
                List.of(chunk("a", 0, "完全无关的内容", 1.0), chunk("b", 0, "vue 与响应式相关介绍", 0.8)),
                5);
        assertEquals("b", out.get(0).getDocId());
    }

    @Test
    void topN_limitsResultSize() {
        List<ChunkSearchResult> out = reranker.rerank(
                "q",
                List.of(),
                List.of(chunk("a", 0, "x", 0.9), chunk("b", 0, "y", 0.8), chunk("c", 0, "z", 0.7)),
                2);
        assertEquals(2, out.size());
    }

    @Test
    void rerankScore_isSetOnResults() {
        List<ChunkSearchResult> out = reranker.rerank(
                "q", List.of("q"),
                List.of(chunk("a", 0, "q 相关内容", 1.0)),
                5);
        assertNotNull(out.get(0).getRerankScore());
        assertTrue(out.get(0).getRerankScore() > 0);
    }

    @Test
    void allNullFusedScore_noCrash() {
        // 全部 fusedScore 为 null 时 maxRrf 兜底为 1，不抛异常
        List<ChunkSearchResult> out = reranker.rerank(
                "q", List.of(),
                List.of(chunk("a", 0, "x", null), chunk("b", 0, "y", null)),
                5);
        assertEquals(2, out.size());
    }
}
