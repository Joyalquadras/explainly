import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { ChevronLeft, ChevronRight, Layers, RefreshCw, Shuffle } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { EmptyState, ErrorState, Skeleton, Spinner } from '../../components/States'
import { api, errorMessage } from '../../lib/api'
import type { Flashcard } from '../../lib/types'
import { useLearn } from './context'

export function FlashcardsPanel() {
  const { detail } = useLearn()
  const id = detail.status.id
  const qc = useQueryClient()
  const [order, setOrder] = useState<number[]>([])
  const [index, setIndex] = useState(0)
  const [flipped, setFlipped] = useState(false)

  const cards = useQuery({ queryKey: ['flashcards', id], queryFn: async () => (await api.get<Flashcard[]>(`/api/analyses/${id}/flashcards`)).data })
  const generate = useMutation({
    mutationFn: async () => (await api.post<Flashcard[]>(`/api/analyses/${id}/flashcards`)).data,
    onSuccess: (data) => qc.setQueryData(['flashcards', id], data),
  })

  const list = cards.data ?? []
  useEffect(() => {
    setOrder(list.map((_, i) => i))
    setIndex(0)
    setFlipped(false)
  }, [list.length, cards.data]) // eslint-disable-line react-hooks/exhaustive-deps

  const go = useCallback(
    (delta: number) => {
      if (!list.length) return
      setFlipped(false)
      setIndex((i) => (i + delta + list.length) % list.length)
    },
    [list.length],
  )

  if (cards.isLoading) return <Skeleton className="h-72" />
  if (cards.isError) return <ErrorState message={errorMessage(cards.error)} onRetry={() => void cards.refetch()} />
  if (list.length === 0) {
    return (
      <EmptyState icon={Layers} title="Revise with flashcards">
        <p>Create a deck of question-and-answer cards from this video.</p>
        <button className="btn-primary mt-4" onClick={() => generate.mutate()} disabled={generate.isPending}>
          {generate.isPending && <Spinner />} {generate.isPending ? 'Creating cards…' : 'Create flashcards'}
        </button>
        {generate.isError && <p className="mt-2 text-rose-600">{errorMessage(generate.error)}</p>}
      </EmptyState>
    )
  }

  const card = list[order[index] ?? 0]
  return (
    <div
      className="mx-auto max-w-xl outline-none"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'ArrowRight') go(1)
        if (e.key === 'ArrowLeft') go(-1)
        if (e.key === ' ' || e.key === 'Enter') {
          e.preventDefault()
          setFlipped((f) => !f)
        }
      }}
      aria-label="Flashcards. Use arrow keys to move and space to flip."
    >
      <div className="mb-3 flex items-center justify-between text-sm text-slate-500">
        <span>
          Card {index + 1} of {list.length}
        </span>
        <div className="flex gap-1">
          <button
            className="btn-ghost px-2 py-1.5"
            onClick={() => {
              setOrder((o) => [...o].sort(() => Math.random() - 0.5))
              setIndex(0)
              setFlipped(false)
            }}
          >
            <Shuffle className="size-4" /> Shuffle
          </button>
          <button className="btn-ghost px-2 py-1.5" onClick={() => generate.mutate()} disabled={generate.isPending}>
            {generate.isPending ? <Spinner /> : <RefreshCw className="size-4" />} New deck
          </button>
        </div>
      </div>
      <button type="button" onClick={() => setFlipped((f) => !f)} className="block w-full [perspective:1200px]" aria-live="polite" aria-label={flipped ? 'Show question' : 'Show answer'}>
        <div className={clsx('relative h-64 transition-transform duration-500 [transform-style:preserve-3d]', flipped && '[transform:rotateY(180deg)]')}>
          <div className="absolute inset-0 flex flex-col items-center justify-center rounded-2xl bg-gradient-to-br from-brand-600 to-violet-600 p-6 text-center text-white shadow-lg [backface-visibility:hidden]">
            <span className="mb-3 text-xs font-semibold uppercase tracking-widest text-white/70">Question</span>
            <p className="text-lg font-semibold">{card.question}</p>
            <span className="mt-6 text-xs text-white/60">Tap to reveal</span>
          </div>
          <div className="absolute inset-0 flex flex-col items-center justify-center overflow-y-auto rounded-2xl border border-slate-200 bg-white p-6 text-center shadow-lg [backface-visibility:hidden] [transform:rotateY(180deg)] dark:border-slate-700 dark:bg-slate-900">
            <span className="mb-3 text-xs font-semibold uppercase tracking-widest text-slate-400">Answer</span>
            <p className="text-lg font-medium">{card.answer}</p>
            {card.definition && <p className="mt-4 rounded-xl bg-slate-50 p-3 text-sm text-slate-600 dark:bg-slate-800 dark:text-slate-300">📘 {card.definition}</p>}
          </div>
        </div>
      </button>
      <div className="mt-4 flex items-center justify-center gap-3">
        <button className="btn-secondary" onClick={() => go(-1)} aria-label="Previous card">
          <ChevronLeft className="size-4" /> Prev
        </button>
        <button className="btn-secondary" onClick={() => go(1)} aria-label="Next card">
          Next <ChevronRight className="size-4" />
        </button>
      </div>
    </div>
  )
}
