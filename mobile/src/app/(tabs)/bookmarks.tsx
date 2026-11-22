import Ionicons from '@expo/vector-icons/Ionicons'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Redirect, router } from 'expo-router'
import { FlatList, Pressable, Text, View } from 'react-native'
import { EmptyView, ErrorView, Loading } from '../../components/ui'
import { api, errorMessage } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { clock } from '../../lib/format'
import { useColors } from '../../lib/theme'
import type { Bookmark, Page } from '../../lib/types'

export default function BookmarksScreen() {
  const c = useColors()
  const { user, ready } = useAuth()
  const qc = useQueryClient()
  const list = useInfiniteQuery({
    queryKey: ['bookmarks', 'infinite'],
    queryFn: async ({ pageParam }) => (await api.get<Page<Bookmark>>('/api/bookmarks', { params: { page: pageParam, size: 20 } })).data,
    initialPageParam: 0,
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
    enabled: Boolean(user),
  })
  const remove = useMutation({
    mutationFn: (id: number) => api.delete(`/api/bookmarks/${id}`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['bookmarks'] }),
  })
  if (ready && !user) return <Redirect href="/sign-in" />
  const items = list.data?.pages.flatMap((p) => p.items) ?? []
  return (
    <FlatList
      data={items}
      keyExtractor={(b) => String(b.id)}
      contentContainerStyle={{ padding: 16, gap: 10, flexGrow: 1 }}
      renderItem={({ item: b }) => (
        <Pressable
          onPress={() => router.push({ pathname: '/learn/[id]', params: { id: String(b.analysisId), t: b.timestampSeconds != null ? String(b.timestampSeconds) : undefined } })}
          style={{ flexDirection: 'row', gap: 12, padding: 14, borderRadius: 16, backgroundColor: c.card, borderWidth: 1, borderColor: c.border, alignItems: 'center' }}
        >
          <Ionicons name="bookmark" size={20} color="#D97706" />
          <View style={{ flex: 1, gap: 2 }}>
            <Text style={{ color: c.text, fontWeight: '700' }}>{b.title}</Text>
            <Text numberOfLines={1} style={{ color: c.muted, fontSize: 13 }}>
              {b.timestampSeconds != null ? `${clock(b.timestampSeconds)} · ` : ''}
              {b.videoTitle}
            </Text>
          </View>
          <Pressable onPress={() => remove.mutate(b.id)} hitSlop={10} accessibilityLabel={`Delete bookmark ${b.title}`} accessibilityRole="button">
            <Ionicons name="trash-outline" size={20} color={c.muted} />
          </Pressable>
        </Pressable>
      )}
      ListEmptyComponent={
        list.isLoading ? (
          <Loading />
        ) : list.isError ? (
          <ErrorView message={errorMessage(list.error)} onRetry={() => void list.refetch()} />
        ) : (
          <EmptyView icon="bookmark-outline" title="No bookmarks yet" text="Bookmark topics, concepts and moments while you learn." />
        )
      }
      onEndReached={() => list.hasNextPage && void list.fetchNextPage()}
      refreshing={list.isRefetching}
      onRefresh={() => void list.refetch()}
    />
  )
}
