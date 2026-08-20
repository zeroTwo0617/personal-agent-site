<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { profile } from '@/data/profile'

const activeSection = ref('home')
const scrolled = ref(false)
const isOpen = ref(false)

const navItems = [
  { name: '首页', href: '#home' },
  { name: '关于', href: '#about' },
  { name: '技能', href: '#skills' },
  { name: '项目', href: '#projects' },
  { name: '经历', href: '#experience' },
  { name: 'AI 分身', href: '#ai' },
  { name: '联系', href: '#contact' }
]

const sections = navItems.map(item => item.href.slice(1))

function onScroll() {
  scrolled.value = window.scrollY > 10
  let current = 'home'
  for (const id of sections) {
    const el = document.getElementById(id)
    if (el) {
      const rect = el.getBoundingClientRect()
      if (rect.top <= 160 && rect.bottom >= 160) current = id
    }
  }
  activeSection.value = current
}

function scrollTo(e: MouseEvent, href: string) {
  e.preventDefault()
  const el = document.querySelector(href) as HTMLElement | null
  if (el) window.scrollTo({ top: el.offsetTop - 72, behavior: 'smooth' })
  activeSection.value = href.slice(1)
  isOpen.value = false
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})

onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))
</script>

<template>
  <div class="portfolio">
    <div class="bg-blobs" aria-hidden="true">
      <span class="blob blob-1" />
      <span class="blob blob-2" />
      <span class="blob blob-3" />
    </div>

    <header class="site-header" :class="{ scrolled }">
      <div class="container header-inner">
        <a href="#home" class="brand" @click.prevent="scrollTo($event, '#home')">
          <span class="brand-mark">ZZH</span>
          <span class="brand-text">
            <span class="brand-name">{{ profile.name }}</span>
            <span class="brand-sub">AI 分身</span>
          </span>
        </a>
        <nav class="desktop-nav">
          <a
            v-for="item in navItems"
            :key="item.href"
            :href="item.href"
            :class="{ active: activeSection === item.href.slice(1) }"
            @click.prevent="scrollTo($event, item.href)"
          >{{ item.name }}</a>
        </nav>
        <div class="header-actions">
          <router-link to="/chat" class="btn btn-primary btn-sm">向 TA 提问</router-link>
          <button class="menu-btn" :aria-expanded="isOpen" @click="isOpen = !isOpen">
            <span />
            <span />
            <span />
          </button>
        </div>
      </div>
      <div v-if="isOpen" class="mobile-nav">
        <a v-for="item in navItems" :key="item.href" :href="item.href" @click.prevent="scrollTo($event, item.href)">{{ item.name }}</a>
      </div>
    </header>

    <main>
      <!-- Hero -->
      <section id="home" class="hero">
        <div class="container hero-inner">
          <span class="eyebrow">Personal AI Agent · 在线分身</span>
          <h1>你好，我是 <span class="gradient-text">{{ profile.name }}</span></h1>
          <p class="hero-title">{{ profile.title }}</p>
          <p class="hero-tagline">{{ profile.tagline }}</p>
          <div class="hero-actions">
            <router-link to="/chat" class="btn btn-primary">
              <span>向 TA 提问</span>
              <span class="arrow">→</span>
            </router-link>
            <a href="#skills" class="btn btn-ghost" @click.prevent="scrollTo($event, '#skills')">浏览技能栈</a>
          </div>
          <div class="hero-meta">
            <span class="online"><i class="dot" />在线</span>
            <span>知识库驱动 · 回答可溯源</span>
          </div>
        </div>
      </section>

      <!-- About -->
      <section id="about" class="section">
        <div class="container">
          <span class="eyebrow">01 / About</span>
          <h2>关于我</h2>
          <p class="lead">{{ profile.about }}</p>
          <div class="feature-grid">
            <div v-for="(s, i) in profile.skills" :key="s.name" class="feature-card">
              <span class="feature-index mono">{{ String(i + 1).padStart(2, '0') }}</span>
              <h3>{{ s.name }}</h3>
              <p>{{ s.desc }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- Skills -->
      <section id="skills" class="section section-alt">
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
                <span v-else class="project-link muted">进行中</span>
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
      <section id="experience" class="section section-alt">
        <div class="container">
          <span class="eyebrow">04 / Experience</span>
          <h2>经历</h2>
          <div v-if="profile.experience.length" class="timeline">
            <div v-for="(e, i) in profile.experience" :key="i" class="timeline-item">
              <span class="timeline-dot" />
              <div class="timeline-card">
                <h3>{{ (e as any).company || (e as any).title || '经历' }}</h3>
                <p class="timeline-period mono">{{ (e as any).period || (e as any).time || '' }}</p>
                <p>{{ (e as any).desc || (e as any).summary || '' }}</p>
              </div>
            </div>
          </div>
          <div v-else class="empty-card">
            <p>完整经历正在整理中，你可以直接向我的 AI 分身提问了解。</p>
            <router-link to="/chat" class="btn btn-primary">向 TA 提问</router-link>
          </div>
        </div>
      </section>

      <!-- AI Clone -->
      <section id="ai" class="section">
        <div class="container">
          <div class="ai-card">
            <span class="eyebrow">05 / AI Clone</span>
            <h2>AI 分身</h2>
            <p>关于项目、技术栈、学习经历，任何问题都可以直接问我的 AI 分身。回答基于知识库，可溯源。</p>
            <router-link to="/chat" class="btn btn-primary">
              开始对话 <span class="arrow">→</span>
            </router-link>
          </div>
        </div>
      </section>
    </main>

    <!-- Contact / Footer -->
    <footer id="contact" class="site-footer">
      <div class="container footer-inner">
        <div>
          <span class="eyebrow">06 / Contact</span>
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
  </div>
</template>

<style scoped>
.portfolio {
  min-height: 100%;
  position: relative;
  background:
    radial-gradient(circle at 10% -10%, rgba(11, 110, 107, 0.08), transparent 34%),
    radial-gradient(circle at 90% 5%, rgba(232, 100, 60, 0.06), transparent 30%),
    var(--bg);
  font-family: var(--font-body);
  color: var(--text);
}

/* ============ Background blobs ============ */
.bg-blobs {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.45;
}
.blob-1 {
  width: 380px;
  height: 380px;
  top: 8%;
  left: 6%;
  background: rgba(11, 110, 107, 0.20);
}
.blob-2 {
  width: 300px;
  height: 300px;
  top: 45%;
  right: 8%;
  background: rgba(232, 100, 60, 0.16);
}
.blob-3 {
  width: 220px;
  height: 220px;
  bottom: 5%;
  left: 20%;
  background: rgba(11, 110, 107, 0.14);
}

/* ============ Header ============ */
.site-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid transparent;
  transition: background 0.25s ease, border-color 0.25s ease, box-shadow 0.25s ease;
}
.site-header.scrolled {
  background: rgba(255, 255, 255, 0.78);
  border-bottom-color: var(--border);
  box-shadow: 0 6px 20px rgba(23, 32, 43, 0.05);
}
.header-inner {
  display: flex;
  align-items: center;
  gap: 24px;
  height: 68px;
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--text);
}
.brand:hover { color: var(--text); }
.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--accent);
  color: #fff;
  font-family: var(--font-code);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  box-shadow: 0 6px 14px rgba(11, 110, 107, 0.22);
}
.brand-text { display: flex; flex-direction: column; line-height: 1.2; }
.brand-name { font-weight: 700; font-size: 15px; }
.brand-sub { font-size: 11px; color: var(--text-dim); font-family: var(--font-code); letter-spacing: 0.06em; }

