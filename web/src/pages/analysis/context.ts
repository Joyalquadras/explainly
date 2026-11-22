import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createContext, useContext } from 'react'
import { useToast } from '../../components/Toast'
import { api, errorMessage } from '../../lib/api'
import type { AnalysisDetail, Bookmark, Progress } from '../../lib/types'

export interface LearnContextValue {
  detail: AnalysisDetail
  seek: (seconds: number) => void
  currentTime: () => number | null
}

export const LearnContext = createContext<LearnContextValue | null>(null)

export function useLearn() {
  const ctx = useContext(LearnContext)
  if (!ctx) throw new Error('useLearn must be used inside the learning dashboard')
  return ctx
}

export function useCreateBookmark(analysisId: number) {
  const qc = useQueryClient()
  const toast = useToast()
  return useMutation({
    mutationFn: async (input: { title: string; chapterId?: number | null; timestampSeconds?: number | null; note?: string }) =>
      (await api.post<Bookmark>('/api/bookmarks', { analysisId, ...input })).data,
    onSuccess: () => {
      toast('Bookmarked', 'success')
      void qc.invalidateQueries({ queryKey: ['bookmarks'] })
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })
}

export function useUpdateProgress(analysisId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (input: { completedChapterIds: number[]; lastPositionSeconds?: number | null }) =>
      (await api.put<Progress>(`/api/history/${analysisId}/progress`, input)).data,
    onSuccess: (progress) => {
      qc.setQueryData<AnalysisDetail>(['analysis', analysisId], (old) => (old ? { ...old, progress } : old))
      void qc.invalidateQueries({ queryKey: ['history'] })
    },
  })
}
