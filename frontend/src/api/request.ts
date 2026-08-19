import axios from 'axios'
import type { Result } from '@/types'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 30000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (resp) => resp.data,
  (err) => {
    const msg = err?.response?.data?.message || err?.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

export function post<T>(url: string, data?: unknown, config?: Record<string, unknown>): Promise<Result<T>> {
  return request.post(url, data, config) as unknown as Promise<Result<T>>
}

export function get<T>(url: string, params?: unknown, config?: Record<string, unknown>): Promise<Result<T>> {
  return request.get(url, { params, ...config }) as unknown as Promise<Result<T>>
}

export default request
