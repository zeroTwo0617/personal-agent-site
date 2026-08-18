-- V3 检索性能优化：
--  1) 关键词召回路：pg_trgm GIN 索引 —— ILIKE '%term%' 前导通配符匹配默认只能全表扫，
--     切 trigram 后走 Bitmap Index Scan，配合 QueryTerms 的中文 bigram 词，数据量增长不劣化；
--  2) 向量召回路：ivfflat → HNSW（需 pgvector >= 0.5，docker-compose 的 pg16 镜像自带 0.7+），
--     免训练、增量插入友好、召回精度接近暴力搜索。
-- 索引名沿用（idx_chunk_embedding），Mapper 的 SQL 零改动；Flyway 重启自动执行。

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 确保 pgvector >= 0.5（HNSW 支持）。
-- 注意：全新库（docker-compose 镜像自带 0.7+）扩展已是默认最新版，直接 ALTER EXTENSION
-- UPDATE 会因无更新路径而报错导致迁移失败，故仅在版本低于 0.5.0 时才升级。
DO $$
BEGIN
    IF (SELECT extversion FROM pg_extension WHERE extname = 'vector') < '0.5.0' THEN
        ALTER EXTENSION vector UPDATE;
    END IF;
END $$;

-- 关键词路：content 的 trigram GIN 索引（gin_trgm_ops 内部转小写，兼容 ILIKE 语义）
CREATE INDEX IF NOT EXISTS idx_chunk_content_trgm
    ON chunk USING gin (content gin_trgm_ops);

-- 向量路：删除旧 ivfflat，重建为 HNSW（m=16 / ef_construction=64 为官方推荐默认档）
DROP INDEX IF EXISTS idx_chunk_embedding;
CREATE INDEX IF NOT EXISTS idx_chunk_embedding
    ON chunk USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
