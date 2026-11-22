import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Redirect } from 'expo-router'
import { useState } from 'react'
import { Alert, FlatList, ScrollView, View } from 'react-native'
import { Chip, EmptyView, ErrorView, Loading } from '../../components/ui'
import { VideoRow } from '../../components/VideoRow'
import { api, errorMessage } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import type { HistoryItem, HistoryStats, Page } from '../../lib/types'

export default function LibraryScreen() {
  const { user, ready } = useAuth()
  const qc = useQueryClient()
  const [category, setCategory] = useState('')
  const stats = useQuery({ queryKey: ['history', 'stats'], queryFn: async () => (await api.get<HistoryStats>('/api/history/stats')).data, enabled: Boolean(user) })
  const list = useInfiniteQuery({
    queryKey: ['history', 'infinite', category],
    queryFn: async ({ pageParam }) =>
      (await api.get<Page<HistoryItem>>('/api/history', { params: { page: pageParam, size: 15, category: category || undefined } })).data,
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
    enabled: Boolean(user),
  })
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/api/history/${id}`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['history'] }),
    onError: (e) => Alert.alert('Could not remove', errorMessage(e)),
  })

  if (ready && !user) return <Redirect href="/sign-in" />
  const items = list.data?.pages.flatMap((p) => p.items) ?? []
  const categories = Object.entries(stats.data?.categories ?? {})

  return (
    <FlatList
      data={items}
      keyExtractor={(i) => String(i.analysisId)}
      contentContainerStyle={{ padding: 16, gap: 10, flexGrow: 1 }}
      ListHeaderComponent={
        categories.length > 0 ? (
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingBottom: 6 }}>
            <Chip label="All" active={!category} onPress={() => setCategory('')} />
            {categories.map(([name, count]) => (
              <Chip key={name} label={`${name} ${count}`} active={category === name} onPress={() => setCategory(name)} />
            ))}
          </ScrollView>
        ) : undefined
      }
      renderItem={({ item }) => (
        <VideoRow
          item={item}
          onLongPress={() =>
            Alert.alert('Remove from library?', item.title, [
              { text: 'Cancel', style: 'cancel' },
              { text: 'Remove', style: 'destructive', onPress: () => remove.mutate(item.analysisId) },
            ])
          }
        />
      )}
      ListEmptyComponent={
        list.isLoading ? (
          <Loading />
        ) : list.isError ? (
          <ErrorView message={errorMessage(list.error)} onRetry={() => void list.refetch()} />
        ) : (
          <EmptyView icon="library-outline" title="Your library is empty" text="Analyze a video from the Home tab to start learning." />
        )
      }
      onEndReached={() => list.hasNextPage && !list.isFetchingNextPage && void list.fetchNextPage()}
      onEndReachedThreshold={0.4}
      ListFooterComponent={list.isFetchingNextPage ? <View style={{ padding: 16 }}><Loading /></View> : undefined}
      refreshing={list.isRefetching}
      onRefresh={() => void list.refetch()}
    />
  )
}
