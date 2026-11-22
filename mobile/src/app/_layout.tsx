import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import axios from 'axios'
import { Stack } from 'expo-router'
import { StatusBar } from 'expo-status-bar'
import * as WebBrowser from 'expo-web-browser'
import { SafeAreaProvider } from 'react-native-safe-area-context'
import { AuthProvider } from '../lib/auth'
import { useColors } from '../lib/theme'

WebBrowser.maybeCompleteAuthSession()

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (count, error) => {
        const status = axios.isAxiosError(error) ? error.response?.status : undefined
        return !(status && status >= 400 && status < 500) && count < 2
      },
    },
  },
})

function Navigator() {
  const c = useColors()
  return (
    <>
      <StatusBar style="auto" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: c.card },
          headerTintColor: c.text,
          headerTitleStyle: { fontWeight: '700' },
          contentStyle: { backgroundColor: c.bg },
        }}
      >
        <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
        <Stack.Screen name="sign-in" options={{ title: 'Sign in', presentation: 'modal' }} />
        <Stack.Screen name="learn/[id]" options={{ title: 'Learn' }} />
      </Stack>
    </>
  )
}

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <Navigator />
        </AuthProvider>
      </QueryClientProvider>
    </SafeAreaProvider>
  )
}
