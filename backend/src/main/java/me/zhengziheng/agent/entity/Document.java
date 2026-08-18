package me.zhengziheng.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档元信息实体，对应 document 表。
 * 注意区分两个 ID：
 *  - id：数据库自增主键（技术字段，对外不暴露）
 *  - docId：业务主键（UUID 字符串），前端/接口与 chunk 表都用它引用文档
 */
@Data
@TableName("document")
public class Document {

    /** 数据库自增主键（MyBatis-Plus AUTO，内部使用，不对外暴露） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务主键：上传时生成的 UUID，接口与分块表均通过它关联文档 */
    private String docId;
    /** 原始上传文件名 */
    private String name;
    /** 状态：READY 表示已解析入库完成（M1 仅此一种取值） */
    private String status;
    /** 该文档被切分出的文本块数量 */
    private Integer chunkCount;
    /** 内容 SHA-256 摘要（seed 幂等用：同名同 hash 跳过，同名异 hash 重灌） */
    private String contentHash;
    /** 上传时间 */
    private LocalDateTime createdAt;
}
