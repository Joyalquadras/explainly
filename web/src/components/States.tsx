import { AlertTriangle, type LucideIcon } from 'lucide-react'
import clsx from 'clsx'
import type { ReactNode } from 'react'

export function Skeleton({ className }: { className?: string }) {
  return <div className={clsx('animate-pulse rounded-xl bg-slate-200 dark:bg-slate-800', className)} aria-hidden="true" />
}

export function Spinner({ className }: { className?: string }) {
  return (
    <svg className={clsx('size-4 animate-spin', className)} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeOpacity="0.25" strokeWidth="4" />
      <path d="M22 12a10 10 0 0 0-10-10" stroke="currentColor" strokeWidth="4" strokeLinecap="round" />
    </svg>
  )
}

export function EmptyState({ icon: Icon, title, children }: { icon: LucideIcon; title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-slate-300 px-6 py-12 text-center dark:border-slate-700">
      <div className="rounded-2xl bg-brand-50 p-3 text-brand-600 dark:bg-brand-900/30 dark:text-brand-300">
        <Icon className="size-6" />
      </div>
      <h3 className="font-semibold">{title}</h3>
      {children && <div className="max-w-md text-sm text-slate-500 dark:text-slate-400">{children}</div>}
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-3 rounded-2xl border border-rose-200 bg-rose-50 px-6 py-8 text-center dark:border-rose-900/50 dark:bg-rose-950/30">
      <AlertTriangle className="size-6 text-rose-500" />
      <p className="text-sm text-rose-800 dark:text-rose-200">{message}</p>
      {onRetry && (
        <button className="btn-secondary" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  )
}
