package me.zhengziheng.agent.service;

import me.zhengziheng.agent.common.BusinessException;
import me.zhengziheng.agent.common.ErrorCode;
import me.zhengziheng.agent.dto.response.ChatResponse;
import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 问答服务（M1 抽取式降级路径）。
 * 用混合检索取 Top-K 片段后，直接拼接片段内容作为答案；sources 携带引用来源与检索元信息。
 * 当未配置 LLM key 时，ChatTaskService 会走本服务（已具备向量+关键词混合召回能力）。
 */
@Service
public class ChatService {

    private final HybridRetrievalService hybridRetrievalService;

    public ChatService(HybridRetrievalService hybridRetrievalService) {
        this.hybridRetrievalService = hybridRetrievalService;
    }

    public ChatResponse ask(String question, int topK) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "问题为空");
        }
        List<ChunkSearchResult> sources = hybridRetrievalService.retrieve(question, topK);

        StringBuilder answer = new StringBuilder();
        for (ChunkSearchResult s : sources) {
            answer.append(s.getContent()).append("\n\n");
        }

        ChatResponse response = new ChatResponse();
        response.setAnswer(answer.toString().trim());
        response.setSources(sources);
        response.setQueryEmbedded(true);
        return response;
    }
}
