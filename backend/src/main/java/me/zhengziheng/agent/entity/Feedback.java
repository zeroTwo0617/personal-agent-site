package me.zhengziheng.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户反馈实体，对应 feedback 表。
 * 用户对每条问答点赞(1)/点踩(-1)，可附评论；同一用户对同一问答重复提交时覆盖更新。
 */
@Data
@TableName("feedback")
public class Feedback {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的问答记录（qa_log.id） */
    private Long qaId;

    /** 反馈用户（未登录为 anonymous，与 qa_log 对齐） */
    private String username;

    /** 1 赞 / -1 踩 */
    private Integer rating;

    /** 可选评论（点踩时常见：缺例子/没答到点子上） */
    private String comment;

    /** 反馈时间 */
    private LocalDateTime createdAt;

    /** 统计视图用：关联问答的问题原文（联表查询填充，非表字段） */
    @TableField(exist = false)
    private String question;
}
