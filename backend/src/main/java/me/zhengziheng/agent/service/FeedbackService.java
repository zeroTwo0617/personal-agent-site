package me.zhengziheng.agent.service;

import me.zhengziheng.agent.common.BusinessException;
import me.zhengziheng.agent.common.ErrorCode;
import me.zhengziheng.agent.dto.request.FeedbackRequest;
import me.zhengziheng.agent.dto.response.FeedbackStatsVO;
import me.zhengziheng.agent.entity.Feedback;
import me.zhengziheng.agent.mapper.FeedbackMapper;
import me.zhengziheng.agent.mapper.QaLogMapper;
import org.springframework.stereotype.Service;

/**
 * 反馈闭环服务：提交反馈（校验问答存在 + 覆盖更新）+ 统计聚合。
 * 反馈数据是「数据驱动优化」的起点：差评可导出为评测集扩充（见二期需求文档 P2）。
 */
@Service
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;
    private final QaLogMapper qaLogMapper;

    public FeedbackService(FeedbackMapper feedbackMapper, QaLogMapper qaLogMapper) {
        this.feedbackMapper = feedbackMapper;
        this.qaLogMapper = qaLogMapper;
    }

    /** 提交反馈：问答必须存在；rating 仅允许 1/-1；同一用户重复提交覆盖更新 */
    public void submit(FeedbackRequest req, String username) {
        Integer rating = req.getRating();
        if (rating == null || (rating != 1 && rating != -1)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "rating 仅支持 1(赞) / -1(踩)");
        }
        if (qaLogMapper.selectById(req.getQaId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "问答记录不存在");
        }
        Feedback f = new Feedback();
        f.setQaId(req.getQaId());
        f.setUsername((username == null || username.isBlank()) ? "anonymous" : username);
        f.setRating(rating);
        f.setComment(req.getComment());
        feedbackMapper.upsert(f);
    }

    /** 反馈统计：总数 / 好评率 / 最近差评 */
    public FeedbackStatsVO stats() {
        long total = feedbackMapper.countAll();
        long positive = feedbackMapper.countPositive();
        long negative = total - positive;
        FeedbackStatsVO vo = new FeedbackStatsVO();
        vo.setTotal(total);
        vo.setPositive(positive);
        vo.setNegative(negative);
        // 好评率保留 1 位小数（无反馈时为 0）
        vo.setPositiveRate(total == 0 ? 0 : Math.round(positive * 1000.0 / total) / 10.0);
        vo.setRecentNegative(feedbackMapper.selectRecentNegative(10));
        return vo;
    }
}
