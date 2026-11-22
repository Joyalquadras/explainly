import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from './api'
import { tokens } from './tokens'
import type { AuthResponse, PublicConfig, User } from './types'

interface AuthContextValue {
  user: User | null
  loading: boolean
  config: PublicConfig | undefined
  login: (email: string, password: string) => Promise<User>
  register: (email: string, password: string, displayName: string) => Promise<User>
  continueAsGuest: () => Promise<User>
  signInWithGoogle: (idToken: string) => Promise<User>
  ensureSession: () => Promise<User>
  logout: () => Promise<void>
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(() => Boolean(tokens.access || tokens.refresh))

  const config = useQuery({
    queryKey: ['config'],
    queryFn: async () => (await api.get<PublicConfig>('/api/auth/config')).data,
    staleTime: 5 * 60_000,
  })

  const refreshUser = useCallback(async () => {
    if (!tokens.access && !tokens.refresh) {
      setUser(null)
      setLoading(false)
      return
    }
    try {
      const res = await api.get<User>('/api/users/me')
      setUser(res.data)
    } catch {
      tokens.clear()
      setUser(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refreshUser()
  }, [refreshUser])

  // Keep tabs in sync and react to forced sign-out (e.g. refresh token revoked).
  useEffect(() => {
    const unsubscribe = tokens.subscribe(() => {
      if (!tokens.access && !tokens.refresh) {
        setUser(null)
        queryClient.clear()
      }
    })
    const onStorage = (e: StorageEvent) => {
      if (e.key?.startsWith('explainly.')) void refreshUser()
    }
    window.addEventListener('storage', onStorage)
    return () => {
      unsubscribe()
      window.removeEventListener('storage', onStorage)
    }
  }, [queryClient, refreshUser])

  const accept = useCallback(
    (data: AuthResponse) => {
      tokens.set(data.accessToken, data.refreshToken)
      setUser(data.user)
      void queryClient.invalidateQueries()
      return data.user
    },
    [queryClient],
  )

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      loading,
      config: config.data,
      login: async (email, password) => accept((await api.post<AuthResponse>('/api/auth/login', { email, password })).data),
      register: async (email, password, displayName) =>
        accept((await api.post<AuthResponse>('/api/auth/register', { email, password, displayName })).data),
      continueAsGuest: async () => accept((await api.post<AuthResponse>('/api/auth/guest')).data),
      signInWithGoogle: async (idToken) => accept((await api.post<AuthResponse>('/api/auth/google', { idToken })).data),
      ensureSession: async () => user ?? accept((await api.post<AuthResponse>('/api/auth/guest')).data),
      logout: async () => {
        const refreshToken = tokens.refresh
        if (refreshToken) await api.post('/api/auth/logout', { refreshToken }).catch(() => undefined)
        tokens.clear()
        setUser(null)
        queryClient.clear()
      },
      refreshUser,
    }),
    [user, loading, config.data, accept, queryClient, refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
