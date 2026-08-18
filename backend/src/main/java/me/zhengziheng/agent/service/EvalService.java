package me.zhengziheng.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.dto.response.EvalItemResult;
import me.zhengziheng.agent.dto.response.EvalReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评测服务（M3 质量闭环核心）。
 *
 * 流程：加载 eval/questions.json 评测集 → 对每条问题：
 *   1) 调 HybridRetrievalService 召回 Top-K；
 *   2) 算 recallAtK = 期望要点(expectedPoints)在 Top-K 片段中的覆盖率（衡量"检索是否拉到相关材料"）；
 *   3) 算 docRecall = 应命中文档(expectedDocs)命中率（严格指标，未配置文档名则为 null）；
 *   4) 有 LLM key 时：基于召回上下文生成答案 → 拒答检测(reject 类) + LLM-as-judge 忠实度；
 *      无 key 时忠实度置 null 并标记 skipped（评测集仍能量化检索召回）；
 *   5) 记录耗时。
 * 最后聚合为 EvalReport（整体/分类型召回率、忠实度、平均耗时），内存存最近一次供 /api/eval/report 取。
 *
 * 指标口径详见 EvalReport / EvalItemResult 的字段注释。
 */
@Service
public class EvalService {

    private final HybridRetrievalService hybridRetrievalService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final int defaultTopK;

    /** 评测集（问题三元组） */
    private final List<Map<String, Object>> dataset;
    /** 最近一次评测报告（单实例演示足够） */
    private EvalReport lastReport;

    public EvalService(HybridRetrievalService hybridRetrievalService,
                       LlmClient llmClient,
                       ObjectMapper objectMapper,
                       @Value("${eval.top-k:6}") int defaultTopK) {
        this.hybridRetrievalService = hybridRetrievalService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.defaultTopK = defaultTopK;
        this.dataset = loadDataset();
    }

    private List<Map<String, Object>> loadDataset() {
        try {
            ClassPathResource res = new ClassPathResource("eval/questions.json");
            try (InputStream in = res.getInputStream()) {
                return objectMapper.readValue(in, new TypeReference<List<Map<String, Object>>>() {
                });
            }
        } catch (Exception e) {
            // 评测集缺失不应阻断启动，run() 会返回空报告
            return List.of();
        }
    }

    /** 跑完整评测集（默认混合检索），返回并缓存报告 */
    public EvalReport run(int topK) {
        return run(topK, "hybrid");
    }

    /** 跑完整评测集，指定检索模式（hybrid / vector），返回并缓存报告 */
    public EvalReport run(int topK, String mode) {
        int k = topK > 0 ? topK : defaultTopK;
        boolean faithfulnessEnabled = llmClient.available();
        List<EvalItemResult> items = new ArrayList<>();
        for (Map<String, Object> q : dataset) {
            items.add(evalOne(q, k, faithfulnessEnabled, mode));
        }
        EvalReport report = aggregate(items, faithfulnessEnabled, mode);
        this.lastReport = report;
        return report;
    }

    public EvalReport getLastReport() {
        return lastReport;
    }

