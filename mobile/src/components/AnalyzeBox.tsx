import { router } from 'expo-router'
import { useState } from 'react'
import { Text, View } from 'react-native'
import { api, errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'
import { looksLikeYouTubeUrl } from '../lib/format'
import { useColors } from '../lib/theme'
import type { SubmitResponse } from '../lib/types'
import { Button, Input } from './ui'

export function AnalyzeBox() {
  const c = useColors()
  const { user, continueAsGuest } = useAuth()
  const [url, setUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async () => {
    setError(null)
    if (!looksLikeYouTubeUrl(url)) return setError('Please paste a YouTube video link.')
    setBusy(true)
    try {
      if (!user) await continueAsGuest()
      const res = await api.post<SubmitResponse>('/api/videos/analyze', { url: url.trim() })
      setUrl('')
      router.push({ pathname: '/learn/[id]', params: { id: String(res.data.analysis.id) } })
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <View style={{ gap: 10 }}>
      <Input
        value={url}
        onChangeText={setUrl}
        placeholder="Paste a YouTube link…"
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType="url"
        returnKeyType="go"
        onSubmitEditing={submit}
        accessibilityLabel="YouTube video URL"
      />
      <Button title={busy ? 'Starting…' : 'Analyze video'} icon="sparkles" onPress={submit} loading={busy} disabled={!url.trim()} />
      {error && (
        <Text accessibilityRole="alert" style={{ color: c.danger }}>
          {error}
        </Text>
      )}
    </View>
  )
}
