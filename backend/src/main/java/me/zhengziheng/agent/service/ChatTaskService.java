package me.zhengziheng.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengziheng.agent.dto.request.ChatTurn;
import me.zhengziheng.agent.dto.response.AgentResult;
import me.zhengziheng.agent.dto.response.AgentStepEvent;
import me.zhengziheng.agent.dto.response.ChatHistoryVO;
import me.zhengziheng.agent.dto.response.ChatResponse;
import me.zhengziheng.agent.dto.response.ChatTaskResult;
import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.entity.QaLog;
import me.zhengziheng.agent.mapper.QaLogMapper;
import me.zhengziheng.agent.service.agent.AgentChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 问答任务服务（M2 流式生成 + 多轮对话记忆）。
 * - submit：生成 taskId 立即返回，提交线程池执行「(追问改写) → 检索 → LLM 流式生成」，写入内存任务表；
 * - 线程池：core=4 / max=8 + 有界队列 100，队列满时 CallerRunsPolicy 背压（不丢任务、天然限流）；
 * - TTL 清理：任务带过期时间（执行中 3 分钟 / 终态 10 分钟），清扫线程每 60s 回收，get() 惰性删除兜底，
 *   杜绝 tasks / emitters 两个 Map 只增不减的内存泄漏；
 * - 多轮记忆：请求携带 history（之前的问答），后台把历史并入 LLM 提示词（助手能理解"那它/再讲讲"等指代），
 *   并在 LLM 可用时对追问做「查询改写」，把省略所指补全成独立检索词，提升召回命中；
 * - 落库：问答完成后写一条 qa_log（含引用来源），供 GET /api/chat/history 跨设备回溯；
 * - SSE：前端连 /chat/stream 时注册 SseEmitter，后台每产生一段文本就推送增量（打字机效果），
 *   结束推送 done 事件（携带 sources 与完整 answer）；连接时若已有累积文本会先 flush 历史，避免丢失。
 * - 降级：未配置 LLM key 时退回 M1 抽取式（直接拼接 Top-K 片段）。
 * 内存任务表仅适用于单实例演示；多实例生产应换 Redis/数据库存储任务状态。
 */
@Service
public class ChatTaskService {

    private static final Logger log = LoggerFactory.getLogger(ChatTaskService.class);

    /** 进行中任务最长存活时间：超过说明执行超时（如 LLM 卡死），清扫器回收，避免永久 pending */
    private static final long RUNNING_TTL_MS = 3 * 60 * 1000L;
    /** 终态（完成/失败）任务保留窗口：给前端轮询与 SSE 重连留足取结果的时间 */
    private static final long DONE_TTL_MS = 10 * 60 * 1000L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 线程命名序号（rag-task-1, rag-task-2, ...），jstack 排查时一眼可辨 */
    private static final AtomicInteger THREAD_SEQ = new AtomicInteger(1);

