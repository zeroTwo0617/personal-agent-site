<script setup lang="ts">
import { ref } from 'vue'
import { useAdminStore } from '@/store/admin'
import { login, getQa, getFeedbackStats, getKbStatus, rebuildKb } from '@/api/admin'
import type { FeedbackStatsVO, KbStatus, QaLogItem } from '@/types'

const admin = useAdminStore()
const username = ref('')
const password = ref('')
const loggingIn = ref(false)
const loginErr = ref('')

const stats = ref<FeedbackStatsVO | null>(null)
const qa = ref<QaLogItem[]>([])
const qaTotal = ref(0)
const kb = ref<KbStatus | null>(null)
const kbMsg = ref('')

async function doLogin() {
  loggingIn.value = true
  loginErr.value = ''
  try {
    const resp = await login(username.value, password.value)
    admin.setToken(resp.data.token)
    await loadAll()
  } catch (e) {
    loginErr.value = (e as Error).message
  } finally {
    loggingIn.value = false
  }
}

async function loadAll() {
  try { stats.value = (await getFeedbackStats()).data } catch { }
  try { kb.value = (await getKbStatus()).data } catch { }
  await loadQa()
}

async function loadQa(page = 1) {
  try {
    const resp = await getQa(page, 20)
    qa.value = resp.data.list
    qaTotal.value = resp.data.total
  } catch (e) { if ((e as Error).message.includes('401')) admin.logout() }
}

async function doRebuild() {
  kbMsg.value = '重建中…'
  try {
    const resp = await rebuildKb()
    kb.value = resp.data
    kbMsg.value = '重建完成'
  } catch (e) {
    kbMsg.value = (e as Error).message
  }
}

function fmt(s?: string) { return s ? new Date(s).toLocaleString() : '-' }
</script>

<template>
  <div class="admin container">
    <template v-if="!admin.token">
      <div class="login">
        <h2>站长登录</h2>
        <input v-model="username" placeholder="用户名" />
        <input v-model="password" type="password" placeholder="密码" @keydown.enter="doLogin" />
        <button :disabled="loggingIn" @click="doLogin">{{ loggingIn ? '登录中…' : '登录' }}</button>
        <p v-if="loginErr" class="err">{{ loginErr }}</p>
        <router-link to="/">← 返回主页</router-link>
      </div>
    </template>

    <template v-else>
      <header class="bar">
        <router-link to="/">← 主页</router-link>
        <button class="ghost" @click="admin.logout()">退出</button>
      </header>

      <section class="panel">
        <h2>反馈统计</h2>
        <div v-if="stats" class="stats">
          <div>总反馈：{{ stats.total }}</div>
          <div>好评：{{ stats.positive }}</div>
          <div>差评：{{ stats.negative }}</div>
          <div>好评率：{{ stats.positiveRate }}%</div>
        </div>
        <div v-if="stats && stats.recentNegative.length" class="neg">
          <h3>最近差评</h3>
          <div v-for="(n, i) in stats.recentNegative" :key="i" class="neg-item">
            <div class="q">{{ n.question }}</div>
            <div class="c">{{ n.comment || '（无评论）' }}</div>
          </div>
        </div>
      </section>

      <section class="panel">
        <h2>访客提问记录（共 {{ qaTotal }} 条）</h2>
        <div class="qa-list">
          <div v-for="q in qa" :key="q.id" class="qa-item">
            <div class="q">Q: {{ q.question }}</div>
            <div class="a">A: {{ q.answer.slice(0, 120) }}{{ q.answer.length > 120 ? '…' : '' }}</div>
            <div class="t">{{ fmt(q.createdAt) }}</div>
          </div>
        </div>
      </section>

      <section class="panel">
        <h2>知识库</h2>
        <div v-if="kb">文档数：{{ kb.docCount }} · 上次 seed：{{ fmt(kb.lastSeedAt) }}</div>
        <button @click="doRebuild">重建索引</button>
        <span v-if="kbMsg" class="kb-msg">{{ kbMsg }}</span>
      </section>
    </template>
  </div>
</template>

<style scoped>
.admin { padding: 24px 20px; }
.login { max-width: 320px; margin: 80px auto; display: flex; flex-direction: column; gap: 12px; text-align: center; }
.err { color: var(--err); font-size: 13px; }
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.ghost { background: transparent; border: 1px solid var(--border-strong); color: var(--text-dim); }
.ghost:hover { background: var(--hover); color: var(--text); }
.panel { background: var(--card); border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 20px; margin-bottom: 20px; }
.panel h2 { margin-bottom: 12px; font-size: 18px; }
.stats { display: flex; gap: 24px; color: var(--text-dim); }
.neg-item { border-top: 1px solid var(--border); padding: 8px 0; }
.neg-item .q { font-size: 13px; }
.neg-item .c { font-size: 12px; color: var(--err); }
.qa-list { max-height: 400px; overflow-y: auto; }
.qa-item { border-top: 1px solid var(--border); padding: 10px 0; }
.qa-item .q { font-size: 13px; }
.qa-item .a { font-size: 12px; color: var(--text-dim); }
.qa-item .t { font-size: 11px; color: var(--text-dim); }
.kb-msg { margin-left: 10px; font-size: 13px; color: var(--accent-soft); }
</style>
