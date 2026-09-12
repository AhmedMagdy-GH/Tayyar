import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useCurrentUser } from '../hooks/useCustomer'
import { ErrorState } from './States'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const session = useCurrentUser()
  const location = useLocation()
  if (session.isPending) return <div className="session-loading" role="status" aria-live="polite"><span className="loader" /> Checking your session…</div>
  if (session.isError) return <div className="shell standalone-state"><ErrorState title="We couldn’t check your session" onRetry={() => void session.refetch()} /></div>
  if (!session.data) return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  if (!session.data.roles.includes('CUSTOMER')) return <div className="shell standalone-state"><ErrorState title="This area is for customer accounts" /></div>
  return children
}
