import Ionicons from '@expo/vector-icons/Ionicons'
import { useState } from 'react'
import { Pressable, Text, View } from 'react-native'
import { Markdown } from '../../components/Markdown'
import { Body, Button, Card, Chip, Input, Timestamp } from '../../components/ui'
import { useColors } from '../../lib/theme'
import type { Chapter } from '../../lib/types'
import { Diagram } from './Diagram'
import { useBookmark, useLearn, useProgress } from './context'

function Heading({ children }: { children: string }) {
  return <Text style={{ color: useColors().text, fontWeight: '800', fontSize: 16, marginBottom: 6 }}>{children}</Text>
}

export function OverviewSection() {
  const c = useColors()
  const { detail } = useLearn()
  const o = detail.overview
  if (!o) return null
  return (
    <View style={{ gap: 14 }}>
      <Text style={{ color: c.text, fontSize: 22, fontWeight: '800' }}>{o.title}</Text>
      <Body>{o.whatIsItAbout}</Body>
      <Card>
        <Heading>What you'll learn</Heading>
        {o.whatYouWillLearn.map((x) => (
          <Body key={x}>✓ {x}</Body>
        ))}
      </Card>
      <Card>
        <Heading>Why it matters</Heading>
        <Body>{o.whyItMatters}</Body>
      </Card>
      <Card>
        <Heading>Who should watch</Heading>
        <Body>{o.whoShouldWatch}</Body>
      </Card>
      {o.keyTakeaways.length > 0 && (
        <Card>
          <Heading>Key takeaways</Heading>
          {o.keyTakeaways.map((k, i) => (
            <Body key={k}>
              {i + 1}. {k}
            </Body>
          ))}
        </Card>
      )}
      {detail.illustrations
        .filter((i) => i.chapterId == null)
        .map((i) => (
          <Diagram key={i.id} illustration={i} />
        ))}
    </View>
  )
}

export function TopicsSection() {
  const { detail } = useLearn()
  const [open, setOpen] = useState<number | null>(detail.chapters[0]?.id ?? null)
  return (
    <View style={{ gap: 10 }}>
      <Text style={{ color: useColors().muted }}>{detail.progress.progressPercent}% understood</Text>
      {detail.chapters.map((ch) => (
        <ChapterItem key={ch.id} chapter={ch} open={open === ch.id} onToggle={() => setOpen(open === ch.id ? null : ch.id)} />
      ))}
    </View>
  )
}

function ChapterItem({ chapter, open, onToggle }: { chapter: Chapter; open: boolean; onToggle: () => void }) {
  const c = useColors()
  const { detail, seek } = useLearn()
  const progress = useProgress(detail.status.id)
  const bookmark = useBookmark(detail.status.id)
  const done = detail.progress.completedChapterIds.includes(chapter.id)
  const toggleDone = () => {
    const ids = new Set(detail.progress.completedChapterIds)
    if (done) ids.delete(chapter.id)
    else ids.add(chapter.id)
    progress.mutate({ completedChapterIds: [...ids] })
  }
  return (
    <Card style={{ padding: 0 }}>
      <Pressable onPress={onToggle} accessibilityRole="button" accessibilityState={{ expanded: open }} style={{ flexDirection: 'row', gap: 12, padding: 14, alignItems: 'center' }}>
        <View style={{ width: 28, height: 28, borderRadius: 14, alignItems: 'center', justifyContent: 'center', backgroundColor: done ? c.success : c.brandSoft }}>
          <Text style={{ color: done ? '#fff' : c.brandText, fontWeight: '800', fontSize: 12 }}>{done ? '✓' : chapter.position + 1}</Text>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={{ color: c.text, fontWeight: '700', fontSize: 15 }}>{chapter.title}</Text>
          <Text style={{ color: c.muted, fontSize: 13 }} numberOfLines={open ? undefined : 1}>
            {chapter.shortSummary}
          </Text>
        </View>
        <Ionicons name={open ? 'chevron-up' : 'chevron-down'} size={18} color={c.muted} />
      </Pressable>
      {open && (
        <View style={{ padding: 14, paddingTop: 0, gap: 12 }}>
          <Timestamp seconds={chapter.startSeconds} onSeek={seek} />
          <View style={{ backgroundColor: c.brandSoft, padding: 12, borderRadius: 12 }}>
            <Text style={{ color: c.brandText, fontWeight: '800', fontSize: 11, marginBottom: 4 }}>IN SIMPLE WORDS</Text>
            <Body>{chapter.simpleExplanation}</Body>
          </View>
          <Markdown onTimestamp={seek}>{chapter.detailedExplanation}</Markdown>
          {!!chapter.realWorldExample && (
            <View style={{ backgroundColor: c.warnSoft, padding: 12, borderRadius: 12 }}>
              <Text style={{ color: c.warnText, fontWeight: '700' }}>Real-world example</Text>
              <Body>{chapter.realWorldExample}</Body>
            </View>
          )}
          {!!chapter.practicalUseCase && (
            <View style={{ backgroundColor: c.successSoft, padding: 12, borderRadius: 12 }}>
              <Text style={{ color: c.success, fontWeight: '700' }}>Practical use</Text>
              <Body>{chapter.practicalUseCase}</Body>
            </View>
          )}
          {chapter.keyPoints.length > 0 && (
            <View>
              <Heading>Remember this</Heading>
              {chapter.keyPoints.map((k) => (
                <Body key={k}>• {k}</Body>
              ))}
            </View>
          )}
          {detail.illustrations
            .filter((i) => i.chapterId === chapter.id)
            .map((i) => (
              <Diagram key={i.id} illustration={i} />
            ))}
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <Button title={done ? 'Understood' : 'Mark understood'} icon="checkmark" variant={done ? 'secondary' : 'primary'} onPress={toggleDone} loading={progress.isPending} style={{ flex: 1 }} />
            <Button
              title="Bookmark"
              icon="bookmark-outline"
              variant="secondary"
              onPress={() => bookmark.mutate({ title: chapter.title, chapterId: chapter.id, timestampSeconds: chapter.startSeconds })}
            />
          </View>
        </View>
      )}
    </Card>
  )
}

