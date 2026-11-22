import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Bot, SendHorizontal, Trash2 } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Markdown } from '../../components/Markdown'
import { ErrorState, Skeleton, Spinner } from '../../components/States'
import { Timestamp } from '../../components/Timestamp'
import { api, errorMessage } from '../../lib/api'
import type { ChatMessage } from '../../lib/types'
import { useLearn } from './context'

const SUGGESTIONS = [
  'Explain the main idea in even simpler words',
  'Give me a real-world example',
  'Why is this important?',
  'Summarize the most important chapter',
  'What should I learn next?',
]

export function ChatPanel() {
  const { detail, seek } = useLearn()
  const id = detail.status.id
  const qc = useQueryClient()
  const [input, setInput] = useState('')
  const [pending, setPending] = useState<string | null>(null)
  const scroller = useRef<HTMLDivElement>(null)

  const history = useQuery({ queryKey: ['chat', id], queryFn: async () => (await api.get<ChatMessage[]>(`/api/analyses/${id}/chat`)).data })
  const ask = useMutation({
    mutationFn: async (message: string) => (await api.post<ChatMessage>(`/api/analyses/${id}/chat`, { message })).data,
    onMutate: (message) => setPending(message),
    onSettled: () => {
      setPending(null)
      void qc.invalidateQueries({ queryKey: ['chat', id] })
    },
  })
  const clear = useMutation({
    mutationFn: () => api.delete(`/api/analyses/${id}/chat`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['chat', id] }),
  })

  useEffect(() => {
    scroller.current?.scrollTo({ top: scroller.current.scrollHeight, behavior: 'smooth' })
  }, [history.data, pending])

  const send = (message: string) => {
    const text = message.trim()
    if (!text || ask.isPending) return
    setInput('')
    ask.mutate(text)
  }
  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    send(input)
  }
  const messages = history.data ?? []

  return (
    <div className="flex h-[32rem] flex-col">
      <div className="mb-2 flex items-center justify-between">
        <p className="text-sm text-slate-500">Answers come from this video and link to the moment it's discussed.</p>
        {messages.length > 0 && (
          <button className="btn-ghost px-2 py-1.5 text-xs" onClick={() => clear.mutate()} disabled={clear.isPending}>
            <Trash2 className="size-3.5" /> Clear
          </button>
        )}
      </div>
      <div ref={scroller} className="flex-1 space-y-4 overflow-y-auto rounded-xl bg-slate-50 p-4 dark:bg-slate-950/50" aria-live="polite">
        {history.isLoading ? (
          <Skeleton className="h-16" />
        ) : history.isError ? (
          <ErrorState message={errorMessage(history.error)} onRetry={() => void history.refetch()} />
        ) : messages.length === 0 && !pending ? (
          <div className="flex h-full flex-col items-center justify-center gap-4 text-center">
            <div className="rounded-2xl bg-brand-100 p-3 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200">
              <Bot className="size-6" />
            </div>
            <p className="text-sm text-slate-600 dark:text-slate-400">Ask anything about this video. Try one of these:</p>
            <div className="flex max-w-lg flex-wrap justify-center gap-2">
              {SUGGESTIONS.map((s) => (
                <button key={s} onClick={() => send(s)} className="chip bg-white px-3 py-1.5 text-sm text-slate-700 ring-1 ring-slate-200 hover:bg-brand-50 dark:bg-slate-900 dark:text-slate-300 dark:ring-slate-700">
                  {s}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <>
            {messages.map((m) => (
              <Bubble key={m.id} message={m} onSeek={seek} />
            ))}
            {pending && (
              <>
                <Bubble message={{ id: -1, role: 'user', content: pending, citations: [], createdAt: '' }} onSeek={seek} />
                <div className="flex items-center gap-2 text-sm text-slate-500">
                  <Spinner /> Thinking about the video…
                </div>
              </>
            )}
          </>
        )}
        {ask.isError && <p className="text-sm text-rose-600">{errorMessage(ask.error)}</p>}
      </div>
      <form onSubmit={onSubmit} className="mt-3 flex gap-2">
        <label className="flex-1">
          <span className="sr-only">Ask a question about the video</span>
          <input className="input" value={input} maxLength={2000} onChange={(e) => setInput(e.target.value)} placeholder="Ask a question about this video…" />
        </label>
        <button className="btn-primary px-3" disabled={!input.trim() || ask.isPending} aria-label="Send">
          {ask.isPending ? <Spinner /> : <SendHorizontal className="size-4" />}
        </button>
      </form>
    </div>
  )
}

function Bubble({ message, onSeek }: { message: ChatMessage; onSeek: (s: number) => void }) {
  const mine = message.role === 'user'
  return (
    <div className={clsx('flex', mine ? 'justify-end' : 'justify-start')}>
      <div className={clsx('max-w-[88%] rounded-2xl px-4 py-2.5 text-sm', mine ? 'bg-brand-600 text-white' : 'bg-white shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-800')}>
        {mine ? (
          <p className="whitespace-pre-wrap">{message.content}</p>
        ) : (
          <>
            <Markdown className="prose-sm" onTimestamp={onSeek}>
              {message.content}
            </Markdown>
            {message.citations.length > 0 && (
              <div className="mt-2 flex flex-wrap items-center gap-1.5 border-t border-slate-100 pt-2 text-xs text-slate-500 dark:border-slate-800">
                Sources:
                {message.citations.map((c) => (
                  <Timestamp key={c} seconds={c} onSeek={onSeek} />
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
