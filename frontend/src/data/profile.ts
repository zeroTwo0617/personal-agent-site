/**
 * 落地页公开数据（提交仓库）。
 * ⚠️ 联系方式走部署环境变量/构建期注入，不写在这里（避免个人信息进公开仓库）。
 * 【待填】用真实简历数据替换占位内容。
 */
export const profile = {
  name: '郑梓恒',
  title: 'Java / Go 后端开发 · AI 应用工程化',
  tagline: '独立搭建 RAG 知识库问答系统与健身小程序后端，欢迎向我的 AI 分身提问。',
  about: '【待填】自我介绍段落。',
  skills: [
    { name: '后端', desc: 'Java / Spring Boot / Go / MySQL / PostgreSQL' },
    { name: 'RAG / AI', desc: '混合检索 / Rerank / Agent(ReAct) / Embedding / LLM 接入' },
    { name: '前端', desc: 'Vue3 / Vite / TypeScript / Pinia' },
    { name: '工程化', desc: 'Docker / GitHub Actions CI / JUnit / Vitest / 评测闭环' }
  ],
  projects: [
    { title: 'RAG 知识库问答系统', desc: 'Vue3 + Spring Boot + PGVector，混合检索 + Agent 化问答 + 评测闭环', tech: ['Java', 'Spring Boot', 'PGVector', 'Vue3'], url: '' },
    { title: 'FitLog 健身小程序后端', desc: '图片托管 API + AI Agent(chat/search/insight)', tech: ['Java', 'Spring Boot', 'MySQL', 'RAG'], url: '' }
  ],
  experience: [
    // 【待填】实习/工作经历
  ],
  socials: [
    { name: 'GitHub', url: 'https://github.com/Garretqaq' },
    { name: '博客', url: 'https://www.hanhandato.top' }
  ]
}
