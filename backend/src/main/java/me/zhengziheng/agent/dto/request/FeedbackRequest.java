package me.zhengziheng.agent.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 反馈提交请求：对某条问答点赞/点踩，可附评论。
 */
@Data
public class FeedbackRequest {

    /** 关联的问答记录 ID（qa_log.id），必填 */
    @NotNull(message = "qaId 不能为空")
    private Long qaId;

    /** 1 赞 / -1 踩（service 层校验取值范围） */
    @NotNull(message = "rating 不能为空")
    private Integer rating;

    /** 可选评论（≤500 字） */
    private String comment;
}
