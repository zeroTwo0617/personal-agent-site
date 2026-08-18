package me.zhengziheng.agent.service.rerank;

import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.service.LlmClient;
import me.zhengziheng.agent.service.LlmMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 重排：把 (问题, 片段) 交给大模型打 0~100 相关性分，按分重排。
 *
 * 相比启发式更贴近"语义相关性"，但每次问答会多若干次 LLM 调用（成本/延迟权衡，
 * 已用 rag.rerank.llm-cap 限制打分候选数）。无 key 时 available()=false，
 * 上层会自动回退到 HeuristicReranker。
 */
@Component
public class LlmReranker implements Reranker {

    private final LlmClient llmClient;
    private final int cap;

    public LlmReranker(LlmClient llmClient,
                       @Value("${rag.rerank.llm-cap:10}") int cap) {
        this.llmClient = llmClient;
        this.cap = cap;
    }

    @Override
    public String strategy() {
        return "llm";
    }

    @Override
    public boolean available() {
        return llmClient.available();
    }

    @Override
    public List<ChunkSearchResult> rerank(String query,
                                          List<String> terms,
                                          List<ChunkSearchResult> candidates,
                                          int topN) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(cap, candidates.size());
        List<ChunkSearchResult> toScore = candidates.subList(0, limit);

        Pattern num = Pattern.compile("\\d+");
        List<Scored> scored = new ArrayList<>(candidates.size());
        for (ChunkSearchResult c : toScore) {
            double score = scoreWithLlm(query, c, num);
            c.setRerankScore(score);
            scored.add(new Scored(c, score));
        }
        // 超出 cap 的候选按融合分兜底排在后面（保持 Top-N 选取的稳定）
        for (int i = limit; i < candidates.size(); i++) {
            ChunkSearchResult c = candidates.get(i);
            double fallback = (c.getFusedScore() == null ? 0 : c.getFusedScore()) * 100;
            c.setRerankScore(fallback);
            scored.add(new Scored(c, fallback));
        }
        scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());

        int n = Math.min(topN, scored.size());
        List<ChunkSearchResult> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(scored.get(i).chunk);
        }
        return out;
    }

    private double scoreWithLlm(String query, ChunkSearchResult c, Pattern num) {
        try {
            String passage = c.getContent() == null ? "" : c.getContent();
            if (passage.length() > 800) {
                passage = passage.substring(0, 800);
            }
            List<LlmMessage> msgs = List.of(
                    new LlmMessage("system",
                            "你是相关性评分器。给定用户问题和一段文本，只输出一个 0 到 100 的整数，" +
                                    "表示这段文本对回答该问题的相关程度。不要输出任何解释或标点。"),
                    new LlmMessage("user", "问题：" + query + "\n文本：" + passage)
            );
            String raw = llmClient.generate(msgs);
            Matcher m = num.matcher(raw == null ? "" : raw);
            if (m.find()) {
                return Integer.parseInt(m.group());
            }
            return (c.getFusedScore() == null ? 0 : c.getFusedScore()) * 100;
        } catch (Exception e) {
            // LLM 打分失败则回退到融合分，保证流程不中断
            return (c.getFusedScore() == null ? 0 : c.getFusedScore()) * 100;
        }
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
