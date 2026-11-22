import { Dialog, DialogPanel, DialogTitle } from '@headlessui/react'
import { Maximize2, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { api } from '../../lib/api'
import { svgDataUri } from '../../lib/format'
import type { Illustration } from '../../lib/types'

const TYPE_LABEL: Record<string, string> = {
  flowchart: 'Flowchart',
  process: 'Process',
  architecture: 'Architecture',
  steps: 'Step by step',
  mindmap: 'Mind map',
  comparison: 'Comparison',
  cycle: 'Cycle',
}

/** AI images are protected; fetch them with the auth header and show as object URLs. */
function useProtectedImage(id: number, enabled: boolean) {
  const [src, setSrc] = useState<string | null>(null)
  useEffect(() => {
    if (!enabled) return
    let url: string | null = null
    let cancelled = false
    api
      .get<Blob>(`/api/illustrations/${id}/image`, { responseType: 'blob' })
      .then((res) => {
        if (cancelled) return
        url = URL.createObjectURL(res.data)
        setSrc(url)
      })
      .catch(() => undefined)
    return () => {
      cancelled = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [id, enabled])
  return src
}

export function IllustrationCard({ illustration }: { illustration: Illustration }) {
  const [open, setOpen] = useState(false)
  const image = useProtectedImage(illustration.id, illustration.hasImage)
  const svg = illustration.svg ? svgDataUri(illustration.svg) : null

  return (
    <figure className="overflow-hidden rounded-2xl border border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-950/50">
      <div className="flex items-center justify-between gap-2 border-b border-slate-200 px-4 py-2.5 dark:border-slate-800">
        <div className="min-w-0">
          <span className="text-[11px] font-semibold uppercase tracking-wider text-brand-600 dark:text-brand-300">{TYPE_LABEL[illustration.type] ?? 'Diagram'}</span>
          <h4 className="truncate text-sm font-semibold">{illustration.title}</h4>
        </div>
        {svg && (
          <button className="btn-ghost p-2" onClick={() => setOpen(true)} aria-label={`Enlarge ${illustration.title}`}>
            <Maximize2 className="size-4" />
          </button>
        )}
      </div>
      <button type="button" className="block w-full cursor-zoom-in bg-white p-3 dark:bg-slate-100" onClick={() => svg && setOpen(true)} aria-label={`Enlarge ${illustration.title}`}>
        {image ? (
          <img src={image} alt={illustration.title} className="mx-auto max-h-96 rounded-xl" />
        ) : svg ? (
          <img src={svg} alt={`${illustration.title}: ${illustration.explanation}`} className="mx-auto max-h-[28rem] w-auto max-w-full" />
        ) : null}
      </button>
      <figcaption className="space-y-1.5 px-4 py-3 text-sm">
        {illustration.explanation && <p className="text-slate-700 dark:text-slate-300">{illustration.explanation}</p>}
        {illustration.example && (
          <p className="text-slate-500 dark:text-slate-400">
            <span className="font-semibold text-slate-600 dark:text-slate-300">Example: </span>
            {illustration.example}
          </p>
        )}
      </figcaption>
      <Dialog open={open} onClose={() => setOpen(false)} className="relative z-50">
        <div className="fixed inset-0 bg-black/70" aria-hidden="true" />
        <div className="fixed inset-0 flex items-center justify-center p-2 sm:p-6">
          <DialogPanel className="relative max-h-full w-full max-w-6xl overflow-auto rounded-2xl bg-white p-4">
            <div className="mb-2 flex items-center justify-between">
              <DialogTitle className="font-semibold text-slate-900">{illustration.title}</DialogTitle>
              <button className="rounded-lg p-2 text-slate-600 hover:bg-slate-100" onClick={() => setOpen(false)} aria-label="Close">
                <X className="size-5" />
              </button>
            </div>
            {svg && <img src={svg} alt={illustration.title} className="mx-auto w-full" />}
          </DialogPanel>
        </div>
      </Dialog>
    </figure>
  )
}
