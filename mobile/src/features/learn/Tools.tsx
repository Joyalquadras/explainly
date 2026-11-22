import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { File, Paths } from 'expo-file-system'
import * as Sharing from 'expo-sharing'
import { useEffect, useState } from 'react'
import { Alert, Pressable, Text, View } from 'react-native'
import { Markdown } from '../../components/Markdown'
import { Body, Button, Card, Chip, EmptyView, ErrorView, Input, Loading, Timestamp } from '../../components/ui'
import { API_BASE, api, errorMessage } from '../../lib/api'
import { tokens } from '../../lib/tokens'
import { useColors } from '../../lib/theme'
import type { AttemptResult, ChatMessage, Flashcard, NoteDto, Quiz } from '../../lib/types'
import { useLearn } from './context'

const SUGGESTIONS = ['Explain it in simpler words', 'Give me a real-world example', 'Why is this important?', 'Summarize the key chapter']

export function ChatSection() {
  const c = useColors()
  const { detail, seek } = useLearn()
  const id = detail.status.id
  const qc = useQueryClient()
  const [input, setInput] = useState('')
  const history = useQuery({ queryKey: ['chat', id], queryFn: async () => (await api.get<ChatMessage[]>(`/api/analyses/${id}/chat`)).data })
  const ask = useMutation({
    mutationFn: async (message: string) => (await api.post<ChatMessage>(`/api/analyses/${id}/chat`, { message })).data,
    onSettled: () => void qc.invalidateQueries({ queryKey: ['chat', id] }),
  })
  const send = (text: string) => {
    if (!text.trim() || ask.isPending) return
    setInput('')
    ask.mutate(text.trim())
  }
  return (
    <View style={{ gap: 10 }}>
      {history.isLoading ? (
        <Loading />
      ) : (history.data ?? []).length === 0 && !ask.isPending ? (
        <View style={{ gap: 8 }}>
          <Body muted>Ask anything about this video. Answers cite the moment in the video.</Body>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
            {SUGGESTIONS.map((s) => (
              <Chip key={s} label={s} onPress={() => send(s)} />
            ))}
          </View>
        </View>
      ) : (
        (history.data ?? []).map((m) => (
          <View
            key={m.id}
            style={{
              alignSelf: m.role === 'user' ? 'flex-end' : 'flex-start',
              maxWidth: '90%',
              backgroundColor: m.role === 'user' ? c.brand : c.card,
              borderColor: c.border,
              borderWidth: m.role === 'user' ? 0 : 1,
              borderRadius: 16,
              padding: 12,
            }}
          >
            {m.role === 'user' ? <Text style={{ color: '#fff', fontSize: 15 }}>{m.content}</Text> : <Markdown onTimestamp={seek}>{m.content}</Markdown>}
            {m.citations.length > 0 && (
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginTop: 6 }}>
                {m.citations.map((s) => (
                  <Timestamp key={s} seconds={s} onSeek={seek} />
                ))}
              </View>
            )}
          </View>
        ))
      )}
      {ask.isPending && <Body muted>Thinking about the video…</Body>}
      {ask.isError && <Text style={{ color: c.danger }}>{errorMessage(ask.error)}</Text>}
      <View style={{ flexDirection: 'row', gap: 8 }}>
        <Input style={{ flex: 1 }} value={input} onChangeText={setInput} placeholder="Ask about this video…" returnKeyType="send" onSubmitEditing={() => send(input)} maxLength={2000} />
        <Button title="Ask" onPress={() => send(input)} loading={ask.isPending} disabled={!input.trim()} />
      </View>
    </View>
  )
}

