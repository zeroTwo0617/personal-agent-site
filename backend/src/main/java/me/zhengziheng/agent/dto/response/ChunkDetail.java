package me.zhengziheng.agent.dto.response;

import lombok.Data;

/**
 * 文档详情中的分块预览项（单块的内容与位置信息）。
 */
@Data
public class ChunkDetail {

    /** 块序号 */
    private Integer chunkIndex;
    /** 所属标题 */
    private String section;
    /** 块文本 */
    private String content;
    /** 估算 token 数 */
    private Integer tokenCount;
}
