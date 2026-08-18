-- V4 反馈闭环：回答点赞/点踩，打通「反馈 → 数据驱动优化」故事线
-- 设计要点：
--  - 同一用户对同一问答重复提交 → 覆盖更新（ON CONFLICT DO UPDATE），如点赞改点踩
--  - username 与 qa_log 对齐（未登录为 anonymous）
--  - rating 约束：1 赞 / -1 踩
--  - qa_id 级联：删除问答记录时反馈一并清除

CREATE TABLE IF NOT EXISTS feedback (
    id          BIGSERIAL PRIMARY KEY,
    qa_id       BIGINT      NOT NULL REFERENCES qa_log (id) ON DELETE CASCADE,
    username    VARCHAR(64) NOT NULL DEFAULT 'anonymous',
    rating      SMALLINT    NOT NULL CHECK (rating IN (1, -1)),
    comment     TEXT,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_feedback_qa ON feedback (qa_id);
CREATE INDEX IF NOT EXISTS idx_feedback_created ON feedback (created_at);

-- 同一用户对同一问答只保留一条反馈（重复提交即覆盖）
CREATE UNIQUE INDEX IF NOT EXISTS uk_feedback_qa_user ON feedback (qa_id, username);
