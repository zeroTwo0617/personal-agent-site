package me.zhengziheng.agent.controller;

import me.zhengziheng.agent.common.Result;
import me.zhengziheng.agent.dto.response.EvalReport;
import me.zhengziheng.agent.service.EvalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测接口（M3 质量闭环）。
 *
 *  GET/POST /api/eval/run?topK=6&mode=hybrid   跑评测集，返回并缓存本次 EvalReport（GET 方便浏览器直接打开测试）
 *  GET       /api/eval/report                 取最近一次评测报告（JSON）
 *  GET       /api/eval/report/html            评测报告 HTML 页面（浏览器友好，浅/深色自适应）
 *
 * 单实例演示用内存缓存最近报告；生产应落库以便历史对比。
 */
@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    /**
     * 跑评测集。同时支持 GET/POST，方便浏览器地址栏直接打开测试。
     *   mode=hybrid（默认）：向量+关键词+RRF 融合+Rerank
     *   mode=vector：纯向量基线（用于 before/after 对照，归因"关键词+RRF"带来的召回提升）
     * 浏览器：http://localhost:8080/api/eval/run?mode=hybrid&topK=6
     * curl  ：curl -X POST "http://localhost:8080/api/eval/run?mode=vector&topK=6"
     */
    @RequestMapping(value = "/run", method = {RequestMethod.POST, RequestMethod.GET})
    public Result<EvalReport> run(@RequestParam(defaultValue = "6") int topK,
                                  @RequestParam(defaultValue = "hybrid") String mode) {
        return Result.success(evalService.run(topK, mode));
    }

    @GetMapping("/report")
    public Result<EvalReport> report() {
        return Result.success(evalService.getLastReport());
    }

    /**
     * 评测报告 HTML 页面（浏览器友好，自带浅/深色样式）。
     * 先访问 /api/eval/run?mode=hybrid&topK=6 运行，再打开本页查看。
     */
    @GetMapping(value = "/report/html", produces = "text/html;charset=UTF-8")
    public String reportHtml() {
        return renderHtml(evalService.getLastReport());
    }

    // ===== HTML 渲染（自包含，无外部依赖；内容已做 HTML 转义）=====

    private String renderHtml(EvalReport r) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='zh-CN'><head><meta charset='utf-8'>")
          .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
          .append("<title>RAG 评测报告</title><style>").append(css()).append("</style></head><body><div class='wrap'>");

        if (r == null) {
            sb.append("<h1>RAG 知识库评测报告</h1>")
              .append("<p class='muted'>尚未运行评测。请先访问 <code>/api/eval/run?mode=hybrid&amp;topK=6</code> 运行，再刷新本页。</p>")
              .append("</div></body></html>");
            return sb.toString();
        }

        sb.append("<h1>RAG 知识库评测报告</h1>")
          .append("<div class='meta'>运行时间：").append(esc(r.getRunAt()))
          .append(" ｜ 模式：<b>").append(esc(r.getMode())).append("</b> ｜ 忠实度：")
          .append(r.isFaithfulnessSkipped() ? "未计算（无 LLM key）" : "已计算").append("</div>");

        sb.append("<div class='cards'>")
          .append(card("整体召回率 Recall@K", pct(r.getRecallAtK())))
          .append(card("严格文档命中率", pct(r.getDocRecall())))
          .append(card("忠实度 Faithfulness", pct(r.getFaithfulness())))
          .append(card("平均耗时", r.getAvgLatencyMs() == null ? "—" : (r.getAvgLatencyMs() + " ms")))
          .append(card("评测条数", String.valueOf(r.getTotal())))
          .append("</div>");

        if (r.getRecallByType() != null && !r.getRecallByType().isEmpty()) {
            sb.append("<h2>分类型召回率</h2><div class='tags'>");
            for (var e : r.getRecallByType().entrySet()) {
                sb.append("<span class='tag'>").append(esc(e.getKey())).append("：").append(pct(e.getValue())).append("</span>");
            }
            sb.append("</div>");
        }

        if (r.getPerItem() != null && !r.getPerItem().isEmpty()) {
            sb.append("<h2>逐条明细</h2><div class='tablewrap'><table>")
              .append("<thead><tr><th>#</th><th>类型</th><th>问题</th><th>Recall@K</th>")
              .append("<th>文档命中</th><th>拒答</th><th>忠实度</th><th>耗时</th><th>答案 / 备注</th></tr></thead><tbody>");
            for (var it : r.getPerItem()) {
                sb.append("<tr>")
                  .append("<td>").append(esc(it.getId())).append("</td>")
                  .append("<td><span class='t-").append(esc(it.getType())).append("'>").append(esc(it.getType())).append("</span></td>")
                  .append("<td class='q'>").append(esc(it.getQuestion())).append("</td>")
                  .append("<td>").append(pct(it.getRecallAtK())).append("</td>")
                  .append("<td>").append(pct(it.getDocRecall())).append("</td>")
                  .append("<td>").append(it.getRefusalDetected() == null ? "—" : (it.getRefusalDetected() ? "是" : "否")).append("</td>")
                  .append("<td>").append(faith(it.getFaithfulness())).append("</td>")
                  .append("<td>").append(it.getLatencyMs() == null ? "—" : (it.getLatencyMs() + "ms")).append("</td>")
                  .append("<td class='a'>");
                if (it.getAnswer() != null && !it.getAnswer().isEmpty()) {
                    sb.append(esc(truncate(it.getAnswer(), 240)));
                }
                if (it.getNote() != null && !it.getNote().isEmpty()) {
                    sb.append("<div class='note'>").append(esc(it.getNote())).append("</div>");
                }
                sb.append("</td></tr>");
            }
            sb.append("</tbody></table></div>");
        }

        sb.append("<p class='foot'>提示：先访问 <code>/api/eval/run?mode=vector&amp;topK=6</code> 跑纯向量基线，")
          .append("再访问 <code>/api/eval/run?mode=hybrid&amp;topK=6</code> 跑混合检索，对比两次「整体召回率」即得量化提升。</p>")
          .append("</div></body></html>");
        return sb.toString();
    }

    private String card(String k, String v) {
        return "<div class='card'><div class='k'>" + esc(k) + "</div><div class='v'>" + esc(v) + "</div></div>";
    }

    private String pct(Double v) {
        return v == null ? "—" : String.format("%.1f%%", v * 100);
    }

    private String faith(Double v) {
        if (v == null) return "—";
        return v >= 0.5 ? "忠实" : "编造";
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String css() {
        return """
            :root{color-scheme:light dark}
            *{box-sizing:border-box}
            body{margin:0;padding:32px;font-family:-apple-system,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;background:#f5f7fb;color:#1f2330;line-height:1.55}
            .wrap{max-width:1100px;margin:0 auto}
            h1{font-size:24px;margin:0 0 6px}
            h2{font-size:17px;margin:28px 0 12px;padding-left:10px;border-left:3px solid #5b8cff}
            .meta{color:#7a8194;font-size:13px;margin-bottom:18px}
            .meta b{color:#3a76ff}
            .cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:14px}
            .card{background:#fff;border:1px solid #e7eaf2;border-radius:14px;padding:16px 18px;box-shadow:0 1px 3px rgba(20,30,60,.04)}
            .card .k{font-size:12px;color:#8a91a4;margin-bottom:6px}
            .card .v{font-size:26px;font-weight:700;letter-spacing:-.5px}
            .tags{display:flex;flex-wrap:wrap;gap:10px}
            .tag{background:#eef3ff;color:#3a76ff;padding:5px 12px;border-radius:999px;font-size:13px;font-weight:600}
            .tablewrap{overflow-x:auto;border:1px solid #e7eaf2;border-radius:14px}
            table{border-collapse:collapse;width:100%;font-size:13px;background:#fff}
            th,td{padding:10px 12px;text-align:left;border-bottom:1px solid #eef0f5;vertical-align:top}
            th{background:#fafbfe;color:#6b7280;font-weight:600;white-space:nowrap}
            tr:last-child td{border-bottom:none}
            td.q{max-width:280px}
            td.a{max-width:360px;color:#4a5160}
            .note{margin-top:6px;font-size:12px;color:#b06a00;background:#fff7e6;padding:4px 8px;border-radius:8px;display:inline-block}
            .t-fact{color:#0a8f5b;font-weight:600}
            .t-cross{color:#b06a00;font-weight:600}
            .t-reject{color:#c0392b;font-weight:600}
            .foot{margin-top:26px;font-size:12px;color:#8a91a4}
            code{background:#eef0f5;padding:1px 6px;border-radius:6px;font-size:12px}
            .muted{color:#8a91a4}
            @media (prefers-color-scheme:dark){
              body{background:#0f1320;color:#e6e8ef}
              .card{background:#171c2b;border-color:#262c3d;box-shadow:none}
              .tag{background:#1c2740;color:#7ea6ff}
              .tablewrap{border-color:#262c3d}
              table{background:#131826}
              th{background:#161c2b;color:#9aa3b8}
              td{border-color:#222838}
              td.a{color:#b9c0d0}
              .note{background:#2a2310;color:#f0b860}
              .meta,.foot{color:#9aa3b8}
              .meta b{color:#7ea6ff}
              code{background:#1c2233}
              h2{border-color:#5b8cff}
            }
            """;
    }
}
