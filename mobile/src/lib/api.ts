import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { tokens } from './tokens'
import type { AuthResponse } from './types'

export const API_BASE = (process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, '')

export const api = axios.create({ baseURL: API_BASE, timeout: 120_000 })

api.interceptors.request.use((config) => {
  if (tokens.access) config.headers.Authorization = `Bearer ${tokens.access}`
  return config
})

let refreshing: Promise<string | null> | null = null

export function refreshAccessToken(): Promise<string | null> {
  const refresh = tokens.refresh
  if (!refresh) return Promise.resolve(null)
  refreshing ??= axios
    .post<AuthResponse>(`${API_BASE}/api/auth/refresh`, { refreshToken: refresh })
    .then(async (res) => {
      await tokens.set(res.data.accessToken, res.data.refreshToken)
      return res.data.accessToken
    })
    .catch(async () => {
      await tokens.clear()
      return null
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
  if (error.response?.status === 401 && original && !original._retried && !original.url?.startsWith('/api/auth/') && tokens.refresh) {
    original._retried = true
    const fresh = await refreshAccessToken()
    if (fresh) {
      original.headers.Authorization = `Bearer ${fresh}`
      return api(original)
    }
  }
  return Promise.reject(error)
})

export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { detail?: string; fieldErrors?: Record<string, string> } | undefined
    const field = data?.fieldErrors ? Object.values(data.fieldErrors)[0] : undefined
    if (field) return `${data?.detail ?? ''} ${field}`.trim()
    if (data?.detail) return data.detail
    if (!error.response) return "Can't reach Explainly. Check your connection."
  }
  return fallback
}
