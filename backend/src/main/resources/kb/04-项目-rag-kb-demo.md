# rag-kb-demo — 个人 RAG 知识库问答系统

## 背景与目标
我独立搭建一个 RAG（检索增强生成）知识库问答系统，核心是把个人零散的技术笔记（Markdown）变成可对话、可溯源、可评测的知识库。它和普通"套壳聊天"的壁垒在于三点：答案只来自用户自己的文档不瞎编（faithfulness）、每个回答标注引用来源可回溯、检索链路工程化可评测（召回率/忠实度量化）。定位是简历项目，展示"Vue3 + Spring Boot + PostgreSQL/PGVector"全栈能力与 RAG 工程深度。

## 我的职责
独立完成全部设计与实现：前端、后端、数据库、检索管线、Agent、评测闭环、自动化测试、容器化与 CI。一人全栈，从 0 到可部署。

## 技术选型与架构
- 前端：Vue 3（Composition API + script setup）+ Vite + TypeScript + Pinia + Vue Router + Element Plus + Axios。
- 后端：Spring Boot 3.2 + MyBatis-Plus + Spring Security(JWT) + SpringDoc(OpenAPI) + Flyway（包名 com.ragdemo）。
- 数据：PostgreSQL 16 + pgvector（向量）+ pg_trgm（关键词 trigram 索引），结构化数据与向量同库。
- RAG 管线：Markdown 按标题分块 → 长度二次切 + 重叠窗口 → Embedding(bge 类/OpenAI 兼容) → 混合检索(RRF) → Rerank → LLM 流式生成(SSE) + Agent(ReAct)。
- 部署：Docker Compose（PG+后端）、多阶段 Dockerfile、GitHub Actions 双 job（后端 verify + 前端 test/build）、前端 Vercel/Nginx。

## 核心难点与解决方案
1. 中文检索召回质量：纯向量检索对中文术语/缩写召回差。用混合检索——向量召回(语义) + 关键词召回(中文 bigram 分词 + pg_trgm ILIKE 字面匹配)，RRF 倒数排名融合(k=60)取长补短，再 Rerank 精排。
2. 分块策略(简历亮点)：第一层按 Markdown 二级标题(##)切结构块保留语义边界；第二层长块(>400 字)按句末标点递归二次切；相邻块重叠 80 字避免边界知识点被切断；每块记 section/chunk_index 元数据。块大小与重叠都做成配置，用评测集实测调优（中文甜点区 300-500 字、重叠 50-100 字）。
3. Agent 化多步问答：单轮检索答不了"对比/综合"类问题。ReAct 循环(THOUGHT→ACTION→ACTION_INPUT→ANSWER)，最多 5 步，工具含语义检索/关键词检索/列文档/读文档；每步结果按全局编号回填，最终答案 [N] 与引用来源一一对应；还有"过早放弃"拦截——模型说没找到但实际没检索时强制补一次语义检索。
4. 多轮对话指代：追问"那它呢"无法直接检索，用 LLM 把追问改写成自包含检索词并把历史并入提示词。
5. 评测闭环：评测集(问题 + 期望关键词 + 参考答案，含拒答题)跑 Recall@K + LLM-as-judge，HTML 报告可回溯每题；差评经点赞/点踩反馈回流扩充评测集。

## 量化成果
- 后端 45 个单元测试 + 10 个 Testcontainers 集成测试；前端 23 个 Vitest 用例；JaCoCo 覆盖率门禁（核心分块/检索类 ≥80%）。
- 混合检索(RRF 融合)相对纯向量检索，评测集 Recall@K 有明显提升（关键词路补齐语义盲区）。
- 里程碑 M1~M5 全完成：PGVector 建表+索引(HNSW+pg_trgm)、Markdown 入库、混合检索+Rerank、SSE 流式+多轮记忆、评测闭环、自动化测试、容器化、CI/CD、反馈闭环、Agent 化问答。
- 种子数据：随 Flyway 预切 3 篇示例笔记(Vue3/Spring Boot/SQL)，本地一键即可提问演示。

## 复盘与可追问点
- 为什么用 PGVector 而不是独立向量库(Milvus)：demo 规模，向量与结构化数据同库省一套运维，HNSW 索引足够。
- 为什么 RRF 融合而不是加权求和：两路分数尺度不同(余弦距离 vs 命中次数)，RRF 只关心排名、免调参、鲁棒。
- Agent 与普通 RAG 的边界：普通 RAG 一步检索即生成；Agent 适合"多跳/对比/需反思再检索"的问题，代价是多次 LLM 调用更慢更贵——无 LLM key 时自动降级为本地 hash 向量。
- Embedding 无 key 兜底：本地 hash 向量(字符袋+哈希分桶)质量低于语义模型但零成本可跑通，用于演示降级；单实例用内存任务表 + SSE 推送，多实例需换 Redis/DB。

## 知识库更新机制
- 知识库文档放在 `backend/src/main/resources/kb/*.md`，**应用启动时幂等灌入**(按文件名 + content_hash 去重：同名同内容跳过，内容变化则删旧重灌)，重启不会重复入库；
- 站长可通过管理接口手动触发重建(`POST /api/admin/kb/rebuild`)，修改一篇 md 后重启或调该接口即生效，无需重新打包；
- 上线后支持 `KB_DIR` 环境变量指向服务器外部目录，改内容不用重建镜像，重启即生效。
