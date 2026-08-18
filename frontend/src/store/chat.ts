import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import type { ChatMessage } from '@/types'

const STORAGE_KEY = 'pa-chat-history'

function load(): ChatMessage[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const list = raw ? (JSON.parse(raw) as ChatMessage[]) : []
    return list.map((m) => {
      if (m.role === 'ai' && !m.content && (!m.status || m.status === 'pending' || m.status === 'streaming')) {
        return { ...m, status: 'failed', content: '上一次查询未完成（页面已刷新），请重新提问。' }
      }
      return m
    })
  } catch {
    return []
  }
}

export const useChatStore = defineStore('chat', () => {
  const messages = ref<ChatMessage[]>(load())

  function add(msg: ChatMessage): ChatMessage {
    messages.value.push(msg)
    return messages.value[messages.value.length - 1]
  }

  function reset() {
    messages.value = []
    localStorage.removeItem(STORAGE_KEY)
  }

  function persist() {
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(messages.value)) } catch { /* 忽略 */ }
  }

  watch(messages, persist, { deep: true })

  return { messages, add, reset, persist }
})
