package me.zhengziheng.agent.service.agent;

import me.zhengziheng.agent.dto.request.ChatTurn;
import me.zhengziheng.agent.dto.response.AgentResult;
import me.zhengziheng.agent.dto.response.AgentStepEvent;
import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.service.HybridRetrievalService;
import me.zhengziheng.agent.service.LlmClient;
import me.zhengziheng.agent.service.LlmMessage;
import me.zhengziheng.agent.service.PersonaPrompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent 化问答核心（P1 能力升维）：ReAct 风格「检索 → 反思 → 再检索 → 生成」循环。
 *
 * 循环（每轮一步）：
 *   LLM 输出 → 解析 THOUGHT / ACTION / ACTION_INPUT / ANSWER
 *    - ANSWER    → 结束循环，输出最终答案
 *    - ACTION    → AgentToolRegistry 分派工具执行，结果按「全局编号」回填上下文，继续下一轮
 *
 * 引用溯源保证：所有工具命中的片段按顺序收集到全局列表（去重），回填上下文的编号即全局编号，
 * 因此最终答案里的 [N] 与返回的 sources[N-1] 严格一一对应，可点击溯源。
 *
 * 保护：MAX_STEPS=5 步上限；LLM 输出无法解析时追问一次格式；最终未收敛则兜底用已收集内容拼答案。
 */
@Service
public class AgentChatService {

    private static final Logger log = LoggerFactory.getLogger(AgentChatService.class);

    /** 步数上限：防止 Agent 无限循环烧 token */
    private static final int MAX_STEPS = 5;

    /** 收集的引用片段上限（控制上下文体积） */
    private static final int MAX_CHUNKS = 30;

    /** "未找到/未检索到"类过早放弃的识别：命中且从未检索过时，强制补一次语义检索再作答 */
    private static final Pattern NOT_FOUND_PATTERN = Pattern.compile(
            "未找到|未检索到|没有.*(相关|对应|关于).*|无法.*(回答|给出|对比)|不存在|未涵盖");

    private final AgentToolRegistry toolRegistry;
    private final LlmClient llmClient;
    private final HybridRetrievalService hybridRetrievalService;
    private final PersonaPrompts personaPrompts;

    public AgentChatService(AgentToolRegistry toolRegistry,
                            LlmClient llmClient,
                            HybridRetrievalService hybridRetrievalService,
                            PersonaPrompts personaPrompts) {
        this.toolRegistry = toolRegistry;
        this.llmClient = llmClient;
        this.hybridRetrievalService = hybridRetrievalService;
        this.personaPrompts = personaPrompts;
    }

    /**
     * 运行 Agent 循环（非流式版本，兼容既有调用/测试）。
     */
    public AgentResult run(String question, List<ChatTurn> history, Consumer<AgentStepEvent> onStep) {
        return run(question, history, onStep, null);
    }

    /**
     * 运行 Agent 循环。
     *
     * @param question 用户问题
     * @param history  多轮对话历史（可为空）
     * @param onStep   每步工具执行事件回调（SSE 推送用，可为 null）
     * @param onDelta  最终答案增量回调（SSE 逐字流式用，可为 null；null 时与非流式一致）
     * @return Agent 结果（答案 + 引用 + 轨迹）
     */
    public AgentResult run(String question, List<ChatTurn> history, Consumer<AgentStepEvent> onStep, Consumer<String> onDelta) {
        AgentResult result = new AgentResult();
        List<ChunkSearchResult> collected = new ArrayList<>();
        List<AgentStepEvent> trace = new ArrayList<>();

        // 1) 系统提示：角色约束 + 工具清单 + 文档总览（一次给出，省得模型先 list）
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage("system", buildSystemPrompt()));
        if (history != null) {
            for (ChatTurn t : history) {
                if (t.getContent() != null && !t.getContent().isBlank()) {
                    messages.add(new LlmMessage(t.getRole(), t.getContent()));
                }
            }
        }
        messages.add(new LlmMessage("user", "【问题】" + question));

