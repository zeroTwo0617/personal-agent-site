package me.zhengziheng.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengziheng.agent.entity.QaLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 问答历史 Mapper。
 * sources 为 JSONB 列：插入时用 CAST(#{sources} AS jsonb) 让 PG 按 jsonb 解析；
 * 读取时返回 String，由服务层反序列化为引用来源列表。
 */
@Mapper
public interface QaLogMapper extends BaseMapper<QaLog> {

    /**
     * 写入问答历史并返回自增主键（PostgreSQL RETURNING）：
     * 该 id 即 qaId，前端据此提交点赞/点踩反馈。
     * sources 为 JSONB 列：插入时用 CAST(#{sources} AS jsonb) 让 PG 按 jsonb 解析。
     */
    @Select("""
            INSERT INTO qa_log (username, question, answer, sources)
            VALUES (#{username}, #{question}, #{answer}, CAST(COALESCE(#{sources}, '[]') AS jsonb))
            RETURNING id
            """)
    Long insertLog(QaLog log);

    /** 取某用户最近的 N 条历史（新→旧） */
    @Select("SELECT * FROM qa_log WHERE username = #{username} ORDER BY created_at DESC, id DESC LIMIT #{limit}")
    List<QaLog> selectRecent(@Param("username") String username, @Param("limit") int limit);

    /** 管理页：全部问答分页（按问题关键词可选过滤，新→旧） */
    @Select("<script>" +
            "SELECT * FROM qa_log WHERE 1=1 " +
            "<if test='q != null and q != \"\"'> AND question ILIKE '%' || #{q} || '%'</if> " +
            "ORDER BY created_at DESC, id DESC LIMIT #{size} OFFSET #{offset}" +
            "</script>")
    List<QaLog> selectAllPage(@Param("q") String q, @Param("size") int size, @Param("offset") int offset);

    /** 全部问答总数 */
    @Select("SELECT COUNT(*) FROM qa_log")
    long countAll();
}
