import { post } from './request'
import type { AgentStepEvent, ChatSubmitResponse, ChatTurn, ChunkSearchResult } from '@/types'

/** 提交问题：默认 agent 模式（深度思考）；history 最多 10 轮 */
export function ask(question: string, topK = 5, history: ChatTurn[] = [], mode = 'agent') {
  return post<ChatSubmitResponse>('/chat', { question, topK, history, mode })
}

/**
 * 订阅 SSE 流式：delta（逐字）/ agent_step（思考步骤）/ done（结束）/ error。
 * 返回 AbortController 可中断。
 */
export function openChatStream(
  taskId: string,
  handlers: {
    onDelta: (text: string) => void
    onAgentStep?: (step: AgentStepEvent) => void
    onDone: (payload: { sources: ChunkSearchResult[]; answer: string; qaId?: number }) => void
    onError: (msg: string) => void
  }
): AbortController {
  const ctrl = new AbortController()
  const base = import.meta.env.VITE_API_BASE || '/api'
  let finished = false
  const finish = (fn: () => void) => {
    if (finished) return
    finished = true
    fn()
  }

  fetch(`${base}/chat/stream?taskId=${encodeURIComponent(taskId)}`, { signal: ctrl.signal })
    .then((res) => {
      if (!res.ok || !res.body) { finish(() => handlers.onError('流式连接失败')); return }
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      const pump = () => {
        reader.read().then(({ done, value }) => {
          if (done) { finish(() => handlers.onError('连接已关闭')); return }
          buffer += decoder.decode(value, { stream: true })
          let idx
          while ((idx = buffer.indexOf('\n\n')) >= 0) {
            const raw = buffer.slice(0, idx)
            buffer = buffer.slice(idx + 2)
            for (const line of raw.split('\n').filter((l) => l.startsWith('data:'))) {
              const jsonStr = line.slice(5).trim()
              if (!jsonStr) continue
              try {
                const evt = JSON.parse(jsonStr)
                if (evt.type === 'delta') handlers.onDelta(evt.content || '')
                else if (evt.type === 'agent_step') handlers.onAgentStep?.(evt)
                else if (evt.type === 'done') finish(() => handlers.onDone({ sources: evt.sources || [], answer: evt.answer || '', qaId: evt.qaId }))
                else if (evt.type === 'error') finish(() => handlers.onError(evt.message || '生成出错'))
              } catch { /* 跳过无法解析的行 */ }
            }
          }
          pump()
        }).catch(() => finish(() => handlers.onError('读取流失败')))
      }
      pump()
    })
    .catch(() => finish(() => handlers.onError('无法连接流式接口')))

  return ctrl
}
