import { get, post } from './request'
import type { FeedbackStatsVO, KbStatus, QaLogItem } from '@/types'

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
