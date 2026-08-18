package me.zhengziheng.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengziheng.agent.dto.response.ChunkDetail;
import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.entity.Chunk;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChunkMapper extends BaseMapper<Chunk> {

    // 写入分块：embedding 经 VectorTypeHandler 序列化为 "[0.1,0.2,...]" 字符串，
    // 再用 `::vector` 强转成 PostgreSQL 的 vector 类型存进向量列
    @Insert("INSERT INTO chunk (doc_id, section, chunk_index, content, token_count, embedding) " +
            "VALUES (#{docId}, #{section}, #{chunkIndex}, #{content}, #{tokenCount}, " +
            "#{embedding, typeHandler=me.zhengziheng.agent.mapper.VectorTypeHandler}::vector)")
    void insertChunk(@Param("docId") String docId,
                     @Param("section") String section,
                     @Param("chunkIndex") int chunkIndex,
                     @Param("content") String content,
                     @Param("tokenCount") Integer tokenCount,
                     @Param("embedding") float[] embedding);

    /**
     * 向量余弦检索（PG 专属 SQL，重点理解）：
     *  - `c.embedding <=> ?` 里 `<=>` 是 pgvector 的"余弦距离"算子，越小越相似；
     *  - `?::vector` 把传入的向量字符串强转成 vector 类型（MySQL 9.0 等价写法是 DISTANCE(...)）；
     *  - 结果 distance 即余弦距离，上层用 1-distance 得到"相似度 %"。
     * 关联 document 取文档名，便于前端展示引用来源；ORDER BY distance LIMIT topK 即 Top-K 召回。
     */
    @Select("SELECT c.doc_id, d.name AS doc_name, c.section, c.chunk_index, c.content, " +
            "(c.embedding <=> #{queryVec, typeHandler=me.zhengziheng.agent.mapper.VectorTypeHandler}::vector) AS distance " +
            "FROM chunk c JOIN document d ON c.doc_id = d.doc_id " +
            "ORDER BY distance LIMIT #{topK}")
    @Results({
            @Result(column = "doc_id", property = "docId"),
            @Result(column = "doc_name", property = "docName"),
            @Result(column = "section", property = "section"),
            @Result(column = "chunk_index", property = "chunkIndex"),
            @Result(column = "content", property = "content"),
            @Result(column = "distance", property = "distance")
    })
    List<ChunkSearchResult> search(@Param("queryVec") float[] queryVec, @Param("topK") int topK);

    @Select("SELECT chunk_index, section, content, token_count " +
            "FROM chunk WHERE doc_id = #{docId} ORDER BY chunk_index")
    @Results({
            @Result(column = "chunk_index", property = "chunkIndex"),
            @Result(column = "section", property = "section"),
            @Result(column = "content", property = "content"),
            @Result(column = "token_count", property = "tokenCount")
    })
    List<ChunkDetail> listByDocId(@Param("docId") String docId);

    /**
     * 关键词召回（混合检索第二路）：用 ILIKE 做大小写不敏感的子串匹配。
     * 中文场景下 PostgreSQL 默认全文检索无分词器，故用「字级 bigram 词 + 整词」做 ILIKE，
     * 由 QueryTerms 预先切好 terms 传入。各 term 以 OR 连接，命中任一即召回；
     * 命中的"排名"由上层（HybridRetrievalService）按命中 term 数重排，这里仅取候选。
     */
    @Select("<script>" +
            "SELECT c.doc_id, d.name AS doc_name, c.section, c.chunk_index, c.content, " +
            "NULL AS distance " +
            "FROM chunk c JOIN document d ON c.doc_id = d.doc_id " +
            "WHERE 1=1 " +
            "<if test='terms != null and terms.size > 0'>" +
            "<foreach collection='terms' item='t' open='AND (' separator=' OR ' close=')'>" +
            "c.content ILIKE '%' || #{t} || '%'" +
            "</foreach>" +
            "</if>" +
            "ORDER BY c.chunk_index LIMIT #{topK}" +
            "</script>")
    @Results({
            @Result(column = "doc_id", property = "docId"),
            @Result(column = "doc_name", property = "docName"),
            @Result(column = "section", property = "section"),
            @Result(column = "chunk_index", property = "chunkIndex"),
            @Result(column = "content", property = "content"),
            @Result(column = "distance", property = "distance")
    })
    List<ChunkSearchResult> keywordSearch(@Param("terms") List<String> terms, @Param("topK") int topK);
}