.desktop-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}
.desktop-nav a {
  position: relative;
  padding: 7px 12px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-dim);
  transition: color 0.2s ease, background 0.2s ease;
}
.desktop-nav a:hover { color: var(--accent); background: var(--hover); }
.desktop-nav a.active { color: var(--accent); background: rgba(11, 110, 107, 0.10); font-weight: 600; }
.desktop-nav a.active::after {
  content: "";
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 2px;
  height: 2px;
  border-radius: 2px;
  background: var(--accent);
}

.header-actions { display: flex; align-items: center; gap: 10px; }
.menu-btn {
  display: none;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  width: 38px;
  height: 38px;
  padding: 8px;
  background: transparent;
  border: 1px solid var(--border-strong);
  border-radius: 9px;
}
.menu-btn span {
  display: block;
  height: 2px;
  width: 18px;
  background: var(--text);
  border-radius: 2px;
}

.mobile-nav {
  display: none;
  padding: 10px 24px 18px;
  border-top: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.92);
}
.mobile-nav a {
  display: block;
  padding: 11px 12px;
  border-radius: 10px;
  color: var(--text-dim);
  font-weight: 500;
}
.mobile-nav a:hover { background: var(--hover); color: var(--accent); }

/* ============ Buttons ============ */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border-radius: 999px;
  padding: 12px 22px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease, border-color 0.2s ease;
}
.btn-sm { padding: 9px 16px; font-size: 13px; }
.btn-primary {
  background: var(--accent);
  color: #fff;
  box-shadow: 0 8px 20px rgba(11, 110, 107, 0.20);
}
.btn-primary:hover {
  background: var(--accent-hover);
  color: #fff;
  transform: translateY(-2px);
  box-shadow: 0 12px 26px rgba(11, 110, 107, 0.26);
}
.btn-ghost {
  background: transparent;
  color: var(--text);
  border: 1px solid var(--border-strong);
}
.btn-ghost:hover {
  background: var(--card);
  border-color: var(--accent);
  color: var(--accent);
  transform: translateY(-2px);
}
.arrow { transition: transform 0.2s ease; }
.btn-primary:hover .arrow { transform: translateX(3px); }