    @SuppressWarnings("unchecked")
    private EvalItemResult evalOne(Map<String, Object> q, int k, boolean faithfulnessEnabled, String mode) {
        String id = String.valueOf(q.get("id"));
        String question = String.valueOf(q.get("question"));
        String type = String.valueOf(q.getOrDefault("type", "fact"));
        boolean expectRefusal = Boolean.TRUE.equals(q.get("expectRefusal"));
        List<String> expectedPoints = (List<String>) q.getOrDefault("expectedPoints", List.of());
        List<String> expectedDocs = (List<String>) q.getOrDefault("expectedDocs", List.of());

        EvalItemResult r = new EvalItemResult();
        r.setId(id);
        r.setQuestion(question);
        r.setType(type);

        long start = System.currentTimeMillis();
        List<ChunkSearchResult> hits;
        try {
            hits = hybridRetrievalService.retrieve(question, k, mode);
        } catch (Exception e) {
            // 单条检索失败不应中断整轮评测
            r.setNote("检索失败: " + e.getMessage());
            r.setRecallAtK(null);
            r.setDocRecall(null);
            r.setLatencyMs(System.currentTimeMillis() - start);
            return r;
        }

        // 1) 召回：期望要点覆盖率（关键词在 Top-K 片段内容中的命中比例）
        if (expectedPoints != null && !expectedPoints.isEmpty()) {
            String joined = hits.stream()
                    .map(c -> c.getContent() == null ? "" : c.getContent())
                    .collect(Collectors.joining("\n"))
                    .toLowerCase();
            int hit = 0;
            for (String p : expectedPoints) {
                if (joined.contains(p.toLowerCase())) {
                    hit++;
                }
            }
            r.setRecallAtK((double) hit / expectedPoints.size());
        } else {
            r.setRecallAtK(null);
        }

        // 2) 严格文档命中率（文档名对齐时为强指标）
        if (expectedDocs != null && !expectedDocs.isEmpty()) {
            Set<String> retrievedDocs = hits.stream()
                    .map(c -> c.getDocName() == null ? "" : c.getDocName())
                    .collect(Collectors.toSet());
            int hit = 0;
            for (String d : expectedDocs) {
                final String target = d;
                if (retrievedDocs.stream().anyMatch(name -> name.contains(target) || target.contains(name))) {
                    hit++;
                }
            }
            r.setDocRecall((double) hit / expectedDocs.size());
        } else {
            r.setDocRecall(null);
        }

        // 3) 生成 + 拒答检测 + 忠实度（需 LLM key）
        if (faithfulnessEnabled) {
            try {
                StringBuilder ctx = new StringBuilder();
                for (int i = 0; i < hits.size(); i++) {
                    ChunkSearchResult s = hits.get(i);
                    ctx.append("【来源").append(i + 1).append("】来自《").append(s.getDocName())
                            .append("》的「").append(s.getSection()).append("」章节：\n")
                            .append(s.getContent()).append("\n\n");
                }
                List<LlmMessage> msgs = List.of(
                        new LlmMessage("system", "你是一个严谨的知识库问答助手。只能根据下面提供的【上下文】回答用户问题；"
                                + "如果上下文没有相关信息，就如实说「知识库中未找到相关内容」。回答要简洁、准确，并在末尾列出引用来源（文档名与章节）。"),
                        new LlmMessage("user", "【上下文】\n" + ctx + "\n【问题】" + question)
                );
                String answer = llmClient.generate(msgs);
                r.setAnswer(answer);
                if (answer != null && "reject".equals(type)) {
                    r.setRefusalDetected(isRefusal(answer));
                }
                r.setFaithfulness(answer == null ? null : judgeFaithfulness(ctx.toString(), answer));
            } catch (Exception e) {
                r.setNote("生成/忠实度失败: " + e.getMessage());
            }
        } else {
            r.setNote("忠实度跳过: 未配置 LLM key");
        }

        r.setLatencyMs(System.currentTimeMillis() - start);
        return r;
    }

    /** LLM-as-judge：回答是否全部基于上下文。返回 1.0(YES) / 0.0(NO) / null(无法判定) */
    private Double judgeFaithfulness(String context, String answer) {
        try {
            List<LlmMessage> msgs = List.of(
                    new LlmMessage("system", "你是严格的评测裁判。下面给出【上下文】和【回答】。请判断回答中的每个事实性陈述是否都能在上下文中找到依据。只输出 YES 或 NO，并以 '|' 分隔给出一句简短理由。"),
                    new LlmMessage("user", "【上下文】\n" + context + "\n【回答】\n" + answer)
            );
            String raw = llmClient.generate(msgs);
            if (raw == null) {
                return null;
            }
            String up = raw.trim().toUpperCase();
            if (up.startsWith("YES")) {
                return 1.0;
            }
            if (up.startsWith("NO")) {
                return 0.0;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isRefusal(String answer) {
        String a = answer.toLowerCase();
        return a.contains("未找到") || a.contains("未涵盖") || a.contains("没有相关信息")
                || a.contains("不在知识库") || a.contains("无法回答") || a.contains("未包含")
                || a.contains("知识库中未");
    }

    private EvalReport aggregate(List<EvalItemResult> items, boolean faithfulnessEnabled, String mode) {
        EvalReport report = new EvalReport();
        report.setRunAt(OffsetDateTime.now().toString());
        report.setTotal(items.size());
        report.setMode(mode);
        report.setFaithfulnessSkipped(!faithfulnessEnabled);

        Map<String, List<Double>> byType = new LinkedHashMap<>();
        List<Double> allRecall = new ArrayList<>();
        for (EvalItemResult it : items) {
            if (it.getRecallAtK() != null) {
                allRecall.add(it.getRecallAtK());
                byType.computeIfAbsent(it.getType(), x -> new ArrayList<>()).add(it.getRecallAtK());
            }
        }
        report.setRecallAtK(mean(allRecall));
        Map<String, Double> recallByType = new LinkedHashMap<>();
        byType.forEach((t, list) -> recallByType.put(t, mean(list)));
        report.setRecallByType(recallByType);

        List<Double> docList = items.stream().map(EvalItemResult::getDocRecall)
                .filter(Objects::nonNull).collect(Collectors.toList());
        report.setDocRecall(docList.isEmpty() ? null : mean(docList));

        List<Double> faith = items.stream().map(EvalItemResult::getFaithfulness)
                .filter(Objects::nonNull).collect(Collectors.toList());
        report.setFaithfulness(faith.isEmpty() ? null : mean(faith));

        double avg = items.stream().mapToLong(EvalItemResult::getLatencyMs).average().orElse(0);
        report.setAvgLatencyMs((long) avg);
        report.setPerItem(items);
        return report;
    }

    private Double mean(List<Double> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
