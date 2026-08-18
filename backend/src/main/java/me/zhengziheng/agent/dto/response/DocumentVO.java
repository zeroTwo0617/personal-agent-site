package me.zhengziheng.agent.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档视图对象：用于列表/上传返回（轻量，不含分块明细）。
 */
@Data
public class DocumentVO {

    /** 业务主键（接口引用文档用这个，不是自增 id） */
    private String docId;
    /** 文件名 */
    private String name;
    /** 状态：READY */
    private String status;
    /** 分块数量 */
    private Integer chunkCount;
    /** 上传时间 */
    private LocalDateTime createdAt;
}
