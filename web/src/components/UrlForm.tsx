import clsx from 'clsx'
import { ArrowRight, Link2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'
import { looksLikeYouTubeUrl } from '../lib/format'
import type { SubmitResponse } from '../lib/types'
import { Spinner } from './States'

export function UrlForm({ size = 'lg', autoFocus }: { size?: 'lg' | 'md'; autoFocus?: boolean }) {
  const [url, setUrl] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const { ensureSession } = useAuth()
  const navigate = useNavigate()

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    if (!looksLikeYouTubeUrl(url)) {
      setError('Please paste a YouTube video link, like https://www.youtube.com/watch?v=…')
      return
    }
    setBusy(true)
    try {
      await ensureSession()
      const res = await api.post<SubmitResponse>('/api/videos/analyze', { url })
      navigate(`/learn/${res.data.analysis.id}`)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="w-full" noValidate>
      <div
        className={clsx(
          'flex flex-col gap-2 rounded-2xl border bg-white p-2 shadow-lg shadow-brand-500/5 transition focus-within:border-brand-400 focus-within:ring-4 focus-within:ring-brand-500/10 sm:flex-row dark:bg-slate-900',
          error ? 'border-rose-300 dark:border-rose-800' : 'border-slate-200 dark:border-slate-700',
        )}
      >
        <label className="flex flex-1 items-center gap-2 px-3">
          <Link2 className="size-5 shrink-0 text-slate-400" aria-hidden="true" />
          <span className="sr-only">YouTube video URL</span>
          <input
            type="url"
            inputMode="url"
            autoFocus={autoFocus}
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="Paste a YouTube link…"
            className={clsx('w-full bg-transparent outline-none placeholder:text-slate-400', size === 'lg' ? 'py-3 text-base' : 'py-2 text-sm')}
            aria-invalid={Boolean(error)}
            aria-describedby={error ? 'url-error' : undefined}
          />
        </label>
        <button type="submit" className={clsx('btn-primary', size === 'lg' && 'px-6 py-3 text-base')} disabled={busy || !url.trim()}>
          {busy ? <Spinner /> : null}
          {busy ? 'Starting…' : 'Analyze video'}
          {!busy && <ArrowRight className="size-4" />}
        </button>
      </div>
      {error && (
        <p id="url-error" role="alert" className="mt-2 px-2 text-sm text-rose-600 dark:text-rose-400">
          {error}
        </p>
      )}
    </form>
  )
}
