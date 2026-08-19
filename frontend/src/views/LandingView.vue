<script setup lang="ts">
import { profile } from '@/data/profile'
</script>

<template>
  <div class="landing">
    <header class="topbar">
      <div class="container topbar-inner">
        <a class="brand" href="#top">
          <span class="brand-mark">ZZH</span>
          <span class="brand-text">
            <span class="brand-name">{{ profile.name }}</span>
            <span class="brand-sub">AI 分身</span>
          </span>
        </a>
        <nav class="nav">
          <a href="#about">关于</a>
          <a href="#skills">技能</a>
          <a href="#projects">项目</a>
          <a href="#contact">联系</a>
        </nav>
        <router-link to="/chat" class="cta-nav">向 TA 提问</router-link>
      </div>
    </header>

    <main id="top">
      <!-- Hero -->
      <section class="hero">
        <div class="container hero-grid">
          <div class="hero-copy">
            <span class="eyebrow">Personal AI Agent · 在线分身</span>
            <h1>{{ profile.name }}</h1>
            <p class="hero-title">{{ profile.title }}</p>
            <p class="hero-tagline">{{ profile.tagline }}</p>
            <div class="hero-actions">
              <router-link to="/chat" class="btn btn-primary">
                <span>向 TA 提问</span>
                <span class="btn-arrow">→</span>
              </router-link>
              <a href="#skills" class="btn btn-ghost">浏览技能栈</a>
            </div>
            <div class="hero-meta mono">
              <span class="online"><i class="dot" />在线</span>
              <span>知识库驱动 · 回答可溯源</span>
            </div>
          </div>

          <div class="hero-console" aria-label="示例问答">
            <div class="console-head mono">
              <span class="traffic"><i /><i /><i /></span>
              <span>ai-clone@zhengziheng ~ interactive</span>
            </div>
            <div class="console-body">
              <p class="line mono q"><span class="prompt">面试官&nbsp;</span>介绍一下你的 RAG 项目？</p>
              <p class="line mono a">
                <span class="tag">AI&nbsp;</span>
                我独立搭建了基于 Spring Boot + pgvector 的 RAG 知识库问答系统，
                包含混合检索、ReAct Agent 和评测闭环，全程可溯源。
              </p>
              <p class="line mono cite">[1] 04-项目-rag-kb-demo.md · 项目概述</p>
              <p class="line mono cursor-line"><span class="prompt">&gt;</span> <span class="cursor">▍</span></p>
            </div>
          </div>
        </div>
      </section>

      <!-- About -->
      <section id="about" class="section">
        <div class="container">
          <span class="eyebrow">01 / About</span>
          <h2>关于我</h2>
          <div class="about-card" v-if="profile.about">
            <p>{{ profile.about }}</p>
          </div>
        </div>
      </section>

      <!-- Skills -->
      <section id="skills" class="section">
        <div class="container">
          <span class="eyebrow">02 / Skills</span>
          <h2>技能栈</h2>
          <div class="skills-grid">
            <div v-for="(s, i) in profile.skills" :key="s.name" class="skill-card">
              <span class="skill-index mono">{{ String(i + 1).padStart(2, '0') }}</span>
              <h3>{{ s.name }}</h3>
              <p>{{ s.desc }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- Projects -->
      <section id="projects" class="section">
        <div class="container">
          <span class="eyebrow">03 / Projects</span>
          <h2>项目</h2>
          <div class="projects-grid">
            <article v-for="(p, i) in profile.projects" :key="p.title" class="project-card">
              <div class="project-top">
                <span class="project-no mono">P{{ String(i + 1).padStart(2, '0') }}</span>
                <a v-if="p.url" :href="p.url" target="_blank" rel="noopener" class="project-link">访问 ↗</a>
              </div>
              <h3>{{ p.title }}</h3>
              <p class="project-desc">{{ p.desc }}</p>
              <div class="tech-tags">
                <span v-for="t in p.tech" :key="t" class="tag mono">{{ t }}</span>
              </div>
            </article>
          </div>
        </div>
      </section>

      <!-- Experience -->
      <section id="experience" class="section" v-if="profile.experience.length">
        <div class="container">
          <span class="eyebrow">04 / Experience</span>
          <h2>经历</h2>
          <div class="timeline">
            <div v-for="(e, i) in profile.experience" :key="i" class="timeline-item">
              <div class="timeline-marker mono">{{ String(i + 1).padStart(2, '0') }}</div>
              <div class="timeline-card">
                <h3>{{ (e as any).company || (e as any).title || '经历' }}</h3>
                <p class="timeline-period mono">{{ (e as any).period || (e as any).time || '' }}</p>
                <p>{{ (e as any).desc || (e as any).summary || '' }}</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Contact / Footer -->
      <footer id="contact" class="footer">
        <div class="container footer-inner">
          <div>
            <span class="eyebrow">05 / Contact</span>
            <h2>保持联系</h2>
            <p class="footer-note">这是我的 AI 分身，回答基于本人简历与项目经历；如有出入，以本人为准。</p>
          </div>
          <div class="socials">
            <a v-for="s in profile.socials" :key="s.name" :href="s.url" target="_blank" rel="noopener">
              <span class="social-name">{{ s.name }}</span>
              <span class="social-arrow">↗</span>
            </a>
          </div>
        </div>
        <div class="container footer-bottom mono">
          <span>© {{ new Date().getFullYear() }} {{ profile.name }} · personal-agent-site</span>
          <router-link to="/admin">管理入口</router-link>
        </div>
      </footer>
    </main>
  </div>
</template>

<style scoped>
.landing { min-height: 100%; }

/* ============ Top bar ============ */
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(243, 245, 247, 0.82);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--border);
}
.topbar-inner {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 64px;
}
.brand { display: inline-flex; align-items: center; gap: 10px; color: var(--text); }
.brand:hover { color: var(--text); }
.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  background: var(--accent);
  color: #fff;
  border-radius: 9px;
  font-family: var(--font-code);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  box-shadow: 0 4px 10px rgba(11, 110, 107, 0.22);
}
.brand-text { display: flex; flex-direction: column; line-height: 1.2; }
.brand-name { font-weight: 700; font-size: 15px; }
.brand-sub { font-size: 11px; color: var(--text-dim); font-family: var(--font-code); letter-spacing: 0.06em; }
.nav { display: flex; gap: 20px; margin-left: auto; }
.nav a { font-size: 14px; color: var(--text-dim); }
.nav a:hover { color: var(--accent); }
.cta-nav {
  background: var(--accent);
  color: #fff;
  border-radius: 999px;
  padding: 8px 18px;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(11, 110, 107, 0.16);
  transition: background var(--dur) var(--ease), box-shadow var(--dur) var(--ease), transform var(--dur) var(--ease);
}
.cta-nav:hover { background: var(--accent-hover); color: #fff; transform: translateY(-1px); }

/* ============ Hero ============ */
.hero {
  padding: 72px 0 80px;
}
.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 56px;
  align-items: center;
}
.hero h1 {
  font-family: var(--font-display);
  font-size: clamp(44px, 6vw, 72px);
  line-height: 1.04;
  letter-spacing: -0.03em;
  margin: 18px 0 12px;
}
.hero-title {
  color: var(--accent);
  font-size: 17px;
  font-weight: 600;
  margin-bottom: 16px;
}
.hero-tagline {
  color: var(--text-dim);
  font-size: 16px;
  max-width: 540px;
  margin-bottom: 28px;
}
.hero-actions { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 24px; }
.btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border-radius: var(--radius-md);
  padding: 12px 22px;
  font-size: 15px;
  font-weight: 600;
  transition: transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease), background var(--dur) var(--ease), border-color var(--dur) var(--ease);
}
.btn-primary { background: var(--accent); color: #fff; box-shadow: 0 6px 16px rgba(11, 110, 107, 0.22); }
.btn-primary:hover { background: var(--accent-hover); color: #fff; transform: translateY(-2px); box-shadow: 0 10px 24px rgba(11, 110, 107, 0.26); }
.btn-arrow { transition: transform var(--dur) var(--ease); }
.btn-primary:hover .btn-arrow { transform: translateX(3px); }
.btn-ghost { background: transparent; color: var(--text); border: 1px solid var(--border-strong); }
.btn-ghost:hover { background: var(--card); border-color: var(--accent); color: var(--accent); transform: translateY(-2px); }
.hero-meta { display: flex; gap: 16px; font-size: 12px; color: var(--text-dim); align-items: center; }
.online { display: inline-flex; align-items: center; gap: 6px; color: var(--accent); font-weight: 600; }
.online .dot { width: 7px; height: 7px; border-radius: 50%; background: var(--ok); box-shadow: 0 0 0 0 rgba(14, 159, 110, 0.4); animation: pulse 2s infinite; }
@keyframes pulse { 70% { box-shadow: 0 0 0 7px rgba(14, 159, 110, 0); } 100% { box-shadow: 0 0 0 0 rgba(14, 159, 110, 0); } }

.hero-console {
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
}
.console-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: var(--bg-soft);
  border-bottom: 1px solid var(--border);
  font-size: 11px;
  color: var(--text-dim);
}
.traffic { display: inline-flex; gap: 6px; }
.traffic i { width: 9px; height: 9px; border-radius: 50%; background: #c9d2dc; }
.traffic i:first-child { background: #f6a192; }
.traffic i:nth-child(2) { background: #f4cf8f; }
.traffic i:nth-child(3) { background: #9bc9b8; }
.console-body { padding: 20px 20px 18px; display: flex; flex-direction: column; gap: 12px; }
.line { font-size: 12px; line-height: 1.7; }
.line.q { color: var(--text); }
.line.a { color: var(--text-dim); background: var(--surface-2); border-left: 2px solid var(--accent); padding: 10px 12px; border-radius: 0 var(--radius-md) var(--radius-md) 0; }
.line.a .tag { color: var(--accent); font-weight: 700; }
.line.cite { color: var(--text-faint); font-size: 11px; }
.prompt { color: var(--signal); font-weight: 600; }
.cursor-line { color: var(--text-dim); }
.cursor { color: var(--accent); animation: blink 1s steps(1) infinite; }
@keyframes blink { 50% { opacity: 0; } }

/* ============ Sections ============ */
.section { padding: 64px 0; border-top: 1px solid var(--border); scroll-margin-top: 72px; }
.section h2 { font-family: var(--font-display); font-size: 34px; letter-spacing: -0.02em; margin: 12px 0 28px; }

.about-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 28px 32px;
  font-size: 16px;
  color: var(--text-dim);
  line-height: 1.85;
  box-shadow: var(--shadow-sm);
}

.skills-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}
.skill-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 20px;
  transition: transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease), border-color var(--dur) var(--ease);
}
.skill-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-md); border-color: rgba(11, 110, 107, 0.28); }
.skill-index { font-size: 12px; color: var(--text-faint); display: block; margin-bottom: 12px; }
.skill-card h3 { font-size: 17px; margin-bottom: 8px; color: var(--accent-strong); }
.skill-card p { font-size: 13px; color: var(--text-dim); }

