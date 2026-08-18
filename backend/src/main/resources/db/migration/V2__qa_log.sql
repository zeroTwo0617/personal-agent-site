-- V2 问答历史记录：为「多轮对话记忆 + 历史回溯」落库
-- 每次问答完成后写入一条记录；sources 以 JSONB 保存引用来源，便于历史回看时还原引用。

CREATE TABLE IF NOT EXISTS qa_log (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL DEFAULT 'anonymous',
    question    TEXT         NOT NULL,
    answer      TEXT         NOT NULL,
    sources     JSONB,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 按用户倒序取历史（GET /api/chat/history 使用）
CREATE INDEX IF NOT EXISTS idx_qa_log_username_created
    ON qa_log (username, created_at DESC, id DESC);
