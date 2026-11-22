import { useMutation, useQueryClient } from '@tanstack/react-query'
import * as DocumentPicker from 'expo-document-picker'
import { useState } from 'react'
import { ActivityIndicator, Image, ScrollView, Text, View } from 'react-native'
import { Body, Button, Card, Chip, Input } from '../../components/ui'
import { api, errorMessage } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { useColors } from '../../lib/theme'
import type { AnalysisDetail } from '../../lib/types'

const STEPS = [
  ['FETCHING_TRANSCRIPT', 'Getting the transcript'],
  ['PROCESSING', 'Reading the whole video'],
  ['GENERATING_EXPLANATION', 'Writing simple explanations'],
  ['GENERATING_ILLUSTRATIONS', 'Drawing diagrams'],
] as const
const ORDER = ['PENDING', 'FETCHING_TRANSCRIPT', 'PROCESSING', 'GENERATING_EXPLANATION', 'GENERATING_ILLUSTRATIONS', 'COMPLETED']

export function Processing({ detail }: { detail: AnalysisDetail }) {
  const c = useColors()
  const { status, video } = detail
  const current = ORDER.indexOf(status.status)
  return (
    <ScrollView contentContainerStyle={{ padding: 16, gap: 14 }} keyboardShouldPersistTaps="handled">
      {video.thumbnailUrl && <Image source={{ uri: video.thumbnailUrl }} style={{ width: '100%', aspectRatio: 16 / 9, borderRadius: 16 }} />}
      <Text style={{ color: c.text, fontSize: 20, fontWeight: '800' }}>{video.title}</Text>
      {status.status === 'AWAITING_TRANSCRIPT' || status.status === 'FAILED' ? (
        <>
          <Card style={{ backgroundColor: status.status === 'FAILED' ? c.dangerSoft : c.warnSoft, gap: 6 }}>
            <Text style={{ color: c.text, fontWeight: '700' }}>{status.status === 'FAILED' ? "We couldn't finish this video" : 'We need the transcript'}</Text>
            <Body>{status.status === 'FAILED' ? status.errorMessage : status.statusMessage}</Body>
          </Card>
          {status.canRetry && <RetryButton id={status.id} />}
          {status.canSubmitTranscript ? (
            <TranscriptForm id={status.id} />
          ) : (
            status.status === 'AWAITING_TRANSCRIPT' && <Body muted>Only the person who added this video can provide its transcript.</Body>
          )}
        </>
      ) : (
        <Card style={{ gap: 14 }} accessibilityLiveRegion="polite">
          <Text style={{ color: c.text, fontWeight: '700' }}>
            {status.statusMessage ?? 'Working…'} · {status.progress}%
          </Text>
          <View style={{ height: 8, backgroundColor: c.border, borderRadius: 4, overflow: 'hidden' }}>
            <View style={{ width: `${Math.max(4, status.progress)}%`, height: 8, backgroundColor: c.brand }} />
          </View>
          {STEPS.map(([key, label]) => {
            const idx = ORDER.indexOf(key)
            return (
              <View key={key} style={{ flexDirection: 'row', gap: 10, alignItems: 'center' }}>
                {idx === current ? <ActivityIndicator size="small" color={c.brand} /> : <Text style={{ color: idx < current ? c.success : c.muted }}>{idx < current ? '✓' : '○'}</Text>}
                <Text style={{ color: idx <= current ? c.text : c.muted }}>{label}</Text>
              </View>
            )
          })}
          <Body muted style={{ fontSize: 13 }}>You can leave this screen — the video will be ready in your library.</Body>
        </Card>
      )}
    </ScrollView>
  )
}

function RetryButton({ id }: { id: number }) {
  const qc = useQueryClient()
  const retry = useMutation({ mutationFn: () => api.post(`/api/analyses/${id}/retry`), onSuccess: () => void qc.invalidateQueries({ queryKey: ['analysis', id] }) })
  return <Button title="Try again" icon="refresh" loading={retry.isPending} onPress={() => retry.mutate()} />
}

function TranscriptForm({ id }: { id: number }) {
  const c = useColors()
  const qc = useQueryClient()
  const { config } = useAuth()
  const [mode, setMode] = useState<'paste' | 'file'>('paste')
  const [text, setText] = useState('')
  const submit = useMutation({
    mutationFn: async (kind: 'text' | 'upload' | 'audio') => {
      if (kind === 'text') return api.post(`/api/analyses/${id}/transcript`, { text })
      const picked = await DocumentPicker.getDocumentAsync({
        type: kind === 'audio' ? ['audio/*'] : ['text/plain', 'text/vtt', 'application/x-subrip', '*/*'],
        copyToCacheDirectory: true,
      })
      if (picked.canceled) return null
      const asset = picked.assets[0]
      const form = new FormData()
      form.append('file', { uri: asset.uri, name: asset.name, type: asset.mimeType ?? 'application/octet-stream' } as unknown as Blob)
      return api.post(`/api/analyses/${id}/transcript/${kind}`, form, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: kind === 'audio' ? 600_000 : 120_000,
      })
    },
    onSuccess: (res) => res && void qc.invalidateQueries({ queryKey: ['analysis', id] }),
  })
  return (
    <Card style={{ gap: 12 }}>
      <Body muted style={{ fontSize: 13 }}>
        Tip: on YouTube open the description, tap “Show transcript”, copy the text and paste it here.
      </Body>
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Chip label="Paste text" active={mode === 'paste'} onPress={() => setMode('paste')} />
        <Chip label="Upload file" active={mode === 'file'} onPress={() => setMode('file')} />
      </View>
      {mode === 'paste' ? (
        <>
          <Input value={text} onChangeText={setText} multiline placeholder={'0:00\nWelcome…'} style={{ minHeight: 180, textAlignVertical: 'top', paddingTop: 12 }} />
          <Button title="Explain this transcript" loading={submit.isPending} disabled={text.trim().length < 50} onPress={() => submit.mutate('text')} />
        </>
      ) : (
        <>
          <Button title="Choose .srt, .vtt or .txt" icon="document-text-outline" variant="secondary" loading={submit.isPending} onPress={() => submit.mutate('upload')} />
          {config?.audioTranscriptionEnabled && (
            <Button title="Choose audio to transcribe" icon="musical-notes-outline" variant="secondary" loading={submit.isPending} onPress={() => submit.mutate('audio')} />
          )}
        </>
      )}
      {submit.isError && <Text style={{ color: c.danger }}>{errorMessage(submit.error)}</Text>}
    </Card>
  )
}
