import { Compass } from 'lucide-react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../components/States'

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-lg px-4 py-16">
      <EmptyState icon={Compass} title="Page not found">
        <Link to="/" className="btn-primary mt-3">
          Go home
        </Link>
      </EmptyState>
    </div>
  )
}
