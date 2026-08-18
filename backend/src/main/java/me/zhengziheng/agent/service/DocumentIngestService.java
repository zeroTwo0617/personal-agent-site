package me.zhengziheng.agent.service;

import me.zhengziheng.agent.common.BusinessException;
import me.zhengziheng.agent.common.ErrorCode;
import me.zhengziheng.agent.entity.Document;
import me.zhengziheng.agent.mapper.ChunkMapper;
import me.zhengziheng.agent.mapper.DocumentMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 文档入库管线：解析 -> 分块 -> 向量化 -> 写入 PGVector。
 * 提供两条入口：
 *  - ingest(MultipartFile)：上传入口（访客管理功能，保留但前端不暴露）；
 *  - ingestContent(name, content)：内容入口（KbSeedService 启动灌库复用）。
 * 入库时计算内容 SHA-256 写入 document.content_hash，供 seed 幂等判断。
 */
@Service
public class DocumentIngestService {

    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final MarkdownParser markdownParser;
    private final ChunkSplitter chunkSplitter;

    public DocumentIngestService(DocumentMapper documentMapper,
                                 ChunkMapper chunkMapper,
                                 EmbeddingService embeddingService,
                                 MarkdownParser markdownParser,
                                 ChunkSplitter chunkSplitter) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingService = embeddingService;
        this.markdownParser = markdownParser;
        this.chunkSplitter = chunkSplitter;
    }

    /** 单块最大字符数（application.yml: rag.chunk.max-chars） */
    @Value("${rag.chunk.max-chars:400}")
    private int maxChars;

    /** 相邻块重叠字符数（application.yml: rag.chunk.overlap） */
    @Value("${rag.chunk.overlap:80}")
    private int overlap;

    /** 上传入口：校验 .md 后读取内容，走 ingestContent */
    @Transactional
    public Document ingest(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".md")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持 .md 文件");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取文件失败");
        }
        return ingestContent(name, content);
    }

    /** 内容入库（seed 与上传共用）：解析 -> 分块 -> 向量化 -> 写库，幂等判断交给调用方 */
    @Transactional
    public Document ingestContent(String name, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文档内容为空");
        }

        String docId = UUID.randomUUID().toString().replace("-", "");
        Document doc = new Document();
        doc.setDocId(docId);
        doc.setName(name);
        doc.setStatus("READY");
        doc.setChunkCount(0);
        doc.setContentHash(sha256(content));
        doc.setCreatedAt(LocalDateTime.now());
        documentMapper.insert(doc);

        List<MarkdownParser.ParsedSection> sections = markdownParser.parse(content);

        int chunkIndex = 0;
        int count = 0;
        for (MarkdownParser.ParsedSection section : sections) {
            List<ChunkSplitter.ChunkUnit> units = chunkSplitter.split(section, maxChars, overlap);
            for (ChunkSplitter.ChunkUnit unit : units) {
                float[] vector = embeddingService.embed(unit.getContent());
                chunkMapper.insertChunk(
                        docId,
                        unit.getSection(),
                        chunkIndex++,
                        unit.getContent(),
                        estimateTokens(unit.getContent()),
                        vector
                );
                count++;
            }
        }

        doc.setChunkCount(count);
        documentMapper.updateById(doc);
        return doc;
    }

    private int estimateTokens(String text) {
        return (int) Math.ceil(text.length() / 1.5);
    }

    /** 内容 SHA-256 摘要（十六进制小写） */
    private String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            // SHA-256 必然可用；兜底用内容长度，避免崩溃
            return Integer.toHexString(content.hashCode());
        }
    }
}