export function NotesSection() {
  const c = useColors()
  const { detail } = useLearn()
  const id = detail.status.id
  const qc = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  const [sharing, setSharing] = useState(false)
  const notes = useQuery({ queryKey: ['notes', id], queryFn: async () => (await api.get<NoteDto>(`/api/analyses/${id}/notes`)).data })
  useEffect(() => {
    if (notes.data) setDraft(notes.data.content)
  }, [notes.data])
  const save = useMutation({
    mutationFn: async () => (await api.put<NoteDto>(`/api/analyses/${id}/notes`, { content: draft })).data,
    onSuccess: (d) => {
      qc.setQueryData(['notes', id], d)
      setEditing(false)
    },
    onError: (e) => Alert.alert('Could not save', errorMessage(e)),
  })
  const exportPdf = async () => {
    setSharing(true)
    try {
      const target = new File(Paths.cache, `explainly-notes-${id}.pdf`)
      if (target.exists) target.delete()
      const file = await File.downloadFileAsync(`${API_BASE}/api/analyses/${id}/notes/pdf`, target, {
        headers: { Authorization: `Bearer ${tokens.access ?? ''}` },
      })
      await Sharing.shareAsync(file.uri, { mimeType: 'application/pdf', UTI: 'com.adobe.pdf' })
    } catch (e) {
      Alert.alert('Could not export', errorMessage(e, 'Please try again.'))
    } finally {
      setSharing(false)
    }
  }
  if (notes.isLoading) return <Loading />
  if (notes.isError) return <ErrorView message={errorMessage(notes.error)} onRetry={() => void notes.refetch()} />
  return (
    <View style={{ gap: 10 }}>
      <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
        {editing ? (
          <>
            <Button title="Save" icon="save-outline" onPress={() => save.mutate()} loading={save.isPending} />
            <Button title="Cancel" variant="secondary" onPress={() => {
              setDraft(notes.data?.content ?? '')
              setEditing(false)
            }} />
          </>
        ) : (
          <>
            <Button title="Edit" icon="create-outline" variant="secondary" onPress={() => setEditing(true)} />
            <Button title="Share PDF" icon="share-outline" variant="secondary" onPress={() => void exportPdf()} loading={sharing} />
          </>
        )}
      </View>
      <Text style={{ color: c.muted, fontSize: 12 }}>{notes.data?.edited ? 'Your edited notes' : 'AI-generated notes'}</Text>
      {editing ? (
        <Input multiline value={draft} onChangeText={setDraft} style={{ minHeight: 360, textAlignVertical: 'top', paddingTop: 12, fontFamily: 'monospace', fontSize: 14 }} />
      ) : (
        <Card>
          <Markdown>{draft}</Markdown>
        </Card>
      )}
    </View>
  )
}

export function QuizSection() {
  const c = useColors()
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
    mutationFn: async (q: Quiz) => (await api.post<AttemptResult>(`/api/quizzes/${q.id}/attempts`, { answers: q.questions.map((x) => answers[x.id] ?? null) })).data,
    onSuccess: (r) => {
      setResult(r)
      void qc.invalidateQueries({ queryKey: ['history', 'stats'] })
    },
  })
  if (quiz.isLoading) return <Loading />
  if (quiz.isError) return <ErrorView message={errorMessage(quiz.error)} onRetry={() => void quiz.refetch()} />
  if (!quiz.data) {
    return (
      <EmptyView icon="checkbox-outline" title="Test your understanding" text="Multiple-choice, true/false and coding questions with explanations.">
        <Button title={generate.isPending ? 'Writing questions…' : 'Generate quiz'} loading={generate.isPending} onPress={() => generate.mutate()} />
        {generate.isError && <Text style={{ color: c.danger }}>{errorMessage(generate.error)}</Text>}
      </EmptyView>
    )
  }
  const q = quiz.data
  return (
    <View style={{ gap: 12 }}>
      {result && (
        <Card style={{ backgroundColor: c.brand, borderColor: c.brand }}>
          <Text style={{ color: '#fff', fontSize: 24, fontWeight: '800' }}>
            {result.attempt.score} / {result.attempt.total} ({result.attempt.percent}%)
          </Text>
          <Text style={{ color: '#E0E7FF' }}>{result.attempt.percent >= 80 ? 'Excellent work!' : 'Review the explanations below.'}</Text>
        </Card>
      )}
      {q.questions.map((qq, i) => {
        const r = result?.results.find((x) => x.questionId === qq.id)
        return (
          <Card key={qq.id} style={{ gap: 8 }}>
            <Text style={{ color: c.muted, fontSize: 11, fontWeight: '800' }}>QUESTION {i + 1}</Text>
            <Text style={{ color: c.text, fontWeight: '700', fontSize: 15 }}>{qq.question}</Text>
            {qq.codeSnippet && (
              <View style={{ backgroundColor: '#0F172A', padding: 10, borderRadius: 10 }}>
                <Text style={{ color: '#E2E8F0', fontFamily: 'monospace' }}>{qq.codeSnippet}</Text>
              </View>
            )}
            {qq.options.map((opt, idx) => {
              const selected = answers[qq.id] === idx
              const correct = r && r.correctIndex === idx
              const wrong = r && selected && !r.correct
              return (
                <Pressable
                  key={idx}
                  disabled={Boolean(result)}
                  onPress={() => setAnswers((a) => ({ ...a, [qq.id]: idx }))}
                  accessibilityRole="radio"
                  accessibilityState={{ checked: selected }}
                  style={{
                    padding: 12,
                    borderRadius: 12,
                    borderWidth: 1.5,
                    borderColor: correct ? c.success : wrong ? c.danger : selected ? c.brand : c.border,
                    backgroundColor: correct ? c.successSoft : wrong ? c.dangerSoft : selected ? c.brandSoft : 'transparent',
                  }}
                >
                  <Text style={{ color: c.text }}>{opt}</Text>
                </Pressable>
              )
            })}
            {r && (
              <View style={{ gap: 4 }}>
                <Text style={{ color: r.correct ? c.success : c.danger, fontWeight: '700' }}>{r.correct ? 'Correct!' : 'Not quite.'}</Text>
                <Body>{r.explanation}</Body>
                <Timestamp seconds={r.timestampSeconds} onSeek={seek} />
              </View>
            )}
          </Card>
        )
      })}
      {result ? (
        <Button title="Retake" icon="refresh" variant="secondary" onPress={() => {
          setResult(null)
          setAnswers({})
        }} />
      ) : (
        <Button title="Check answers" loading={submit.isPending} disabled={q.questions.some((x) => answers[x.id] == null)} onPress={() => submit.mutate(q)} />
      )}
      <Button title="New quiz" variant="ghost" loading={generate.isPending} onPress={() => generate.mutate()} />
    </View>
  )
}

