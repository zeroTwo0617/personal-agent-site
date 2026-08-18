package me.zhengziheng.agent.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 问答历史条目（GET /api/chat/history 返回），对应 qa_log 表一行。
 * sources 为完成时保存的引用来源，历史回看可还原引用。
 */
@Data
public class ChatHistoryVO {

    /** 问答记录 ID（qa_log.id）：历史回看时也可提交反馈 */
    private Long qaId;

    /** 用户问题 */
    private String question;

    /** 当时的答案 */
    private String answer;

    /** 当时的引用来源（可为空列表） */
    private List<ChunkSearchResult> sources;

    /** 完成时间 */
    private LocalDateTime createdAt;
}
