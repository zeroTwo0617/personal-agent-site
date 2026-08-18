package me.zhengziheng.agent.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 文档详情视图对象：在 DocumentVO 基础上附加分块预览列表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentDetailVO extends DocumentVO {

    /** 该文档的所有分块（按 chunkIndex 排序），用于前端详情页预览 */
    private List<ChunkDetail> chunks;
}
