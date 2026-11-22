import * as SecureStore from 'expo-secure-store'
import { Platform } from 'react-native'

const ACCESS = 'explainly.accessToken'
const REFRESH = 'explainly.refreshToken'

/** Tokens live in the OS keychain/keystore (SecureStore); an in-memory copy avoids async reads per request. */
let access: string | null = null
let refresh: string | null = null
const listeners = new Set<() => void>()

const store = {
  get: (k: string) => (Platform.OS === 'web' ? Promise.resolve(globalThis.localStorage?.getItem(k) ?? null) : SecureStore.getItemAsync(k)),
  set: (k: string, v: string) => (Platform.OS === 'web' ? Promise.resolve(globalThis.localStorage?.setItem(k, v)) : SecureStore.setItemAsync(k, v)),
  del: (k: string) => (Platform.OS === 'web' ? Promise.resolve(globalThis.localStorage?.removeItem(k)) : SecureStore.deleteItemAsync(k)),
}

export const tokens = {
  get access() {
    return access
  },
  get refresh() {
    return refresh
  },
  async load() {
    ;[access, refresh] = await Promise.all([store.get(ACCESS), store.get(REFRESH)])
  },
  async set(a: string, r: string) {
    access = a
    refresh = r
    await Promise.all([store.set(ACCESS, a), store.set(REFRESH, r)])
    listeners.forEach((l) => l())
  },
  async clear() {
    access = null
    refresh = null
    await Promise.all([store.del(ACCESS), store.del(REFRESH)])
    listeners.forEach((l) => l())
  },
  subscribe(l: () => void) {
    listeners.add(l)
    return () => {
      listeners.delete(l)
    }
  },
}