        // 2) ReAct 循环
        String finalAnswer = null;
        int step = 0;
        int toolSteps = 0;
        boolean formatRetried = false;
        for (; step < MAX_STEPS; step++) {
            String raw;
            try {
                raw = llmClient.generate(messages, "agent");
            } catch (Exception e) {
                log.warn("Agent LLM 调用失败: {}", e.getMessage());
                break;
            }
            if (raw == null || raw.isBlank()) {
                break;
            }

            ParsedAction action = ParsedAction.parse(raw);
            if (action == null) {
                // 模型没按 ReAct 格式输出：
                // ① 若明显是"直接回答"形态（无 THOUGHT/ACTION 标记的成段文本），直接采纳为最终答案，
                //    避免"明明能答却因格式不合而放弃"（此前表现为 Agent 模式误报"未找到相关内容"）；
                // ② 否则（如只有 THOUGHT 没有动作）提示格式重试一次，仍失败则终止走兜底。
                boolean hasMarkers = raw.contains("THOUGHT") || raw.contains("ACTION") || raw.contains("ANSWER");
                if (!hasMarkers && raw.trim().length() > 10) {
                    // 未检索拦截：全程没检索过就"直接回答" → 强制补一次语义检索再作答（防模型凭常识编造）
                    if (collected.isEmpty() && forceRetrieve(question, messages, collected)) {
                        messages.add(new LlmMessage("assistant", raw));
                        messages.add(new LlmMessage("user",
                                "以上是补充检索到的知识库内容。请基于这些片段重新回答用户问题；若确实与问题无关，再说明未找到。"));
                        continue;
                    }
                    String streamed = streamFinalAnswer(messages, onDelta);
                    finalAnswer = (streamed != null) ? streamed : raw.trim();
                    break;
                }
                messages.add(new LlmMessage("assistant", raw));
                if (!formatRetried) {
                    formatRetried = true;
                    messages.add(new LlmMessage("user",
                            "输出格式不正确。请严格按下面格式输出（一次只输出一种）：\n"
                                    + "THOUGHT: 判断信息是否足够\n"
                                    + "ACTION: 工具名\n"
                                    + "ACTION_INPUT: {\"参数\": \"值\"}\n"
                                    + "或信息足够时直接：ANSWER: 最终答案"));
                } else {
                    // 第二次仍不合格式：终止，用已收集内容兜底
                    break;
                }
                continue;
            }

            if (action.answer != null) {
                // 未检索拦截：从未检索就输出 ANSWER → 强制补检索后重新回答（防凭常识编造）
                if (collected.isEmpty() && forceRetrieve(question, messages, collected)) {
                    messages.add(new LlmMessage("assistant", action.answer));
                    messages.add(new LlmMessage("user",
                            "以上是补充检索到的知识库内容。请基于这些片段重新回答用户问题；若确实与问题无关，再说明未找到。"));
                    continue;
                }
                String streamed = streamFinalAnswer(messages, onDelta);
                finalAnswer = (streamed != null) ? streamed : action.answer;
                break;
            }

            // 工具调用
            AgentTool tool = toolRegistry.get(action.toolName);
            AgentStepEvent running = new AgentStepEvent();
            running.setStep(step + 1);
            running.setTool(action.toolName);
            running.setArgs(action.toolInput);
            running.setStatus("running");
            trace.add(running);
            if (onStep != null) {
                onStep.accept(running);
            }
            toolSteps++;

            // 终态事件独立对象（running/done 若复用同一实例，SSE 侧会拿到被改写的最终状态）
            AgentStepEvent done = new AgentStepEvent();
            done.setStep(step + 1);
            done.setTool(action.toolName);
            done.setArgs(action.toolInput);
            String toolResultText;
            if (tool == null) {
                toolResultText = "（工具不存在：" + action.toolName + "，可用工具：" + availableToolNames() + "）";
                done.setStatus("error");
                done.setSummary("工具不存在");
            } else {
                try {
                    AgentToolResult r = tool.execute(action.toolInput == null ? Map.of() : action.toolInput);
                    // 收集片段（去重）→ 全局编号
                    int before = collected.size();
                    for (ChunkSearchResult c : r.getChunks()) {
                        addIfAbsent(collected, c);
                    }
                    toolResultText = buildGlobalContext(collected, before < collected.size());
                    done.setStatus("done");
                    done.setHits(r.getHits());
                    done.setSummary(r.getSummary());
                } catch (Exception e) {
                    toolResultText = "（工具执行出错：" + e.getMessage() + "）";
                    done.setStatus("error");
                    done.setSummary("执行出错");
                }
            }
            trace.add(done);
            if (onStep != null) {
                onStep.accept(done);
            }

            // 回填：assistant 的原始动作 + 工具结果
            messages.add(new LlmMessage("assistant", raw));
            messages.add(new LlmMessage("user", "【工具结果】\n" + toolResultText));
        }

