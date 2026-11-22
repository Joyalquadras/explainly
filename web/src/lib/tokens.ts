const ACCESS = 'explainly.accessToken'
const REFRESH = 'explainly.refreshToken'

type Listener = () => void
const listeners = new Set<Listener>()

function read(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export const tokens = {
  get access() {
    return read(ACCESS)
  },
  get refresh() {
    return read(REFRESH)
  },
  set(access: string, refresh: string) {
    try {
      localStorage.setItem(ACCESS, access)
      localStorage.setItem(REFRESH, refresh)
    } catch {
      /* storage unavailable: session lasts for this page only */
    }
    listeners.forEach((l) => l())
  },
  clear() {
    try {
      localStorage.removeItem(ACCESS)
      localStorage.removeItem(REFRESH)
    } catch {
      /* ignore */
    }
    listeners.forEach((l) => l())
  },
  subscribe(listener: Listener) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
}
