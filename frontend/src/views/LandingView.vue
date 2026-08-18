<script setup lang="ts">
import { profile } from '@/data/profile'
</script>

<template>
  <div class="landing">
    <section class="hero">
      <h1>{{ profile.name }}</h1>
      <p class="title">{{ profile.title }}</p>
      <p class="tagline">{{ profile.tagline }}</p>
      <router-link to="/chat"><button class="cta">向 TA 提问 →</button></router-link>
    </section>

    <section class="block" v-if="profile.about">
      <h2>关于我</h2>
      <p>{{ profile.about }}</p>
    </section>

    <section class="block">
      <h2>技能栈</h2>
      <div class="skills">
        <div v-for="s in profile.skills" :key="s.name" class="skill">
          <div class="skill-name">{{ s.name }}</div>
          <div class="skill-desc">{{ s.desc }}</div>
        </div>
      </div>
    </section>

    <section class="block">
      <h2>项目</h2>
      <div class="projects">
        <div v-for="p in profile.projects" :key="p.title" class="project">
          <h3>{{ p.title }}</h3>
          <p>{{ p.desc }}</p>
          <div class="tech">{{ p.tech.join(' · ') }}</div>
        </div>
      </div>
    </section>

    <section class="block" v-if="profile.experience.length">
      <h2>经历</h2>
      <div v-for="(e, i) in profile.experience" :key="i" class="exp">{{ e as any }}</div>
    </section>

    <footer class="footer">
      <div class="socials">
        <a v-for="s in profile.socials" :key="s.name" :href="s.url" target="_blank" rel="noopener">{{ s.name }}</a>
      </div>
      <p class="privacy">本站为 AI 分身，内容基于本人简历与项目经历，如有出入以本人为准。</p>
    </footer>
  </div>
</template>

<style scoped>
.landing { max-width: 860px; margin: 0 auto; padding: 0 20px; }
.hero { text-align: center; padding: 80px 0 60px; }
.hero h1 { font-size: 42px; margin-bottom: 8px; }
.title { color: var(--accent-soft); font-size: 18px; margin-bottom: 12px; }
.tagline { color: var(--text-dim); margin-bottom: 32px; }
.cta { font-size: 16px; padding: 14px 28px; }
.block { padding: 32px 0; border-top: 1px solid var(--border); }
.block h2 { margin-bottom: 16px; }
.skills { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
.skill { background: var(--card); border: 1px solid var(--border); border-radius: 10px; padding: 16px; }
.skill-name { color: var(--accent-soft); font-weight: 600; margin-bottom: 6px; }
.skill-desc { font-size: 13px; color: var(--text-dim); }
.projects { display: grid; gap: 12px; }
.project { background: var(--card); border: 1px solid var(--border); border-radius: 10px; padding: 18px; }
.project h3 { margin-bottom: 8px; }
.project p { color: var(--text-dim); margin-bottom: 8px; }
.tech { font-size: 13px; color: var(--accent-soft); }
.footer { padding: 40px 0; text-align: center; border-top: 1px solid var(--border); }
.socials { display: flex; gap: 16px; justify-content: center; margin-bottom: 16px; }
.privacy { font-size: 12px; color: var(--text-dim); }
</style>
