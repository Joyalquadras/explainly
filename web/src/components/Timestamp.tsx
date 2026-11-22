import { Play } from 'lucide-react'
import { clock } from '../lib/format'

export function Timestamp({ seconds, onSeek }: { seconds: number | null | undefined; onSeek: (s: number) => void }) {
  if (seconds == null) return null
  return (
    <button
      type="button"
      onClick={() => onSeek(seconds)}
      className="inline-flex shrink-0 items-center gap-1 rounded-md bg-slate-100 px-1.5 py-0.5 font-mono text-xs font-semibold text-slate-700 transition hover:bg-brand-100 hover:text-brand-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-brand-900/50 dark:hover:text-brand-200"
      aria-label={`Play from ${clock(seconds)}`}
    >
      <Play className="size-3 fill-current" /> {clock(seconds)}
    </button>
  )
}
