import { router } from 'expo-router'
import { Image, Pressable, Text, View } from 'react-native'
import { relativeTime } from '../lib/format'
import { useColors } from '../lib/theme'
import type { HistoryItem } from '../lib/types'

const LABEL: Partial<Record<HistoryItem['status'], string>> = { FAILED: 'Failed', AWAITING_TRANSCRIPT: 'Needs transcript', COMPLETED: '' }

export function VideoRow({ item, onLongPress }: { item: HistoryItem; onLongPress?: () => void }) {
  const c = useColors()
  const label = LABEL[item.status] ?? 'Processing…'
  return (
    <Pressable
      onPress={() => router.push({ pathname: '/learn/[id]', params: { id: String(item.analysisId) } })}
      onLongPress={onLongPress}
      accessibilityRole="button"
      accessibilityHint={onLongPress ? 'Long press for options' : undefined}
      style={({ pressed }) => ({ flexDirection: 'row', gap: 12, padding: 10, borderRadius: 16, backgroundColor: pressed ? c.brandSoft : c.card, borderWidth: 1, borderColor: c.border })}
    >
      <Image source={item.thumbnailUrl ? { uri: item.thumbnailUrl } : undefined} style={{ width: 112, height: 63, borderRadius: 10, backgroundColor: c.border }} />
      <View style={{ flex: 1, gap: 4 }}>
        <Text numberOfLines={2} style={{ color: c.text, fontWeight: '700', fontSize: 14 }}>
          {item.title}
        </Text>
        <Text style={{ color: label ? c.warnText : c.muted, fontSize: 12 }}>{label || `${item.category ?? ''} · ${relativeTime(item.lastAccessedAt)}`}</Text>
        {item.status === 'COMPLETED' && (
          <View style={{ height: 4, borderRadius: 2, backgroundColor: c.border, overflow: 'hidden' }}>
            <View style={{ width: `${item.progressPercent}%`, height: 4, backgroundColor: c.brand }} />
          </View>
        )}
      </View>
    </Pressable>
  )
}
