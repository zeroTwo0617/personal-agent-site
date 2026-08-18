package me.zhengziheng.agent.dto.response;

import lombok.Data;

import java.util.Map;

/**
 * 检索命中结果（向量 / 关键词 / 融合后通用）。
 * distance 为向量召回的余弦距离；其余 fusedScore / *Rank / rerankScore 为混合检索阶段填充，
 * 供调试与前端展示检索元信息，前端可忽略未知字段。
 */
@Data
public class ChunkSearchResult {

    private String docId;
    private String docName;
    private String section;
    private Integer chunkIndex;
    private String content;
    private Double distance;

    /** RRF 融合后的综合分（向量路 + 关键词路 1/(k+rank) 之和） */
    private Double fusedScore;
    /** 向量召回路中的排名（1 起；未命中为 null） */
    private Integer vectorRank;
    /** 关键词召回路中的排名（1 起；未命中为 null） */
    private Integer keywordRank;
    /** 重排后的最终得分（启发式归一化分 / LLM 打分） */
    private Double rerankScore;
    /** 检索元信息（mode / rerank / 各路命中数），供前端徽标展示 */
    private Map<String, Object> retrievalMeta;

    /** 余弦相似度：1 - 余弦距离，范围 0~1 */
    public Double getScore() {
        return distance == null ? null : 1 - distance;
    }
}
