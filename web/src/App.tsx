import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import axios from 'axios'
import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { RequireAuth } from './components/RequireAuth'
import { Skeleton } from './components/States'
import { ToastProvider } from './components/Toast'
import { AuthProvider } from './lib/auth'
import { ThemeProvider } from './lib/theme'
import { HomePage } from './pages/HomePage'
import { NotFoundPage } from './pages/NotFoundPage'

const AnalysisPage = lazy(() => import('./pages/analysis/AnalysisPage').then((m) => ({ default: m.AnalysisPage })))
const BookmarksPage = lazy(() => import('./pages/BookmarksPage').then((m) => ({ default: m.BookmarksPage })))
const LibraryPage = lazy(() => import('./pages/LibraryPage').then((m) => ({ default: m.LibraryPage })))
const SettingsPage = lazy(() => import('./pages/SettingsPage').then((m) => ({ default: m.SettingsPage })))
const SignInPage = lazy(() => import('./pages/SignInPage').then((m) => ({ default: m.SignInPage })))
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false,
      retry: (count, error) => {
        const status = axios.isAxiosError(error) ? error.response?.status : undefined
        if (status && status >= 400 && status < 500) return false
        return count < 2
      },
    },
  },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <ToastProvider>
          <AuthProvider>
            <BrowserRouter>
              <Suspense fallback={<div className="mx-auto max-w-7xl p-6"><Skeleton className="h-64" /></div>}>
              <Routes>
                <Route element={<Layout />}>
                  <Route index element={<HomePage />} />
                  <Route path="signin" element={<SignInPage />} />
                  <Route path="learn/:id" element={<RequireAuth><AnalysisPage /></RequireAuth>} />
                  <Route path="library" element={<RequireAuth><LibraryPage /></RequireAuth>} />
                  <Route path="bookmarks" element={<RequireAuth><BookmarksPage /></RequireAuth>} />
                  <Route path="settings" element={<RequireAuth><SettingsPage /></RequireAuth>} />
                  <Route path="*" element={<NotFoundPage />} />
                </Route>
              </Routes>
              </Suspense>
            </BrowserRouter>
          </AuthProvider>
        </ToastProvider>
      </ThemeProvider>
    </QueryClientProvider>
  )
}
