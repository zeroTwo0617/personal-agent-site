-- V1 初始化：启用 pgvector，建立 document / chunk 表与索引
-- Flyway 在应用启动时自动执行（baseline-on-migrate=true）

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document (
    id          BIGSERIAL PRIMARY KEY,
    doc_id      VARCHAR(64)  NOT NULL UNIQUE,
    name        VARCHAR(255) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'READY',
    chunk_count INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chunk (
    id          BIGSERIAL PRIMARY KEY,
    doc_id      VARCHAR(64)  NOT NULL REFERENCES document (doc_id) ON DELETE CASCADE,
    section     VARCHAR(255),
    chunk_index INT          NOT NULL,
    content     TEXT         NOT NULL,
    token_count INT,
    embedding   vector(1024),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chunk_doc_id ON chunk (doc_id);
-- 余弦距离检索索引（小数据量下即便无索引也可直接 <#> 计算）
CREATE INDEX IF NOT EXISTS idx_chunk_embedding
    ON chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
