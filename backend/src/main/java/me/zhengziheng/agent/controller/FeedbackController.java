package me.zhengziheng.agent.controller;

import me.zhengziheng.agent.common.JwtUtil;
import me.zhengziheng.agent.common.Result;
import me.zhengziheng.agent.dto.request.FeedbackRequest;
import me.zhengziheng.agent.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 反馈闭环接口：提交点赞/点踩（匿名）。
 * 统计看板已移至 /api/admin/feedback/stats（站长鉴权，见 AdminController）。
 */
@RestController
@RequestMapping("/api/feedback")
@Tag(name = "反馈闭环")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final JwtUtil jwtUtil;

    public FeedbackController(FeedbackService feedbackService, JwtUtil jwtUtil) {
        this.feedbackService = feedbackService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping
    @Operation(summary = "提交反馈：点赞(1)/点踩(-1)，可附评论；同一问答重复提交覆盖")
    public Result<Void> submit(@Valid @RequestBody FeedbackRequest req,
                               @RequestHeader(value = "Authorization", required = false) String auth) {
        feedbackService.submit(req, resolveUsername(auth));
        return Result.success();
    }

    /** 从 Authorization: Bearer <jwt> 解析用户名；未登录 / 解析失败回退 anonymous */
    private String resolveUsername(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            return "anonymous";
        }
        try {
            return jwtUtil.parseUsername(auth.substring(7));
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