.projects-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; }
.project-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease), border-color var(--dur) var(--ease);
}
.project-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-md); border-color: rgba(11, 110, 107, 0.28); }
.project-top { display: flex; justify-content: space-between; align-items: center; }
.project-no { font-size: 12px; color: var(--text-faint); }
.project-link { font-size: 13px; font-weight: 600; }
.project-card h3 { font-size: 20px; letter-spacing: -0.01em; }
.project-desc { color: var(--text-dim); font-size: 14px; flex: 1; }
.tech-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.tag {
  display: inline-block;
  font-family: var(--font-code);
  font-size: 11px;
  background: var(--bg-soft);
  border: 1px solid var(--border);
  color: var(--text-dim);
  padding: 4px 9px;
  border-radius: 999px;
}

.timeline { display: flex; flex-direction: column; gap: 14px; }
.timeline-item { display: grid; grid-template-columns: 44px 1fr; gap: 16px; }
.timeline-marker {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent-strong);
  font-size: 12px;
  font-weight: 700;
  margin-top: 4px;
}
.timeline-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 18px 22px;
}
.timeline-card h3 { font-size: 16px; margin-bottom: 4px; }
.timeline-period { font-size: 12px; color: var(--text-faint); margin-bottom: 8px; }
.timeline-card p:last-child { color: var(--text-dim); font-size: 14px; }

