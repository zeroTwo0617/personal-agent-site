import { get, post } from './request'
import type { EvalReport, FeedbackStatsVO, KbStatus, QaLogItem } from '@/types'

export function login(username: string, password: string) {
  return post<{ token: string; username: string; expiresIn: number }>('/auth/login', { username, password })
}

export function getQa(page = 1, size = 20, q?: string) {
  return get<{ list: QaLogItem[]; page: number; size: number; total: number }>('/admin/qa', { page, size, q })
}

export function getFeedbackStats() {
  return get<FeedbackStatsVO>('/admin/feedback/stats')
}

export function getKbStatus() {
  return get<KbStatus>('/admin/kb/status')
}

export function rebuildKb() {
  return post<KbStatus>('/admin/kb/rebuild')
}

/** 跑评测集（GET 也行，后端同时支持 GET/POST）；返回本次报告。评测需 2-4 分钟，单独加长超时 */
export function runEval(topK = 6, mode = 'hybrid') {
  return get<EvalReport>('/eval/run', { topK, mode }, { timeout: 600000 })
}

/** 最近一次评测报告 */
export function getEvalReport() {
  return get<EvalReport>('/eval/report')
}
