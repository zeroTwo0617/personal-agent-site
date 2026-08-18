package me.zhengziheng.agent.integration;

import me.zhengziheng.agent.dto.response.ChunkDetail;
import me.zhengziheng.agent.dto.response.ChunkSearchResult;
import me.zhengziheng.agent.entity.Document;
import me.zhengziheng.agent.mapper.ChunkMapper;
import me.zhengziheng.agent.mapper.DocumentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集成测试：Testcontainers 起 pgvector 镜像，验证 Flyway 迁移、Mapper CRUD、级联删除、
 * 向量写入/检索、关键词检索。本机无 Docker 时自动跳过（disabledWithoutDocker）。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class DocumentChunkIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("ragkb")
            .withUsername("ragkb")
            .withPassword("ragkb");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DocumentMapper documentMapper;
    @Autowired
    private ChunkMapper chunkMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.execute("TRUNCATE chunk, document RESTART IDENTITY CASCADE");
    }

    /** 生成 1536 维测试向量（与 schema vector(1536) 对齐） */
    private float[] vec(double seed) {
        float[] v = new float[1536];
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) Math.sin(i + seed);
        }
        return v;
    }

    private Document insertDoc(String docId, String name) {
        Document doc = new Document();
        doc.setDocId(docId);
        doc.setName(name);
        doc.setStatus("READY");
        doc.setChunkCount(0);
        doc.setCreatedAt(LocalDateTime.now());
        documentMapper.insert(doc);
        return doc;
    }

    @Test
    void flywayMigratesAllVersions() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);
        assertTrue(tables.containsAll(List.of("document", "chunk", "qa_log", "feedback")), "核心表应全部建立");

        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank", String.class);
        assertEquals(List.of("1", "2", "3", "4"), versions, "V1~V4 迁移应全部执行");
    }

    @Test
    void documentCrudAndCascadeDelete() {
        insertDoc("doc-a", "vue3-notes.md");
        chunkMapper.insertChunk("doc-a", "响应式基础", 0, "Vue3 的响应式基于 Proxy。", 10, vec(1));
        chunkMapper.insertChunk("doc-a", "响应式基础", 1, "ref 与 reactive 的区别。", 9, vec(2));

        Document loaded = documentMapper.selectByDocId("doc-a");
        assertEquals("vue3-notes.md", loaded.getName());
        assertEquals(2, chunkMapper.listByDocId("doc-a").size());

        documentMapper.deleteByDocId("doc-a");
        assertTrue(chunkMapper.listByDocId("doc-a").isEmpty(), "删除文档应级联删除分块");
        assertEquals(0, documentMapper.countAll());
    }

    @Test
    void vectorWriteRoundTripAndCosineSearch() {
        insertDoc("doc-v", "向量测试.md");
        float[] near = vec(100.0);
        float[] far = vec(99999.0);
        chunkMapper.insertChunk("doc-v", "第一节", 0, "与查询向量接近的内容", 8, near);
        chunkMapper.insertChunk("doc-v", "第二节", 1, "与查询向量很远的内容", 8, far);

        // 向量列确实存的是 vector 类型（写回后可读回文本形式）
        String stored = jdbcTemplate.queryForObject(
                "SELECT embedding::text FROM chunk WHERE doc_id = 'doc-v' AND chunk_index = 0", String.class);
        assertTrue(stored != null && stored.startsWith("["), "embedding 应已落库为向量字面量");

        List<ChunkSearchResult> hits = chunkMapper.search(vec(100.0), 5);
        assertFalse(hits.isEmpty());
        assertEquals("doc-v", hits.get(0).getDocId());
        assertEquals(0, hits.get(0).getChunkIndex());
        assertTrue(hits.get(0).getDistance() < hits.get(1).getDistance(), "余弦距离小的应排前面");
    }

    @Test
    void keywordSearch_findsExactTermCaseInsensitive() {
        insertDoc("doc-k", "mybatis-notes.md");
        chunkMapper.insertChunk("doc-k", "分页", 0, "MyBatis 分页插件 PageHelper 用法", 12, vec(3));
        chunkMapper.insertChunk("doc-k", "缓存", 1, "一级缓存与二级缓存区别", 10, vec(4));

        List<ChunkSearchResult> hits = chunkMapper.keywordSearch(List.of("pagehelper"), 5);
        assertFalse(hits.isEmpty());
        assertEquals("MyBatis 分页插件 PageHelper 用法", hits.get(0).getContent());

        List<ChunkSearchResult> cnHits = chunkMapper.keywordSearch(List.of("缓存"), 5);
        assertEquals(1, cnHits.size());
        assertEquals("一级缓存与二级缓存区别", cnHits.get(0).getContent());
        // 关键词命中不应伪造余弦距离（distance 为 null），否则前端会显示假的"相似度 100%"
        assertNull(cnHits.get(0).getDistance(), "关键词命中不应有伪造的余弦距离");
    }

    @Test
    void listByDocId_orderedByChunkIndex() {
        insertDoc("doc-l", "ordered.md");
        chunkMapper.insertChunk("doc-l", "第三节", 2, "内容三", 3, vec(1));
        chunkMapper.insertChunk("doc-l", "第一节", 0, "内容一", 3, vec(2));
        chunkMapper.insertChunk("doc-l", "第二节", 1, "内容二", 3, vec(3));

        List<ChunkDetail> chunks = chunkMapper.listByDocId("doc-l");
        assertEquals(3, chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertEquals(1, chunks.get(1).getChunkIndex());
        assertEquals(2, chunks.get(2).getChunkIndex());
    }

    @Test
    void selectPage_ordersByCreatedAtDesc() {
        insertDoc("doc-p1", "old.md");
        insertDoc("doc-p2", "new.md");
        List<Document> page = documentMapper.selectPage(10, 0);
        assertEquals(2, page.size());
        assertEquals("doc-p2", page.get(0).getDocId(), "最新创建的文档应排最前");
        Map<String, Object> count = jdbcTemplate.queryForMap("SELECT COUNT(*) AS c FROM document");
        assertEquals(2L, count.get("c"));
    }
}
