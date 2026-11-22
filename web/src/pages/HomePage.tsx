import { useQuery } from '@tanstack/react-query'
import { ArrowRight, BookOpenCheck, Lightbulb, MessagesSquare, PenLine, Shapes } from 'lucide-react'
import { Link } from 'react-router-dom'
import { UrlForm } from '../components/UrlForm'
import { Skeleton } from '../components/States'
import { VideoCard } from '../components/VideoCard'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { CATEGORIES } from '../lib/categories'
import type { HistoryItem, HistoryStats, Page } from '../lib/types'

const FEATURES = [
  { icon: Lightbulb, title: 'Simple explanations', text: 'Every topic explained like a patient teacher would, with everyday examples.' },
  { icon: Shapes, title: 'Diagrams & visuals', text: 'Flowcharts, architecture diagrams and mind maps for the key ideas.' },
  { icon: MessagesSquare, title: 'Ask the video', text: 'Chat with an AI tutor that answers from the video and cites timestamps.' },
  { icon: BookOpenCheck, title: 'Notes, quizzes & flashcards', text: 'Revise with editable notes, scored quizzes and flashcards.' },
]

export function HomePage() {
  const { user, config } = useAuth()
  const recent = useQuery({
    queryKey: ['history', 'recent'],
    queryFn: async () => (await api.get<Page<HistoryItem>>('/api/history', { params: { size: 4 } })).data,
    enabled: Boolean(user),
  })
  const stats = useQuery({
    queryKey: ['history', 'stats'],
    queryFn: async () => (await api.get<HistoryStats>('/api/history/stats')).data,
    enabled: Boolean(user),
  })

  return (
    <div>
      <section className="relative overflow-hidden">
        <div className="pointer-events-none absolute inset-0 -z-10 bg-[radial-gradient(ellipse_at_top,rgba(99,102,241,0.18),transparent_60%)] dark:bg-[radial-gradient(ellipse_at_top,rgba(99,102,241,0.25),transparent_60%)]" />
        <div className="mx-auto flex max-w-3xl flex-col items-center px-4 pb-14 pt-14 text-center sm:pt-20">
          <span className="chip mb-5 bg-brand-50 text-brand-700 ring-1 ring-brand-100 dark:bg-brand-900/30 dark:text-brand-200 dark:ring-brand-800">
            <PenLine className="size-3.5" /> Your AI study partner for YouTube
          </span>
          <h1 className="text-balance text-4xl font-extrabold tracking-tight sm:text-5xl md:text-6xl">
            Turn Any YouTube Video Into Something You{' '}
            <span className="bg-gradient-to-r from-brand-600 to-violet-500 bg-clip-text text-transparent">Can Understand.</span>
          </h1>
          <p className="mt-5 max-w-2xl text-pretty text-lg text-slate-600 dark:text-slate-400">
            Paste a video link. Learn difficult topics through simple explanations, illustrations, and real-world examples.
          </p>
          <div className="mt-8 w-full max-w-2xl">
            <UrlForm autoFocus />
            {config && !config.aiConfigured && (
              <p className="mt-3 rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                AI isn't configured on this server yet, so new videos can't be explained. An administrator needs to set an AI provider key.
              </p>
            )}
            {!user && (
              <p className="mt-3 text-sm text-slate-500">
                No account needed to try it.{' '}
                <Link to="/signin" className="font-medium text-brand-600 hover:underline dark:text-brand-300">
                  Sign in
                </Link>{' '}
                to keep your learning history.
              </p>
            )}
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-7xl space-y-14 px-4 sm:px-6">
        {user && (
          <section aria-labelledby="recent-heading">
            <div className="mb-4 flex items-end justify-between">
              <h2 id="recent-heading" className="text-xl font-bold">
                Continue learning
              </h2>
              {(recent.data?.totalItems ?? 0) > 4 && (
                <Link to="/library" className="flex items-center gap-1 text-sm font-medium text-brand-600 hover:underline dark:text-brand-300">
                  View all <ArrowRight className="size-4" />
                </Link>
              )}
            </div>
            {recent.isLoading ? (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {Array.from({ length: 4 }).map((_, i) => (
                  <Skeleton key={i} className="h-60" />
                ))}
              </div>
            ) : recent.data && recent.data.items.length > 0 ? (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {recent.data.items.map((item) => (
                  <VideoCard key={item.analysisId} item={item} />
                ))}
              </div>
            ) : (
              <p className="rounded-2xl border border-dashed border-slate-300 p-6 text-center text-sm text-slate-500 dark:border-slate-700">
                Videos you analyze will appear here so you can pick up where you left off.
              </p>
            )}
            {stats.data && stats.data.videos > 0 && (
              <dl className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
                {[
                  ['Videos', stats.data.videos],
                  ['Completed', stats.data.completedVideos],
                  ['Quiz average', stats.data.quizAttempts ? `${stats.data.averageQuizScore}%` : '—'],
                  ['Bookmarks', stats.data.bookmarks],
                ].map(([label, value]) => (
                  <div key={label} className="card px-4 py-3">
                    <dt className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</dt>
                    <dd className="mt-1 text-2xl font-bold">{value}</dd>
                  </div>
                ))}
              </dl>
            )}
          </section>
        )}

        <section aria-labelledby="categories-heading">
          <h2 id="categories-heading" className="mb-1 text-xl font-bold">
            Learning categories
          </h2>
          <p className="mb-4 text-sm text-slate-500">Explainly works with any subject. Browse your library by topic.</p>
          <div className="flex flex-wrap gap-2">
            {CATEGORIES.map((c) => {
              const count = stats.data?.categories[c.name]
              return (
                <Link
                  key={c.name}
                  to={user ? `/library?category=${encodeURIComponent(c.name)}` : '/signin'}
                  className={`chip px-3 py-2 text-sm transition hover:scale-[1.03] ${c.color}`}
                >
                  <c.icon className="size-4" /> {c.name}
                  {count ? <span className="ml-1 rounded-full bg-white/70 px-1.5 text-xs dark:bg-black/30">{count}</span> : null}
                </Link>
              )
            })}
          </div>
        </section>

        <section aria-labelledby="features-heading" className="pb-6">
          <h2 id="features-heading" className="sr-only">
            What you get
          </h2>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {FEATURES.map((f) => (
              <div key={f.title} className="card p-5">
                <div className="mb-3 inline-flex rounded-xl bg-brand-50 p-2.5 text-brand-600 dark:bg-brand-900/30 dark:text-brand-300">
                  <f.icon className="size-5" />
                </div>
                <h3 className="font-semibold">{f.title}</h3>
                <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">{f.text}</p>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}
