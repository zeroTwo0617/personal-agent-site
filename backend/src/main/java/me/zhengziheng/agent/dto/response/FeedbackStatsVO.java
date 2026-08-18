package me.zhengziheng.agent.dto.response;

import me.zhengziheng.agent.entity.Feedback;
import lombok.Data;

import java.util.List;

/**
 * 反馈统计视图（GET /api/feedback/stats 返回）：
 * 总数 / 好评数 / 差评数 / 好评率 / 最近差评（含问题原文）。
 */
@Data
public class FeedbackStatsVO {

    /** 反馈总数 */
    private long total;

    /** 好评数（rating=1） */
    private long positive;

    /** 差评数（rating=-1） */
    private long negative;

    /** 好评率（0~100，保留 1 位小数；无反馈时为 0） */
    private double positiveRate;

    /** 最近 10 条差评（含问题原文，供人工复核） */
    private List<Feedback> recentNegative;
}
