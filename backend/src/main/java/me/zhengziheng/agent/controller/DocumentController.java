package me.zhengziheng.agent.controller;

import me.zhengziheng.agent.common.PageResult;
import me.zhengziheng.agent.common.Result;
import me.zhengziheng.agent.dto.response.ChunkDetail;
import me.zhengziheng.agent.dto.response.DocumentDetailVO;
import me.zhengziheng.agent.dto.response.DocumentVO;
import me.zhengziheng.agent.entity.Document;
import me.zhengziheng.agent.mapper.ChunkMapper;
import me.zhengziheng.agent.mapper.DocumentMapper;
import me.zhengziheng.agent.service.DocumentIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "文档管理")
public class DocumentController {

    private final DocumentIngestService ingestService;
    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;

    public DocumentController(DocumentIngestService ingestService,
                              DocumentMapper documentMapper,
                              ChunkMapper chunkMapper) {
        this.ingestService = ingestService;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
    }

    @PostMapping("/upload")
    @Operation(summary = "上传 Markdown 并解析入库")
    public Result<DocumentVO> upload(@RequestParam("file") MultipartFile file) {
        Document doc = ingestService.ingest(file);
        return Result.success(toVO(doc));
    }

    @GetMapping
    @Operation(summary = "文档列表（分页）")
    public Result<PageResult<DocumentVO>> list(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<Document> docs = documentMapper.selectPage(size, offset);
        long total = documentMapper.countAll();
        List<DocumentVO> vos = docs.stream().map(this::toVO).collect(Collectors.toList());
        return Result.success(PageResult.of(vos, page, size, total));
    }

    @GetMapping("/{id}")
    @Operation(summary = "文档详情（含分块预览）")
    public Result<DocumentDetailVO> detail(@PathVariable("id") String id) {
        Document doc = documentMapper.selectByDocId(id);
        if (doc == null) {
            return Result.error(404, "文档不存在");
        }
        List<ChunkDetail> chunks = chunkMapper.listByDocId(id);
        DocumentDetailVO vo = new DocumentDetailVO();
        vo.setDocId(doc.getDocId());
        vo.setName(doc.getName());
        vo.setStatus(doc.getStatus());
        vo.setChunkCount(doc.getChunkCount());
        vo.setCreatedAt(doc.getCreatedAt());
        vo.setChunks(chunks);
        return Result.success(vo);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档（级联删除分块）")
    public Result<Void> delete(@PathVariable("id") String id) {
        Document doc = documentMapper.selectByDocId(id);
        if (doc == null) {
            return Result.error(404, "文档不存在");
        }
        documentMapper.deleteByDocId(id);
        return Result.success();
    }

    private DocumentVO toVO(Document doc) {
        DocumentVO vo = new DocumentVO();
        vo.setDocId(doc.getDocId());
        vo.setName(doc.getName());
        vo.setStatus(doc.getStatus());
        vo.setChunkCount(doc.getChunkCount());
        vo.setCreatedAt(doc.getCreatedAt());
        return vo;
    }
}
