import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from './api'
import { tokens } from './tokens'
import type { AuthResponse, PublicConfig, User } from './types'

interface AuthValue {
  user: User | null
  ready: boolean
  config: PublicConfig | undefined
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName: string) => Promise<void>
  continueAsGuest: () => Promise<void>
  signInWithGoogle: (idToken: string) => Promise<void>
  logout: () => Promise<void>
  refreshUser: () => Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [ready, setReady] = useState(false)
  const config = useQuery({ queryKey: ['config'], queryFn: async () => (await api.get<PublicConfig>('/api/auth/config')).data, staleTime: 300_000 })

  const refreshUser = useCallback(async () => {
    try {
      if (!tokens.access && !tokens.refresh) return setUser(null)
      setUser((await api.get<User>('/api/users/me')).data)
    } catch {
      setUser(null)
    }
  }, [])

  useEffect(() => {
    void tokens
      .load()
      .then(refreshUser)
      .finally(() => setReady(true))
    return tokens.subscribe(() => {
      if (!tokens.access && !tokens.refresh) {
        setUser(null)
        qc.clear()
      }
    })
  }, [qc, refreshUser])

  const accept = useCallback(
    async (data: AuthResponse) => {
      await tokens.set(data.accessToken, data.refreshToken)
      setUser(data.user)
      void qc.invalidateQueries()
    },
    [qc],
  )

  const value = useMemo<AuthValue>(
    () => ({
      user,
      ready,
      config: config.data,
      login: async (email, password) => accept((await api.post<AuthResponse>('/api/auth/login', { email, password })).data),
      register: async (email, password, displayName) => accept((await api.post<AuthResponse>('/api/auth/register', { email, password, displayName })).data),
      continueAsGuest: async () => accept((await api.post<AuthResponse>('/api/auth/guest')).data),
      signInWithGoogle: async (idToken) => accept((await api.post<AuthResponse>('/api/auth/google', { idToken })).data),
      logout: async () => {
        if (tokens.refresh) await api.post('/api/auth/logout', { refreshToken: tokens.refresh }).catch(() => undefined)
        await tokens.clear()
      },
      refreshUser,
    }),
    [user, ready, config.data, accept, refreshUser],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
