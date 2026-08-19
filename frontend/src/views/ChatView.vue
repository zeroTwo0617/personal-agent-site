<script setup lang="ts">
import { ref, nextTick, computed } from 'vue'
import { useChatStore } from '@/store/chat'
import { ask, getChatResult, openChatStream } from '@/api/chat'
import { submitFeedback } from '@/api/feedback'
import { renderMd } from '@/utils/markdown'
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
      onError: async (msg) => {
        // SSE 断开兜底：任务可能已完成，轮询一次结果；拿不到再标记失败
        try {
          const r = await getChatResult(taskId)
          if (r.data && r.data.status === 'completed' && r.data.answer) {
            ai.content = r.data.answer
            ai.sources = r.data.sources
            ai.qaId = r.data.qaId
            ai.status = 'completed'
            ai.thinkOpen = false
            store.persist()
            scrollBottom()
            return
          }
        } catch { /* 轮询失败继续走失败分支 */ }
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
    <!-- 侧栏 -->
    <aside class="sidebar">
      <router-link to="/" class="home-btn">
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5M12 19l-7-7 7-7" /></svg>
        <span>返回主页</span>
      </router-link>

      <div class="brand">
        <span class="brand-mark mono">AI</span>
        <span class="brand-name">郑梓恒 · 分身</span>
      </div>

      <button class="new-chat-btn" @click="newChat">
        <span class="icon" aria-hidden="true">+</span>
        <span>新对话</span>
      </button>

      <nav class="workspace">
        <div class="ws-title mono">WORKSPACE</div>
        <div v-if="store.messages.length === 0" class="ws-empty">暂无历史</div>
        <div v-else class="ws-list">
          <div class="ws-item current">
            <span class="ws-dot" />
            <span class="ws-label">当前对话</span>
          </div>
        </div>
      </nav>

      <div class="sidebar-foot mono">
        <span class="status-dot" /> RAG Online
      </div>
    </aside>

    <!-- 主区 -->
    <section class="main">
      <header class="topbar">
        <div class="topbar-inner">
          <span class="id-badge"><i class="pulse-dot" /> AI 分身 · 基于本人真实经历作答</span>
          <span class="topbar-mode mono">{{ deepThink ? 'agent / deep' : 'normal / fast' }}</span>
        </div>
      </header>

      <div class="list" ref="listEl">
        <div v-if="store.messages.length === 0" class="empty">
          <div class="empty-card">
            <span class="eyebrow">Interactive Console</span>
            <h2>你好，我是郑梓恒的 AI 分身 👋</h2>
            <p>关于我的项目、技术栈、经历，任何问题都可以问我。</p>
            <div class="suggests">
              <button v-for="s in suggestions" :key="s" class="chip" @click="submit(s)">{{ s }}</button>
            </div>
          </div>
        </div>

        <div v-for="(m, i) in store.messages" :key="i" class="msg" :class="m.role">
          <div class="bubble">
            <!-- AI 头部 -->
            <div v-if="m.role === 'ai'" class="ai-head">
              <span class="ai-avatar mono">AI</span>
              <span class="ai-name">郑梓恒 · 分身</span>
              <span v-if="m.status === 'streaming'" class="ai-status mono">生成中…</span>
              <span v-else-if="m.status === 'failed'" class="ai-status mono error">失败</span>
              <span v-else-if="m.sources?.length" class="ai-status mono">引用 {{ m.sources.length }}</span>
            </div>

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
                <span class="think-icon">💭</span>
                <span>{{ m.status === 'streaming' ? '思考中…' : '思考过程' }}</span>
                <span class="think-caret">{{ m.thinkOpen ? '−' : '+' }}</span>
              </button>
              <div v-if="m.thinkOpen" class="think-content">{{ m.thinking }}</div>
            </div>

            <!-- 内容：AI 渲染 Markdown(含 [N] 引用角标)；用户纯文本 -->
            <div v-if="m.role === 'ai' && m.content" class="content ai-content" v-html="renderMd(m.content)"></div>
            <div v-else-if="m.content" class="content" style="white-space: pre-wrap">{{ m.content }}</div>
            <span v-else-if="m.status === 'streaming'" class="cursor">▍</span>

            <!-- 引用来源 -->
            <div v-if="m.sources && m.sources.length" class="sources">
              <div class="src-title mono">SOURCES</div>
              <div v-for="(s, si) in m.sources" :key="si" class="src">
                <span class="src-index mono">{{ String(si + 1).padStart(2, '0') }}</span>
                <span class="src-name">{{ s.docName }} · {{ s.section }}</span>
              </div>
            </div>

            <!-- 反馈 -->
            <div v-if="m.role === 'ai' && m.status === 'completed' && m.qaId" class="fb">
              <button class="mini" @click="feedback(m, 1)" title="有帮助">👍 有帮助</button>
              <button class="mini" @click="feedback(m, -1)" title="没有帮助">👎 待改进</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <footer class="composer-wrap">
        <div class="composer">
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
            <span class="meta mono">{{ input.length }}/500</span>
            <div class="foot-right">
              <button class="mode-btn" :class="{ active: deepThink }" @click="deepThink = !deepThink" title="切换深度思考">
                <span class="mode-dot" />
                深度思考
              </button>
              <button class="send-btn" :disabled="!canSend" @click="submit()" title="发送">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 19V5M5 12l7-7 7 7" /></svg>
              </button>
            </div>
          </div>
        </div>
        <div class="privacy">本站为 AI 分身，内容基于本人简历与项目经历，如有出入以本人为准。</div>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.chat-page { display: flex; height: 100%; min-height: 0; }

/* ============ Sidebar ============ */
.sidebar {
  width: 236px;
  flex-shrink: 0;
  background: var(--bg-soft);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  padding: 14px;
  gap: 4px;
}
.home-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  background: var(--card);
  color: var(--text);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 600;
  box-shadow: var(--shadow-sm);
  transition: border-color var(--dur) var(--ease), background var(--dur) var(--ease), color var(--dur) var(--ease), transform var(--dur) var(--ease);
}
.home-btn:hover { border-color: var(--accent); color: var(--accent); background: var(--card); transform: translateY(-1px); }
.brand { display: flex; align-items: center; gap: 10px; padding: 14px 6px 16px; }
.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  background: var(--accent);
  color: #fff;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
}
.brand-name { font-size: 14px; font-weight: 700; color: var(--text); }
.new-chat-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  background: var(--card);
  border: 1px solid var(--border-strong);
  color: var(--text);
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 500;
  justify-content: flex-start;
  box-shadow: var(--shadow-sm);
}
.new-chat-btn:hover { background: var(--card); border-color: var(--accent); color: var(--accent); }
.new-chat-btn .icon { font-size: 16px; line-height: 1; width: 16px; text-align: center; }
.workspace { margin-top: 16px; }
.ws-title {
  font-size: 11px;
  color: var(--text-faint);
  letter-spacing: 0.08em;
  padding: 4px 8px;
  margin-bottom: 6px;
}
.ws-empty { font-size: 12px; color: var(--text-faint); padding: 8px 10px; }
.ws-list { display: flex; flex-direction: column; gap: 2px; }
.ws-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-dim);
  cursor: default;
}
.ws-item.current { background: var(--card); color: var(--text); box-shadow: var(--shadow-sm); }
.ws-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--accent); }
.sidebar-foot {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 8px 4px;
  font-size: 11px;
  color: var(--text-dim);
}
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--ok); box-shadow: 0 0 0 3px rgba(14,159,110,.14); }

