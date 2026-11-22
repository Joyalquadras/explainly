import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Library, Trash2 } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { useToast } from '../components/Toast'
import { UrlForm } from '../components/UrlForm'
import { VideoCard } from '../components/VideoCard'
import { api, errorMessage } from '../lib/api'
import type { HistoryItem, HistoryStats, Page } from '../lib/types'

export function LibraryPage() {
  const [params, setParams] = useSearchParams()
  const category = params.get('category') ?? ''
  const page = Number(params.get('page') ?? 0)
  const qc = useQueryClient()
  const toast = useToast()

  const stats = useQuery({ queryKey: ['history', 'stats'], queryFn: async () => (await api.get<HistoryStats>('/api/history/stats')).data })
  const list = useQuery({
    queryKey: ['history', 'list', category, page],
    queryFn: async () => (await api.get<Page<HistoryItem>>('/api/history', { params: { category: category || undefined, page, size: 12 } })).data,
    placeholderData: keepPreviousData,
  })
  const remove = useMutation({
    mutationFn: (analysisId: number) => api.delete(`/api/history/${analysisId}`),
    onSuccess: () => {
      toast('Removed from your library', 'success')
      void qc.invalidateQueries({ queryKey: ['history'] })
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  const setFilter = (next: Record<string, string | null>) => {
    const p = new URLSearchParams(params)
    Object.entries(next).forEach(([k, v]) => (v ? p.set(k, v) : p.delete(k)))
    setParams(p)
  }
  const categories = Object.entries(stats.data?.categories ?? {})

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold">Your library</h1>
          <p className="text-sm text-slate-500">Every video you've analyzed, with your progress.</p>
        </div>
        <div className="w-full sm:max-w-md">
          <UrlForm size="md" />
        </div>
      </div>

      {categories.length > 0 && (
        <div className="mb-6 flex gap-2 overflow-x-auto pb-1" role="group" aria-label="Filter by category">
          {[['', stats.data?.videos ?? 0] as [string, number], ...categories].map(([name, count]) => (
            <button
              key={name || 'all'}
              onClick={() => setFilter({ category: name || null, page: null })}
              className={clsx(
                'chip shrink-0 px-3 py-1.5 text-sm',
                category === name ? 'bg-brand-600 text-white' : 'bg-white text-slate-700 ring-1 ring-slate-200 hover:bg-slate-50 dark:bg-slate-900 dark:text-slate-300 dark:ring-slate-700',
              )}
              aria-pressed={category === name}
            >
              {name || 'All'} <span className="opacity-70">{count}</span>
            </button>
          ))}
        </div>
      )}

      {list.isLoading ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} className="h-64" />
          ))}
        </div>
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => void list.refetch()} />
      ) : list.data && list.data.items.length > 0 ? (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {list.data.items.map((item) => (
              <div key={item.analysisId} className="group relative">
                <VideoCard item={item} />
                <button
                  className="absolute right-2 top-2 rounded-lg bg-black/60 p-1.5 text-white opacity-100 transition hover:bg-rose-600 sm:opacity-0 sm:group-hover:opacity-100 focus:opacity-100"
                  aria-label={`Remove ${item.title} from library`}
                  onClick={() => {
                    if (confirm('Remove this video from your library?')) remove.mutate(item.analysisId)
                  }}
                >
                  <Trash2 className="size-4" />
                </button>
              </div>
            ))}
          </div>
          {list.data.totalPages > 1 && (
            <nav className="mt-8 flex items-center justify-center gap-3" aria-label="Pagination">
              <button className="btn-secondary" disabled={page === 0} onClick={() => setFilter({ page: String(page - 1) })}>
                Previous
              </button>
              <span className="text-sm text-slate-500">
                Page {page + 1} of {list.data.totalPages}
              </span>
              <button className="btn-secondary" disabled={!list.data.hasNext} onClick={() => setFilter({ page: String(page + 1) })}>
                Next
              </button>
            </nav>
          )}
        </>
      ) : (
        <EmptyState icon={Library} title={category ? `No ${category} videos yet` : 'Your library is empty'}>
          Paste a YouTube link above to start learning. Everything you analyze is saved here.
        </EmptyState>
      )}
    </div>
  )
}
