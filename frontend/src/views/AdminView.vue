<script setup lang="ts">
import { ref } from 'vue'
import { useAdminStore } from '@/store/admin'
import { login, getQa, getFeedbackStats, getKbStatus, rebuildKb, runEval, getEvalReport } from '@/api/admin'
import type { EvalReport, FeedbackStatsVO, KbStatus, QaLogItem } from '@/types'

const admin = useAdminStore()
const username = ref('')
const password = ref('')
const loggingIn = ref(false)
const loginErr = ref('')

const tab = ref<'data' | 'eval'>('data')

const stats = ref<FeedbackStatsVO | null>(null)
const qa = ref<QaLogItem[]>([])
const qaTotal = ref(0)
const kb = ref<KbStatus | null>(null)
const kbMsg = ref('')

const evalReport = ref<EvalReport | null>(null)
const evalRunning = ref(false)
const evalMsg = ref('')
const evalTopK = ref(6)
const evalMode = ref('hybrid')

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
  try { evalReport.value = (await getEvalReport()).data } catch { }
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

async function doEval() {
  evalRunning.value = true
  evalMsg.value = '评测中…（30 题 × 检索+生成+裁判，约 2-4 分钟）'
  evalReport.value = null
  try {
    const resp = await runEval(evalTopK.value, evalMode.value)
    evalReport.value = resp.data
    evalMsg.value = '评测完成'
  } catch (e) {
    evalMsg.value = '评测失败：' + (e as Error).message
  } finally {
    evalRunning.value = false
  }
}

function fmt(s?: string) { return s ? new Date(s).toLocaleString() : '-' }
function pct(v?: number) { return v === null || v === undefined ? '—' : (v * 100).toFixed(1) + '%' }
function itemFlag(it: any): string {
  const flags: string[] = []
  if (it.type === 'reject') {
    if (!it.refusalDetected) flags.push('未拒答')
  } else {
    if (it.recallAtK !== null && it.recallAtK !== undefined && it.recallAtK < 1) flags.push('召回低')
    if (it.faithfulness !== null && it.faithfulness !== undefined && it.faithfulness < 1) flags.push('不忠实')
  }
  return flags.join(' ')
}
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

      <nav class="tabs">
        <button class="tab" :class="{ active: tab === 'data' }" @click="tab = 'data'">数据总览</button>
        <button class="tab" :class="{ active: tab === 'eval' }" @click="tab = 'eval'">评测</button>
      </nav>

      <template v-if="tab === 'data'">
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

      <template v-else>
        <section class="panel">
          <h2>评测（面试官高频问题集）</h2>
          <div class="eval-controls">
            <label>topK <select v-model.number="evalTopK"><option :value="6">6</option><option :value="8">8</option><option :value="10">10</option></select></label>
            <label>模式 <select v-model="evalMode"><option value="hybrid">hybrid（混合检索）</option><option value="vector">vector（纯向量对照）</option></select></label>
            <button :disabled="evalRunning" @click="doEval">{{ evalRunning ? '评测中…' : '跑评测' }}</button>
            <span v-if="evalMsg" class="kb-msg">{{ evalMsg }}</span>
          </div>

          <div v-if="evalReport" class="eval-result">
            <div class="eval-cards">
              <div class="eval-card"><div class="k">召回率 Recall@K</div><div class="v">{{ pct(evalReport.recallAtK) }}</div></div>
              <div class="eval-card"><div class="k">文档命中</div><div class="v">{{ pct(evalReport.docRecall) }}</div></div>
              <div class="eval-card"><div class="k">忠实度（非拒答）</div><div class="v">{{ pct(evalReport.faithfulness) }}</div></div>
              <div class="eval-card"><div class="k">平均耗时</div><div class="v">{{ evalReport.avgLatencyMs }}ms</div></div>
              <div class="eval-card"><div class="k">条数</div><div class="v">{{ evalReport.total }}</div></div>
            </div>
            <div v-if="evalReport.recallByType" class="by-type">
              分类型召回：
              <span v-for="(v, k) in evalReport.recallByType" :key="k" class="type-tag">{{ k }}: {{ (v * 100).toFixed(1) }}%</span>
            </div>
            <p v-if="evalReport.faithfulnessSkipped" class="note">⚠️ 未配置 LLM key，忠实度未计算（当前已配置则正常显示）</p>

            <table class="eval-table">
              <thead><tr><th>ID</th><th>类型</th><th>问题</th><th>召回</th><th>忠实度</th><th>拒答</th><th>标记</th></tr></thead>
              <tbody>
                <tr v-for="it in evalReport.perItem" :key="it.id" :class="{ bad: itemFlag(it) }">
                  <td>{{ it.id }}</td>
                  <td>{{ it.type }}</td>
                  <td class="q">{{ it.question }}</td>
                  <td>{{ pct(it.recallAtK) }}</td>
                  <td>{{ pct(it.faithfulness) }}</td>
                  <td>{{ it.type === 'reject' ? (it.refusalDetected ? '✅' : '❌') : '—' }}</td>
                  <td class="flag">{{ itemFlag(it) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </template>
    </template>
  </div>
</template>

<style scoped>
.admin { padding: 24px 20px; }
.login { max-width: 320px; margin: 80px auto; display: flex; flex-direction: column; gap: 12px; text-align: center; }
.err { color: var(--err); font-size: 13px; }
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.ghost { background: transparent; border: 1px solid var(--border-strong); color: var(--text-dim); }
.ghost:hover { background: var(--hover); color: var(--text); }
.tabs { display: flex; gap: 8px; margin-bottom: 20px; }
.tab { background: var(--card); border: 1px solid var(--border); color: var(--text-dim); padding: 8px 20px; }
.tab.active { background: var(--accent); color: #fff; border-color: var(--accent); }
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
.eval-controls { display: flex; gap: 16px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.eval-controls label { font-size: 13px; color: var(--text-dim); display: flex; align-items: center; gap: 6px; }
.eval-controls select { background: var(--bg-soft); border: 1px solid var(--border); color: var(--text); border-radius: 6px; padding: 4px 8px; }
.eval-cards { display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
.eval-card { background: var(--bg-soft); border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 12px 18px; min-width: 120px; }
.eval-card .k { font-size: 12px; color: var(--text-dim); }
.eval-card .v { font-size: 20px; font-weight: 600; color: var(--text); }
.by-type { margin-bottom: 12px; font-size: 13px; color: var(--text-dim); }
.type-tag { display: inline-block; background: var(--bg-soft); border: 1px solid var(--border); border-radius: 20px; padding: 2px 10px; margin-left: 8px; }
.note { font-size: 12px; color: var(--warn); margin-bottom: 12px; }
.eval-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.eval-table th, .eval-table td { border: 1px solid var(--border); padding: 6px 8px; text-align: left; }
.eval-table th { background: var(--bg-soft); color: var(--text-dim); }
.eval-table td.q { max-width: 320px; }
.eval-table tr.bad { background: rgba(239, 93, 108, 0.08); }
.eval-table .flag { color: var(--err); font-size: 11px; }
</style>
