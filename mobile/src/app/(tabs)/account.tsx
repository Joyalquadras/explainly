import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as Linking from 'expo-linking'
import { router } from 'expo-router'
import * as WebBrowser from 'expo-web-browser'
import { useState } from 'react'
import { Alert, ScrollView, Text, View } from 'react-native'
import { Body, Button, Card, Input } from '../../components/ui'
import { api, errorMessage } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import { useColors } from '../../lib/theme'
import type { YouTubeConnection } from '../../lib/types'

export default function AccountScreen() {
  const c = useColors()
  const qc = useQueryClient()
  const { user, logout, refreshUser } = useAuth()
  const [name, setName] = useState(user?.displayName ?? '')
  const connection = useQuery({
    queryKey: ['youtube', 'connection'],
    queryFn: async () => (await api.get<YouTubeConnection>('/api/youtube/connection')).data,
    enabled: Boolean(user),
  })
  const saveName = useMutation({
    mutationFn: () => api.patch('/api/users/me', { displayName: name }),
    onSuccess: () => void refreshUser(),
    onError: (e) => Alert.alert('Could not save', errorMessage(e)),
  })
  const connect = useMutation({
    mutationFn: async () => {
      const returnTo = Linking.createURL('account')
      const { data } = await api.post<{ authorizationUrl: string }>('/api/youtube/connect', { returnTo })
      const result = await WebBrowser.openAuthSessionAsync(data.authorizationUrl, returnTo)
      if (result.type === 'success') {
        const ok = result.url.includes('youtube=connected')
        Alert.alert(ok ? 'YouTube connected' : 'YouTube not connected', ok ? 'Captions for videos you manage can now be imported.' : 'Access was not granted.')
      }
    },
    onSettled: () => void qc.invalidateQueries({ queryKey: ['youtube'] }),
    onError: (e) => Alert.alert('Could not connect', errorMessage(e)),
  })
  const disconnect = useMutation({
    mutationFn: () => api.delete('/api/youtube/connection'),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['youtube'] }),
  })
  const deleteAccount = useMutation({
    mutationFn: () => api.delete('/api/users/me'),
    onSuccess: () => void logout(),
    onError: (e) => Alert.alert('Could not delete', errorMessage(e)),
  })

  if (!user) {
    return (
      <View style={{ padding: 20, gap: 12 }}>
        <Body>Sign in to save your learning across devices.</Body>
        <Button title="Sign in" onPress={() => router.push('/sign-in')} />
      </View>
    )
  }

  const yt = connection.data
  return (
    <ScrollView contentContainerStyle={{ padding: 16, gap: 14 }}>
      {user.role === 'GUEST' && (
        <Card style={{ gap: 10, backgroundColor: c.brandSoft }}>
          <Text style={{ color: c.text, fontWeight: '700' }}>You're using a guest session</Text>
          <Body muted>Create a free account to keep your library and analyze more videos each day.</Body>
          <Button title="Create account" onPress={() => router.push('/sign-in')} />
        </Card>
      )}
      <Card style={{ gap: 10 }}>
        <Text style={{ color: c.text, fontWeight: '700', fontSize: 16 }}>Profile</Text>
        <Input value={name} onChangeText={setName} accessibilityLabel="Display name" maxLength={120} />
        <Button title="Save name" variant="secondary" loading={saveName.isPending} disabled={!name.trim() || name === user.displayName} onPress={() => saveName.mutate()} />
        {user.email && <Body muted>Signed in as {user.email}</Body>}
      </Card>
      <Card style={{ gap: 10 }}>
        <Text style={{ color: c.text, fontWeight: '700', fontSize: 16 }}>YouTube connection</Text>
        <Body muted>Import captions automatically for videos you own or manage. We only request caption access and never see your password.</Body>
        {!yt?.enabled ? (
          <Body muted>Connecting YouTube isn't configured on this server.</Body>
        ) : user.role === 'GUEST' ? (
          <Body muted>Create an account to connect YouTube.</Body>
        ) : yt.connected ? (
          <>
            <Text style={{ color: c.success, fontWeight: '700' }}>✓ Connected{yt.accountName ? ` as ${yt.accountName}` : ''}</Text>
            <Button title="Disconnect & revoke access" variant="secondary" loading={disconnect.isPending} onPress={() => disconnect.mutate()} />
          </>
        ) : (
          <Button title="Connect YouTube" icon="logo-youtube" loading={connect.isPending} onPress={() => connect.mutate()} />
        )}
      </Card>
      <Button title="Sign out" variant="secondary" icon="log-out-outline" onPress={() => void logout()} />
      <Button
        title="Delete account"
        variant="danger"
        onPress={() =>
          Alert.alert('Delete your account?', 'This permanently deletes your notes, scores, bookmarks and history.', [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Delete', style: 'destructive', onPress: () => deleteAccount.mutate() },
          ])
        }
      />
    </ScrollView>
  )
}
