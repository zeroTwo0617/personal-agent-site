<script setup lang="ts">
import { ref, nextTick, computed } from 'vue'
import { useChatStore } from '@/store/chat'
import { ask, openChatStream } from '@/api/chat'
import { submitFeedback } from '@/api/feedback'
import type { ChatMessage, ChatTurn } from '@/types'

const store = useChatStore()
const input = ref('')
const deepThink = ref(true)
const sending = ref(false)
const listEl = ref<HTMLElement | null>(null)

const suggestions = [
  '做一下自我介绍',
  '介绍一下你的 RAG 项目',
  '你在项目里遇到过最大的困难是什么',
  '你的技术栈有哪些'
]

function scrollBottom() {
  nextTick(() => { if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight })
}

function history(): ChatTurn[] {
  return store.messages.slice(-10).flatMap((m): ChatTurn[] => {
    if (m.role === 'user') return [{ role: 'user', content: m.content }]
    if (m.role === 'ai' && m.content) return [{ role: 'assistant', content: m.content }]
    return []
  })
}

async function submit(text?: string) {
  const q = (text ?? input.value).trim()
  if (!q || sending.value || q.length > 500) return
  input.value = ''
  store.add({ role: 'user', content: q })
  const ai = store.add({ role: 'ai', content: '', status: 'streaming', agentSteps: [] })
  sending.value = true
  scrollBottom()

  try {
    const resp = await ask(q, 5, history(), deepThink.value ? 'agent' : 'normal')
    const taskId = resp.data.taskId
    openChatStream(taskId, {
      onDelta: (t) => {
        ai.content += t
        ai.status = 'streaming'
        store.persist()
        scrollBottom()
      },
      onAgentStep: (step) => {
        ai.agentSteps = ai.agentSteps || []
        const i = ai.agentSteps.findIndex((s) => s.step === step.step && s.tool === step.tool && s.status === 'running')
        if (step.status === 'done' || step.status === 'error') {
          if (i >= 0) ai.agentSteps[i] = step
          else ai.agentSteps.push(step)
        } else {
          ai.agentSteps.push(step)
        }
        scrollBottom()
      },
      onThinking: (t) => {
        ai.thinking = (ai.thinking || '') + t
        ai.thinkOpen = true   // 思考中保持展开
        store.persist()
        scrollBottom()
      },
      onDone: (payload) => {
        if (!ai.content) ai.content = payload.answer
        ai.sources = payload.sources
        ai.qaId = payload.qaId
        ai.status = 'completed'
        ai.thinkOpen = false   // 给完答案回收起
        store.persist()
        scrollBottom()
      },
      onError: (msg) => {
        ai.status = 'failed'
        ai.content = ai.content || msg
        store.persist()
        scrollBottom()
      }
    })
  } catch (e) {
    ai.status = 'failed'
    ai.content = (e as Error).message || '请求失败，请重试'
    store.persist()
  } finally {
    sending.value = false
    scrollBottom()
  }
}

function newChat() {
  store.reset()
}

async function feedback(msg: ChatMessage, rating: 1 | -1) {
  if (!msg.qaId) return
  try { await submitFeedback(msg.qaId, rating) } catch { /* 忽略 */ }
}

function toggleThink(m: ChatMessage) {
  m.thinkOpen = !m.thinkOpen
}

const toolLabel: Record<string, string> = {
  retrieve: '语义检索',
  retrieve_keyword: '关键词检索',
  list_documents: '列出文档',
  get_document: '读取文档'
}

const toolName = (tool: string) => toolLabel[tool] || tool
const canSend = computed(() => !sending.value && input.value.trim().length > 0 && input.value.length <= 500)
</script>

