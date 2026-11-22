import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { Download, Eye, PencilLine, RotateCcw, Save } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Markdown } from '../../components/Markdown'
import { ErrorState, Skeleton, Spinner } from '../../components/States'
import { useToast } from '../../components/Toast'
import { api, downloadFile, errorMessage } from '../../lib/api'
import { relativeTime } from '../../lib/format'
import type { NoteDto } from '../../lib/types'
import { useLearn } from './context'

export function NotesPanel() {
  const { detail } = useLearn()
  const id = detail.status.id
  const qc = useQueryClient()
  const toast = useToast()
  const [mode, setMode] = useState<'preview' | 'edit'>('preview')
  const [draft, setDraft] = useState('')
  const [exporting, setExporting] = useState(false)
  const notes = useQuery({ queryKey: ['notes', id], queryFn: async () => (await api.get<NoteDto>(`/api/analyses/${id}/notes`)).data })

  useEffect(() => {
    if (notes.data) setDraft(notes.data.content)
  }, [notes.data])

  const dirty = notes.data != null && draft !== notes.data.content
  useEffect(() => {
    if (!dirty) return
    const warn = (e: BeforeUnloadEvent) => e.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const save = useMutation({
    mutationFn: async () => (await api.put<NoteDto>(`/api/analyses/${id}/notes`, { content: draft })).data,
    onSuccess: (data) => {
      qc.setQueryData(['notes', id], data)
      toast('Notes saved', 'success')
    },
    onError: (e) => toast(errorMessage(e), 'error'),
  })
  const reset = useMutation({
    mutationFn: async () => (await api.post<NoteDto>(`/api/analyses/${id}/notes/reset`)).data,
    onSuccess: (data) => {
      qc.setQueryData(['notes', id], data)
      toast('Notes restored to the AI version', 'info')
    },
  })

  const exportPdf = async () => {
    if (dirty) await save.mutateAsync()
    setExporting(true)
    try {
      await downloadFile(`/api/analyses/${id}/notes/pdf`, 'notes.pdf')
    } catch (e) {
      toast(errorMessage(e), 'error')
    } finally {
      setExporting(false)
    }
  }

  if (notes.isLoading) return <Skeleton className="h-80" />
  if (notes.isError) return <ErrorState message={errorMessage(notes.error)} onRetry={() => void notes.refetch()} />

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <div className="inline-flex rounded-xl bg-slate-100 p-1 dark:bg-slate-800">
          {(['preview', 'edit'] as const).map((m) => (
            <button
              key={m}
              onClick={() => setMode(m)}
              aria-pressed={mode === m}
              className={clsx('flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium', mode === m ? 'bg-white shadow-sm dark:bg-slate-900' : 'text-slate-500')}
            >
              {m === 'preview' ? <Eye className="size-4" /> : <PencilLine className="size-4" />}
              {m === 'preview' ? 'Read' : 'Edit'}
            </button>
          ))}
        </div>
        <span className="text-xs text-slate-500">
          {dirty ? 'Unsaved changes' : notes.data?.edited && notes.data.updatedAt ? `Saved ${relativeTime(notes.data.updatedAt)}` : 'AI-generated notes'}
        </span>
        <div className="ml-auto flex flex-wrap gap-2">
          {notes.data?.edited && (
            <button className="btn-ghost" onClick={() => confirm('Replace your notes with the AI-generated version?') && reset.mutate()} disabled={reset.isPending}>
              <RotateCcw className="size-4" /> Reset
            </button>
          )}
          <button className="btn-secondary" onClick={() => void exportPdf()} disabled={exporting}>
            {exporting ? <Spinner /> : <Download className="size-4" />} PDF
          </button>
          <button className="btn-primary" onClick={() => save.mutate()} disabled={!dirty || save.isPending}>
            {save.isPending ? <Spinner /> : <Save className="size-4" />} Save
          </button>
        </div>
      </div>
      {mode === 'edit' ? (
        <>
          <label htmlFor="notes-editor" className="sr-only">
            Notes (markdown)
          </label>
          <textarea id="notes-editor" className="input min-h-[28rem] font-mono text-sm leading-relaxed" value={draft} onChange={(e) => setDraft(e.target.value)} />
          <p className="mt-1 text-xs text-slate-500">Formatting: # Heading, - bullet, **bold**, `code`.</p>
        </>
      ) : (
        <div className="max-h-[36rem] overflow-y-auto rounded-xl border border-slate-200 p-5 dark:border-slate-800">
          <Markdown>{draft || '_No notes yet._'}</Markdown>
        </div>
      )}
    </div>
  )
}
