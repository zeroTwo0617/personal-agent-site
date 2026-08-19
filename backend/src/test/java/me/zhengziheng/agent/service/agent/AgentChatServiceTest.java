package me.zhengziheng.agent.service.agent;

import me.zhengziheng.agent.dto.response.AgentResult;
import me.zhengziheng.agent.dto.response.AgentStepEvent;
import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.service.HybridRetrievalService;
import me.zhengziheng.agent.service.LlmClient;
import me.zhengziheng.agent.service.LlmMessage;
import me.zhengziheng.agent.service.PersonaPrompts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AgentChatService 单测：ReAct 循环（直接作答 / 工具调用 / 未知工具 / 步数上限 / 格式纠错）。
 * 用脚本化 FakeLlm 按顺序返回预置响应，不依赖真实 LLM 与网络。
 */
class AgentChatServiceTest {

    /** 按脚本顺序返回的假 LLM */
    static class FakeLlm implements LlmClient {
        private final List<String> responses = new ArrayList<>();
        private int idx = 0;

        FakeLlm(String... rs) {
            responses.addAll(List.of(rs));
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public void streamGenerate(List<LlmMessage> messages, Consumer<String> onDelta, Consumer<String> onThinking, String mode) {
        }

        @Override
        public String generate(List<LlmMessage> messages, String mode) {
            return idx < responses.size() ? responses.get(idx++) : "ANSWER: 兜底";
        }
    }

    /** 返回一个片段的 echo 工具 */
    static class EchoTool implements AgentTool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "测试工具";
        }

        @Override
        public String parameters() {
            return "query(必填, string)";
        }

