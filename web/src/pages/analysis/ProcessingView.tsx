import { Tab, TabGroup, TabList, TabPanel, TabPanels } from '@headlessui/react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { AlertTriangle, CheckCircle2, Circle, FileAudio, FileText, Loader2, RotateCcw, Upload } from 'lucide-react'
import { useRef, useState, type ReactNode } from 'react'
import { Spinner } from '../../components/States'
import { api, errorMessage } from '../../lib/api'
import { useAuth } from '../../lib/auth'
import type { AnalysisDetail, AnalysisStatus, StatusDto } from '../../lib/types'

const STEPS: { status: AnalysisStatus; label: string }[] = [
  { status: 'FETCHING_TRANSCRIPT', label: 'Getting the transcript' },
  { status: 'PROCESSING', label: 'Reading the whole video' },
  { status: 'GENERATING_EXPLANATION', label: 'Writing simple explanations' },
  { status: 'GENERATING_ILLUSTRATIONS', label: 'Drawing diagrams' },
  { status: 'COMPLETED', label: 'Ready to learn' },
]
const ORDER: AnalysisStatus[] = ['PENDING', 'FETCHING_TRANSCRIPT', 'PROCESSING', 'GENERATING_EXPLANATION', 'GENERATING_ILLUSTRATIONS', 'COMPLETED']

export function ProcessingView({ detail }: { detail: AnalysisDetail }) {
  const { status } = detail
  const { video } = detail
  return (
    <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6">
      <div className="card overflow-hidden">
        <div className="flex flex-col gap-4 p-5 sm:flex-row">
          {video.thumbnailUrl && <img src={video.thumbnailUrl} alt="" className="aspect-video w-full rounded-xl object-cover sm:w-56" />}
          <div className="min-w-0">
            <h1 className="text-xl font-bold leading-snug">{video.title}</h1>
            {video.channelTitle && <p className="mt-1 text-sm text-slate-500">{video.channelTitle}</p>}
          </div>
        </div>
        <div className="border-t border-slate-200 p-5 dark:border-slate-800">
          {status.status === 'AWAITING_TRANSCRIPT' ? (
            <TranscriptNeeded status={status} />
          ) : status.status === 'FAILED' ? (
            <Failed status={status} />
          ) : (
            <Progressing status={status} />
          )}
        </div>
      </div>
    </div>
  )
}

function Progressing({ status }: { status: StatusDto }) {
  const current = ORDER.indexOf(status.status)
  return (
    <div aria-live="polite">
      <div className="mb-2 flex items-center justify-between text-sm">
        <span className="font-semibold">{status.statusMessage ?? 'Working…'}</span>
        <span className="tabular-nums text-slate-500">{status.progress}%</span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800" role="progressbar" aria-valuenow={status.progress} aria-valuemin={0} aria-valuemax={100}>
        <div className="h-full rounded-full bg-gradient-to-r from-brand-500 to-violet-500 transition-all duration-700" style={{ width: `${Math.max(status.progress, 4)}%` }} />
      </div>
      <ol className="mt-6 space-y-3">
        {STEPS.map((step) => {
          const idx = ORDER.indexOf(step.status)
          const done = idx < current
          const active = idx === current
          return (
            <li key={step.status} className={clsx('flex items-center gap-3 text-sm', done || active ? 'text-slate-900 dark:text-slate-100' : 'text-slate-400')}>
              {done ? <CheckCircle2 className="size-5 text-emerald-500" /> : active ? <Loader2 className="size-5 animate-spin text-brand-500" /> : <Circle className="size-5" />}
              {step.label}
            </li>
          )
        })}
      </ol>
      <p className="mt-6 text-xs text-slate-500">Long videos can take a few minutes. You can leave this page — we'll keep working and it will be in your library.</p>
    </div>
  )
}

function Failed({ status }: { status: StatusDto }) {
  const qc = useQueryClient()
  const retry = useMutation({
    mutationFn: () => api.post(`/api/analyses/${status.id}/retry`),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['analysis', status.id] }),
  })
  return (
    <div>
      <div className="flex items-start gap-3 rounded-xl bg-rose-50 p-4 text-sm text-rose-800 dark:bg-rose-950/40 dark:text-rose-200" role="alert">
        <AlertTriangle className="mt-0.5 size-5 shrink-0" />
        <div>
          <p className="font-semibold">We couldn't finish this video</p>
          <p className="mt-1">{status.errorMessage ?? 'Something went wrong.'}</p>
        </div>
      </div>
      {status.canRetry && (
        <button className="btn-primary mt-4" onClick={() => retry.mutate()} disabled={retry.isPending}>
          {retry.isPending ? <Spinner /> : <RotateCcw className="size-4" />} Try again
        </button>
      )}
      {retry.isError && <p className="mt-2 text-sm text-rose-600">{errorMessage(retry.error)}</p>}
      {status.canSubmitTranscript && (
        <div className="mt-6">
          <p className="mb-3 text-sm text-slate-600 dark:text-slate-400">Or provide a different transcript:</p>
          <TranscriptForms analysisId={status.id} />
        </div>
      )}
    </div>
  )
}

