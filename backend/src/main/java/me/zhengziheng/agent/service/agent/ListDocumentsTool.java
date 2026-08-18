package me.zhengziheng.agent.service.agent;

import me.zhengziheng.agent.entity.Document;
import me.zhengziheng.agent.mapper.DocumentMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 文档清单工具：让 LLM 知道知识库里有哪些文档（决定要不要换文档 / 查看具体某篇）。
 */
@Component
public class ListDocumentsTool implements AgentTool {

    private static final int MAX_DOCS = 20;

    private final DocumentMapper documentMapper;

    public ListDocumentsTool(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }

    @Override
    public String name() {
        return "list_documents";
    }

    @Override
    public String description() {
        return "列出知识库中的全部文档（文件名 + 分块数）。"
                + "当需要确认知识库有哪些内容、或判断该查哪篇文档时使用。";
    }

    @Override
    public String parameters() {
        return "无参数";
    }

    @Override
    public AgentToolResult execute(Map<String, Object> args) {
        List<Document> docs = documentMapper.selectPage(MAX_DOCS, 0);
        StringBuilder sb = new StringBuilder();
        for (Document d : docs) {
            sb.append("- ").append(d.getName()).append("（").append(d.getChunkCount()).append(" 个分块，docId=")
                    .append(d.getDocId()).append("）\n");
        }
        if (docs.isEmpty()) {
            sb.append("（知识库为空，请先上传 Markdown 笔记）");
        }
        AgentToolResult r = new AgentToolResult();
        r.setHits(docs.size());
        r.setSummary("共 " + docs.size() + " 篇文档");
        r.setText(sb.toString());
        return r;
    }
}
