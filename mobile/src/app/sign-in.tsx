import * as Google from 'expo-auth-session/providers/google'
import { router } from 'expo-router'
import { useEffect, useState } from 'react'
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from 'react-native'
import { Body, Button, Card, Chip, H1, Input } from '../components/ui'
import { errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'
import { useColors } from '../lib/theme'

const GOOGLE_IDS = {
  webClientId: process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID || undefined,
  iosClientId: process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID || undefined,
  androidClientId: process.env.EXPO_PUBLIC_GOOGLE_ANDROID_CLIENT_ID || undefined,
}
const hasGoogleIdForPlatform =
  Platform.OS === 'ios' ? Boolean(GOOGLE_IDS.iosClientId) : Platform.OS === 'android' ? Boolean(GOOGLE_IDS.androidClientId) : Boolean(GOOGLE_IDS.webClientId)

/** Google sign-in through the system browser (expo-auth-session). Only mounted when client IDs are configured. */
function GoogleButton({ onToken, busy }: { onToken: (t: string) => void; busy: boolean }) {
  const [request, response, promptAsync] = Google.useIdTokenAuthRequest(GOOGLE_IDS)
  useEffect(() => {
    if (response?.type === 'success') {
      const token = response.params.id_token ?? response.authentication?.idToken
      if (token) onToken(token)
    }
  }, [response, onToken])
  return <Button title="Continue with Google" icon="logo-google" variant="secondary" disabled={!request || busy} onPress={() => void promptAsync()} />
}

export default function SignInScreen() {
  const c = useColors()
  const { login, register, continueAsGuest, signInWithGoogle, config, user } = useAuth()
  const [mode, setMode] = useState<'signin' | 'register'>(user?.role === 'GUEST' ? 'register' : 'signin')
  const [form, setForm] = useState({ email: '', password: '', name: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const run = async (action: () => Promise<void>) => {
    setError(null)
    setBusy(true)
    try {
      await action()
      if (router.canGoBack()) router.back()
      else router.replace('/')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={{ padding: 20, gap: 16 }} keyboardShouldPersistTaps="handled">
        <View style={{ gap: 6 }}>
          <H1>{user?.role === 'GUEST' ? 'Save your progress' : 'Welcome to Explainly'}</H1>
          <Body muted>Keep your notes, quiz scores and history across devices.</Body>
        </View>
        <Card style={{ gap: 14 }}>
          {config?.googleSignInEnabled && hasGoogleIdForPlatform && <GoogleButton busy={busy} onToken={(t) => void run(() => signInWithGoogle(t))} />}
          <View style={{ flexDirection: 'row', gap: 8 }}>
            <Chip label="Sign in" active={mode === 'signin'} onPress={() => setMode('signin')} />
            <Chip label="Create account" active={mode === 'register'} onPress={() => setMode('register')} />
          </View>
          {mode === 'register' && <Input placeholder="Your name" value={form.name} onChangeText={(name) => setForm((f) => ({ ...f, name }))} autoComplete="name" />}
          <Input
            placeholder="Email"
            value={form.email}
            onChangeText={(email) => setForm((f) => ({ ...f, email }))}
            autoCapitalize="none"
            keyboardType="email-address"
            autoComplete="email"
          />
          <Input
            placeholder={mode === 'register' ? 'Password (8+ characters)' : 'Password'}
            value={form.password}
            onChangeText={(password) => setForm((f) => ({ ...f, password }))}
            secureTextEntry
            autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
          />
          <Button
            title={mode === 'register' ? 'Create account' : 'Sign in'}
            loading={busy}
            onPress={() => void run(() => (mode === 'register' ? register(form.email, form.password, form.name) : login(form.email, form.password)))}
          />
          {error && (
            <Text accessibilityRole="alert" style={{ color: c.danger }}>
              {error}
            </Text>
          )}
          {!user && <Button title="Continue as guest" variant="ghost" onPress={() => void run(continueAsGuest)} disabled={busy} />}
        </Card>
        <Body muted style={{ textAlign: 'center', fontSize: 12 }}>
          We never ask for your Google or YouTube password.
        </Body>
      </ScrollView>
    </KeyboardAvoidingView>
  )
}
