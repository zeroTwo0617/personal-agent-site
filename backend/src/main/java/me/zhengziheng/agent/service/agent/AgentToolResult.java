package me.zhengziheng.agent.service.agent;

import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具执行结果：回填给 LLM 的文本 + 命中统计（SSE 展示）+ 命中的片段（引用溯源收集）。
 */
@Data
public class AgentToolResult {

    /** 回填给 LLM 的工具结果文本（含片段原文） */
    private String text;

    /** 命中片段数量（SSE agent_step 事件展示） */
    private int hits;

    /** 一句话摘要（SSE 展示，如"检索到 3 个片段"） */
    private String summary;

    /** 本次命中的片段（供上层按全局顺序收集为引用来源） */
    private List<ChunkSearchResult> chunks = new ArrayList<>();
}