    // taskId -> 任务（结果 + 过期时间戳）
    private final Map<String, TaskEntry> tasks = new ConcurrentHashMap<>();
    // taskId -> SSE 连接（前端订阅生成过程用）
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // 问答任务执行线程池：core=4 / max=8 / 有界队列 100 / 队列满时 CallerRunsPolicy 由提交线程直接执行（背压，不丢任务）
    private final ExecutorService executor = new ThreadPoolExecutor(
            4, 8, 60, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            r -> {
                Thread t = new Thread(r, "rag-task-" + THREAD_SEQ.getAndIncrement());
                t.setDaemon(true); // 守护线程：JVM 退出不阻塞
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy());

    // 过期任务清扫：每 60s 扫描一次，回收超时任务与残留 SSE 连接
    private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "task-sweeper");
        t.setDaemon(true);
        return t;
    });

    private final ChatService chatService;
    private final HybridRetrievalService hybridRetrievalService;
    private final LlmClient llmClient;
    private final QaLogMapper qaLogMapper;
    private final AgentChatService agentChatService;
    private final PersonaPrompts personaPrompts;
    private final SensitiveGuard sensitiveGuard;

    public ChatTaskService(ChatService chatService,
                           HybridRetrievalService hybridRetrievalService,
                           LlmClient llmClient,
                           QaLogMapper qaLogMapper,
                           AgentChatService agentChatService,
                           PersonaPrompts personaPrompts,
                           SensitiveGuard sensitiveGuard) {
        this.chatService = chatService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.llmClient = llmClient;
        this.qaLogMapper = qaLogMapper;
        this.agentChatService = agentChatService;
        this.personaPrompts = personaPrompts;
        this.sensitiveGuard = sensitiveGuard;
        // 启动过期清扫（固定 60s 周期；配合 get() 惰性删除双保险）
        sweeper.scheduleAtFixedRate(this::sweepExpired, 60, 60, TimeUnit.SECONDS);
    }

    /** 任务包装：任务结果 + 过期时间戳（进行中 3 分钟 / 终态 10 分钟） */
    private static class TaskEntry {
        final ChatTaskResult task = new ChatTaskResult();
        volatile long expireAt;
    }

    /** 提交问答：落库任务（pending）并提交线程池生成，返回 taskId 供前端订阅 SSE / 轮询 */
    public String submit(String question, int topK, List<ChatTurn> history, String username) {
        return submit(question, topK, history, username, null);
    }

    /** 提交问答（带模式）：mode=agent 走 Agent 化多步问答；其余走单轮检索-生成 */
    public String submit(String question, int topK, List<ChatTurn> history, String username, String mode) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        TaskEntry entry = new TaskEntry();
        entry.task.setStatus("pending");
        // 执行中任务给 3 分钟 TTL：超过说明执行超时（如 LLM 卡死），清扫器回收，不会永久 pending
        entry.expireAt = System.currentTimeMillis() + RUNNING_TTL_MS;
        tasks.put(taskId, entry);
        // 线程池异步执行（core=4/max=8 + 有界队列；队列满时 CallerRunsPolicy 让提交线程兜底执行）
        executor.execute(() -> runTask(taskId, entry.task, question, topK, history, username, mode));
        return taskId;
    }

    /** 按 taskId 取任务结果（轮询降级用）；已过期则惰性清理并返回 null */
    public ChatTaskResult get(String taskId) {
        TaskEntry entry = tasks.get(taskId);
        if (entry == null) {
            return null;
        }
        // 惰性删除：清扫线程没跑时，过期任务在读取路径上也能被回收
        if (System.currentTimeMillis() > entry.expireAt) {
            tasks.remove(taskId, entry);
            emitters.remove(taskId);
            return null;
        }
        return entry.task;
    }

    /** 任务进入终态：保留窗口从 3 分钟延长到 10 分钟，供前端轮询 / SSE 重连取结果 */
    private void markDone(String taskId) {
        TaskEntry entry = tasks.get(taskId);
        if (entry != null) {
            entry.expireAt = System.currentTimeMillis() + DONE_TTL_MS;
        }
    }

    /** 周期清扫：回收过期任务与残留 SSE 连接（配合 get() 的惰性删除双保险） */
    private void sweepExpired() {
        long now = System.currentTimeMillis();
        tasks.forEach((taskId, entry) -> {
            if (now > entry.expireAt) {
                // remove(key, value) 条件移除：即使期间 entry 被替换也不会误删新任务
                tasks.remove(taskId, entry);
                emitters.remove(taskId);
            }
        });
    }

    /** 容器关闭时优雅释放线程资源 */
    @PreDestroy
    public void shutdown() {
        sweeper.shutdown();
        executor.shutdown();
    }

    /** 当前用户问答历史（新→旧，最多 limit 条，1~100） */
    public List<ChatHistoryVO> history(String username, int limit) {
        int n = Math.max(1, Math.min(limit, 100));
        String name = (username == null || username.isBlank()) ? "anonymous" : username;
        List<QaLog> logs = qaLogMapper.selectRecent(name, n);
        List<ChatHistoryVO> out = new ArrayList<>();
        for (QaLog log : logs) {
            ChatHistoryVO vo = new ChatHistoryVO();
            vo.setQaId(log.getId());
            vo.setQuestion(log.getQuestion());
            vo.setAnswer(log.getAnswer());
            vo.setCreatedAt(log.getCreatedAt());
            vo.setSources(parseSources(log.getSources()));
            out.add(vo);
        }
        return out;
    }

    /** 前端连上 SSE 时注册 emitter：先 flush 已有累积文本（历史不丢），若已完成则直接推 done 并结束 */
    public void registerEmitter(String taskId, SseEmitter emitter) {
        emitters.put(taskId, emitter);
        TaskEntry entry = tasks.get(taskId);
        if (entry == null) {
            return;
        }
        ChatTaskResult task = entry.task;
        String existing = task.getAnswer();
        if (existing != null && !existing.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("delta")
                        .data(objectMapper.writeValueAsString(Map.of("type", "delta", "content", existing))));
            } catch (Exception ignore) {
                emitters.remove(taskId);
            }
        }
        if ("completed".equals(task.getStatus()) || "failed".equals(task.getStatus())) {
            flushDone(taskId, task, existing == null ? "" : existing);
        }
    }

    /** 移除 SSE 连接（前端断开 / 完成时调用） */
    public void removeEmitter(String taskId) {
        emitters.remove(taskId);
    }

    /** 任务执行：追问改写 → 混合检索 →（有 key）LLM 流式生成 /（无 key）抽取式降级 → 落库历史 */
    private void runTask(String taskId, ChatTaskResult task, String question, int topK,
                         List<ChatTurn> history, String username, String mode) {
        try {
            // Agent 模式：需要 LLM key（Agent 循环本质是多次 LLM 推理）；无 key 时降级单轮检索
            if ("agent".equalsIgnoreCase(mode) && llmClient.available()) {
                runAgentTask(taskId, task, question, history, username);
                return;
            }

            List<ChatTurn> turns = normalizeHistory(history);
            // 多轮记忆：仅当带历史且 LLM 可用时做追问改写，把省略所指补全为独立检索词
            String searchQuery = question;
            if (!turns.isEmpty() && llmClient.available()) {
                searchQuery = rewriteQuery(question, turns);
            }

            List<ChunkSearchResult> sources = hybridRetrievalService.retrieve(searchQuery, topK);
            // 检索元信息（mode / rerank 策略 / 各路命中数）从首条来源提取，供前端徽标展示
            if (sources != null && !sources.isEmpty() && sources.get(0).getRetrievalMeta() != null) {
                Map<String, Object> meta = sources.get(0).getRetrievalMeta();
                if (!searchQuery.equals(question)) {
                    meta.put("queryRewritten", true);
                    meta.put("searchQuery", searchQuery);
                }
                task.setRetrievalMeta(meta);
            }

            String answer;
            if (llmClient.available()) {
                // 拼装上下文：标注每个来源的文档名与章节，便于 LLM 引用
                StringBuilder ctx = new StringBuilder();
                for (int i = 0; i < sources.size(); i++) {
                    ChunkSearchResult s = sources.get(i);
                    ctx.append("【来源").append(i + 1).append("】来自《").append(s.getDocName())
                            .append("》的「").append(s.getSection()).append("」章节：\n")
                            .append(s.getContent()).append("\n\n");
                }

                List<LlmMessage> messages = new ArrayList<>();
                messages.add(new LlmMessage("system", personaPrompts.systemPrompt()));
                // 多轮对话记忆：把之前的问答原样带入提示词（截断保护在 normalizeHistory 完成）
                for (ChatTurn t : turns) {
                    messages.add(new LlmMessage(t.getRole(), t.getContent()));
                }
                messages.add(new LlmMessage("user", "【上下文】\n" + ctx + "\n【问题】" + question));

                StringBuilder acc = new StringBuilder();
                task.setStatus("generating");
                // 流式生成：每段增量累积进 answer 并实时推送给前端 SSE
                llmClient.streamGenerate(messages, delta -> {
                    acc.append(delta);
                    synchronized (task) {
                        task.setAnswer(acc.toString());
                    }
                    SseEmitter em = emitters.get(taskId);
                    if (em != null) {
                        try {
                            em.send(SseEmitter.event().name("delta")
                                    .data(objectMapper.writeValueAsString(Map.of("type", "delta", "content", delta))));
                        } catch (Exception e) {
                            emitters.remove(taskId);
                        }
                    }
                });

                String masked = sensitiveGuard.mask(acc.toString());
                task.setAnswer(masked);
                task.setSources(sources);
                task.setStatus("completed");
                answer = masked;
                // 先落库拿 qaId，再推 done 事件（事件携带 qaId，前端凭它提交点赞/点踩）
                task.setQaId(saveHistory(username, question, answer, sources));
                flushDone(taskId, task, answer);
                markDone(taskId);
            } else {
                // 无 LLM key：M1 抽取式降级（直接拼接片段）
                ChatResponse resp = chatService.ask(question, topK);
                task.setStatus("completed");
                task.setAnswer(resp.getAnswer());
                task.setSources(resp.getSources());
                task.setQueryEmbedded(resp.isQueryEmbedded());
                answer = resp.getAnswer();
                task.setQaId(saveHistory(username, question, answer, sources));
                flushDone(taskId, task, answer);
                markDone(taskId);
            }

            // 请求了 agent 但 LLM 不可用 → 标注降级，前端可提示"已退回单轮检索"
            if ("agent".equalsIgnoreCase(mode)) {
                Map<String, Object> meta = task.getRetrievalMeta();
                if (meta == null) {
                    meta = new LinkedHashMap<>();
                }
                meta.put("agentMode", "fallback-no-llm");
                task.setRetrievalMeta(meta);
            }
        } catch (Exception e) {
            task.setStatus("failed");
            task.setAnswer("抱歉，查询出错，请稍后重试。");
            flushError(taskId, e.getMessage() == null ? "未知错误" : e.getMessage());
            markDone(taskId);
        }
    }

    /** Agent 模式任务执行：多步 检索→反思→再检索→生成；每步工具调用实时推 agent_step 事件，最终答案逐字流式 */
    private void runAgentTask(String taskId, ChatTaskResult task, String question,
                              List<ChatTurn> history, String username) {
        task.setStatus("generating");
        StringBuilder streamed = new StringBuilder();
        AgentResult ar = agentChatService.run(question, history,
                evt -> pushAgentStep(taskId, evt),
                delta -> {
                    synchronized (task) {
                        streamed.append(delta);
                        task.setAnswer(streamed.toString());   // 轮询路径也能看到流式进度
                    }
                    pushDelta(taskId, delta);
                },
                thinking -> pushThinking(taskId, thinking));

        task.setAnswer(ar.getAnswer());
        task.setSources(ar.getSources() == null ? List.of() : ar.getSources());
        task.setAgentTrace(ar.getTrace());
        task.setStatus("completed");
        // 检索元信息：mode=agent + 步数 + 降级原因（前端徽标/调试）
        Map<String, Object> meta = ar.getRetrievalMeta() == null ? new LinkedHashMap<>() : ar.getRetrievalMeta();
        meta.put("mode", "agent");
        meta.put("steps", ar.getSteps());
        if (ar.getFallbackReason() != null) {
            meta.put("fallbackReason", ar.getFallbackReason());
        }
        task.setRetrievalMeta(meta);
        // 落库历史（拿 qaId 供反馈）后推 done
        task.setQaId(saveHistory(username, question, ar.getAnswer(), ar.getSources()));
        flushDone(taskId, task, ar.getAnswer());
        markDone(taskId);
    }

    /** 推送 agent_step 事件（与 delta 同通道，前端按 type 分发） */
    private void pushAgentStep(String taskId, AgentStepEvent evt) {
        SseEmitter em = emitters.get(taskId);
        if (em == null) {
            return;
        }
        try {
            em.send(SseEmitter.event().name("delta").data(objectMapper.writeValueAsString(evt)));
        } catch (Exception e) {
            emitters.remove(taskId);
        }
    }

    /** 推送答案增量文本（agent 模式最终答案流式） */
    private void pushDelta(String taskId, String delta) {
        SseEmitter em = emitters.get(taskId);
        if (em == null) {
            return;
        }
        try {
            em.send(SseEmitter.event().name("delta")
                    .data(objectMapper.writeValueAsString(Map.of("type", "delta", "content", delta))));
        } catch (Exception e) {
            emitters.remove(taskId);
        }
    }

    /** 推送思考过程增量（agent 模式最终答案的 reasoning_content，前端"思考"块用） */
    private void pushThinking(String taskId, String thinking) {
        SseEmitter em = emitters.get(taskId);
        if (em == null) {
            return;
        }
        try {
            em.send(SseEmitter.event().name("delta")
                    .data(objectMapper.writeValueAsString(Map.of("type", "thinking", "content", thinking))));
        } catch (Exception e) {
            emitters.remove(taskId);
        }
    }

    /** 清洗对话历史：只保留 user/assistant 且非空的内容，最多 10 轮，总长超限时丢弃最早轮次 */
    private List<ChatTurn> normalizeHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<ChatTurn> turns = new ArrayList<>();
        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            ChatTurn t = history.get(i);
            if (t == null || t.getContent() == null || t.getContent().isBlank()) {
                continue;
            }
            String role = t.getRole();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            turns.add(new ChatTurn(role, t.getContent().trim()));
        }
        // 总长保护：超过约 6000 字则从最早的轮次开始丢弃（避免历史撑爆上下文）
        int budget = 6000;
        for (int i = 0; i < turns.size() && budget > 0; i++) {
            budget -= turns.get(i).getContent().length();
        }
        if (budget < 0) {
            int keep = Math.max(1, turns.size() - (int) Math.ceil(-budget / 6000.0 * turns.size()));
            return new ArrayList<>(turns.subList(Math.max(0, turns.size() - keep), turns.size()));
        }
        return turns;
    }

    /** 追问改写：用最近对话把"那它的原理是什么"改写为自包含的独立检索查询；失败/无 LLM 时回退原问题 */
    private String rewriteQuery(String question, List<ChatTurn> turns) {
        try {
            StringBuilder recent = new StringBuilder();
            for (ChatTurn t : turns) {
                recent.append("用户: ").append(t.getRole().equals("user") ? t.getContent() : "")
                        .append("\n助手: ").append(t.getRole().equals("assistant") ? t.getContent() : "")
                        .append("\n");
            }
            List<LlmMessage> messages = List.of(
                    new LlmMessage("system",
                            "你是检索查询改写助手。用户正在进行知识库多轮问答，下面给出最近的对话历史。"
                                    + "请把【最新问题】改写为一个“自包含”的独立检索查询：补全“它/这个/那个/上述/再讲讲”等"
                                    + "省略与指代，使其脱离历史也能被搜索引擎理解。"
                                    + "只输出改写后的查询本身，不要任何解释、引号或前后缀；若最新问题已自包含，原样输出。"),
                    new LlmMessage("user", "【最近对话】\n" + recent + "\n【最新问题】" + question)
            );
            String rewritten = llmClient.generate(messages);
            String q = rewritten == null ? "" : rewritten.trim().replaceAll("^[\"'“”]|[\"'“”]$", "");
            if (!q.isBlank() && q.length() <= 200) {
                return q;
            }
        } catch (Exception ignore) {
            // 改写失败不阻塞主流程，回退原问题检索
        }
        return question;
    }

    /**
     * 问答完成落库（尽力而为）：成功返回 qa_log 自增主键（前端反馈用），失败返回 null 不影响主流程。
     */
    private Long saveHistory(String username, String question, String answer, List<ChunkSearchResult> sources) {
        try {
            QaLog log = new QaLog();
            log.setUsername((username == null || username.isBlank()) ? "anonymous" : username);
            log.setQuestion(question);
            log.setAnswer(answer == null ? "" : answer);
            if (sources != null && !sources.isEmpty()) {
                log.setSources(objectMapper.writeValueAsString(sources));
            }
            return qaLogMapper.insertLog(log);
        } catch (Exception e) {
            // 历史记录失败不影响问答主流程；但记 WARN 便于排查「为什么没有历史」
            // （常见原因：V2__qa_log.sql 迁移未生效、数据库未重启后端、sources JSON 非法）
            log.warn("问答历史落库失败（不影响本次回答）: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    /** 把 qa_log.sources 的 JSON 反序列化为引用来源列表（解析失败返回空列表） */
    private List<ChunkSearchResult> parseSources(String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isBlank()) {
            return List.of();
        }
        try {
            List<ChunkSearchResult> list = objectMapper.readValue(
                    sourcesJson, new TypeReference<List<ChunkSearchResult>>() {
                    });
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 推送 done 事件（携带 sources 与完整 answer）并结束 SSE */
    private void flushDone(String taskId, ChatTaskResult task, String answer) {
        SseEmitter em = emitters.get(taskId);
        if (em == null) {
            return;
        }
        try {
            em.send(SseEmitter.event().name("done").data(objectMapper.writeValueAsString(Map.of(
                    "type", "done",
                    "sources", task.getSources() == null ? List.of() : task.getSources(),
                    "answer", answer,
                    "qaId", task.getQaId(),
                    "agentTrace", task.getAgentTrace() == null ? List.of() : task.getAgentTrace(),
                    "retrievalMeta", task.getRetrievalMeta() == null ? Map.of() : task.getRetrievalMeta()
            ))));
            em.complete();
        } catch (Exception ignore) {
            emitters.remove(taskId);
        }
    }

    /** 推送 error 事件并结束 SSE */
    private void flushError(String taskId, String message) {
        SseEmitter em = emitters.get(taskId);
        if (em == null) {
            return;
        }
        try {
            em.send(SseEmitter.event().name("error")
                    .data(objectMapper.writeValueAsString(Map.of("type", "error", "message", message))));
            em.complete();
        } catch (Exception ignore) {
            emitters.remove(taskId);
        }
    }
}
