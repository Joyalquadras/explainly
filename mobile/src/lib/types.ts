export type Role = 'GUEST' | 'USER' | 'ADMIN'

export interface User {
  id: number
  email: string | null
  displayName: string
  avatarUrl: string | null
  role: Role
  authProvider: 'LOCAL' | 'GOOGLE' | 'GUEST'
  createdAt: string
}

export interface AuthResponse {
  accessToken: string
  expiresIn: number
  refreshToken: string
  user: User
}

export interface PublicConfig {
  googleSignInEnabled: boolean
  googleWebClientId: string | null
  youtubeConnectEnabled: boolean
  aiConfigured: boolean
  aiProvider: string | null
  audioTranscriptionEnabled: boolean
  imageGenerationEnabled: boolean
  maxTranscriptUploadBytes: number
  maxAudioUploadBytes: number
}

export type AnalysisStatus =
  | 'PENDING'
  | 'FETCHING_TRANSCRIPT'
  | 'AWAITING_TRANSCRIPT'
  | 'PROCESSING'
  | 'GENERATING_EXPLANATION'
  | 'GENERATING_ILLUSTRATIONS'
  | 'COMPLETED'
  | 'FAILED'

export type TranscriptSource = 'YOUTUBE_CAPTIONS' | 'USER_PASTE' | 'USER_UPLOAD' | 'AUDIO_TRANSCRIPTION'

export interface StatusDto {
  id: number
  status: AnalysisStatus
  progress: number
  statusMessage: string | null
  errorMessage: string | null
  category: string | null
  transcriptSource: TranscriptSource | null
  canSubmitTranscript: boolean
  canRetry: boolean
  updatedAt: string
}

export interface Video {
  id: number
  youtubeId: string
  title: string
  channelTitle: string | null
  description: string | null
  thumbnailUrl: string | null
  durationSeconds: number | null
  publishedAt: string | null
}

export interface SubmitResponse {
  analysis: StatusDto
  video: Video
  reused: boolean
}

export interface Overview {
  title: string
  whatIsItAbout: string
  whatYouWillLearn: string[]
  whyItMatters: string
  whoShouldWatch: string
  difficulty: 'Beginner' | 'Intermediate' | 'Advanced'
  keyTakeaways: string[]
  language?: string
}

export interface Concept {
  term: string
  kind: 'DEFINITION' | 'TERMINOLOGY' | 'FORMULA' | 'CONCEPT'
  definition: string
  simpleExplanation: string
  timestampSeconds: number | null
}

export interface Chapter {
  id: number
  position: number
  title: string
  startSeconds: number | null
  endSeconds: number | null
  shortSummary: string
  simpleExplanation: string
  detailedExplanation: string
  realWorldExample: string
  practicalUseCase: string
  keyPoints: string[]
  concepts: string[]
}

export interface Illustration {
  id: number
  chapterId: number | null
  position: number
  title: string
  type: string
  explanation: string
  example: string
  svg: string | null
  hasImage: boolean
}

export interface Progress {
  progressPercent: number
  completedChapterIds: number[]
  lastPositionSeconds: number | null
}

export interface AnalysisDetail {
  status: StatusDto
  video: Video
  overview: Overview | null
  beginnerExplanation: string | null
  technicalExplanation: string | null
  concepts: Concept[]
  summaries: { oneMinute: string; fiveMinute: string; detailed: string } | null
  chapters: Chapter[]
  illustrations: Illustration[]
  progress: Progress
  transcriptTimed: boolean
  aiModel: string | null
}

export interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  citations: number[]
  createdAt: string
}

export interface NoteDto {
  content: string
  edited: boolean
  updatedAt: string | null
}

export interface QuizQuestion {
  id: number
  position: number
  type: 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'CODE'
  question: string
  codeSnippet: string | null
  options: string[]
  timestampSeconds: number | null
}

export interface AttemptSummary {
  id: number
  score: number
  total: number
  percent: number
  createdAt: string
}

export interface Quiz {
  id: number
  analysisId: number
  title: string
  createdAt: string
  questions: QuizQuestion[]
  bestAttempt: AttemptSummary | null
  attemptCount: number
}

export interface QuestionResult {
  questionId: number
  selectedIndex: number | null
  correctIndex: number
  correct: boolean
  explanation: string
  timestampSeconds: number | null
}

export interface AttemptResult {
  attempt: AttemptSummary
  results: QuestionResult[]
}

export interface Flashcard {
  id: number
  position: number
  question: string
  answer: string
  definition: string | null
}

export interface Bookmark {
  id: number
  analysisId: number
  chapterId: number | null
  timestampSeconds: number | null
  title: string
  note: string | null
  videoTitle: string | null
  youtubeId: string | null
  createdAt: string
}

export interface HistoryItem {
  analysisId: number
  youtubeId: string
  title: string
  channelTitle: string | null
  thumbnailUrl: string | null
  durationSeconds: number | null
  status: AnalysisStatus
  category: string | null
  progressPercent: number
  lastPositionSeconds: number | null
  lastAccessedAt: string
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
  hasNext: boolean
}

export interface HistoryStats {
  videos: number
  completedVideos: number
  quizAttempts: number
  averageQuizScore: number
  bookmarks: number
  notes: number
  categories: Record<string, number>
}

export interface YouTubeConnection {
  enabled: boolean
  connected: boolean
  accountName: string | null
  scopes: string | null
  connectedAt: string | null
}

export interface TranscriptView {
  source: TranscriptSource
  language: string | null
  timed: boolean
  segments: { start: number | null; end: number | null; text: string }[]
}
