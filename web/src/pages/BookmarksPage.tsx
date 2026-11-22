import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bookmark as BookmarkIcon, Clock, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState, ErrorState, Skeleton } from '../components/States'
import { useToast } from '../components/Toast'
import { api, errorMessage } from '../lib/api'
import { clock, relativeTime } from '../lib/format'
import type { Bookmark, Page } from '../lib/types'

export function BookmarksPage() {
  const [page, setPage] = useState(0)
  const qc = useQueryClient()
  const toast = useToast()
  const list = useQuery({
    queryKey: ['bookmarks', 'all', page],
    queryFn: async () => (await api.get<Page<Bookmark>>('/api/bookmarks', { params: { page, size: 20 } })).data,
    placeholderData: keepPreviousData,
  })
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/api/bookmarks/${id}`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['bookmarks'] }),
    onError: (e) => toast(errorMessage(e), 'error'),
  })

  return (
    <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6">
      <h1 className="text-2xl font-bold">Bookmarks</h1>
      <p className="mb-6 text-sm text-slate-500">Moments and concepts you saved to come back to.</p>
      {list.isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-20" />
          ))}
        </div>
      ) : list.isError ? (
        <ErrorState message={errorMessage(list.error)} onRetry={() => void list.refetch()} />
      ) : list.data && list.data.items.length > 0 ? (
        <>
          <ul className="space-y-3">
            {list.data.items.map((b) => (
              <li key={b.id} className="card flex items-start gap-3 p-4">
                <div className="rounded-xl bg-amber-50 p-2 text-amber-600 dark:bg-amber-950/40 dark:text-amber-300">
                  <BookmarkIcon className="size-5" />
                </div>
                <Link to={`/learn/${b.analysisId}${b.timestampSeconds != null ? `?t=${b.timestampSeconds}` : ''}`} className="min-w-0 flex-1">
                  <p className="font-semibold hover:text-brand-600 dark:hover:text-brand-300">{b.title}</p>
                  <p className="truncate text-sm text-slate-500">{b.videoTitle}</p>
                  {b.note && <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">{b.note}</p>}
                  <p className="mt-1 flex items-center gap-3 text-xs text-slate-400">
                    {b.timestampSeconds != null && (
                      <span className="flex items-center gap-1">
                        <Clock className="size-3" /> {clock(b.timestampSeconds)}
                      </span>
                    )}
                    {relativeTime(b.createdAt)}
                  </p>
                </Link>
                <button className="btn-ghost p-2" aria-label={`Delete bookmark ${b.title}`} onClick={() => remove.mutate(b.id)}>
                  <Trash2 className="size-4" />
                </button>
              </li>
            ))}
          </ul>
          {list.data.totalPages > 1 && (
            <div className="mt-6 flex justify-center gap-3">
              <button className="btn-secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
                Previous
              </button>
              <button className="btn-secondary" disabled={!list.data.hasNext} onClick={() => setPage((p) => p + 1)}>
                Next
              </button>
            </div>
          )}
        </>
      ) : (
        <EmptyState icon={BookmarkIcon} title="No bookmarks yet">
          While learning, tap the bookmark icon on a topic, concept or moment in the video to save it here.
        </EmptyState>
      )}
    </div>
  )
}
