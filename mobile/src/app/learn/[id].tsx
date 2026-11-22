import { useQuery } from '@tanstack/react-query'
import { Redirect, Stack, useLocalSearchParams } from 'expo-router'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { KeyboardAvoidingView, Platform, ScrollView, View, useWindowDimensions, type ScrollViewInstance } from 'react-native'
import YoutubePlayer, { type YoutubeIframeRef } from 'react-native-youtube-iframe'
import { Chip, ErrorView, Loading } from '../../components/ui'
import { LearnContext, useProgress } from '../../features/learn/context'
import { Processing } from '../../features/learn/Processing'
import { ConceptsSection, ExplainSection, OverviewSection, SummarySection, TopicsSection } from '../../features/learn/Sections'
import { ChatSection, FlashcardsSection, NotesSection, QuizSection } from '../../features/learn/Tools'
import { api, errorMessage } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import type { AnalysisDetail } from '../../lib/types'

const IN_FLIGHT = new Set(['PENDING', 'FETCHING_TRANSCRIPT', 'PROCESSING', 'GENERATING_EXPLANATION', 'GENERATING_ILLUSTRATIONS'])
const SECTIONS = [
  { key: 'overview', label: 'Overview', icon: 'compass-outline', View: OverviewSection },
  { key: 'topics', label: 'Topics', icon: 'list-outline', View: TopicsSection },
  { key: 'explain', label: 'Explain', icon: 'school-outline', View: ExplainSection },
  { key: 'concepts', label: 'Concepts', icon: 'bulb-outline', View: ConceptsSection },
  { key: 'summary', label: 'Summary', icon: 'document-text-outline', View: SummarySection },
  { key: 'chat', label: 'Ask AI', icon: 'chatbubbles-outline', View: ChatSection },
  { key: 'notes', label: 'Notes', icon: 'create-outline', View: NotesSection },
  { key: 'quiz', label: 'Quiz', icon: 'checkbox-outline', View: QuizSection },
  { key: 'cards', label: 'Cards', icon: 'layers-outline', View: FlashcardsSection },
] as const

export default function LearnScreen() {
  const { id, t } = useLocalSearchParams<{ id: string; t?: string }>()
  const { user, ready } = useAuth()
  const analysisId = Number(id)
  const detail = useQuery({
    queryKey: ['analysis', analysisId],
    queryFn: async () => (await api.get<AnalysisDetail>(`/api/analyses/${analysisId}`)).data,
    enabled: Boolean(user) && Number.isFinite(analysisId),
    refetchInterval: (q) => (q.state.data && IN_FLIGHT.has(q.state.data.status.status) ? 2500 : false),
  })
  if (ready && !user) return <Redirect href="/sign-in" />
  if (detail.isLoading || !ready) return <Loading label="Loading…" />
  if (detail.isError || !detail.data) return <View style={{ padding: 16 }}><ErrorView message={errorMessage(detail.error)} onRetry={() => void detail.refetch()} /></View>
  return (
    <>
      <Stack.Screen options={{ title: detail.data.overview?.title ?? detail.data.video.title }} />
      {detail.data.status.status === 'COMPLETED' ? <Learning detail={detail.data} start={t ? Number(t) : null} /> : <Processing detail={detail.data} />}
    </>
  )
}

function Learning({ detail, start }: { detail: AnalysisDetail; start: number | null }) {
  const { width } = useWindowDimensions()
  const player = useRef<YoutubeIframeRef>(null)
  const scroller = useRef<ScrollViewInstance>(null)
  const [section, setSection] = useState<(typeof SECTIONS)[number]['key']>('overview')
  const [playing, setPlaying] = useState(false)
  const initial = useMemo(() => start ?? detail.progress.lastPositionSeconds ?? 0, []) // eslint-disable-line react-hooks/exhaustive-deps
  const progress = useProgress(detail.status.id)

  const seek = useCallback((s: number) => {
    player.current?.seekTo(s, true)
    setPlaying(true)
  }, [])
  const currentTime = useCallback(async () => (player.current ? await player.current.getCurrentTime() : null), [])

  // Save the playback position periodically so learning can resume on web or mobile.
  const completed = useRef(detail.progress.completedChapterIds)
  completed.current = detail.progress.completedChapterIds
  const saveProgress = progress.mutate
  useEffect(() => {
    let last = detail.progress.lastPositionSeconds ?? -100
    const timer = setInterval(async () => {
      const now = await currentTime()
      if (now != null && Math.abs(now - last) >= 10) {
        last = Math.floor(now)
        saveProgress({ completedChapterIds: completed.current, lastPositionSeconds: last })
      }
    }, 15_000)
    return () => clearInterval(timer)
  }, [currentTime, saveProgress, detail.progress.lastPositionSeconds])

  const Active = SECTIONS.find((s) => s.key === section)!.View
  return (
    <LearnContext.Provider value={{ detail, seek, currentTime }}>
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined} keyboardVerticalOffset={90}>
        <YoutubePlayer
          ref={player}
          height={(width * 9) / 16}
          videoId={detail.video.youtubeId}
          play={playing}
          onChangeState={(s: string) => setPlaying(s === 'playing' || s === 'buffering')}
          initialPlayerParams={{ start: Math.floor(initial), modestbranding: true, rel: false }}
        />
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ flexGrow: 0 }} contentContainerStyle={{ gap: 8, padding: 12 }}>
          {SECTIONS.map((s) => (
            <Chip
              key={s.key}
              label={s.label}
              icon={s.icon}
              active={section === s.key}
              onPress={() => {
                setSection(s.key)
                scroller.current?.scrollTo({ y: 0, animated: false })
              }}
            />
          ))}
        </ScrollView>
        <ScrollView ref={scroller} contentContainerStyle={{ padding: 16, paddingTop: 4, paddingBottom: 48 }} keyboardShouldPersistTaps="handled">
          <Active />
        </ScrollView>
      </KeyboardAvoidingView>
    </LearnContext.Provider>
  )
}
