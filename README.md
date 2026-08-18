# 个人 AI 分身站（personal-agent-site）

把个人经历蒸馏成结构化知识库，做一个面试官可"先看先问"的网站：一页式简历落地页 + 与 AI 分身对话（第一人称"我是本人"、只答知识库事实、引用可溯源）。

## 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Vue 3 + Vite + TypeScript + Pinia + Vue Router |
| 后端 | Spring Boot 3.2 + MyBatis-Plus + Spring Security(JWT) + Flyway（包名 `me.zhengziheng.agent`） |
| 数据 | PostgreSQL + pgvector（向量）+ pg_trgm（关键词） |
| RAG | Markdown 分块 → Embedding(bge-m3) → 混合检索(RRF) → Rerank → LLM 流式(SSE) + Agent(ReAct) |
| 部署 | CI(GitHub Actions) 构建镜像推 GHCR → 服务器 docker compose pull 运行；Nginx + certbot |

## 目录

```
backend/     # Spring Boot 后端（src/main/resources/kb/ 为知识库）
frontend/    # Vue3 前端（落地页 / 聊天页 / 管理页）
deploy/      # docker-compose + nginx.conf + .env.example
docs/        # PLAN / 需求分析 / 可行性分析 / 技术方案 / 蒸馏模板
eval/        # 面试官高频问题评测集（待补）
```

## 本地开发

```bash
# 后端（需本机 Docker 起 pgvector，或改 DB_HOST 指向已有 PG）
cd backend
# 1) 起数据库：docker compose -f ../deploy/docker-compose.yml up -d pg
# 2) 建本地密钥配置（gitignored）
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# 填 LLM_API_KEY / EMBEDDING_API_KEY
# 3) 启动
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 前端
cd frontend && npm install && npm run dev   # http://localhost:5173，已代理 /api 到 8080
```

## 关键配置（环境变量）

见 `deploy/.env.example`：LLM（DeepSeek）、Embedding（硅基流动 bge-m3）、站长账号、CORS、限流、人设展示名等。全部 gitignored，仓库零密钥。

## 设计文档

- `docs/技术方案.md` —— 详细技术设计（接口/配置/迁移/部署）
- `docs/知识库蒸馏模板.md` —— 如何新增一篇项目深挖

## 知识库

`backend/src/main/resources/kb/*.md`，启动时幂等灌入（name + content_hash 去重），改后重启或 `POST /api/admin/kb/rebuild` 生效。
