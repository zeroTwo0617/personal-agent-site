package me.zhengziheng.agent.service.agent;

import me.zhengziheng.agent.dto.response.ChunkDetail;
import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.entity.Document;
import me.zhengziheng.agent.mapper.ChunkMapper;
import me.zhengziheng.agent.mapper.DocumentMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查看指定文档工具：按 docId 拉取整篇文档的分块。
 * ⚠️ 暂不注册为 @Component（从 Agent 工具列表移除）：
 *    小知识库下 retrieve 已足够，get_document 依赖模型传对 docId，实测频繁失败（0 分块）。
 *    需要"整篇阅读"场景时再恢复注册。
 */
public class GetDocumentTool implements AgentTool {

    private final ChunkMapper chunkMapper;
    private final DocumentMapper documentMapper;

    public GetDocumentTool(ChunkMapper chunkMapper, DocumentMapper documentMapper) {
        this.chunkMapper = chunkMapper;
        this.documentMapper = documentMapper;
    }

    @Override
    public String name() {
        return "get_document";
    }

    @Override
    public String description() {
        return "查看指定文档的全部内容（按分块列出）。"
                + "当确定答案在某一篇文档里、需要读全文来综合回答时使用。docId 从 list_documents 或检索结果的 (docId=xxx) 标注获得。";
    }

    @Override
    public String parameters() {
        return "docId(必填, string, 文档 ID，来自 list_documents 或检索结果标注)";
    }

    @Override
    public AgentToolResult execute(Map<String, Object> args) {
        String docId = RetrieveTool.asString(args.get("docId"));
        if (docId == null || docId.isBlank()) {
            throw new IllegalArgumentException("get_document 需要 docId 参数（先调用 list_documents 获取）");
        }
        // 解析真实文档名（docId 无效时为 null，返回明确指引）
        Document doc = documentMapper.selectByDocId(docId);
        String docName = doc == null ? null : doc.getName();

        List<ChunkDetail> chunks = chunkMapper.listByDocId(docId);
        List<ChunkSearchResult> results = new ArrayList<>();
        for (ChunkDetail c : chunks) {
            ChunkSearchResult s = new ChunkSearchResult();
            s.setDocId(docId);
            s.setDocName(docName == null ? ("docId=" + docId) : docName);
            s.setSection(c.getSection());
            s.setChunkIndex(c.getChunkIndex());
            s.setContent(c.getContent());
            results.add(s);
        }

        AgentToolResult r = new AgentToolResult();
        r.setHits(chunks.size());
        r.setSummary("文档共 " + chunks.size() + " 个分块");
        r.setChunks(results);
        if (chunks.isEmpty()) {
            r.setText("（docId 不存在或该文档没有分块：请先调用 list_documents 获取正确的 docId，再重试 get_document）");
        } else {
            r.setText("已读取文档《" + docName + "》全部内容，片段已按全局编号列出。");
        }
        return r;
    }
}
