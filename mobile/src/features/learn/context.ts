import { useMutation, useQueryClient } from '@tanstack/react-query'
import { createContext, useContext } from 'react'
import { Alert } from 'react-native'
import { api, errorMessage } from '../../lib/api'
import type { AnalysisDetail, Progress } from '../../lib/types'

export const LearnContext = createContext<{ detail: AnalysisDetail; seek: (s: number) => void; currentTime: () => Promise<number | null> } | null>(null)

export function useLearn() {
  const ctx = useContext(LearnContext)
  if (!ctx) throw new Error('useLearn outside LearnContext')
  return ctx
}

export function useBookmark(analysisId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (input: { title: string; chapterId?: number | null; timestampSeconds?: number | null; note?: string }) =>
      api.post('/api/bookmarks', { analysisId, ...input }),
    onSuccess: () => {
      Alert.alert('Bookmarked')
      void qc.invalidateQueries({ queryKey: ['bookmarks'] })
    },
    onError: (e) => Alert.alert('Could not bookmark', errorMessage(e)),
  })
}

export function useProgress(analysisId: number) {
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