function TranscriptNeeded({ status }: { status: StatusDto }) {
  return (
    <div>
      <div className="mb-5 rounded-xl bg-amber-50 p-4 text-sm text-amber-900 dark:bg-amber-950/40 dark:text-amber-100">
        <p className="font-semibold">We need the transcript to explain this video</p>
        <p className="mt-1">{status.statusMessage}</p>
        <p className="mt-2 text-amber-800/80 dark:text-amber-200/80">
          Tip: on YouTube, open the video description and choose <em>Show transcript</em>, then copy the text (timestamps included) and paste it below.
        </p>
      </div>
      {status.canSubmitTranscript ? (
        <TranscriptForms analysisId={status.id} />
      ) : (
        <p className="text-sm text-slate-500">Only the person who added this video can provide its transcript. Analyze it yourself from the home page to create your own copy.</p>
      )}
    </div>
  )
}

function TranscriptForms({ analysisId }: { analysisId: number }) {
  const { config } = useAuth()
  const qc = useQueryClient()
  const [text, setText] = useState('')
  const [error, setError] = useState<string | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const audioRef = useRef<HTMLInputElement>(null)

  const submit = useMutation({
    mutationFn: async (input: { kind: 'text' | 'file' | 'audio'; payload: string | File }) => {
      if (input.kind === 'text') return api.post(`/api/analyses/${analysisId}/transcript`, { text: input.payload })
      const form = new FormData()
      form.append('file', input.payload as File)
      return api.post(`/api/analyses/${analysisId}/transcript/${input.kind === 'file' ? 'upload' : 'audio'}`, form, {
        timeout: input.kind === 'audio' ? 600_000 : 120_000,
      })
    },
    onMutate: () => setError(null),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['analysis', analysisId] }),
    onError: (e) => setError(errorMessage(e)),
  })

  const tabs: { label: string; icon: typeof FileText; panel: ReactNode }[] = [
    {
      label: 'Paste text',
      icon: FileText,
      panel: (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            submit.mutate({ kind: 'text', payload: text })
          }}
        >
          <label htmlFor="transcript-text" className="sr-only">
            Transcript text
          </label>
          <textarea
            id="transcript-text"
            className="input min-h-48 font-mono text-xs"
            placeholder={'0:00\nWelcome to this video…\n0:12\nToday we will learn…'}
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
          <button className="btn-primary mt-3" disabled={submit.isPending || text.trim().length < 50}>
            {submit.isPending && <Spinner />} Explain this transcript
          </button>
        </form>
      ),
    },
    {
      label: 'Upload file',
      icon: Upload,
      panel: (
        <div className="rounded-xl border-2 border-dashed border-slate-300 p-6 text-center dark:border-slate-700">
          <p className="text-sm text-slate-600 dark:text-slate-400">Upload a caption or transcript file: .srt, .vtt or .txt</p>
          <input
            ref={fileRef}
            type="file"
            accept=".srt,.vtt,.txt,text/plain,text/vtt"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0]
              if (file) submit.mutate({ kind: 'file', payload: file })
              e.target.value = ''
            }}
          />
          <button className="btn-secondary mt-3" onClick={() => fileRef.current?.click()} disabled={submit.isPending}>
            {submit.isPending ? <Spinner /> : <Upload className="size-4" />} Choose file
          </button>
        </div>
      ),
    },
  ]
  if (config?.audioTranscriptionEnabled) {
    tabs.push({
      label: 'Upload audio',
      icon: FileAudio,
      panel: (
        <div className="rounded-xl border-2 border-dashed border-slate-300 p-6 text-center dark:border-slate-700">
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Upload an audio file you have the right to use (mp3, m4a, wav, webm; up to {Math.round((config.maxAudioUploadBytes ?? 0) / 1048576)} MB). We'll transcribe it with
            speech-to-text.
          </p>
          <input
            ref={audioRef}
            type="file"
            accept="audio/*,.mp3,.m4a,.wav,.webm,.ogg,.flac"
            className="hidden"
            onChange={(e) => {
              const file = e.target.files?.[0]
              if (file) submit.mutate({ kind: 'audio', payload: file })
              e.target.value = ''
            }}
          />
          <button className="btn-secondary mt-3" onClick={() => audioRef.current?.click()} disabled={submit.isPending}>
            {submit.isPending ? <Spinner /> : <FileAudio className="size-4" />} Choose audio
          </button>
        </div>
      ),
    })
  }

  return (
    <TabGroup>
      <TabList className="mb-4 flex gap-2">
        {tabs.map((t) => (
          <Tab
            key={t.label}
            className={({ selected }) =>
              clsx('flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-medium outline-none', selected ? 'bg-brand-50 text-brand-700 dark:bg-brand-900/40 dark:text-brand-200' : 'text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800')
            }
          >
            <t.icon className="size-4" /> {t.label}
          </Tab>
        ))}
      </TabList>
      <TabPanels>
        {tabs.map((t) => (
          <TabPanel key={t.label}>{t.panel}</TabPanel>
        ))}
      </TabPanels>
      {error && (
        <p role="alert" className="mt-3 text-sm text-rose-600 dark:text-rose-400">
          {error}
        </p>
      )}
    </TabGroup>
  )
}