export function FlashcardsSection() {
  const c = useColors()
  const { detail } = useLearn()
  const id = detail.status.id
  const qc = useQueryClient()
  const [index, setIndex] = useState(0)
  const [flipped, setFlipped] = useState(false)
  const cards = useQuery({ queryKey: ['flashcards', id], queryFn: async () => (await api.get<Flashcard[]>(`/api/analyses/${id}/flashcards`)).data })
  const generate = useMutation({
    mutationFn: async () => (await api.post<Flashcard[]>(`/api/analyses/${id}/flashcards`)).data,
    onSuccess: (d) => {
      qc.setQueryData(['flashcards', id], d)
      setIndex(0)
      setFlipped(false)
    },
  })
  if (cards.isLoading) return <Loading />
  if (cards.isError) return <ErrorView message={errorMessage(cards.error)} onRetry={() => void cards.refetch()} />
  const list = cards.data ?? []
  if (!list.length) {
    return (
      <EmptyView icon="layers-outline" title="Revise with flashcards">
        <Button title={generate.isPending ? 'Creating cards…' : 'Create flashcards'} loading={generate.isPending} onPress={() => generate.mutate()} />
        {generate.isError && <Text style={{ color: c.danger }}>{errorMessage(generate.error)}</Text>}
      </EmptyView>
    )
  }
  const card = list[Math.min(index, list.length - 1)]
  const go = (d: number) => {
    setFlipped(false)
    setIndex((i) => (i + d + list.length) % list.length)
  }
  return (
    <View style={{ gap: 12 }}>
      <Text style={{ color: c.muted, textAlign: 'center' }}>
        Card {index + 1} of {list.length}
      </Text>
      <Pressable
        onPress={() => setFlipped((f) => !f)}
        accessibilityRole="button"
        accessibilityHint={flipped ? 'Shows the question' : 'Shows the answer'}
        style={{ minHeight: 240, borderRadius: 22, padding: 22, alignItems: 'center', justifyContent: 'center', backgroundColor: flipped ? c.card : c.brand, borderWidth: 1, borderColor: flipped ? c.border : c.brand }}
      >
        <Text style={{ color: flipped ? c.muted : '#E0E7FF', fontWeight: '800', fontSize: 11, marginBottom: 10 }}>{flipped ? 'ANSWER' : 'QUESTION'}</Text>
        <Text style={{ color: flipped ? c.text : '#fff', fontSize: 19, fontWeight: '700', textAlign: 'center' }}>{flipped ? card.answer : card.question}</Text>
        {flipped && card.definition && <Text style={{ color: c.muted, marginTop: 14, textAlign: 'center' }}>📘 {card.definition}</Text>}
        {!flipped && <Text style={{ color: '#C7D2FE', marginTop: 18, fontSize: 12 }}>Tap to reveal</Text>}
      </Pressable>
      <View style={{ flexDirection: 'row', gap: 10 }}>
        <Button title="Prev" icon="chevron-back" variant="secondary" onPress={() => go(-1)} style={{ flex: 1 }} />
        <Button title="Next" icon="chevron-forward" variant="secondary" onPress={() => go(1)} style={{ flex: 1 }} />
      </View>
      <Button title="New deck" variant="ghost" loading={generate.isPending} onPress={() => generate.mutate()} />
    </View>
  )
}
