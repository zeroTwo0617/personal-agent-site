package me.zhengziheng.agent.service;

import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.mapper.ChunkMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检索服务：把问题向量化，调用 PGVector 余弦距离检索取 Top-K。
 * 返回的 distance 由前端/上层转换为相似度 score = 1 - distance。
 */
@Service
public class RetrievalService {

    private final ChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;

    public RetrievalService(ChunkMapper chunkMapper, EmbeddingService embeddingService) {
        this.chunkMapper = chunkMapper;
        this.embeddingService = embeddingService;
    }

    public List<ChunkSearchResult> retrieve(String question, int topK) {
        float[] queryVector = embeddingService.embed(question);
        return chunkMapper.search(queryVector, topK);
    }
}