export function ExplainSection() {
  const { detail } = useLearn()
  const [mode, setMode] = useState<'beginner' | 'technical'>('beginner')
  return (
    <View style={{ gap: 12 }}>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Chip label="Beginner" icon="school-outline" active={mode === 'beginner'} onPress={() => setMode('beginner')} />
        <Chip label="Technical" icon="hardware-chip-outline" active={mode === 'technical'} onPress={() => setMode('technical')} />
      </View>
      <Markdown>{mode === 'beginner' ? detail.beginnerExplanation : detail.technicalExplanation}</Markdown>
    </View>
  )
}

export function ConceptsSection() {
  const c = useColors()
  const { detail, seek } = useLearn()
  const bookmark = useBookmark(detail.status.id)
  const [q, setQ] = useState('')
  const list = detail.concepts.filter((x) => `${x.term} ${x.definition}`.toLowerCase().includes(q.toLowerCase()))
  return (
    <View style={{ gap: 10 }}>
      <Input value={q} onChangeText={setQ} placeholder="Search concepts…" accessibilityLabel="Search concepts" />
      {list.map((x) => (
        <Card key={x.term} style={{ gap: 6 }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
            <Text style={{ color: c.text, fontWeight: '800', flex: 1 }}>{x.term}</Text>
            <Timestamp seconds={x.timestampSeconds} onSeek={seek} />
            <Pressable hitSlop={8} accessibilityLabel={`Bookmark ${x.term}`} onPress={() => bookmark.mutate({ title: x.term, timestampSeconds: x.timestampSeconds, note: x.simpleExplanation })}>
              <Ionicons name="bookmark-outline" size={18} color={c.muted} />
            </Pressable>
          </View>
          <Text style={{ color: c.brandText, fontSize: 11, fontWeight: '800' }}>{x.kind}</Text>
          <Body style={x.kind === 'FORMULA' ? { fontFamily: 'monospace' } : undefined}>{x.definition}</Body>
          <Body muted>💡 {x.simpleExplanation}</Body>
        </Card>
      ))}
      {list.length === 0 && <Body muted>No concepts match.</Body>}
    </View>
  )
}

export function SummarySection() {
  const { detail } = useLearn()
  const [len, setLen] = useState<'oneMinute' | 'fiveMinute' | 'detailed'>('oneMinute')
  if (!detail.summaries) return null
  return (
    <View style={{ gap: 12 }}>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Chip label="1 min" active={len === 'oneMinute'} onPress={() => setLen('oneMinute')} />
        <Chip label="5 min" active={len === 'fiveMinute'} onPress={() => setLen('fiveMinute')} />
        <Chip label="Detailed" active={len === 'detailed'} onPress={() => setLen('detailed')} />
      </View>
      <Markdown>{detail.summaries[len]}</Markdown>
    </View>
  )
}
