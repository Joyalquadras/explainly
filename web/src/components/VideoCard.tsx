import clsx from 'clsx'
import { Link } from 'react-router-dom'
import { duration, relativeTime } from '../lib/format'
import type { HistoryItem } from '../lib/types'

const STATUS_LABEL: Record<string, string> = {
  COMPLETED: '',
  FAILED: 'Failed',
  AWAITING_TRANSCRIPT: 'Needs transcript',
}

export function VideoCard({ item }: { item: HistoryItem }) {
  const status = STATUS_LABEL[item.status] ?? 'Processing…'
  return (
    <Link to={`/learn/${item.analysisId}`} className="group card flex flex-col overflow-hidden transition hover:-translate-y-0.5 hover:shadow-md">
      <div className="relative aspect-video bg-slate-200 dark:bg-slate-800">
        {item.thumbnailUrl && <img src={item.thumbnailUrl} alt="" loading="lazy" className="size-full object-cover" />}
        {item.durationSeconds ? (
          <span className="absolute bottom-2 right-2 rounded-md bg-black/75 px-1.5 py-0.5 text-[11px] font-medium text-white">{duration(item.durationSeconds)}</span>
        ) : null}
        {status && (
          <span
            className={clsx(
              'absolute left-2 top-2 chip',
              item.status === 'FAILED' ? 'bg-rose-600 text-white' : item.status === 'AWAITING_TRANSCRIPT' ? 'bg-amber-400 text-amber-950' : 'bg-brand-600 text-white',
            )}
          >
            {status}
          </span>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-2 p-4">
        <h3 className="line-clamp-2 font-semibold leading-snug group-hover:text-brand-600 dark:group-hover:text-brand-300">{item.title}</h3>
        <div className="mt-auto flex items-center justify-between gap-2 text-xs text-slate-500">
          <span className="truncate">{item.category ?? item.channelTitle ?? ''}</span>
          <span className="shrink-0">{relativeTime(item.lastAccessedAt)}</span>
        </div>
        {item.status === 'COMPLETED' && (
          <div className="h-1.5 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800" aria-label={`${item.progressPercent}% complete`}>
            <div className="h-full rounded-full bg-gradient-to-r from-brand-500 to-violet-500" style={{ width: `${item.progressPercent}%` }} />
          </div>
        )}
      </div>
    </Link>
  )
}