/* ============ Main ============ */
.main { flex: 1; display: flex; flex-direction: column; min-width: 0; background: var(--bg); }
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  border-bottom: 1px solid var(--border);
  background: rgba(255,255,255,0.65);
  backdrop-filter: blur(10px);
}
.topbar-inner { display: flex; align-items: center; gap: 12px; width: 100%; }
.id-badge { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-dim); }
.pulse-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--ok); box-shadow: 0 0 0 0 rgba(14,159,110,.4); animation: pulse 2s infinite; }
@keyframes pulse { 70% { box-shadow: 0 0 0 7px rgba(14,159,110,0); } 100% { box-shadow: 0 0 0 0 rgba(14,159,110,0); } }
.topbar-mode { font-size: 11px; color: var(--text-faint); margin-left: auto; }

.list { flex: 1; overflow-y: auto; padding: 28px 32px; scroll-behavior: smooth; }
.empty { display: flex; justify-content: center; padding-top: 9vh; }
.empty-card {
  max-width: 640px;
  text-align: center;
  background: var(--card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 40px 32px;
  box-shadow: var(--shadow-sm);
}
.empty-card h2 { margin: 14px 0 8px; font-family: var(--font-display); font-size: 28px; letter-spacing: -0.02em; }
.empty-card p { color: var(--text-dim); margin-bottom: 24px; }
.suggests { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
.chip {
  background: var(--surface-2);
  border: 1px solid var(--border);
  color: var(--text);
  padding: 8px 16px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  box-shadow: none;
}
.chip:hover { background: var(--card); border-color: var(--accent); color: var(--accent); box-shadow: var(--shadow-sm); }

/* ============ Messages ============ */
.msg { display: flex; margin-bottom: 24px; }
.msg.user { justify-content: flex-end; }
.msg.ai { justify-content: flex-start; }
.bubble { max-width: 78%; }
.msg.user .bubble {
  background: var(--accent);
  color: #fff;
  border-radius: var(--radius-lg) var(--radius-lg) 4px var(--radius-lg);
  padding: 12px 16px;
  box-shadow: 0 6px 16px rgba(11,110,107,.16);
}
.msg.ai .bubble { max-width: 100%; width: 100%; }

.ai-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.ai-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: var(--accent);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}
.ai-name { font-size: 13px; font-weight: 700; color: var(--text); }
.ai-status { font-size: 11px; color: var(--text-faint); margin-left: auto; }
.ai-status.error { color: var(--err); }

.ai-content {
  display: block;
  background: var(--card);
  border: 1px solid var(--border);
  border-left: 3px solid var(--accent);
  border-radius: var(--radius-md);
  padding: 16px 18px;
  line-height: 1.8;
  box-shadow: var(--shadow-sm);
}

.steps { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.step {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-dim);
  background: var(--surface-2);
  border: 1px solid var(--border);
  padding: 4px 10px;
  border-radius: 999px;
  font-family: var(--font-code);
}
.step.running .dot { background: var(--warn); animation: pulse 1s infinite; }
.step.done .dot { background: var(--ok); }
.step.error .dot { background: var(--err); }
.dot { width: 7px; height: 7px; border-radius: 50%; }
@keyframes pulse { 50% { opacity: 0.3; } }

.think-block { margin-bottom: 10px; border: 1px solid var(--border); border-radius: var(--radius-md); overflow: hidden; background: var(--surface-2); }
.think-toggle {
  width: 100%;
  text-align: left;
  background: transparent;
  border: none;
  border-radius: 0;
  color: var(--text-dim);
  padding: 9px 14px;
  font-size: 13px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: none;
}
.think-toggle:hover { color: var(--text); background: var(--hover); }
.think-icon { font-size: 13px; }
.think-caret { margin-left: auto; font-family: var(--font-code); color: var(--text-faint); }
.think-content { padding: 12px 14px; font-size: 12px; color: var(--text-dim); white-space: pre-wrap; max-height: 240px; overflow-y: auto; border-top: 1px solid var(--border); line-height: 1.7; background: var(--card); }

.cursor { animation: blink 1s steps(1) infinite; color: var(--accent); }
@keyframes blink { 50% { opacity: 0; } }

/* ===== AI 内容 Markdown 渲染样式(v-html 注入,需 :deep 穿透) ===== */
.ai-content :deep(.cite) {
  display: inline-block;
  margin: 0 2px;
  padding: 0 5px;
  font-family: var(--font-code);
  font-size: 11px;
  line-height: 1.5;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 4px;
  cursor: pointer;
  vertical-align: baseline;
}
.ai-content :deep(p) { margin: 6px 0; }
.ai-content :deep(strong) { color: var(--text); font-weight: 600; }
.ai-content :deep(ul), .ai-content :deep(ol) { margin: 6px 0; padding-left: 1.4em; }
.ai-content :deep(li) { margin: 2px 0; }
.ai-content :deep(code) {
  font-family: var(--font-code);
  font-size: 13px;
  background: var(--bg-soft);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 1px 5px;
}
.ai-content :deep(a) { color: var(--accent); text-decoration: underline; }
.ai-content :deep(h1), .ai-content :deep(h2), .ai-content :deep(h3), .ai-content :deep(h4) { margin: 10px 0 6px; font-weight: 600; }
.ai-content :deep(blockquote) { border-left: 3px solid var(--border-strong); padding-left: 10px; color: var(--text-dim); margin: 6px 0; }
.ai-content :deep(table) { border-collapse: collapse; margin: 8px 0; font-size: 13px; }
.ai-content :deep(th), .ai-content :deep(td) { border: 1px solid var(--border-strong); padding: 4px 10px; }
.ai-content :deep(th) { background: var(--bg-soft); }

.sources { margin-top: 10px; border-top: 1px solid var(--border); padding-top: 10px; }
.src-title { font-size: 11px; color: var(--text-faint); letter-spacing: 0.08em; margin-bottom: 6px; }
.src {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 12px;
  color: var(--text-dim);
  padding: 5px 0;
}
.src-index { color: var(--accent); font-weight: 700; min-width: 22px; }
.src-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.fb { margin-top: 10px; display: flex; gap: 8px; }
.mini { background: var(--card); border: 1px solid var(--border-strong); padding: 5px 12px; border-radius: 999px; color: var(--text-dim); font-size: 12px; box-shadow: none; }
.mini:hover { background: var(--hover); border-color: var(--accent); color: var(--accent); box-shadow: none; }

/* ============ Composer ============ */
.composer-wrap { padding: 0 32px 14px; }
.composer {
  margin: 0 auto;
  max-width: 820px;
  background: var(--card);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  transition: border-color var(--dur) var(--ease), box-shadow var(--dur) var(--ease);
  overflow: hidden;
}
.composer:focus-within { border-color: var(--accent); box-shadow: 0 0 0 3px rgba(11,110,107,.12), var(--shadow-md); }
.composer-row { padding: 14px 16px 4px; }
.composer-row textarea {
  width: 100%;
  resize: none;
  min-height: 24px;
  max-height: 200px;
  background: transparent;
  border: none;
  box-shadow: none;
  padding: 0;
  font-size: 15px;
  line-height: 1.5;
  -webkit-appearance: none;
  appearance: none;
  overflow: hidden;
}
.composer-row textarea:focus { border: none; box-shadow: none; }
.composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 12px 12px 16px;
}
.foot-right { display: flex; align-items: center; gap: 8px; }
.meta { font-size: 11px; color: var(--text-faint); }
.mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: 1px solid var(--border-strong);
  color: var(--text-dim);
  font-size: 12px;
  padding: 6px 14px;
  border-radius: 999px;
  box-shadow: none;
}
.mode-btn:hover:not(.active) { background: var(--hover); color: var(--text); box-shadow: none; }
.mode-btn.active { background: var(--accent); border-color: var(--accent); color: #fff; box-shadow: 0 4px 10px rgba(11,110,107,.18); }
.mode-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.send-btn {
  width: 34px;
  height: 34px;
  padding: 0;
  background: var(--accent);
  color: #fff;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 10px rgba(11,110,107,.22);
  -webkit-appearance: none;
  appearance: none;
}
.send-btn:hover:not(:disabled) { background: var(--accent-hover); transform: translateY(-1px); box-shadow: 0 6px 14px rgba(11,110,107,.26); }
.send-btn:disabled { background: var(--bg-soft); color: var(--text-faint); box-shadow: none; cursor: not-allowed; }

.privacy { text-align: center; font-size: 11px; color: var(--text-faint); padding: 8px 0 2px; }

/* ============ Responsive ============ */
@media (max-width: 820px) {
  .sidebar { width: 58px; padding: 10px 8px; }
  .brand-name, .home-btn span, .new-chat-btn span:last-child, .workspace .ws-title, .ws-item .ws-label, .sidebar-foot { display: none; }
  .home-btn, .new-chat-btn { justify-content: center; padding: 10px 0; }
  .brand { justify-content: center; padding: 10px 0 14px; }
  .list { padding: 20px 16px; }
  .composer-wrap { padding: 0 16px 12px; }
  .bubble { max-width: 88%; }
}
</style>
