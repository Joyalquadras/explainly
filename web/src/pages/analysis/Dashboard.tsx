import { Tab, TabGroup, TabList, TabPanel, TabPanels } from '@headlessui/react'
import clsx from 'clsx'
import { BookmarkPlus, Check, ExternalLink, Layers, ListChecks, MessagesSquare, NotebookPen } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { YouTubePlayer, type PlayerHandle } from '../../components/YouTubePlayer'
import { categoryMeta } from '../../lib/categories'
import { clock, duration } from '../../lib/format'
import type { AnalysisDetail } from '../../lib/types'
import { ChatPanel } from './ChatPanel'
import { ContentTabs } from './ContentTabs'
import { LearnContext, useCreateBookmark, useUpdateProgress } from './context'
import { FlashcardsPanel } from './FlashcardsPanel'
import { NotesPanel } from './NotesPanel'
import { QuizPanel } from './QuizPanel'

const TOOLS = [
  { label: 'AI Chat', icon: MessagesSquare, Panel: ChatPanel },
  { label: 'Notes', icon: NotebookPen, Panel: NotesPanel },
  { label: 'Quiz', icon: ListChecks, Panel: QuizPanel },
  { label: 'Flashcards', icon: Layers, Panel: FlashcardsPanel },
]

export function Dashboard({ detail }: { detail: AnalysisDetail }) {
  const player = useRef<PlayerHandle>(null)
  const playerBox = useRef<HTMLDivElement>(null)
  const [params] = useSearchParams()
  const [selectedChapter, setSelectedChapter] = useState<number | null>(null)
  const bookmark = useCreateBookmark(detail.status.id)
  const progress = useUpdateProgress(detail.status.id)
  const initialStart = useMemo(() => {
    const t = Number(params.get('t'))
    return Number.isFinite(t) && params.has('t') ? t : (detail.progress.lastPositionSeconds ?? null)
    // only on first render
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const seek = useCallback((seconds: number) => {
    player.current?.seek(seconds)
    const box = playerBox.current?.getBoundingClientRect()
    if (box && (box.top < 64 || box.bottom > window.innerHeight)) {
      playerBox.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }, [])
  const currentTime = useCallback(() => player.current?.currentTime() ?? null, [])

  // Persist the playback position so the learner can resume later.
  const completedRef = useRef(detail.progress.completedChapterIds)
  completedRef.current = detail.progress.completedChapterIds
  const lastSaved = useRef<number | null>(detail.progress.lastPositionSeconds)
  const mutateProgress = progress.mutate
  useEffect(() => {
    const timer = setInterval(() => {
      const t = player.current?.currentTime()
      if (t == null) return
      const rounded = Math.floor(t)
      if (lastSaved.current == null || Math.abs(rounded - lastSaved.current) >= 10) {
        lastSaved.current = rounded
        mutateProgress({ completedChapterIds: completedRef.current, lastPositionSeconds: rounded })
      }
    }, 15_000)
    return () => clearInterval(timer)
  }, [mutateProgress])

  const { video, overview, chapters } = detail
  const cat = categoryMeta(detail.status.category)
  const done = new Set(detail.progress.completedChapterIds)

  const openChapter = (id: number, start: number | null) => {
    setSelectedChapter(id)
    if (start != null) seek(start)
    setTimeout(() => document.getElementById(`chapter-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50)
  }

  return (
    <LearnContext.Provider value={{ detail, seek, currentTime }}>
      <div className="mx-auto max-w-7xl px-3 py-5 sm:px-6">
        <div className="grid gap-5 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
          {/* Left: video, details, timeline */}
          <aside className="space-y-4 lg:sticky lg:top-20 lg:max-h-[calc(100dvh-6rem)] lg:self-start lg:overflow-y-auto lg:pb-4">
            <div ref={playerBox} className="scroll-mt-20">
              <YouTubePlayer ref={player} videoId={video.youtubeId} title={video.title} start={initialStart} />
            </div>
            <div className="card p-4">
              <h1 className="text-lg font-bold leading-snug">{video.title}</h1>
              <div className="mt-2 flex flex-wrap items-center gap-2 text-xs">
                <span className={`chip ${cat.color}`}>
                  <cat.icon className="size-3.5" /> {detail.status.category ?? 'Other'}
                </span>
                {overview?.difficulty && <span className="chip bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300">{overview.difficulty}</span>}
                {video.durationSeconds ? <span className="text-slate-500">{duration(video.durationSeconds)}</span> : null}
                {video.channelTitle && <span className="text-slate-500">· {video.channelTitle}</span>}
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <button
                  className="btn-secondary px-3 py-2 text-xs"
                  onClick={() => {
                    const t = currentTime()
                    bookmark.mutate({ title: `Moment at ${clock(t ?? 0)}`, timestampSeconds: t == null ? null : Math.floor(t) })
                  }}
                >
                  <BookmarkPlus className="size-4" /> Bookmark this moment
                </button>
                <a className="btn-ghost px-3 py-2 text-xs" href={`https://www.youtube.com/watch?v=${video.youtubeId}`} target="_blank" rel="noopener noreferrer">
                  <ExternalLink className="size-4" /> Open on YouTube
                </a>
              </div>
            </div>
            <nav className="card p-4" aria-label="Video timeline">
              <div className="mb-3 flex items-center justify-between">
                <h2 className="font-semibold">Timeline</h2>
                <span className="text-xs font-medium text-slate-500">{detail.progress.progressPercent}% understood</span>
              </div>
              <div className="mb-3 h-1.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
                <div className="h-full rounded-full bg-emerald-500 transition-all" style={{ width: `${detail.progress.progressPercent}%` }} />
              </div>
              <ol className="space-y-1">
                {chapters.map((c) => (
                  <li key={c.id}>
                    <button
                      onClick={() => openChapter(c.id, c.startSeconds)}
                      className={clsx(
                        'flex w-full items-start gap-3 rounded-xl p-2 text-left transition hover:bg-slate-50 dark:hover:bg-slate-800/60',
                        selectedChapter === c.id && 'bg-brand-50 dark:bg-brand-950/40',
                      )}
                    >
                      <span className="w-12 shrink-0 pt-0.5 font-mono text-xs font-semibold text-brand-600 dark:text-brand-300">{c.startSeconds != null ? clock(c.startSeconds) : `#${c.position + 1}`}</span>
                      <span className="min-w-0 flex-1">
                        <span className="block text-sm font-medium leading-snug">{c.title}</span>
                        <span className="line-clamp-1 text-xs text-slate-500">{c.shortSummary}</span>
                      </span>
                      {done.has(c.id) && <Check className="mt-0.5 size-4 shrink-0 text-emerald-500" aria-label="Understood" />}
                    </button>
                  </li>
                ))}
              </ol>
              {!detail.transcriptTimed && <p className="mt-3 text-xs text-slate-500">The transcript had no timestamps, so topics can't jump to exact moments.</p>}
            </nav>
          </aside>

          {/* Right: explanations */}
          <section aria-label="Explanations" className="min-w-0">
            <ContentTabs selectedChapter={selectedChapter} onSelectChapter={setSelectedChapter} />
          </section>
        </div>

        {/* Below: learning tools */}
        <TabGroup as="section" aria-label="Learning tools" className="card mt-5 overflow-hidden">
          <TabList className="grid grid-cols-4 border-b border-slate-200 dark:border-slate-800">
            {TOOLS.map((t) => (
              <Tab
                key={t.label}
                className={({ selected }) =>
                  clsx(
                    'flex flex-col items-center gap-1 border-b-2 py-3 text-xs font-medium outline-none transition sm:flex-row sm:justify-center sm:gap-2 sm:text-sm',
                    selected ? 'border-brand-600 text-brand-700 dark:border-brand-400 dark:text-brand-200' : 'border-transparent text-slate-500 hover:text-slate-800 dark:hover:text-slate-200',
                  )
                }
              >
                <t.icon className="size-5 sm:size-4" /> {t.label}
              </Tab>
            ))}
          </TabList>
          <TabPanels className="p-4 sm:p-6">
            {TOOLS.map((t) => (
              <TabPanel key={t.label} unmount={false}>
                <t.Panel />
              </TabPanel>
            ))}
          </TabPanels>
        </TabGroup>
        {detail.aiModel && <p className="mt-4 text-center text-xs text-slate-400">AI-generated from the video transcript. Double-check important facts.</p>}
      </div>
    </LearnContext.Provider>
  )
}
