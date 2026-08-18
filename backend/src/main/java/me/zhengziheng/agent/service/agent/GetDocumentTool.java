package me.zhengziheng.agent.service.agent;

import me.zhengziheng.agent.dto.response.ChunkDetail;
import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.mapper.ChunkMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查看指定文档工具：按 docId 拉取整篇文档的分块（跨文档综合题 / 换文档检索时使用）。
 */
@Component
public class GetDocumentTool implements AgentTool {

    private final ChunkMapper chunkMapper;

    public GetDocumentTool(ChunkMapper chunkMapper) {
        this.chunkMapper = chunkMapper;
    }

    @Override
    public String name() {
        return "get_document";
    }

    @Override
    public String description() {
        return "查看指定文档的全部内容（按分块列出）。"
                + "当确定答案在某一篇文档里、需要读全文来综合回答时使用。docId 从 list_documents 获得。";
    }

    @Override
    public String parameters() {
        return "docId(必填, string, 文档 ID，来自 list_documents)";
    }

    @Override
    public AgentToolResult execute(Map<String, Object> args) {
        String docId = RetrieveTool.asString(args.get("docId"));
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("get_document 需要 docId 参数（先调用 list_documents 获取）");
        }
        List<ChunkDetail> chunks = chunkMapper.listByDocId(docId);
        List<ChunkSearchResult> results = new ArrayList<>();
        for (ChunkDetail c : chunks) {
            ChunkSearchResult s = new ChunkSearchResult();
            s.setDocId(docId);
            s.setDocName("docId=" + docId);
            s.setSection(c.getSection());
            s.setChunkIndex(c.getChunkIndex());
            s.setContent(c.getContent());
            results.add(s);
        }

        AgentToolResult r = new AgentToolResult();
        r.setHits(chunks.size());
        r.setSummary("文档共 " + chunks.size() + " 个分块");
        r.setChunks(results);
        r.setText(chunks.isEmpty() ? "（文档不存在或没有分块）" : "已读取文档全部内容，片段已按全局编号列出。");
        return r;
    }
}
