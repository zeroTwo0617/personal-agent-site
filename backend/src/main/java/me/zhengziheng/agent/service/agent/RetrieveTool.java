package me.zhengziheng.agent.service.agent;

import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.service.HybridRetrievalService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 语义检索工具：复用 HybridRetrievalService（向量+关键词混合召回 + RRF 融合 + 重排）。
 * Agent 最常用的工具——"找与问题语义相关的内容"时使用。
 */
@Component
public class RetrieveTool implements AgentTool {

    private final HybridRetrievalService hybridRetrievalService;

    public RetrieveTool(HybridRetrievalService hybridRetrievalService) {
        this.hybridRetrievalService = hybridRetrievalService;
    }

    @Override
    public String name() {
        return "retrieve";
    }

    @Override
    public String description() {
        return "语义检索知识库：按语义相似度召回最相关的笔记片段（混合检索 + 重排）。"
                + "当需要找与问题语义相关的内容、或问题用了同义表述时使用。";
    }

    @Override
    public String parameters() {
        return "query(必填, string, 检索词；可先用改写后的自包含查询), topK(选填, number, 1~8, 默认 4)";
    }

    @Override
    public AgentToolResult execute(Map<String, Object> args) {
        String query = asString(args.get("query"));
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("retrieve 需要 query 参数（你要检索什么？）");
        }
        int topK = Math.max(1, Math.min(8, asInt(args.get("topK"), 4)));
        List<ChunkSearchResult> hits = hybridRetrievalService.retrieve(query, topK);

        AgentToolResult r = new AgentToolResult();
        r.setHits(hits.size());
        r.setSummary("语义检索到 " + hits.size() + " 个片段");
        r.setChunks(hits);
        r.setText(hits.isEmpty() ? "（未检索到相关内容，可换关键词再试）" : "语义检索完成，命中片段已按全局编号列出。");
        return r;
    }

    static String asString(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    static int asInt(Object o, int def) {
        if (o == null) {
            return def;
        }
        try {
            if (o instanceof Number n) {
                return n.intValue();
            }
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
