/**
 * 落地页公开数据（提交仓库）。
 * ⚠️ 联系方式走部署环境变量/构建期注入，不写在这里（避免个人信息进公开仓库）。
 * 【待填】用真实简历数据替换占位内容。
 */
export const profile = {
  name: '郑梓恒',
  title: 'Agent 开发 / AI 应用工程 · 2026 届应届生',
  tagline: '独立搭建 RAG 知识库问答系统（含 Agent 化多步问答 + 评测闭环）与 FitLog 训练小程序，欢迎向我的 AI 分身提问。',
  about: '我是郑梓恒，广州商学院计算机科学与技术专业 2026 届本科应届生，求职方向是 Agent 开发 / AI 应用工程（也投软件测试）。后端主用 Java/Spring Boot，前端 Vue3，数据库 MySQL 与 PostgreSQL（含 pgvector），实际落地过 Embedding、混合检索、LLM 接入与 Agent(ReAct) 编排。两个项目均从 0 独立做到可演示、可部署。',
  skills: [
    { name: '后端', desc: 'Java / Spring Boot / Go / MySQL / PostgreSQL' },
    { name: 'RAG / AI', desc: '混合检索 / Rerank / Agent(ReAct) / Embedding / LLM 接入' },
    { name: '前端', desc: 'Vue3 / Vite / TypeScript / Pinia' },
    { name: '工程化', desc: 'Docker / GitHub Actions CI / JUnit / Vitest / 评测闭环' }
  ],
  projects: [
    { title: 'RAG 知识库问答系统', desc: 'Vue3 + Spring Boot + PGVector，混合检索 + Agent 化问答 + 评测闭环', tech: ['Java', 'Spring Boot', 'PGVector', 'Vue3'], url: '' },
    { title: 'FitLog 训练记录小程序', desc: '微信小程序原生 + CloudBase，训练记录 + 数据口径统一 + 训练 Agent 云函数', tech: ['微信小程序', 'CloudBase', '云函数', 'LLM'], url: '' }
  ],
  experience: [
    // 【待填】实习/工作经历
  ],
  socials: [
    { name: 'GitHub', url: 'https://github.com/Garretqaq' }
  ]
}
