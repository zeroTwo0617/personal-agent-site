package me.zhengziheng.agent.controller;

import me.zhengziheng.agent.common.Result;
import me.zhengziheng.agent.dto.response.FeedbackStatsVO;
import me.zhengziheng.agent.entity.QaLog;
import me.zhengziheng.agent.mapper.QaLogMapper;
import me.zhengziheng.agent.service.FeedbackService;
import me.zhengziheng.agent.service.KbSeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理接口（站长专用，/api/admin/** 经 AdminAuthInterceptor 校验 JWT + 站长身份）。
 * 提供：知识库状态/重建、访客提问记录（分页/搜索）、反馈统计。
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "管理")
public class AdminController {

    private final KbSeedService kbSeedService;
    private final QaLogMapper qaLogMapper;
    private final FeedbackService feedbackService;

    public AdminController(KbSeedService kbSeedService,
                           QaLogMapper qaLogMapper,
                           FeedbackService feedbackService) {
        this.kbSeedService = kbSeedService;
        this.qaLogMapper = qaLogMapper;
        this.feedbackService = feedbackService;
    }

    @GetMapping("/kb/status")
    @Operation(summary = "知识库状态（文档数/上次 seed 时间/最近一次重建明细）")
    public Result<KbSeedService.KbStatus> kbStatus() {
        return Result.success(kbSeedService.status());
    }

    @PostMapping("/kb/rebuild")
    @Operation(summary = "重建知识库索引（按 name+content_hash 幂等）")
    public Result<KbSeedService.KbStatus> kbRebuild() {
        return Result.success(kbSeedService.rebuild());
    }

    @GetMapping("/qa")
    @Operation(summary = "访客提问记录（分页 + 问题关键词搜索，新→旧）")
    public Result<Map<String, Object>> qa(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String q) {
        int p = Math.max(1, page);
        int s = Math.min(100, Math.max(1, size));
        long total = qaLogMapper.countAll();
        List<QaLog> list = qaLogMapper.selectAllPage(q == null ? null : q.trim(), s, (p - 1) * s);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("page", p);
        data.put("size", s);
        data.put("total", total);
        return Result.success(data);
    }

    @GetMapping("/feedback/stats")
    @Operation(summary = "反馈统计（总数/好评率/最近差评）")
    public Result<FeedbackStatsVO> feedbackStats() {
        return Result.success(feedbackService.stats());
    }
}