        @Override
        public AgentToolResult execute(Map<String, Object> args) {
            ChunkSearchResult c = new ChunkSearchResult();
            c.setDocId("d1");
            c.setDocName("echo.md");
            c.setSection("测试节");
            c.setChunkIndex(0);
            c.setContent("echo 工具返回的片段内容");
            AgentToolResult r = new AgentToolResult();
            r.setText("echo 完成");
            r.setHits(1);
            r.setSummary("命中 1 个片段");
            r.setChunks(List.of(c));
            return r;
        }
    }

    /** 必然抛错的工具（测 error 事件路径） */
    static class BoomTool implements AgentTool {
        @Override
        public String name() {
            return "boom";
        }

        @Override
        public String description() {
            return "必然失败的工具";
        }

        @Override
        public String parameters() {
            return "无";
        }

        @Override
        public AgentToolResult execute(Map<String, Object> args) {
            throw new IllegalArgumentException("boom 工具故意失败");
        }
    }

    /** 默认混合检索 mock：无命中（各用例按需覆写） */
    private final HybridRetrievalService retrieval = org.mockito.Mockito.mock(HybridRetrievalService.class);

    private AgentChatService service(LlmClient llm) {
        return new AgentChatService(new AgentToolRegistry(List.of(new EchoTool(), new BoomTool())), llm, retrieval, new PersonaPrompts());
    }

    /** 造一个带内容的检索命中（补充检索/引用收集用） */
    private ChunkSearchResult hit(String content) {
        ChunkSearchResult c = new ChunkSearchResult();
        c.setDocId("d9");
        c.setDocName("补充.md");
        c.setSection("补充节");
        c.setChunkIndex(0);
        c.setContent(content);
        c.setDistance(0.3);
        return c;
    }

    @Test
    void directAnswer_noToolCall() {
        AgentResult r = service(new FakeLlm("ANSWER: Vue3 的响应式基于 Proxy。")).run("Vue3 响应式原理", List.of(), null);
        assertEquals("Vue3 的响应式基于 Proxy。", r.getAnswer());
        assertEquals(0, r.getSteps());
        assertTrue(r.getSources().isEmpty());
        assertTrue(r.getTrace().isEmpty());
    }

    @Test
    void toolCallThenAnswer_collectsSourcesAndTrace() {
        List<AgentStepEvent> events = new ArrayList<>();
        AgentResult r = service(new FakeLlm(
                "THOUGHT: 需要检索\nACTION: echo\nACTION_INPUT: {\"query\": \"vue\"}",
                "ANSWER: 答案引用 [1] 的内容。"
        )).run("问题", List.of(), events::add);

        assertEquals("答案引用 [1] 的内容。", r.getAnswer());
        assertEquals(1, r.getSteps());
        assertEquals(1, r.getSources().size());
        assertEquals("echo 工具返回的片段内容", r.getSources().get(0).getContent());
        // 轨迹：running + done 两个事件
        assertEquals(2, events.size());
        assertEquals("running", events.get(0).getStatus());
        assertEquals("done", events.get(1).getStatus());
        assertEquals(1, events.get(1).getHits());
        assertEquals("echo", events.get(0).getTool());
    }

    @Test
    void unknownTool_recordsErrorAndContinues() {
        AgentResult r = service(new FakeLlm(
                "ACTION: no_such_tool\nACTION_INPUT: {}",
                "ANSWER: 最终答案"
        )).run("问题", List.of(), null);

        assertEquals("最终答案", r.getAnswer());
        // 轨迹 = [running, error] 两个独立事件
        assertEquals("running", r.getTrace().get(0).getStatus());
        assertEquals("error", r.getTrace().get(1).getStatus());
        assertEquals("no_such_tool", r.getTrace().get(0).getTool());
    }

    @Test
    void toolException_recordsErrorAndContinues() {
        AgentResult r = service(new FakeLlm(
                "ACTION: boom\nACTION_INPUT: {}",
                "ANSWER: 容错答案"
        )).run("问题", List.of(), null);

        assertEquals("容错答案", r.getAnswer());
        assertEquals("running", r.getTrace().get(0).getStatus());
        assertEquals("error", r.getTrace().get(1).getStatus());
        assertNotNull(r.getTrace().get(1).getSummary());
    }

    @Test
    void stepLimit_fallsBackWithReason() {
        // 5 步全部要求调工具 → 到达 MAX_STEPS，兜底用已收集内容作答
        AgentResult r = service(new FakeLlm(
                "ACTION: echo\nACTION_INPUT: {\"query\": \"a\"}",
                "ACTION: echo\nACTION_INPUT: {\"query\": \"b\"}",
                "ACTION: echo\nACTION_INPUT: {\"query\": \"c\"}",
                "ACTION: echo\nACTION_INPUT: {\"query\": \"d\"}",
                "ACTION: echo\nACTION_INPUT: {\"query\": \"e\"}"
        )).run("问题", List.of(), null);

        assertNotNull(r.getAnswer());
        assertTrue(r.getAnswer().contains("echo 工具返回的片段内容"), "兜底答案应包含已收集内容");
        assertNotNull(r.getFallbackReason());
        assertTrue(r.getFallbackReason().contains("步数上限"));
        assertTrue(r.getSources().size() >= 1);
    }

    @Test
    void plainConversationalAnswer_adoptedDirectly() {
        // 模型没按 ReAct 格式输出、直接以对话口吻回答（无 THOUGHT/ACTION/ANSWER 标记）
        // → 应直接采纳为最终答案，而不是判格式错误后走兜底（此前会误报"未找到相关内容"）
        AgentResult r = service(new FakeLlm(
                "Vue3 的响应式和 Java 反射都属于运行时元编程，都通过拦截机制注入行为，让程序能观察和干预自身的运行。"
        )).run("元编程异同", List.of(), null);

        assertEquals("Vue3 的响应式和 Java 反射都属于运行时元编程，都通过拦截机制注入行为，让程序能观察和干预自身的运行。", r.getAnswer());
        assertEquals(0, r.getSteps());
        assertNull(r.getFallbackReason());
    }

    @Test
    void thoughtOnlyOutput_retriesFormatThenAnswers() {
        // 只输出 THOUGHT（有标记）没有动作/答案 → 判格式错误，提示重试后拿到规范 ANSWER
        AgentResult r = service(new FakeLlm(
                "THOUGHT: 我需要先检索知识库确认",
                "ANSWER: 规范格式的最终答案"
        )).run("问题", List.of(), null);

        assertEquals("规范格式的最终答案", r.getAnswer());
        assertEquals(0, r.getSteps());
    }

    @Test
    void duplicateChunks_deduplicatedInSources() {
        // echo 两次返回同一 chunk → sources 只保留 1 条
        AgentResult r = service(new FakeLlm(
                "ACTION: echo\nACTION_INPUT: {\"query\": \"a\"}",
                "ACTION: echo\nACTION_INPUT: {\"query\": \"b\"}",
                "ANSWER: 答案"
        )).run("问题", List.of(), null);

        assertEquals(1, r.getSources().size(), "重复片段应按 docId#chunkIndex 去重");
        assertEquals(2, r.getSteps());
    }

    @Test
    void prematureNotFound_forcesRetrievalThenReanswers() {
        // 模型一步未检索就断言"未找到/未检索到"（如只列了文档名）→ 应强制补语义检索，
        // 把命中回填后再让模型重新回答，而不是把"未找到"当最终答案
        when(retrieval.retrieve(anyString(), anyInt())).thenReturn(List.of(hit("补充检索到的元编程对比内容")));
        AgentResult r = service(new FakeLlm(
                "ANSWER: 知识库中未检索到任何与 Vue3 响应式、Java 反射或元编程相关的文档内容，无法基于现有资料回答。",
                "ANSWER: 基于补充检索，Vue3 响应式与 Java 反射都是运行时元编程。"
        )).run("元编程异同", List.of(), null);

        assertEquals("基于补充检索，Vue3 响应式与 Java 反射都是运行时元编程。", r.getAnswer());
        assertTrue(r.getSources().size() >= 1, "补充检索的片段应进入引用");
        assertEquals("补充检索到的元编程对比内容", r.getSources().get(0).getContent());
        assertNull(r.getFallbackReason());
    }

    @Test
    void prematureNotFound_plainTextForm_forcesRetrieval() {
        // 同上，但模型是以"无格式纯文本"形式过早放弃（走直接回答采纳分支）→ 同样触发补充检索
        when(retrieval.retrieve(anyString(), anyInt())).thenReturn(List.of(hit("补充内容")));
        AgentResult r = service(new FakeLlm(
                "知识库中未检索到相关内容，无法回答。",
                "ANSWER: 补充检索后给出的答案。"
        )).run("问题", List.of(), null);

        assertEquals("补充检索后给出的答案。", r.getAnswer());
        assertTrue(r.getSources().size() >= 1);
    }

    @Test
    void notFoundAnswer_whenRetrievalEmpty_keptAsIs() {
        // 补充检索也拿不到任何内容 → 保留模型的"未找到"回答，不强行编造
        when(retrieval.retrieve(anyString(), anyInt())).thenReturn(List.of());
        AgentResult r = service(new FakeLlm(
                "ANSWER: 知识库中未检索到任何相关内容，无法回答。"
        )).run("问题", List.of(), null);

        assertTrue(r.getAnswer().contains("未检索到"));
        assertTrue(r.getSources().isEmpty());
    }

    @Test
    void stripReActMarkers_removesMarkers_keepsAnswerContent() {
        // 流式重生成可能带 THOUGHT/ANSWER 前缀:THOUGHT/ACTION 整行丢弃,ANSWER 去前缀保留内容
        assertEquals("我是郑梓恒。\n[1][2]", AgentChatService.stripReActMarkers(
                "THOUGHT: 信息足够\nANSWER: 我是郑梓恒。\n[1][2]"));
        assertEquals("单行答案", AgentChatService.stripReActMarkers("ANSWER: 单行答案"));
        assertNull(AgentChatService.stripReActMarkers("THOUGHT: x\nACTION: retrieve"));
        assertFalse(AgentChatService.containsReActMarker("我是郑梓恒。"));
        assertTrue(AgentChatService.containsReActMarker("ANSWER: 我是郑梓恒。"));
    }
}
