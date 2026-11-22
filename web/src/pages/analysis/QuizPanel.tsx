import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import clsx from 'clsx'
import { CheckCircle2, ClipboardCheck, RefreshCw, RotateCcw, Trophy, XCircle } from 'lucide-react'
import { useState } from 'react'
import { EmptyState, ErrorState, Skeleton, Spinner } from '../../components/States'
import { Timestamp } from '../../components/Timestamp'
import { api, errorMessage } from '../../lib/api'
import type { AttemptResult, Quiz } from '../../lib/types'
import { useLearn } from './context'

const TYPE_LABEL = { MULTIPLE_CHOICE: 'Multiple choice', TRUE_FALSE: 'True or false', CODE: 'Code' }

export function QuizPanel() {
  const { detail, seek } = useLearn()
  const id = detail.status.id
  const qc = useQueryClient()
  const [answers, setAnswers] = useState<Record<number, number>>({})
  const [result, setResult] = useState<AttemptResult | null>(null)

  const quiz = useQuery({
    queryKey: ['quiz', id],
    queryFn: async () => {
      const res = await api.get<Quiz>(`/api/analyses/${id}/quiz`)
      return res.status === 204 ? null : res.data
    },
  })
  const generate = useMutation({
    mutationFn: async () => (await api.post<Quiz>(`/api/analyses/${id}/quiz`)).data,
    onSuccess: (q) => {
      qc.setQueryData(['quiz', id], q)
      setAnswers({})
      setResult(null)
    },
  })
  const submit = useMutation({
    mutationFn: async (q: Quiz) => (await api.post<AttemptResult>(`/api/quizzes/${q.id}/attempts`, { answers: q.questions.map((qq) => answers[qq.id] ?? null) })).data,
    onSuccess: (r) => {
      setResult(r)
      void qc.invalidateQueries({ queryKey: ['quiz', id] })
      void qc.invalidateQueries({ queryKey: ['history', 'stats'] })
      window.scrollTo({ top: document.getElementById('quiz-top')?.offsetTop ?? 0, behavior: 'smooth' })
    },
  })

  if (quiz.isLoading) return <Skeleton className="h-64" />
  if (quiz.isError) return <ErrorState message={errorMessage(quiz.error)} onRetry={() => void quiz.refetch()} />
  if (!quiz.data) {
    return (
      <EmptyState icon={ClipboardCheck} title="Test your understanding">
        <p>Generate a quiz with multiple-choice, true/false and (for technical videos) coding questions, with explanations for every answer.</p>
        <button className="btn-primary mt-4" onClick={() => generate.mutate()} disabled={generate.isPending}>
          {generate.isPending && <Spinner />} {generate.isPending ? 'Writing questions…' : 'Generate quiz'}
        </button>
        {generate.isError && <p className="mt-2 text-rose-600">{errorMessage(generate.error)}</p>}
      </EmptyState>
    )
  }

  const q = quiz.data
  const answeredAll = q.questions.every((qq) => answers[qq.id] != null)
  const resultFor = (questionId: number) => result?.results.find((r) => r.questionId === questionId)

  return (
    <div id="quiz-top" className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="font-semibold">{q.title}</h3>
          <p className="text-xs text-slate-500">
            {q.questions.length} questions
            {q.bestAttempt && ` · Best score ${q.bestAttempt.percent}% · ${q.attemptCount} attempt${q.attemptCount === 1 ? '' : 's'}`}
          </p>
        </div>
        <button className="btn-ghost" onClick={() => generate.mutate()} disabled={generate.isPending}>
          {generate.isPending ? <Spinner /> : <RefreshCw className="size-4" />} New quiz
        </button>
      </div>

      {result && (
        <div className="flex items-center gap-4 rounded-2xl bg-gradient-to-r from-brand-600 to-violet-600 p-5 text-white" role="status">
          <Trophy className="size-10 shrink-0" />
          <div className="flex-1">
            <p className="text-2xl font-bold">
              {result.attempt.score} / {result.attempt.total} ({result.attempt.percent}%)
            </p>
            <p className="text-sm text-white/80">
              {result.attempt.percent >= 80 ? 'Excellent work!' : result.attempt.percent >= 50 ? 'Good effort — review the explanations below.' : 'Keep going — the explanations below will help.'}
            </p>
          </div>
          <button
            className="btn bg-white/15 text-white hover:bg-white/25"
            onClick={() => {
              setResult(null)
              setAnswers({})
            }}
          >
            <RotateCcw className="size-4" /> Retake
          </button>
        </div>
      )}

      <ol className="space-y-4">
        {q.questions.map((qq, i) => {
          const r = resultFor(qq.id)
          return (
            <li key={qq.id} className="rounded-2xl border border-slate-200 p-4 dark:border-slate-800">
              <fieldset disabled={Boolean(result)}>
                <legend className="mb-3">
                  <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Question {i + 1} · {TYPE_LABEL[qq.type]}
                  </span>
                  <span className="mt-1 block font-medium">{qq.question}</span>
                </legend>
                {qq.codeSnippet && <pre className="mb-3 overflow-x-auto rounded-xl bg-slate-900 p-3 text-sm text-slate-100">{qq.codeSnippet}</pre>}
                <div className={clsx('grid gap-2', qq.type === 'TRUE_FALSE' ? 'grid-cols-2' : 'sm:grid-cols-2')}>
                  {qq.options.map((opt, idx) => {
                    const selected = answers[qq.id] === idx
                    const isCorrect = r && r.correctIndex === idx
                    const isWrongPick = r && selected && !r.correct
                    return (
                      <label
                        key={idx}
                        className={clsx(
                          'flex cursor-pointer items-start gap-2.5 rounded-xl border p-3 text-sm transition',
                          isCorrect
                            ? 'border-emerald-500 bg-emerald-50 dark:bg-emerald-950/30'
                            : isWrongPick
                              ? 'border-rose-500 bg-rose-50 dark:bg-rose-950/30'
                              : selected
                                ? 'border-brand-500 bg-brand-50 dark:bg-brand-950/30'
                                : 'border-slate-200 hover:border-slate-300 dark:border-slate-700',
                          result && 'cursor-default',
                        )}
                      >
                        <input
                          type="radio"
                          name={`q-${qq.id}`}
                          className="mt-0.5 accent-brand-600"
                          checked={selected}
                          onChange={() => setAnswers((a) => ({ ...a, [qq.id]: idx }))}
                        />
                        <span className="flex-1">{opt}</span>
                        {isCorrect && <CheckCircle2 className="size-4 text-emerald-600" />}
                        {isWrongPick && <XCircle className="size-4 text-rose-600" />}
                      </label>
                    )
                  })}
                </div>
              </fieldset>
              {r && (
                <div className={clsx('mt-3 rounded-xl p-3 text-sm', r.correct ? 'bg-emerald-50 dark:bg-emerald-950/30' : 'bg-amber-50 dark:bg-amber-950/30')}>
                  <p className="font-semibold">{r.correct ? 'Correct!' : 'Not quite.'}</p>
                  <p className="mt-1 text-slate-700 dark:text-slate-300">{r.explanation}</p>
                  {r.timestampSeconds != null && (
                    <p className="mt-2 flex items-center gap-2 text-xs text-slate-500">
                      Review in the video <Timestamp seconds={r.timestampSeconds} onSeek={seek} />
                    </p>
                  )}
                </div>
              )}
            </li>
          )
        })}
      </ol>
      {!result && (
        <div className="flex items-center gap-3">
          <button className="btn-primary" onClick={() => submit.mutate(q)} disabled={!answeredAll || submit.isPending}>
            {submit.isPending && <Spinner />} Check answers
          </button>
          {!answeredAll && <span className="text-sm text-slate-500">Answer every question to submit.</span>}
          {submit.isError && <span className="text-sm text-rose-600">{errorMessage(submit.error)}</span>}
        </div>
      )}
      {generate.isError && <p className="text-sm text-rose-600">{errorMessage(generate.error)}</p>}
    </div>
  )
}
