package me.zhengziheng.agent.service.agent;

import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.mapper.ChunkMapper;
import me.zhengziheng.agent.service.QueryTerms;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 关键词精确检索工具：字面 ILIKE 匹配（QueryTerms 分词）。
 * 语义检索漏掉专有名词 / 报错码 / API 名时，用本工具做精确命中。
 */
@Component
public class KeywordRetrieveTool implements AgentTool {

    private final ChunkMapper chunkMapper;

    public KeywordRetrieveTool(ChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    @Override
    public String name() {
        return "retrieve_keyword";
    }

    @Override
    public String description() {
        return "关键词精确检索知识库：按字面关键词（如专有名词、API 名、报错码）匹配片段。"
                + "当语义检索没找到精确内容、或需要确认某个术语是否在知识库中出现时使用。";
    }

    @Override
    public String parameters() {
        return "query(必填, string, 关键词；专有名词/API 名/报错码效果最好)";
    }

    @Override
    public AgentToolResult execute(Map<String, Object> args) {
        String query = RetrieveTool.asString(args.get("query"));
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("retrieve_keyword 需要 query 参数（你要找哪个关键词？）");
        }
        List<String> terms = QueryTerms.extract(query);
        List<ChunkSearchResult> hits = terms.isEmpty()
                ? List.of()
                : chunkMapper.keywordSearch(terms, 5);

        AgentToolResult r = new AgentToolResult();
        r.setHits(hits.size());
        r.setSummary("关键词命中 " + hits.size() + " 个片段");
        r.setChunks(hits);
        r.setText(hits.isEmpty() ? "（没有字面匹配，知识库中可能不含该关键词）" : "关键词检索完成，命中片段已按全局编号列出。");
        return r;
    }
}
