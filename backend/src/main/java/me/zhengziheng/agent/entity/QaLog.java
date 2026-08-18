package me.zhengziheng.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答历史记录实体，对应 qa_log 表。
 * 每次问答完成后写入一条（尽力而为，失败不影响问答主流程）；
 * sources 为 JSON 字符串（引用来源序列化结果，对应表内 JSONB 列）。
 */
@Data
@TableName("qa_log")
public class QaLog {

    /** 数据库自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提问用户（未登录时取 JWT 中的用户名，否则为 anonymous） */
    private String username;

    /** 用户问题原文 */
    private String question;

    /** 最终答案（LLM 生成或抽取式拼接） */
    private String answer;

    /** 引用来源的 JSON 序列化（ChunkSearchResult 列表），无引用时为 null */
    private String sources;

    /** 完成时间 */
    private LocalDateTime createdAt;
}
