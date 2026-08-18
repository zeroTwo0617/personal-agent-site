package me.zhengziheng.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.zhengziheng.agent.entity.Document;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    @Select("SELECT * FROM document WHERE doc_id = #{docId}")
    Document selectByDocId(@Param("docId") String docId);

    @Select("SELECT * FROM document WHERE name = #{name} LIMIT 1")
    Document selectByName(@Param("name") String name);

    @Delete("DELETE FROM document WHERE doc_id = #{docId}")
    int deleteByDocId(@Param("docId") String docId);

    @Select("SELECT * FROM document ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<Document> selectPage(@Param("size") int size, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM document")
    long countAll();
}
