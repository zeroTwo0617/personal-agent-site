package me.zhengziheng.agent.service.rerank;

import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 启发式重排（默认，零成本，可解释）：
 *
 *   score = 0.6 * 归一化RRF + 0.4 * 词面重合度(命中term数 / 总term数)
 *
 * 在 RRF 融合分基础上，进一步让"和问题字面更相关"的片段上浮，
 * 缓解纯向量召回可能把语义近但字面无关片段排太前的问题。
 * 不依赖任何外部模型，演示与离线测试零成本。
 */
@Component
public class HeuristicReranker implements Reranker {

    @Override
    public String strategy() {
        return "heuristic";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public List<ChunkSearchResult> rerank(String query,
                                          List<String> terms,
                                          List<ChunkSearchResult> candidates,
                                          int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        double maxRrf = candidates.stream()
                .mapToDouble(c -> c.getFusedScore() == null ? 0d : c.getFusedScore())
                .max().orElse(1d);
        if (maxRrf <= 0) {
            maxRrf = 1d;
        }

        int totalTerms = (terms == null ? 0 : terms.size());
        List<Scored> scored = new ArrayList<>(candidates.size());
        for (ChunkSearchResult c : candidates) {
            double normRrf = (c.getFusedScore() == null ? 0d : c.getFusedScore()) / maxRrf;
            double overlap = totalTerms == 0 ? 0d : (double) matchedTerms(terms, c.getContent()) / totalTerms;
            double score = 0.6 * normRrf + 0.4 * overlap;
            c.setRerankScore(score);
            scored.add(new Scored(c, score));
        }
        scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());

        int n = Math.min(topN, scored.size());
        List<ChunkSearchResult> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(scored.get(i).chunk);
        }
        return out;
    }

    private int matchedTerms(List<String> terms, String content) {
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

    private static final class Scored {
        final ChunkSearchResult chunk;
        final double score;

        Scored(ChunkSearchResult chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
    }
}
