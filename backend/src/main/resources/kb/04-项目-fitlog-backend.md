# fitlog-backend — FitLog 健身小程序统一后端

## 背景与目标
为健身小程序做一个统一独立后端，承载两类能力：① 动作演示图片托管（1324 个 GIF 的 exerciseId→URL 映射）；② AI Agent（chat/search/insight 三种 action，复用 RAG 方法论）。小程序保留 CloudBase 做训练数据读写，只有「AI 问答 / 图片」走后端，前端同时连两套。

## 我的职责
独立完成后端详细设计（表结构/API 契约/RAG 检索/图片存储/里程碑）、Spring Boot 骨架、图片 API、AI Agent 端点、MyBatis 数据层与部署方案。

## 技术选型与架构
- Java 17 + Spring Boot 3 + MySQL 8 + MyBatis（XML mapper，贴合 SQL 书写习惯）。
- **Embedding 存 MySQL JSON 字段，检索在应用内算余弦，不引入 PGVector**——1324 条动作库全量载入内存算余弦，单次查询毫秒级，无需向量索引。
- 图片走对象存储（腾讯云 COS）或服务器静态目录 + CDN；映射写入 exercises_media 表。
- Agent 端点 action 分发：chat（SSE 流式）/ search（语义+关键词检索返回动作列表）/ insight（统计工具 + LLM 解读）。
- 无 LLM key 时 Agent 自动降级为 echo，图片 API 独立可用。

## 核心难点与解决方案
1. **向量检索不引入 PGVector**：用户只熟悉 MySQL，且数据量小（1324 条）。方案是 embedding 用 MySQL JSON 数组存浮点，查询时全量载入内存用 `VectorUtil.cosine` 算余弦排序取 top-k；进阶复用 rag-kb-demo 的混合检索（关键词 LIKE + 向量 → RRF 融合 → Rerank）。
2. **双后端架构**：训练数据在 CloudBase，AI/图片在后端。MVP 阶段由小程序把近期训练摘要直接传入后端（避免跨云读 CloudBase），后端无需服务端 SDK，链路简单可靠。
3. **多轮记忆**：agent_sessions 表按 openid 存 messages JSON，多轮对话持久化。
4. **内容安全**：用户输入先过微信内容安全 API 再送 LLM，避免不合规内容。

## 量化成果
- 图片 API 支撑 1324 个动作 GIF 的映射与批量查询（`/api/media/map?ids=a,b,c` 批量返回）。
- Agent 三 action（chat/search/insight）打通，SSE 流式输出，无 key 自动降级不阻塞图片服务。
- 详细设计文档覆盖表结构/API 契约/RAG/存储/里程碑/风险（11 条待确认项）。

## 复盘与可追问点
- **为什么 embedding 用 MySQL JSON 而非 PGVector**：数据量小（1324 条）应用内算余弦毫秒级即可，避免引入 PostgreSQL 运维成本；数据量大再换专用向量库。
- **为什么前端连两套后端**：CloudBase 已有训练数据与账号体系，迁移成本高；新能力（AI/图片）独立后端，渐进式演进。
- **图片版权**：GIF 来自第三方（Gymvisual），托管需保留署名、商用须授权——这是做内容类产品的合规点。
- **openid 传递 vs code2session**：MVP 直传 openid 简单但可伪造，进阶改用微信 code2session 校验换取会话密钥。