/* ============ Footer ============ */
.footer { border-top: 1px solid var(--border); background: rgba(255,255,255,0.5); padding: 56px 0 28px; }
.footer-inner { display: flex; justify-content: space-between; gap: 32px; align-items: flex-start; }
.footer h2 { font-family: var(--font-display); font-size: 30px; letter-spacing: -0.02em; margin: 10px 0 12px; }
.footer-note { color: var(--text-dim); max-width: 460px; font-size: 14px; }
.socials { display: flex; gap: 12px; }
.socials a {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  padding: 10px 16px;
  font-weight: 600;
  color: var(--text);
  transition: transform var(--dur) var(--ease), border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}
.socials a:hover { transform: translateY(-2px); border-color: var(--accent); color: var(--accent); box-shadow: var(--shadow-sm); }
.social-arrow { color: var(--text-faint); }
.footer-bottom { display: flex; justify-content: space-between; gap: 16px; padding-top: 32px; margin-top: 40px; border-top: 1px solid var(--border); font-size: 12px; color: var(--text-faint); }

/* ============ Responsive ============ */
@media (max-width: 860px) {
  .hero-grid { grid-template-columns: 1fr; gap: 32px; }
  .hero { padding: 48px 0 56px; }
  .footer-inner { flex-direction: column; }
}
@media (max-width: 640px) {
  .nav { display: none; }
  .topbar-inner { gap: 12px; }
  .cta-nav { padding: 7px 12px; font-size: 12px; }
  .section { padding: 48px 0; }
  .section h2 { font-size: 28px; }
  .hero h1 { font-size: 40px; }
  .about-card { padding: 20px; }
  .projects-grid { grid-template-columns: 1fr; }
  .socials { flex-direction: column; width: 100%; }
  .socials a { justify-content: space-between; }
  .footer-bottom { flex-direction: column; text-align: center; }
}
</style>
