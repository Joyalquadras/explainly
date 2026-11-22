import { useEffect, useRef, useState } from 'react'
import { useTheme } from '../lib/theme'

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize(opts: { client_id: string; callback: (r: { credential: string }) => void; ux_mode?: string }): void
          renderButton(el: HTMLElement, opts: Record<string, unknown>): void
        }
      }
    }
  }
}

let gisPromise: Promise<void> | null = null
function loadGis() {
  if (window.google?.accounts) return Promise.resolve()
  gisPromise ??= new Promise<void>((resolve, reject) => {
    const s = document.createElement('script')
    s.src = 'https://accounts.google.com/gsi/client'
    s.async = true
    s.onload = () => resolve()
    s.onerror = () => reject(new Error('Google script failed to load'))
    document.head.appendChild(s)
  })
  return gisPromise
}

/**
 * Official "Sign in with Google" button (Google Identity Services). Google returns an ID token which the
 * backend verifies; the user's Google password never touches Explainly.
 */
export function GoogleSignInButton({ clientId, onCredential }: { clientId: string; onCredential: (idToken: string) => void }) {
  const ref = useRef<HTMLDivElement>(null)
  const { resolved } = useTheme()
  const [failed, setFailed] = useState(false)
  const callback = useRef(onCredential)
  useEffect(() => {
    callback.current = onCredential
  }, [onCredential])

  useEffect(() => {
    let cancelled = false
    loadGis()
      .then(() => {
        if (cancelled || !ref.current || !window.google) return
        window.google.accounts.id.initialize({ client_id: clientId, callback: (r) => callback.current(r.credential) })
        window.google.accounts.id.renderButton(ref.current, {
          theme: resolved === 'dark' ? 'filled_black' : 'outline',
          size: 'large',
          shape: 'pill',
          text: 'continue_with',
          width: 320,
        })
      })
      .catch(() => setFailed(true))
    return () => {
      cancelled = true
    }
  }, [clientId, resolved])

  if (failed) return <p className="text-center text-xs text-slate-500">Google sign-in couldn't load. Check your connection or ad blocker.</p>
  return <div ref={ref} className="flex min-h-11 justify-center" />
}