<template>
  <div class="chat-page">
    <header class="topbar">
      <router-link to="/" class="back">← 返回主页</router-link>
      <div class="id-badge">AI 分身 · 基于本人真实经历作答</div>
      <div class="controls">
        <label class="toggle">
          <input type="checkbox" v-model="deepThink" />
          深度思考
        </label>
        <button class="ghost" @click="newChat">新对话</button>
      </div>
    </header>

    <div class="list" ref="listEl">
      <div v-if="store.messages.length === 0" class="empty">
        <h2>你好，我是郑梓恒的 AI 分身 👋</h2>
        <p>关于我的项目、技术栈、经历，任何问题都可以问我。</p>
        <div class="suggests">
          <button v-for="s in suggestions" :key="s" class="chip" @click="submit(s)">{{ s }}</button>
        </div>
      </div>

      <div v-for="(m, i) in store.messages" :key="i" class="msg" :class="m.role">
        <div class="bubble">
          <!-- Agent 思考步骤 -->
          <div v-if="m.agentSteps && m.agentSteps.length" class="steps">
            <div v-for="(s, j) in m.agentSteps" :key="j" class="step" :class="s.status">
              <span class="dot" />
              <span class="step-tool">{{ toolName(s.tool) }}</span>
              <span class="step-summary">{{ s.status === 'running' ? '进行中…' : s.summary || '' }}</span>
            </div>
          </div>
          <!-- 思考过程：思考中展开、答完自动收起，可点击展开/收起 -->
          <div v-if="m.thinking" class="think-block">
            <button class="think-toggle" @click="toggleThink(m)">
              💭 {{ m.status === 'streaming' ? '思考中…' : '思考过程' }}
            </button>
            <div v-if="m.thinkOpen" class="think-content">{{ m.thinking }}</div>
          </div>
          <span v-if="m.content" class="content" style="white-space: pre-wrap">{{ m.content }}</span>
          <span v-else-if="m.status === 'streaming'" class="cursor">▍</span>
          <div v-if="m.sources && m.sources.length" class="sources">
            <div class="src-title">引用来源</div>
            <div v-for="(s, si) in m.sources" :key="si" class="src">{{ s.docName }} · {{ s.section }}</div>
          </div>
          <div v-if="m.role === 'ai' && m.status === 'completed' && m.qaId" class="fb">
            <button class="mini" @click="feedback(m, 1)">👍</button>
            <button class="mini" @click="feedback(m, -1)">👎</button>
          </div>
        </div>
      </div>
    </div>

    <footer class="inputbar">
      <textarea
        v-model="input"
        :maxlength="500"
        rows="1"
        placeholder="向 TA 提问…（回车发送，Shift+回车换行）"
        @keydown.enter.exact.prevent="submit()"
      />
      <div class="meta">{{ input.length }}/500</div>
      <button :disabled="!canSend" @click="submit()">发送</button>
    </footer>
    <div class="privacy">本站为 AI 分身，内容基于本人简历与项目经历，如有出入以本人为准。</div>
  </div>
</template>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: 100%; }
.topbar { display: flex; align-items: center; gap: 16px; padding: 12px 20px; background: var(--bg-soft); border-bottom: 1px solid var(--border); }
.back { color: var(--text-dim); }
.id-badge { flex: 1; text-align: center; font-size: 12px; color: var(--accent-soft); }
.controls { display: flex; gap: 10px; align-items: center; }
.toggle { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-dim); cursor: pointer; }
.ghost { background: transparent; border: 1px solid var(--border); color: var(--text-dim); padding: 6px 12px; }
.list { flex: 1; overflow-y: auto; padding: 24px 20px; }
.empty { text-align: center; margin-top: 80px; }
.empty h2 { margin-bottom: 8px; }
.empty p { color: var(--text-dim); margin-bottom: 24px; }
.suggests { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
.chip { background: var(--card); border: 1px solid var(--border); color: var(--text); padding: 8px 14px; }
.msg { display: flex; margin-bottom: 16px; }
.msg.user { justify-content: flex-end; }
.msg.ai { justify-content: flex-start; }
.bubble { max-width: 78%; background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 12px 16px; }
.msg.user .bubble { background: var(--accent); border: none; }
.steps { margin-bottom: 8px; display: flex; flex-wrap: wrap; gap: 6px; }
.think-block { margin-bottom: 8px; border: 1px solid var(--border); border-radius: 8px; overflow: hidden; }
.think-toggle { width: 100%; text-align: left; background: var(--bg-soft); border: none; color: var(--text-dim); padding: 6px 12px; font-size: 12px; cursor: pointer; }
.think-toggle:hover { color: var(--text); }
.think-content { padding: 10px 12px; font-size: 12px; color: var(--text-dim); white-space: pre-wrap; max-height: 240px; overflow-y: auto; border-top: 1px solid var(--border); }
.step { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: var(--text-dim); background: var(--bg-soft); padding: 3px 8px; border-radius: 20px; }
.step.running .dot { background: var(--warn); animation: pulse 1s infinite; }
.step.done .dot { background: var(--ok); }
.step.error .dot { background: var(--err); }
.dot { width: 7px; height: 7px; border-radius: 50%; }
@keyframes pulse { 50% { opacity: 0.3; } }
.cursor { animation: blink 1s steps(1) infinite; color: var(--accent-soft); }
@keyframes blink { 50% { opacity: 0; } }
.sources { margin-top: 10px; border-top: 1px solid var(--border); padding-top: 8px; }
.src-title { font-size: 12px; color: var(--text-dim); margin-bottom: 4px; }
.src { font-size: 12px; color: var(--accent-soft); }
.fb { margin-top: 8px; display: flex; gap: 6px; }
.mini { background: transparent; border: 1px solid var(--border); padding: 3px 8px; }
.inputbar { display: flex; align-items: center; gap: 10px; padding: 12px 20px; background: var(--bg-soft); border-top: 1px solid var(--border); }
.inputbar textarea { flex: 1; resize: none; }
.meta { font-size: 11px; color: var(--text-dim); }
.privacy { text-align: center; font-size: 11px; color: var(--text-dim); padding: 6px; }
</style>
