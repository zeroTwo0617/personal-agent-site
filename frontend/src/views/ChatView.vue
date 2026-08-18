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
    <!-- 左侧栏：仿 dsh（返回主页 / 新对话 / 工作区） -->
    <aside class="sidebar">
      <router-link to="/" class="home-btn">
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7" /></svg>
        <span>返回主页</span>
      </router-link>

      <div class="brand">
        <span class="brand-mark">AI</span>
        <span class="brand-name">分身</span>
      </div>

      <button class="new-chat-btn" @click="newChat">
        <span class="icon" aria-hidden="true">+</span>
        <span>新对话</span>
      </button>

      <nav class="workspace">
        <div class="ws-title">工作区</div>
        <div v-if="store.messages.length === 0" class="ws-empty">暂无历史</div>
        <div v-else class="ws-list">
          <div class="ws-item current">
            <span class="ws-dot" />
            <span class="ws-label">当前对话</span>
          </div>
        </div>
      </nav>

    </aside>

    <!-- 主区 -->
    <section class="main">
      <header class="topbar">
        <div class="id-badge">AI 分身 · 基于本人真实经历作答</div>
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
            <!-- 思考过程 -->
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

      <!-- dsh 风格输入框：上方直接输入，右下角深度思考 + 发送 -->
      <footer class="composer">
        <div class="composer-row">
          <textarea
            v-model="input"
            :maxlength="500"
            rows="1"
            placeholder="向 TA 提问…（回车发送，Shift+回车换行）"
            @keydown.enter.exact.prevent="submit()"
          />
        </div>
        <div class="composer-foot">
          <span class="meta">{{ input.length }}/500</span>
          <div class="foot-right">
            <button class="mode-btn" :class="{ active: deepThink }" @click="deepThink = !deepThink" title="切换深度思考">
              <span class="mode-dot" />
              深度思考
            </button>
            <button class="send-btn" :disabled="!canSend" @click="submit()" title="发送">
              <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5M5 12l7-7 7 7" /></svg>
            </button>
          </div>
        </div>
      </footer>
      <div class="privacy">本站为 AI 分身，内容基于本人简历与项目经历，如有出入以本人为准。</div>
    </section>
  </div>
</template>

<style scoped>
.chat-page { display: flex; height: 100%; min-height: 0; }

/* ============ 左侧栏（dsh 极简风） ============ */
.sidebar {
  width: 200px; flex-shrink: 0;
  background: var(--bg-soft);
  border-right: 1px solid var(--border);
  display: flex; flex-direction: column;
  padding: 12px;
}

/* 返回主页：侧栏顶部醒目实心蓝按钮 */
.home-btn {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  width: 100%; padding: 9px 12px;
  background: var(--accent);
  color: #fff;
  border: none; border-radius: var(--radius-md);
  font-size: 13px; font-weight: 500;
  -webkit-appearance: none; appearance: none;
  box-shadow: 0 1px 3px rgba(65, 118, 230, 0.3);
  transition: background var(--dur) var(--ease), transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
  margin-bottom: 12px;
}
.home-btn:hover { background: var(--accent-hover); transform: translateY(-1px); box-shadow: 0 3px 8px rgba(65, 118, 230, 0.38); color: #fff; }
.home-btn:active { transform: translateY(0); }

.brand { display: flex; align-items: center; gap: 8px; padding: 4px 8px 16px; }
.brand-mark {
  display: inline-flex; align-items: center; justify-content: center;
  width: 26px; height: 26px;
  background: var(--accent); color: #fff;
  border-radius: 6px; font-size: 12px; font-weight: 700;
}
.brand-name { font-size: 14px; font-weight: 600; color: var(--text); }

.new-chat-btn {
  display: flex; align-items: center; gap: 8px;
  width: 100%; padding: 9px 12px;
  background: transparent;
  border: 1px solid var(--border-strong);
  color: var(--text); border-radius: var(--radius-md);
  font-size: 13px; margin-bottom: 16px;
  justify-content: flex-start;
}
.new-chat-btn:hover { background: var(--hover); border-color: var(--accent); color: var(--accent); }
.new-chat-btn .icon { font-size: 16px; line-height: 1; width: 16px; text-align: center; }

.workspace .ws-title {
  font-size: 11px; color: var(--text-faint);
  text-transform: uppercase; letter-spacing: 0.5px;
  padding: 4px 12px; margin-bottom: 6px;
}
.ws-empty { font-size: 12px; color: var(--text-faint); padding: 10px 12px; }
.ws-list { display: flex; flex-direction: column; gap: 2px; }
.ws-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; border-radius: var(--radius-md);
  font-size: 13px; color: var(--text-dim); cursor: default;
}
.ws-item.current { background: var(--hover); color: var(--text); }
.ws-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); }

/* ============ 主区 ============ */
.main { flex: 1; display: flex; flex-direction: column; min-width: 0; }

.topbar {
  display: flex; align-items: center; justify-content: center;
  padding: 12px 20px;
  border-bottom: 1px solid var(--border);
  background: var(--bg);
}
.id-badge { font-size: 12px; color: var(--text-faint); }

