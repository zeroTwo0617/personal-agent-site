# rag-kb-demo — 个人 RAG 知识库问答系统

## 背景与目标
独立搭建一个 RAG（检索增强生成）知识库问答系统，用于展示「Vue3 + Spring Boot + PostgreSQL/PGVector」全栈能力与 RAG 工程深度。核心是把自己零散的技术笔记（Markdown）变成可对话、可溯源、可评测的知识库。

## 我的职责
独立完成全部设计与实现：前端、后端、数据库、检索管线、Agent、评测闭环、自动化测试、容器化与 CI。一人全栈，从 0 到可部署。

## 技术选型与架构
- 前端：Vue 3（Composition API + `<script setup>`）+ Vite + TypeScript + Pinia + Vue Router + Element Plus。
- 后端：Spring Boot 3.2 + MyBatis-Plus + Spring Security(JWT) + SpringDoc(OpenAPI) + Flyway。
- 数据：PostgreSQL 16 + pgvector（向量）+ pg_trgm（关键词 trigram 索引）。
- RAG 管线：Markdown 按标题分块 → 长度二次切 + 重叠窗口 → Embedding → 混合检索 → Rerank → LLM 流式生成（SSE）。
- 部署：Docker Compose（PG+后端）、多阶段 Dockerfile、GitHub Actions CI。

## 核心难点与解决方案
1. **中文检索召回质量**：单纯向量检索对中文术语/缩写召回差。方案是「混合检索」——向量召回（语义）+ 关键词召回（QueryTerms 中文 bigram 分词 + pg_trgm ILIKE 字面匹配），再用 RRF 倒数排名融合（k=60）取长补短，最后 Rerank 精排。
2. **Agent 化多步问答**：单轮检索答不了"对比/综合"类问题。实现 ReAct 循环（THOUGHT→ACTION→ACTION_INPUT→ANSWER），最多 5 步，工具包括语义检索/关键词检索/列文档/读文档，每步结果按全局编号回填，保证最终答案里的 [N] 与引用来源一一对应可溯源。
3. **多轮对话指代**：追问"那它呢"无法直接检索。用 LLM 把追问改写成自包含检索词（查询改写），并把历史并入提示词。
4. **引用溯源**：所有召回片段收集去重后全局编号，最终答案 [N] 严格对应 sources[N-1]，前端可点击查看原文章节。
5. **评测闭环**：用评测集 + Recall@K + LLM-as-judge 量化检索与生成质量，HTML 报告可回溯每一题，差评经点赞/点踩反馈闭环回流扩充评测集。

## 量化成果
- 后端 45 个单元测试 + 10 个 Testcontainers 集成测试；前端 23 个 Vitest 用例；JaCoCo 覆盖率门禁（核心分块/检索类 ≥80%）。
- 混合检索（RRF 融合）相对纯向量检索，评测集 Recall@K 有明显提升（关键词路补齐了语义盲区）。
- Docker 多阶段构建镜像体积最小化；GitHub Actions 双 job（后端 verify + 前端 test/build）自动门禁。

## 复盘与可追问点
- **为什么用 PGVector 而不是独立向量库（如 Milvus）**：demo 规模，向量和结构化数据同库省一套运维，HNSW 索引足够。
- **为什么 RRF 融合而不是加权求和**：两路分数尺度不同（余弦距离 vs 命中次数），RRF 只关心排名、免调参。
- **Agent 与普通 RAG 的边界**：普通 RAG 一步检索即生成，Agent 适合"多跳/对比/需要反思再检索"的问题，代价是多次 LLM 调用更慢更贵。
- **Embedding 无 key 时的兜底**：本地 hash 向量（字符袋+哈希分桶），质量低于语义模型但零成本可跑通，用于演示降级。
- **内存任务表 + SSE**：单实例下用内存 Map 存任务态，SSE 逐字推送，任务 TTL 清理防泄漏；多实例需换 Redis/DB。