/* ============ Hero ============ */
.hero {
  position: relative;
  z-index: 1;
  padding: 96px 0 88px;
  text-align: center;
}
.hero-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.hero h1 {
  font-family: var(--font-display);
  font-size: clamp(40px, 6vw, 68px);
  line-height: 1.1;
  letter-spacing: -0.02em;
  margin: 18px 0 14px;
}
.hero-title {
  color: var(--accent);
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
}
.hero-tagline {
  color: var(--text-dim);
  font-size: 16px;
  line-height: 1.8;
  max-width: 640px;
  margin: 0 auto 30px;
}
.hero-actions { display: flex; gap: 12px; flex-wrap: wrap; justify-content: center; margin-bottom: 26px; }
.hero-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--text-dim);
  align-items: center;
  flex-wrap: wrap;
  justify-content: center;
}
.online { display: inline-flex; align-items: center; gap: 6px; color: var(--accent); font-weight: 600; }
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--ok);
  box-shadow: 0 0 0 0 rgba(14, 159, 110, 0.4);
  animation: pulse 2s infinite;
}
@keyframes pulse {
  70% { box-shadow: 0 0 0 7px rgba(14, 159, 110, 0); }
  100% { box-shadow: 0 0 0 0 rgba(14, 159, 110, 0); }
}

.gradient-text {
  background: linear-gradient(120deg, var(--accent-strong), var(--accent) 50%, var(--signal));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

/* ============ Sections ============ */
.section {
  position: relative;
  z-index: 1;
  padding: 72px 0;
  scroll-margin-top: 76px;
  border-top: 1px solid var(--border);
}
.section-alt { background: rgba(233, 237, 242, 0.45); }
.section h2 {
  font-family: var(--font-display);
  font-size: 34px;
  letter-spacing: -0.02em;
  margin: 12px 0 28px;
}
.lead {
  color: var(--text-dim);
  font-size: 16px;
  line-height: 1.9;
  max-width: 860px;
  margin-bottom: 36px;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}
.feature-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 22px;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}
.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-md);
  border-color: rgba(11, 110, 107, 0.30);
}
.feature-index { font-size: 12px; color: var(--text-faint); display: block; margin-bottom: 14px; }
.feature-card h3 { font-size: 17px; color: var(--accent-strong); margin-bottom: 8px; }
.feature-card p { font-size: 13px; color: var(--text-dim); line-height: 1.7; }

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
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}
.skill-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-md); border-color: rgba(11, 110, 107, 0.30); }
.skill-index { font-size: 12px; color: var(--text-faint); display: block; margin-bottom: 12px; }
.skill-card h3 { font-size: 17px; margin-bottom: 8px; color: var(--accent-strong); }
.skill-card p { font-size: 13px; color: var(--text-dim); line-height: 1.7; }

