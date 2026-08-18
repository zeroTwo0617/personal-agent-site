package me.zhengziheng.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengziheng.agent.entity.Feedback;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 反馈 Mapper：UPSERT 提交 + 统计聚合。
 * 统计全部用单条 SQL 完成（count 而非读全表），数据量增长也稳定。
 */
@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {

    /**
     * UPSERT：同一用户对同一 qa 重复提交 → 覆盖更新（点赞改点踩、更新评论）。
     * 依赖 V4 迁移中的唯一索引 uk_feedback_qa_user (qa_id, username)。
     */
    @Insert("""
            INSERT INTO feedback (qa_id, username, rating, comment)
            VALUES (#{qaId}, #{username}, #{rating}, #{comment})
            ON CONFLICT (qa_id, username)
            DO UPDATE SET rating = EXCLUDED.rating, comment = EXCLUDED.comment, created_at = NOW()
            """)
    int upsert(Feedback feedback);

    /** 反馈总数 */
    @Select("SELECT COUNT(*) FROM feedback")
    long countAll();

    /** 好评数（rating = 1） */
    @Select("SELECT COUNT(*) FROM feedback WHERE rating = 1")
    long countPositive();

    /** 最近 N 条差评（关联问题原文，便于人工复核 / 导出扩充评测集） */
    @Select("""
            SELECT f.id, f.qa_id, f.username, f.rating, f.comment, f.created_at, q.question
            FROM feedback f
            JOIN qa_log q ON f.qa_id = q.id
            WHERE f.rating = -1
            ORDER BY f.created_at DESC
            LIMIT #{limit}
            """)
    List<Feedback> selectRecentNegative(@Param("limit") int limit);
}
