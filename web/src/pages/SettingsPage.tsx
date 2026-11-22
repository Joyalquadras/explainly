import { Dialog, DialogPanel, DialogTitle } from '@headlessui/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { CheckCircle2, Monitor, Moon, Sun, MonitorPlay } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Skeleton, Spinner } from '../components/States'
import { useToast } from '../components/Toast'
import { api, errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useTheme, type ThemePreference } from '../lib/theme'
import { tokens } from '../lib/tokens'
import type { User, YouTubeConnection } from '../lib/types'

const REASONS: Record<string, string> = {
  denied: 'You declined access on Google, so YouTube was not connected.',
  cancelled: 'Connecting YouTube was cancelled.',
  expired: 'The connection link expired. Please try again.',
  scope: 'Google did not grant the YouTube permission we need.',
  exchange: "We couldn't finish connecting to Google. Please try again.",
}

export function SettingsPage() {
  const { user, refreshUser, logout } = useAuth()
  const { preference, setPreference } = useTheme()
  const [params, setParams] = useSearchParams()
  const toast = useToast()
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [name, setName] = useState(user?.displayName ?? '')
  const [confirmDelete, setConfirmDelete] = useState(false)

  useEffect(() => {
    const yt = params.get('youtube')
    if (!yt) return
    if (yt === 'connected') toast('YouTube connected', 'success')
    else toast(REASONS[params.get('reason') ?? ''] ?? 'YouTube could not be connected.', 'error')
    setParams({}, { replace: true })
  }, [params, setParams, toast])

  const connection = useQuery({
    queryKey: ['youtube', 'connection'],
    queryFn: async () => (await api.get<YouTubeConnection>('/api/youtube/connection')).data,
  })
  const saveName = useMutation({
    mutationFn: async () => (await api.patch<User>('/api/users/me', { displayName: name })).data,
    onSuccess: async () => {
      await refreshUser()
      toast('Profile updated', 'success')
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })
  const connect = useMutation({
    mutationFn: async () => (await api.post<{ authorizationUrl: string }>('/api/youtube/connect', {})).data,
    onSuccess: (data) => window.location.assign(data.authorizationUrl),
    onError: (e) => toast(errorMessage(e), 'error'),
  })
  const disconnect = useMutation({
    mutationFn: () => api.delete('/api/youtube/connection'),
    onSuccess: () => {
      toast('YouTube disconnected and access revoked', 'success')
      void qc.invalidateQueries({ queryKey: ['youtube'] })
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })
  const deleteAccount = useMutation({
    mutationFn: () => api.delete('/api/users/me'),
    onSuccess: async () => {
      tokens.clear()
      await logout()
      navigate('/')
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  if (!user) return null
  const themes: { value: ThemePreference; label: string; icon: typeof Sun }[] = [
    { value: 'light', label: 'Light', icon: Sun },
    { value: 'dark', label: 'Dark', icon: Moon },
    { value: 'system', label: 'System', icon: Monitor },
  ]

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-8 sm:px-6">
      <h1 className="text-2xl font-bold">Settings</h1>

      {user.role === 'GUEST' && (
        <section className="card border-brand-200 bg-brand-50/60 p-5 dark:border-brand-900 dark:bg-brand-950/30">
          <h2 className="font-semibold">You're using a guest session</h2>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">Create a free account to keep your library on all your devices and analyze more videos each day.</p>
          <Link to="/signin?mode=register" className="btn-primary mt-4">
            Create account
          </Link>
        </section>
      )}

      <section className="card p-5">
        <h2 className="font-semibold">Profile</h2>
        <form
          className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-end"
          onSubmit={(e) => {
            e.preventDefault()
            saveName.mutate()
          }}
        >
          <label className="flex-1">
            <span className="mb-1.5 block text-sm font-medium">Display name</span>
            <input className="input" value={name} maxLength={120} onChange={(e) => setName(e.target.value)} required />
          </label>
          <button className="btn-primary" disabled={saveName.isPending || !name.trim() || name === user.displayName}>
            {saveName.isPending && <Spinner />} Save
          </button>
        </form>
        {user.email && <p className="mt-3 text-sm text-slate-500">Signed in as {user.email}</p>}
      </section>

      <section className="card p-5">
        <h2 className="font-semibold">Appearance</h2>
        <div className="mt-4 grid grid-cols-3 gap-2" role="radiogroup" aria-label="Theme">
          {themes.map((t) => (
            <button
              key={t.value}
              role="radio"
              aria-checked={preference === t.value}
              onClick={() => setPreference(t.value)}
              className={clsx(
                'flex flex-col items-center gap-1.5 rounded-xl border p-3 text-sm font-medium transition',
                preference === t.value ? 'border-brand-500 bg-brand-50 text-brand-700 dark:bg-brand-950/40 dark:text-brand-200' : 'border-slate-200 dark:border-slate-700',
              )}
            >
              <t.icon className="size-5" /> {t.label}
            </button>
          ))}
        </div>
      </section>

      <section className="card p-5">
        <div className="flex items-start gap-3">
          <div className="rounded-xl bg-red-50 p-2 text-red-600 dark:bg-red-950/40">
            <MonitorPlay className="size-5" />
          </div>
          <div className="flex-1">
            <h2 className="font-semibold">YouTube connection</h2>
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              Connect your YouTube account with Google to import captions automatically for videos you own or manage. We only request
              caption access, never your password, and you can disconnect at any time.
            </p>
            <div className="mt-4">
              {connection.isLoading ? (
                <Skeleton className="h-10 w-48" />
              ) : !connection.data?.enabled ? (
                <p className="text-sm text-slate-500">Connecting YouTube isn't configured on this server.</p>
              ) : user.role === 'GUEST' ? (
                <p className="text-sm text-slate-500">Create an account to connect YouTube.</p>
              ) : connection.data.connected ? (
                <div className="flex flex-wrap items-center gap-3">
                  <span className="flex items-center gap-1.5 text-sm font-medium text-emerald-700 dark:text-emerald-400">
                    <CheckCircle2 className="size-4" /> Connected{connection.data.accountName ? ` as ${connection.data.accountName}` : ''}
                  </span>
                  <button className="btn-secondary" onClick={() => disconnect.mutate()} disabled={disconnect.isPending}>
                    {disconnect.isPending && <Spinner />} Disconnect & revoke access
                  </button>
                </div>
              ) : (
                <button className="btn-primary" onClick={() => connect.mutate()} disabled={connect.isPending}>
                  {connect.isPending && <Spinner />} Connect YouTube
                </button>
              )}
            </div>
          </div>
        </div>
      </section>

      <section className="card border-rose-200 p-5 dark:border-rose-900/60">
        <h2 className="font-semibold text-rose-700 dark:text-rose-400">Delete account</h2>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">Permanently delete your account, notes, quiz scores, bookmarks and history.</p>
        <button className="btn-danger mt-4" onClick={() => setConfirmDelete(true)}>
          Delete my account
        </button>
      </section>

      <Dialog open={confirmDelete} onClose={() => setConfirmDelete(false)} className="relative z-50">
        <div className="fixed inset-0 bg-black/40" aria-hidden="true" />
        <div className="fixed inset-0 flex items-center justify-center p-4">
          <DialogPanel className="card w-full max-w-sm p-6">
            <DialogTitle className="text-lg font-semibold">Delete your account?</DialogTitle>
            <p className="mt-2 text-sm text-slate-600 dark:text-slate-400">This cannot be undone.</p>
            <div className="mt-6 flex justify-end gap-2">
              <button className="btn-secondary" onClick={() => setConfirmDelete(false)}>
                Cancel
              </button>
              <button className="btn-danger" onClick={() => deleteAccount.mutate()} disabled={deleteAccount.isPending}>
                {deleteAccount.isPending && <Spinner />} Delete
              </button>
            </div>
          </DialogPanel>
        </div>
      </Dialog>
    </div>
  )
}
