package me.zhengziheng.agent.integration;

import me.zhengziheng.agent.entity.Feedback;
import me.zhengziheng.agent.entity.QaLog;
import me.zhengziheng.agent.mapper.FeedbackMapper;
import me.zhengziheng.agent.mapper.QaLogMapper;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 反馈闭环集成测试：UPSERT 覆盖、统计聚合（总数/好评率/最近差评）。
 * 依赖 V4__feedback.sql 迁移与 qa_log RETURNING id。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class FeedbackIntegrationTest {

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
    private FeedbackMapper feedbackMapper;
    @Autowired
    private QaLogMapper qaLogMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.execute("TRUNCATE feedback, qa_log, chunk, document RESTART IDENTITY CASCADE");
    }

    /** 造一条问答记录，返回 qaId（RETURNING id 链路） */
    private long newQa(String question) {
        QaLog log = new QaLog();
        log.setUsername("alice");
        log.setQuestion(question);
        log.setAnswer("这是答案");
        Long id = qaLogMapper.insertLog(log);
        assertNotNull(id, "insertLog 应返回自增主键（qaId）");
        return id;
    }

    private Feedback feedback(long qaId, String username, int rating, String comment) {
        Feedback f = new Feedback();
        f.setQaId(qaId);
        f.setUsername(username);
        f.setRating(rating);
        f.setComment(comment);
        return f;
    }

    @Test
    void upsert_sameUserOverwritesRating() {
        long qaId = newQa("Vue3 ref 是什么？");

        feedbackMapper.upsert(feedback(qaId, "alice", 1, "很好"));
        feedbackMapper.upsert(feedback(qaId, "alice", -1, "缺例子"));

        // 同一用户同一 qa 只保留一条，rating 被覆盖为 -1
        List<Feedback> rows = jdbcTemplate.query(
                "SELECT * FROM feedback WHERE qa_id = ?",
                (rs, i) -> {
                    Feedback f = new Feedback();
                    f.setId(rs.getLong("id"));
                    f.setQaId(rs.getLong("qa_id"));
                    f.setUsername(rs.getString("username"));
                    f.setRating(rs.getInt("rating"));
                    f.setComment(rs.getString("comment"));
                    return f;
                },
                qaId);
        assertEquals(1, rows.size());
        assertEquals(-1, rows.get(0).getRating());
        assertEquals("缺例子", rows.get(0).getComment());
    }

    @Test
    void upsert_differentUsersKeepSeparateRows() {
        long qaId = newQa("问题");
        feedbackMapper.upsert(feedback(qaId, "alice", 1, null));
        feedbackMapper.upsert(feedback(qaId, "bob", -1, "不太行"));

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feedback WHERE qa_id = ?", Long.class, qaId);
        assertEquals(2L, count);
    }

    @Test
    void stats_aggregatesTotalsAndRate() {
        long q1 = newQa("问题一");
        long q2 = newQa("问题二");
        long q3 = newQa("问题三");
        feedbackMapper.upsert(feedback(q1, "alice", 1, null));
        feedbackMapper.upsert(feedback(q2, "alice", 1, "赞"));
        feedbackMapper.upsert(feedback(q3, "bob", -1, "没答到点子上，缺例子"));

        assertEquals(3L, feedbackMapper.countAll());
        assertEquals(2L, feedbackMapper.countPositive());

        // 最近差评应关联到问题原文
        List<Feedback> recent = feedbackMapper.selectRecentNegative(10);
        assertEquals(1, recent.size());
        assertEquals("问题三", recent.get(0).getQuestion());
        assertEquals("没答到点子上，缺例子", recent.get(0).getComment());
    }

    @Test
    void qaLogDelete_cascadesFeedback() {
        long qaId = newQa("将被删除的问题");
        feedbackMapper.upsert(feedback(qaId, "alice", 1, null));

        jdbcTemplate.update("DELETE FROM qa_log WHERE id = ?", qaId);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feedback WHERE qa_id = ?", Long.class, qaId);
        assertEquals(0L, count, "删除问答记录应级联删除其反馈");
    }
}
