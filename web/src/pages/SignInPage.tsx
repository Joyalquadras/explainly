import { Tab, TabGroup, TabList, TabPanel, TabPanels } from '@headlessui/react'
import clsx from 'clsx'
import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { GoogleSignInButton } from '../components/GoogleSignInButton'
import { Logo } from '../components/Logo'
import { Spinner } from '../components/States'
import { errorMessage } from '../lib/api'
import { useAuth } from '../lib/auth'

export function SignInPage() {
  const [params] = useSearchParams()
  const { login, register, continueAsGuest, signInWithGoogle, config, user } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [form, setForm] = useState({ email: '', password: '', displayName: '' })
  const next = params.get('next') ?? '/'
  const isGuest = user?.role === 'GUEST'

  async function run(action: () => Promise<unknown>) {
    setError(null)
    setBusy(true)
    try {
      await action()
      navigate(next.startsWith('/') ? next : '/')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  const onSignIn = (e: FormEvent) => {
    e.preventDefault()
    void run(() => login(form.email, form.password))
  }
  const onRegister = (e: FormEvent) => {
    e.preventDefault()
    void run(() => register(form.email, form.password, form.displayName))
  }

  const field = (name: keyof typeof form, label: string, type: string, autoComplete: string) => (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium">{label}</span>
      <input
        className="input"
        type={type}
        required
        minLength={name === 'password' ? 8 : undefined}
        autoComplete={autoComplete}
        value={form[name]}
        onChange={(e) => setForm((f) => ({ ...f, [name]: e.target.value }))}
      />
    </label>
  )

  return (
    <div className="mx-auto flex max-w-md flex-col px-4 py-10 sm:py-16">
      <div className="mb-6 flex flex-col items-center text-center">
        <Logo size={44} />
        <h1 className="mt-4 text-2xl font-bold">{isGuest ? 'Save your progress' : 'Welcome to Explainly'}</h1>
        <p className="mt-1 text-sm text-slate-500">
          {isGuest ? 'Create an account and keep everything you learned as a guest.' : 'Sign in to keep your notes, quizzes and history.'}
        </p>
      </div>
      <div className="card p-6">
        {config?.googleSignInEnabled && config.googleWebClientId && (
          <>
            <GoogleSignInButton clientId={config.googleWebClientId} onCredential={(token) => void run(() => signInWithGoogle(token))} />
            <div className="my-5 flex items-center gap-3 text-xs text-slate-400">
              <span className="h-px flex-1 bg-slate-200 dark:bg-slate-700" /> or <span className="h-px flex-1 bg-slate-200 dark:bg-slate-700" />
            </div>
          </>
        )}
        <TabGroup defaultIndex={params.get('mode') === 'register' || isGuest ? 1 : 0}>
          <TabList className="mb-5 grid grid-cols-2 rounded-xl bg-slate-100 p-1 dark:bg-slate-800">
            {['Sign in', 'Create account'].map((t) => (
              <Tab
                key={t}
                className={({ selected }) =>
                  clsx('rounded-lg py-2 text-sm font-medium outline-none transition', selected ? 'bg-white shadow-sm dark:bg-slate-900' : 'text-slate-500')
                }
              >
                {t}
              </Tab>
            ))}
          </TabList>
          <TabPanels>
            <TabPanel as="form" onSubmit={onSignIn} className="space-y-4">
              {field('email', 'Email', 'email', 'email')}
              {field('password', 'Password', 'password', 'current-password')}
              <button className="btn-primary w-full" disabled={busy}>
                {busy && <Spinner />} Sign in
              </button>
            </TabPanel>
            <TabPanel as="form" onSubmit={onRegister} className="space-y-4">
              {field('displayName', 'Your name', 'text', 'name')}
              {field('email', 'Email', 'email', 'email')}
              {field('password', 'Password (8+ characters)', 'password', 'new-password')}
              <button className="btn-primary w-full" disabled={busy}>
                {busy && <Spinner />} Create account
              </button>
            </TabPanel>
          </TabPanels>
        </TabGroup>
        {error && (
          <p role="alert" className="mt-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700 dark:bg-rose-950/40 dark:text-rose-300">
            {error}
          </p>
        )}
        {!user && (
          <button className="btn-ghost mt-4 w-full" onClick={() => void run(continueAsGuest)} disabled={busy}>
            Continue as guest
          </button>
        )}
      </div>
      <p className="mt-6 text-center text-xs text-slate-500">
        We never ask for your Google or YouTube password. <Link to="/" className="underline">Back home</Link>
      </p>
    </div>
  )
}
