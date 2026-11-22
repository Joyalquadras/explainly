import { Disclosure, DisclosureButton, DisclosurePanel, Tab, TabGroup, TabList, TabPanel, TabPanels } from '@headlessui/react'
import clsx from 'clsx'
import { Bookmark, BookOpen, Briefcase, Check, ChevronDown, Cpu, GraduationCap, Lightbulb, ListChecks, Search, Sparkles, Target, Users } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Markdown } from '../../components/Markdown'
import { Timestamp } from '../../components/Timestamp'
import type { Chapter, Concept } from '../../lib/types'
import { useCreateBookmark, useLearn, useUpdateProgress } from './context'
import { IllustrationCard } from './IllustrationCard'

const TABS = ['Overview', 'Topics', 'Beginner', 'Technical', 'Concepts', 'Summary'] as const

export function ContentTabs({ selectedChapter, onSelectChapter }: { selectedChapter: number | null; onSelectChapter: (id: number | null) => void }) {
  const [index, setIndex] = useState(0)
  const { detail } = useLearn()
  return (
    <TabGroup selectedIndex={index} onChange={setIndex} className="card overflow-hidden">
      <TabList className="flex gap-1 overflow-x-auto border-b border-slate-200 px-2 pt-2 dark:border-slate-800" aria-label="Explanation sections">
        {TABS.map((t) => (
          <Tab
            key={t}
            className={({ selected }) =>
              clsx(
                'shrink-0 border-b-2 px-3 py-2.5 text-sm font-medium outline-none transition',
                selected ? 'border-brand-600 text-brand-700 dark:border-brand-400 dark:text-brand-200' : 'border-transparent text-slate-500 hover:text-slate-800 dark:hover:text-slate-200',
              )
            }
          >
            {t}
          </Tab>
        ))}
      </TabList>
      <TabPanels className="p-4 sm:p-6">
        <TabPanel>
          <OverviewPanel onOpenTopics={() => setIndex(1)} />
        </TabPanel>
        <TabPanel>
          <TopicsPanel selectedChapter={selectedChapter} onSelectChapter={onSelectChapter} />
        </TabPanel>
        <TabPanel>
          <ModeIntro icon={GraduationCap} title="Explain like I'm a beginner" text="No background needed. Everyday analogies, step by step." />
          <Markdown>{detail.beginnerExplanation}</Markdown>
        </TabPanel>
        <TabPanel>
          <ModeIntro icon={Cpu} title="Technical deep dive" text="More precise terms, internals and trade-offs." />
          <Markdown>{detail.technicalExplanation}</Markdown>
        </TabPanel>
        <TabPanel>
          <ConceptsPanel />
        </TabPanel>
        <TabPanel>
          <SummaryPanel />
        </TabPanel>
      </TabPanels>
    </TabGroup>
  )
}

function ModeIntro({ icon: Icon, title, text }: { icon: typeof Cpu; title: string; text: string }) {
  return (
    <div className="mb-5 flex items-center gap-3 rounded-xl bg-slate-50 p-3 dark:bg-slate-800/50">
      <Icon className="size-5 text-brand-600 dark:text-brand-300" />
      <div>
        <p className="text-sm font-semibold">{title}</p>
        <p className="text-xs text-slate-500">{text}</p>
      </div>
    </div>
  )
}

function OverviewPanel({ onOpenTopics }: { onOpenTopics: () => void }) {
  const { detail } = useLearn()
  const o = detail.overview
  const bigPicture = detail.illustrations.filter((i) => i.chapterId == null)
  if (!o) return null
  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold">{o.title}</h2>
        <p className="mt-2 text-slate-700 dark:text-slate-300">{o.whatIsItAbout}</p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <InfoBlock icon={ListChecks} title="What you'll learn">
          <ul className="space-y-1.5">
            {o.whatYouWillLearn.map((item) => (
              <li key={item} className="flex gap-2">
                <Check className="mt-0.5 size-4 shrink-0 text-emerald-500" /> {item}
              </li>
            ))}
          </ul>
        </InfoBlock>
        <div className="space-y-4">
          <InfoBlock icon={Target} title="Why it matters">
            {o.whyItMatters}
          </InfoBlock>
          <InfoBlock icon={Users} title="Who should watch">
            {o.whoShouldWatch}
          </InfoBlock>
        </div>
      </div>
      {o.keyTakeaways.length > 0 && (
        <InfoBlock icon={Sparkles} title="Key takeaways">
          <ol className="list-decimal space-y-1 pl-5">
            {o.keyTakeaways.map((k) => (
              <li key={k}>{k}</li>
            ))}
          </ol>
        </InfoBlock>
      )}
      {bigPicture.map((ill) => (
        <IllustrationCard key={ill.id} illustration={ill} />
      ))}
      <button className="btn-primary" onClick={onOpenTopics}>
        <BookOpen className="size-4" /> Start learning topic by topic
      </button>
    </div>
  )
}