        // 3) 收敛：未给出 ANSWER（步数耗尽 / LLM 失败）→ 用已收集内容兜底拼答案
        if (finalAnswer == null) {
            finalAnswer = fallbackAnswer(collected, question);
            result.setFallbackReason(step >= MAX_STEPS
                    ? "达到步数上限（" + MAX_STEPS + " 步），已用已检索内容作答"
                    : "Agent 未收敛，已用已检索内容作答");
        }

        result.setAnswer(finalAnswer);
        result.setSources(collected);
        result.setSteps(toolSteps);
        result.setTrace(trace);
        return result;
    }

    /** 系统提示词：人设 + 工具清单（即提示工程，决定 LLM 会不会/会不会用错工具） */
    private String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append(personaPrompts.personaPrefix())
                .append("你只能基于检索到的文档内容回答；知识库没有相关信息时如实说明，不要编造。\n")
                .append("【强制规则】回答任何问题前，第一步必须用 retrieve 工具检索知识库（输出 ACTION: retrieve），\n")
                .append("拿到候选人的真实经历后再作答；严禁在未检索的情况下凭常识或记忆直接输出 ANSWER。\n")
                .append("回答末尾用 [1][2] 这样的编号标注引用来源，编号对应【工具结果】里列出的片段编号。\n")
                .append("回答要简洁、准确。\n\n")
                .append("可用工具（一次只调用一个）：\n");
        for (AgentTool t : toolRegistry.all()) {
            sb.append("- ").append(t.name()).append("：").append(t.description()).append("\n")
                    .append("  参数：").append(t.parameters()).append("\n");
        }
        sb.append("\n严格按以下格式输出（一次只输出一种）：\n")
                .append("THOUGHT: 判断当前信息是否足够回答\n")
                .append("ACTION: 工具名\n")
                .append("ACTION_INPUT: {\"参数名\": \"值\"}\n")
                .append("或信息足够时：\n")
                .append("ANSWER: 最终答案");
        return sb.toString();
    }

    /** 按全局编号列出已收集片段（编号与最终 sources 一一对应，引用可溯源） */
    private String buildGlobalContext(List<ChunkSearchResult> collected, boolean hasNew) {
        if (collected.isEmpty()) {
            return "（本次没有命中新内容）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < collected.size(); i++) {
            ChunkSearchResult c = collected.get(i);
            sb.append("[").append(i + 1).append("] 来自《").append(c.getDocName() == null ? "?" : c.getDocName())
                    .append("》(docId=").append(c.getDocId() == null ? "?" : c.getDocId()).append(")「")
                    .append(c.getSection() == null ? "" : c.getSection()).append("」：\n")
                    .append(c.getContent()).append("\n\n");
        }
        if (!hasNew) {
            sb.append("（以上是已收集的全部片段，没有新增；如仍不足，请换检索词或换文档）");
        }
        return sb.toString();
    }

    /** 判断模型输出是否为"未找到/未检索到"式的过早放弃（此时往往还没真正检索） */
    private boolean looksLikeNotFound(String text) {
        return text != null && NOT_FOUND_PATTERN.matcher(text).find();
    }

    /**
     * 强制补充一次语义检索（模型过早断言"未找到"时的兜底）：
     * 直接调混合检索拿 Top-K 片段并按全局编号回填上下文；有命中返回 true。
     */
    private boolean forceRetrieve(String question, List<LlmMessage> messages, List<ChunkSearchResult> collected) {
        try {
            List<ChunkSearchResult> hits = hybridRetrievalService.retrieve(question, 5);
            if (hits == null || hits.isEmpty()) {
                return false;
            }
            for (ChunkSearchResult c : hits) {
                addIfAbsent(collected, c);
            }
            messages.add(new LlmMessage("user", "【补充检索结果】\n" + buildGlobalContext(collected, true)));
            return true;
        } catch (Exception e) {
            log.warn("Agent 补充检索失败: {}", e.getMessage());
            return false;
        }
    }

    /** 兜底答案：把已收集片段拼接为答案（不崩溃、有引用） */
    private String fallbackAnswer(List<ChunkSearchResult> collected, String question) {
        if (collected.isEmpty()) {
            return "知识库中未找到与「" + question + "」相关的内容，请换种问法，或先上传相关笔记。";
        }
        StringBuilder sb = new StringBuilder("已检索到以下相关内容（Agent 未能自动组织答案，供参考）：\n\n");
        for (int i = 0; i < collected.size(); i++) {
            ChunkSearchResult c = collected.get(i);
            sb.append("[").append(i + 1).append("] ").append(c.getContent()).append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * 最终答案流式生成：用当前 messages 重新流式生成答案，逐字转发 onDelta。
     * 与循环内 generate() 解析出的答案可能略有差异（非确定性），以流式累计文本为准（所见即所存）。
     * 失败（LLM 异常/空输出）返回 null，调用方回退已解析答案。
     */
    private String streamFinalAnswer(List<LlmMessage> messages, Consumer<String> onDelta) {
        try {
            StringBuilder acc = new StringBuilder();
            llmClient.streamGenerate(messages, delta -> {
                acc.append(delta);
                if (onDelta != null) {
                    onDelta.accept(delta);
                }
            }, "agent");
            String answer = acc.toString();
            return (answer == null || answer.isBlank()) ? null : answer.trim();
        } catch (Exception e) {
            log.warn("Agent 最终答案流式生成失败，回退已解析答案: {}", e.getMessage());
            return null;
        }
    }

    /** 去重收集：docId#chunkIndex 维度 */
    private void addIfAbsent(List<ChunkSearchResult> collected, ChunkSearchResult c) {
        if (collected.size() >= MAX_CHUNKS) {
            return;
        }
        String key = c.getDocId() + "#" + c.getChunkIndex();
        for (ChunkSearchResult exist : collected) {
            if ((exist.getDocId() + "#" + exist.getChunkIndex()).equals(key)) {
                return;
            }
        }
        collected.add(c);
    }

    private String availableToolNames() {
        return toolRegistry.all().stream().map(AgentTool::name).reduce((a, b) -> a + ", " + b).orElse("");
    }

    /** LLM 输出的动作解析结果 */
    static final class ParsedAction {
        String toolName;
        Map<String, Object> toolInput;
        String answer;

        /** 解析 THOUGHT/ACTION/ACTION_INPUT/ANSWER；无法识别返回 null */
        static ParsedAction parse(String raw) {
            ParsedAction p = new ParsedAction();
            StringBuilder answer = new StringBuilder();
            boolean inAnswer = false;
            for (String line : raw.split("\n")) {
                String t = line.trim();
                if (t.startsWith("ANSWER")) {
                    inAnswer = true;
                    String a = stripPrefix(t, "ANSWER");
                    if (!a.isEmpty()) {
                        answer.append(a).append("\n");
                    }
                } else if (inAnswer) {
                    answer.append(t).append("\n");
                } else if (t.startsWith("ACTION_INPUT")) {
                    p.toolInput = parseJson(stripPrefix(t, "ACTION_INPUT"));
                } else if (t.startsWith("ACTION")) {
                    p.toolName = stripPrefix(t, "ACTION").split("\\s+")[0];
                }
                // THOUGHT 仅作推理记录，不需要回填解析
            }
            if (answer.length() > 0) {
                p.answer = answer.toString().trim();
                return p;
            }
            if (p.toolName != null && !p.toolName.isEmpty()) {
                return p;
            }
            return null;
        }

        /** 去掉前缀后剩余内容：同时清掉可能紧跟着的半角/全角冒号与空白（ANSWER: xxx / ACTION：xxx） */
        static String stripPrefix(String line, String prefix) {
            String rest = line.substring(prefix.length()).trim();
            return rest.replaceFirst("^[:：]\\s*", "").trim();
        }

        /** 宽松 JSON 解析：容忍 ``` 围栏、引号差异；解析失败返回空 Map（工具会兜底报缺参） */
        static Map<String, Object> parseJson(String s) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (s == null || s.isBlank()) {
                return map;
            }
            String clean = s.replace("```", "").replace("json", "").trim();
            int start = clean.indexOf('{');
            int end = clean.lastIndexOf('}');
            if (start >= 0 && end > start) {
                clean = clean.substring(start, end + 1);
            }
            Pattern kv = Pattern.compile("\"([^\"]+)\"\\s*[:：]\\s*\"?([^\",}]+)\"?");
            Matcher m = kv.matcher(clean);
            while (m.find()) {
                map.put(m.group(1).trim(), m.group(2).trim());
            }
            if (map.isEmpty()) {
                // 尝试标准 JSON
                try {
                    com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                    var parsed = om.readValue(clean, Map.class);
                    if (parsed != null) {
                        return parsed;
                    }
                } catch (Exception ignore) {
                    // 保持空 Map
                }
            }
            return map;
        }
    }
}
