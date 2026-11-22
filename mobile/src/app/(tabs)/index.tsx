import { useQuery } from '@tanstack/react-query'
import { Link } from 'expo-router'
import { RefreshControl, ScrollView, Text, View } from 'react-native'
import { AnalyzeBox } from '../../components/AnalyzeBox'
import { Body, Card } from '../../components/ui'
import { VideoRow } from '../../components/VideoRow'
import { api } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { useColors } from '../../lib/theme'
import type { HistoryItem, HistoryStats, Page } from '../../lib/types'

export default function HomeScreen() {
  const c = useColors()
  const { user, config } = useAuth()
  const recent = useQuery({
    queryKey: ['history', 'recent'],
    queryFn: async () => (await api.get<Page<HistoryItem>>('/api/history', { params: { size: 5 } })).data,
    enabled: Boolean(user),
  })
  const stats = useQuery({ queryKey: ['history', 'stats'], queryFn: async () => (await api.get<HistoryStats>('/api/history/stats')).data, enabled: Boolean(user) })

  return (
    <ScrollView
      contentContainerStyle={{ padding: 16, gap: 20, paddingBottom: 40 }}
      keyboardShouldPersistTaps="handled"
      refreshControl={<RefreshControl refreshing={recent.isRefetching} onRefresh={() => void recent.refetch()} />}
    >
      <View style={{ gap: 8, paddingTop: 8 }}>
        <Text style={{ color: c.text, fontSize: 30, fontWeight: '800', letterSpacing: -0.8, lineHeight: 36 }}>
          Turn any YouTube video into something you <Text style={{ color: c.brand }}>can understand.</Text>
        </Text>
        <Body muted>Paste a video link. Learn difficult topics through simple explanations, illustrations and real-world examples.</Body>
      </View>
      <Card>
        <AnalyzeBox />
        {config && !config.aiConfigured && <Text style={{ color: c.warnText, marginTop: 10 }}>AI isn't configured on the server yet, so new videos can't be explained.</Text>}
        {!user && (
          <Link href="/sign-in" style={{ color: c.brandText, marginTop: 12, fontWeight: '600' }}>
            Sign in to keep your history →
          </Link>
        )}
      </Card>

      {stats.data && stats.data.videos > 0 && (
        <View style={{ flexDirection: 'row', gap: 10 }}>
          {[
            ['Videos', String(stats.data.videos)],
            ['Quiz avg', stats.data.quizAttempts ? `${stats.data.averageQuizScore}%` : '—'],
            ['Bookmarks', String(stats.data.bookmarks)],
          ].map(([label, value]) => (
            <Card key={label} style={{ flex: 1, padding: 12 }}>
              <Text style={{ color: c.muted, fontSize: 11, fontWeight: '700', textTransform: 'uppercase' }}>{label}</Text>
              <Text style={{ color: c.text, fontSize: 22, fontWeight: '800' }}>{value}</Text>
            </Card>
          ))}
        </View>
      )}

      {user && (
        <View style={{ gap: 10 }}>
          <Text style={{ color: c.text, fontSize: 19, fontWeight: '800' }}>Continue learning</Text>
          {recent.data?.items.length ? (
            recent.data.items.map((item) => <VideoRow key={item.analysisId} item={item} />)
          ) : (
            <Body muted>Videos you analyze will appear here.</Body>
          )}
        </View>
      )}
    </ScrollView>
  )
}