.list { flex: 1; overflow-y: auto; padding: 24px 32px; }
.empty { text-align: center; margin: 80px auto 0; max-width: 640px; }
.empty h2 { margin-bottom: 8px; }
.empty p { color: var(--text-dim); margin-bottom: 24px; }
.suggests { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
.chip { background: var(--card); border: 1px solid var(--border); color: var(--text); padding: 8px 16px; border-radius: 999px; }
.chip:hover { background: var(--hover); border-color: var(--border-strong); }

/* ============ 消息气泡 ============ */
.msg { display: flex; margin-bottom: 20px; }
.msg.user { justify-content: flex-end; }
.msg.ai { justify-content: flex-start; }
.bubble { max-width: 78%; }
.msg.user .bubble { background: var(--accent); color: #fff; border-radius: var(--radius-lg); padding: 10px 16px; }
.msg.user .bubble:hover { background: var(--accent-hover); }
.msg.ai .bubble { padding: 0; max-width: 100%; }

.steps { margin-bottom: 10px; display: flex; flex-wrap: wrap; gap: 6px; }
.step { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: var(--text-dim); background: var(--bg-soft); border: 1px solid var(--border); padding: 3px 10px; border-radius: 999px; font-family: var(--font-code); }
.step.running .dot { background: var(--warn); animation: pulse 1s infinite; }
.step.done .dot { background: var(--ok); }
.step.error .dot { background: var(--err); }
.dot { width: 7px; height: 7px; border-radius: 50%; }
@keyframes pulse { 50% { opacity: 0.3; } }

.think-block { margin-bottom: 10px; border: 1px solid var(--border); border-radius: var(--radius-md); overflow: hidden; background: var(--bg-soft); }
.think-toggle { width: 100%; text-align: left; background: transparent; border: none; border-radius: 0; color: var(--text-dim); padding: 6px 12px; font-size: 12px; cursor: pointer; }
.think-toggle:hover { color: var(--text); background: var(--hover); }
.think-content { padding: 10px 12px; font-size: 12px; color: var(--text-dim); white-space: pre-wrap; max-height: 240px; overflow-y: auto; border-top: 1px solid var(--border); line-height: 1.7; }

.cursor { animation: blink 1s steps(1) infinite; color: var(--accent); }
@keyframes blink { 50% { opacity: 0; } }

.sources { margin-top: 10px; border-top: 1px solid var(--border); padding-top: 8px; }
.src-title { font-size: 12px; color: var(--text-faint); margin-bottom: 4px; }
.src { font-size: 12px; color: var(--accent-soft); font-family: var(--font-code); }

.fb { margin-top: 8px; display: flex; gap: 6px; }
.mini { background: transparent; border: 1px solid var(--border); padding: 3px 8px; border-radius: var(--radius-md); }
.mini:hover { background: var(--hover); }

/* ============ dsh 风格输入框：工具栏 + 输入行 + 元信息 ============ */
.composer {
  margin: 0 auto 8px;
  max-width: 800px;
  width: calc(100% - 64px);
  background: var(--bg-soft);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-lg);
  transition: border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
  overflow: hidden;
}
.composer:focus-within { border-color: var(--accent); box-shadow: 0 0 0 3px rgba(65, 118, 230, 0.12); }

.composer-row { padding: 12px 14px 4px; }
.composer-row textarea {
  width: 100%; resize: none; min-height: 24px; max-height: 200px;
  background: transparent;
  border: none; box-shadow: none;
  padding: 0;
  font-size: 14px; line-height: 1.5;
  -webkit-appearance: none; appearance: none;
  overflow: hidden;
}
.composer-row textarea:focus { border: none; box-shadow: none; }

.composer-foot {
  display: flex; align-items: center; justify-content: space-between;
  padding: 4px 10px 10px 14px;
}
.foot-right { display: flex; align-items: center; gap: 8px; }
.meta { font-size: 11px; color: var(--text-faint); }

.mode-btn {
  display: inline-flex; align-items: center; gap: 6px;
  background: transparent;
  border: 1px solid var(--border-strong);
  color: var(--text-dim);
  font-size: 12px; padding: 5px 12px;
  border-radius: 999px;
}
.mode-btn:hover:not(.active) { background: var(--hover); color: var(--text); }
.mode-btn.active { background: var(--accent); border-color: var(--accent); color: #fff; }
.mode-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }

.send-btn {
  width: 30px; height: 30px; padding: 0;
  background: var(--accent); color: #fff;
  border: none; border-radius: 50%;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 1px 3px rgba(65, 118, 230, 0.35);
  -webkit-appearance: none; appearance: none; /* 关闭 webkit 原生 chrome，避免禁用态被染成蓝灰色 */
  transition: background var(--dur) var(--ease), transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
}
.send-btn:hover:not(:disabled) { background: var(--accent-hover); transform: translateY(-1px); box-shadow: 0 3px 8px rgba(65, 118, 230, 0.4); }
.send-btn:active:not(:disabled) { transform: translateY(0); }
.send-btn:disabled { background: var(--card-hover); color: var(--text-faint); box-shadow: none; cursor: not-allowed; }

.privacy { text-align: center; font-size: 11px; color: var(--text-faint); padding: 6px; }

/* 窄屏：折叠侧栏成图标列 */
@media (max-width: 760px) {
  .sidebar { width: 56px; padding: 8px 6px; }
  .brand-name, .home-btn span, .new-chat-btn span:last-child, .workspace .ws-title, .ws-item .ws-label { display: none; }
  .home-btn, .new-chat-btn { justify-content: center; padding: 9px 0; }
  .home-btn { margin-bottom: 8px; }
}
</style>
