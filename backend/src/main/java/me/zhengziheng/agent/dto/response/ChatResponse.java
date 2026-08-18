package me.zhengziheng.agent.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class ChatResponse {

    /** M1 为抽取式：直接拼接 Top-K 片段内容；后续里程碑改为 LLM 生成 */
    private String answer;

    /** 引用来源（含相似度与原文），是 RAG 区别于普通聊天的硬指标 */
    private List<ChunkSearchResult> sources;

    /** 问题是否成功向量化（无 key 时为本地兜底，仍为 true） */
    private boolean queryEmbedded;
}