function InfoBlock({ icon: Icon, title, children }: { icon: typeof Target; title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-xl border border-slate-200 p-4 text-sm dark:border-slate-800">
      <h3 className="mb-2 flex items-center gap-2 font-semibold">
        <Icon className="size-4 text-brand-600 dark:text-brand-300" /> {title}
      </h3>
      <div className="text-slate-700 dark:text-slate-300">{children}</div>
    </section>
  )
}

function TopicsPanel({ selectedChapter, onSelectChapter }: { selectedChapter: number | null; onSelectChapter: (id: number | null) => void }) {
  const { detail } = useLearn()
  return (
    <div className="space-y-3">
      {detail.chapters.map((c) => (
        <ChapterCard key={`${c.id}-${selectedChapter === c.id}`} chapter={c} defaultOpen={selectedChapter === c.id || (selectedChapter == null && c.position === 0)} onToggle={(open) => open && onSelectChapter(c.id)} />
      ))}
    </div>
  )
}

function ChapterCard({ chapter, defaultOpen, onToggle }: { chapter: Chapter; defaultOpen: boolean; onToggle: (open: boolean) => void }) {
  const { detail, seek } = useLearn()
  const bookmark = useCreateBookmark(detail.status.id)
  const progress = useUpdateProgress(detail.status.id)
  const done = detail.progress.completedChapterIds.includes(chapter.id)
  const illustrations = detail.illustrations.filter((i) => i.chapterId === chapter.id)

  const toggleDone = () => {
    const ids = new Set(detail.progress.completedChapterIds)
    if (done) ids.delete(chapter.id)
    else ids.add(chapter.id)
    progress.mutate({ completedChapterIds: [...ids] })
  }

  return (
    <Disclosure as="article" defaultOpen={defaultOpen} id={`chapter-${chapter.id}`} className="scroll-mt-24 rounded-2xl border border-slate-200 dark:border-slate-800">
      {({ open }) => (
        <>
          <DisclosureButton onClick={() => onToggle(!open)} className="flex w-full items-start gap-3 p-4 text-left">
            <span
              className={clsx(
                'mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-bold',
                done ? 'bg-emerald-500 text-white' : 'bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200',
              )}
            >
              {done ? <Check className="size-4" /> : chapter.position + 1}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block font-semibold leading-snug">{chapter.title}</span>
              <span className="mt-0.5 block text-sm text-slate-500">{chapter.shortSummary}</span>
            </span>
            <ChevronDown className={clsx('mt-1 size-5 shrink-0 text-slate-400 transition', open && 'rotate-180')} />
          </DisclosureButton>
          <DisclosurePanel className="space-y-5 border-t border-slate-200 p-4 dark:border-slate-800">
            <div className="flex flex-wrap items-center gap-2">
              <Timestamp seconds={chapter.startSeconds} onSeek={seek} />
              {chapter.concepts.map((c) => (
                <span key={c} className="chip bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                  {c}
                </span>
              ))}
            </div>
            <div className="rounded-xl bg-brand-50/70 p-4 dark:bg-brand-950/30">
              <p className="mb-1 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-brand-700 dark:text-brand-300">
                <Lightbulb className="size-3.5" /> In simple words
              </p>
              <p className="text-slate-800 dark:text-slate-200">{chapter.simpleExplanation}</p>
            </div>
            <Markdown>{chapter.detailedExplanation}</Markdown>
            <div className="grid gap-3 sm:grid-cols-2">
              <Callout title="Real-world example" icon={Sparkles} tone="amber" text={chapter.realWorldExample} />
              <Callout title="Practical use" icon={Briefcase} tone="emerald" text={chapter.practicalUseCase} />
            </div>
            {chapter.keyPoints.length > 0 && (
              <div>
                <p className="mb-2 text-sm font-semibold">Remember this</p>
                <ul className="space-y-1.5 text-sm">
                  {chapter.keyPoints.map((k) => (
                    <li key={k} className="flex gap-2">
                      <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-brand-500" /> {k}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {illustrations.map((ill) => (
              <IllustrationCard key={ill.id} illustration={ill} />
            ))}
            <div className="flex flex-wrap gap-2 border-t border-slate-100 pt-4 dark:border-slate-800">
              <button className={done ? 'btn-secondary' : 'btn-primary'} onClick={toggleDone} disabled={progress.isPending} aria-pressed={done}>
                <Check className="size-4" /> {done ? 'Understood' : 'Mark as understood'}
              </button>
              <button
                className="btn-secondary"
                onClick={() => bookmark.mutate({ title: chapter.title, chapterId: chapter.id, timestampSeconds: chapter.startSeconds })}
                disabled={bookmark.isPending}
              >
                <Bookmark className="size-4" /> Bookmark topic
              </button>
            </div>
          </DisclosurePanel>
        </>
      )}
    </Disclosure>
  )
}

function Callout({ title, icon: Icon, tone, text }: { title: string; icon: typeof Sparkles; tone: 'amber' | 'emerald'; text: string }) {
  if (!text) return null
  return (
    <div className={clsx('rounded-xl p-4 text-sm', tone === 'amber' ? 'bg-amber-50 dark:bg-amber-950/30' : 'bg-emerald-50 dark:bg-emerald-950/30')}>
      <p className={clsx('mb-1 flex items-center gap-1.5 font-semibold', tone === 'amber' ? 'text-amber-800 dark:text-amber-300' : 'text-emerald-800 dark:text-emerald-300')}>
        <Icon className="size-4" /> {title}
      </p>
      <p className="text-slate-700 dark:text-slate-300">{text}</p>
    </div>
  )
}

const KIND_LABEL: Record<Concept['kind'], string> = { DEFINITION: 'Definition', TERMINOLOGY: 'Term', FORMULA: 'Formula', CONCEPT: 'Concept' }
const KIND_COLOR: Record<Concept['kind'], string> = {
  DEFINITION: 'bg-sky-50 text-sky-700 dark:bg-sky-950/40 dark:text-sky-300',
  TERMINOLOGY: 'bg-violet-50 text-violet-700 dark:bg-violet-950/40 dark:text-violet-300',
  FORMULA: 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300',
  CONCEPT: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300',
}

function ConceptsPanel() {
  const { detail, seek } = useLearn()
  const bookmark = useCreateBookmark(detail.status.id)
  const [query, setQuery] = useState('')
  const [kind, setKind] = useState<Concept['kind'] | 'ALL'>('ALL')
  const filtered = useMemo(
    () =>
      detail.concepts.filter(
        (c) => (kind === 'ALL' || c.kind === kind) && `${c.term} ${c.definition} ${c.simpleExplanation}`.toLowerCase().includes(query.toLowerCase()),
      ),
    [detail.concepts, kind, query],
  )
  const kinds = Array.from(new Set(detail.concepts.map((c) => c.kind)))
  return (
    <div>
      <div className="mb-4 flex flex-col gap-2 sm:flex-row">
        <label className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
          <span className="sr-only">Search concepts</span>
          <input className="input pl-9" placeholder="Search concepts…" value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
        <div className="flex gap-1 overflow-x-auto">
          {(['ALL', ...kinds] as const).map((k) => (
            <button
              key={k}
              onClick={() => setKind(k)}
              aria-pressed={kind === k}
              className={clsx('chip shrink-0 px-3 py-2', kind === k ? 'bg-brand-600 text-white' : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300')}
            >
              {k === 'ALL' ? 'All' : KIND_LABEL[k]}
            </button>
          ))}
        </div>
      </div>
      {filtered.length === 0 ? (
        <p className="py-8 text-center text-sm text-slate-500">No concepts match your search.</p>
      ) : (
        <dl className="grid gap-3 md:grid-cols-2">
          {filtered.map((c) => (
            <div key={c.term} className="rounded-xl border border-slate-200 p-4 dark:border-slate-800">
              <div className="flex items-start justify-between gap-2">
                <dt className="font-semibold">{c.term}</dt>
                <div className="flex items-center gap-1">
                  <Timestamp seconds={c.timestampSeconds} onSeek={seek} />
                  <button
                    className="btn-ghost p-1.5"
                    aria-label={`Bookmark ${c.term}`}
                    onClick={() => bookmark.mutate({ title: c.term, timestampSeconds: c.timestampSeconds, note: c.simpleExplanation })}
                  >
                    <Bookmark className="size-4" />
                  </button>
                </div>
              </div>
              <span className={clsx('chip mt-1', KIND_COLOR[c.kind])}>{KIND_LABEL[c.kind]}</span>
              <dd className="mt-2 space-y-1.5 text-sm">
                <p className={clsx('text-slate-800 dark:text-slate-200', c.kind === 'FORMULA' && 'font-mono')}>{c.definition}</p>
                <p className="text-slate-500 dark:text-slate-400">💡 {c.simpleExplanation}</p>
              </dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  )
}

function SummaryPanel() {
  const { detail } = useLearn()
  const [length, setLength] = useState<'oneMinute' | 'fiveMinute' | 'detailed'>('oneMinute')
  const s = detail.summaries
  if (!s) return null
  const options = [
    ['oneMinute', '1-minute'],
    ['fiveMinute', '5-minute'],
    ['detailed', 'Detailed'],
  ] as const
  return (
    <div>
      <div className="mb-5 inline-flex rounded-xl bg-slate-100 p-1 dark:bg-slate-800" role="radiogroup" aria-label="Summary length">
        {options.map(([key, label]) => (
          <button
            key={key}
            role="radio"
            aria-checked={length === key}
            onClick={() => setLength(key)}
            className={clsx('rounded-lg px-3 py-1.5 text-sm font-medium transition', length === key ? 'bg-white shadow-sm dark:bg-slate-900' : 'text-slate-500')}
          >
            {label}
          </button>
        ))}
      </div>
      <Markdown>{s[length]}</Markdown>
    </div>
  )
}
