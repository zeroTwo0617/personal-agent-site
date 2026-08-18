package me.zhengziheng.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文本分块实体，对应 chunk 表（RAG 检索的最小单元）。
 * 向量列 embedding 不映射到此实体——检索时由 Mapper 直接用 SQL 计算余弦距离，不加载到内存。
 */
@Data
@TableName("chunk")
public class Chunk {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文档业务 ID，关联 document.doc_id */
    private String docId;
    /** 该块来自哪个 Markdown 标题（用于溯源展示） */
    private String section;
    /** 块序号，从 0 开始，同一文档内唯一 */
    private Integer chunkIndex;
    /** 分块文本内容 */
    private String content;
    /** 估算 token 数（用于展示/成本控制） */
    private Integer tokenCount;
    /** 入库时间 */
    private LocalDateTime createdAt;
}
