-- V5 知识库 seed 幂等支持：document 增加 content_hash 列 + name 索引
-- KbSeedService 启动时按「name + content_hash」判断是否需要重灌：
--   同名且 hash 相同 → 跳过；同名但 hash 不同 → 删除旧文档（级联删 chunk）后重灌。
-- 向量维度已在 V1 直接建成 vector(1024)，此处无需 ALTER。

ALTER TABLE document ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_document_name ON document (name);