.projects-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; }
.project-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}
.project-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-md); border-color: rgba(11, 110, 107, 0.30); }
.project-top { display: flex; justify-content: space-between; align-items: center; }
.project-no { font-size: 12px; color: var(--text-faint); }
.project-link { font-size: 13px; font-weight: 600; }
.project-link.muted { color: var(--text-faint); font-weight: 500; }
.project-card h3 { font-size: 20px; letter-spacing: -0.01em; }
.project-desc { color: var(--text-dim); font-size: 14px; flex: 1; line-height: 1.7; }
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

.timeline { display: flex; flex-direction: column; gap: 14px; max-width: 860px; }
.timeline-item { position: relative; display: grid; grid-template-columns: 18px 1fr; gap: 18px; padding-left: 4px; }
.timeline-dot {
  width: 12px;
  height: 12px;
  margin-top: 22px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 4px rgba(11, 110, 107, 0.14);
}
.timeline-card {
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 18px 22px;
}
.timeline-card h3 { font-size: 16px; margin-bottom: 4px; }
.timeline-period { font-size: 12px; color: var(--text-faint); margin-bottom: 8px; }
.timeline-card p:last-child { color: var(--text-dim); font-size: 14px; line-height: 1.7; }

.empty-card {
  background: var(--card);
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-lg);
  padding: 32px;
  text-align: center;
  color: var(--text-dim);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.ai-card {
  background:
    linear-gradient(135deg, rgba(11, 110, 107, 0.08), rgba(232, 100, 60, 0.06)),
    var(--card);
  border: 1px solid var(--border);
  border-radius: 20px;
  padding: 44px 36px;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  box-shadow: var(--shadow-md);
}
.ai-card h2 { margin-bottom: 0; }
.ai-card p { color: var(--text-dim); max-width: 560px; line-height: 1.8; }

/* ============ Footer ============ */
.site-footer {
  position: relative;
  z-index: 1;
  border-top: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.55);
  padding: 56px 0 28px;
}
.footer-inner { display: flex; justify-content: space-between; gap: 32px; align-items: flex-start; }
.footer h2 { font-family: var(--font-display); font-size: 30px; letter-spacing: -0.02em; margin: 10px 0 12px; }
.footer-note { color: var(--text-dim); max-width: 460px; font-size: 14px; line-height: 1.8; }
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
  transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease;
}
.socials a:hover { transform: translateY(-2px); border-color: var(--accent); color: var(--accent); box-shadow: var(--shadow-sm); }
.social-arrow { color: var(--text-faint); }
.footer-bottom {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding-top: 32px;
  margin-top: 40px;
  border-top: 1px solid var(--border);
  font-size: 12px;
  color: var(--text-faint);
}

/* ============ Responsive ============ */
@media (max-width: 860px) {
  .desktop-nav { display: none; }
  .menu-btn { display: inline-flex; }
  .mobile-nav { display: block; }
  .footer-inner { flex-direction: column; }
}
@media (max-width: 640px) {
  .hero { padding: 64px 0 56px; }
  .hero h1 { font-size: 38px; }
  .section { padding: 52px 0; }
  .section h2 { font-size: 28px; }
  .projects-grid { grid-template-columns: 1fr; }
  .socials { flex-direction: column; width: 100%; }
  .socials a { justify-content: space-between; }
  .footer-bottom { flex-direction: column; text-align: center; }
  .ai-card { padding: 32px 20px; }
}
</style>
