import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { tokens } from './tokens'
import type { AuthResponse } from './types'

export const API_BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''

export const api = axios.create({ baseURL: API_BASE, timeout: 120_000 })

api.interceptors.request.use((config) => {
  const token = tokens.access
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let refreshing: Promise<string | null> | null = null

/** Exchanges the refresh token for a new pair. Concurrent callers share one request. */
export function refreshAccessToken(): Promise<string | null> {
  const refresh = tokens.refresh
  if (!refresh) return Promise.resolve(null)
  refreshing ??= axios
    .post<AuthResponse>(`${API_BASE}/api/auth/refresh`, { refreshToken: refresh })
    .then((res) => {
      tokens.set(res.data.accessToken, res.data.refreshToken)
      return res.data.accessToken
    })
    .catch(() => {
      tokens.clear()
      return null
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
  const isAuthCall = original?.url?.startsWith('/api/auth/')
  if (error.response?.status === 401 && original && !original._retried && !isAuthCall && tokens.refresh) {
    original._retried = true
    const fresh = await refreshAccessToken()
    if (fresh) {
      original.headers.Authorization = `Bearer ${fresh}`
      return api(original)
    }
  }
  return Promise.reject(error)
})

interface Problem {
  detail?: string
  code?: string
  fieldErrors?: Record<string, string>
}

/** Friendly message for any error thrown by the API client. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as Problem | undefined
    if (data?.fieldErrors) {
      const first = Object.values(data.fieldErrors)[0]
      if (first) return `${data.detail ?? 'Please check your input.'} ${first}`
    }
    if (data?.detail) return data.detail
    if (error.code === 'ECONNABORTED') return 'That took too long. Please try again.'
    if (!error.response) return "We can't reach Explainly right now. Check your connection and try again."
  }
  return fallback
}

export function errorCode(error: unknown): string | undefined {
  if (axios.isAxiosError(error)) return (error.response?.data as Problem | undefined)?.code
  return undefined
}

/** Downloads a protected file through the authenticated client. */
export async function downloadFile(url: string, fallbackName: string) {
  const res = await api.get<Blob>(url, { responseType: 'blob' })
  const disposition = String(res.headers['content-disposition'] ?? '')
  const match = /filename="?([^";]+)"?/.exec(disposition)
  const href = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = href
  a.download = match?.[1] ?? fallbackName
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(href), 1000)
}
