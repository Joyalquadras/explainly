import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { ErrorState, Skeleton } from '../../components/States'
import { api, errorMessage } from '../../lib/api'
import type { AnalysisDetail } from '../../lib/types'
import { Dashboard } from './Dashboard'
import { ProcessingView } from './ProcessingView'

const IN_FLIGHT = new Set(['PENDING', 'FETCHING_TRANSCRIPT', 'PROCESSING', 'GENERATING_EXPLANATION', 'GENERATING_ILLUSTRATIONS'])

export function AnalysisPage() {
  const id = Number(useParams().id)
  const detail = useQuery({
    queryKey: ['analysis', id],
    queryFn: async () => (await api.get<AnalysisDetail>(`/api/analyses/${id}`)).data,
    enabled: Number.isFinite(id),
    refetchInterval: (q) => (q.state.data && IN_FLIGHT.has(q.state.data.status.status) ? 2500 : false),
  })

  if (detail.isLoading) {
    return (
      <div className="mx-auto grid max-w-7xl gap-5 px-3 py-5 sm:px-6 lg:grid-cols-[5fr_7fr]">
        <div className="space-y-4">
          <Skeleton className="aspect-video" />
          <Skeleton className="h-24" />
          <Skeleton className="h-64" />
        </div>
        <Skeleton className="h-[36rem]" />
      </div>
    )
  }
  if (detail.isError || !detail.data) {
    return (
      <div className="mx-auto max-w-lg px-4 py-16">
        <ErrorState message={errorMessage(detail.error, "We couldn't load this video.")} onRetry={() => void detail.refetch()} />
        <p className="mt-4 text-center text-sm">
          <Link to="/" className="text-brand-600 hover:underline">
            Analyze another video
          </Link>
        </p>
      </div>
    )
  }
  return detail.data.status.status === 'COMPLETED' ? <Dashboard detail={detail.data} /> : <ProcessingView detail={detail.data} />
}
