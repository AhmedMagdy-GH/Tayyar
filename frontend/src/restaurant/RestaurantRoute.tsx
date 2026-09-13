import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useCurrentUser } from '../hooks/useCustomer'
import { ErrorState } from '../components/States'
import { RestaurantContextProvider, useRestaurantContext, useRestaurantOperationsQuery } from './RestaurantContext'
import { RestaurantShell } from './RestaurantShell'

export function RestaurantRoute() {
  const session = useCurrentUser()
  const hasOperationsRole = Boolean(session.data?.roles.some((role) => role === 'RESTAURANT_OWNER' || role === 'RESTAURANT_STAFF'))
  const context = useRestaurantOperationsQuery(hasOperationsRole)
  const location = useLocation()
  if (session.isPending || (hasOperationsRole && context.isPending)) return <div className="session-loading" role="status"><span className="loader" /> Loading restaurant operations…</div>
  if (session.isError || context.isError) return <div className="shell standalone-state"><ErrorState title="We couldn’t load restaurant access" onRetry={() => { void session.refetch(); void context.refetch() }} /></div>
  if (!session.data) return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  if (!hasOperationsRole) return <Navigate to="/" replace />
  if (!context.data?.length) return <div className="shell standalone-state"><ErrorState title="No restaurant operations access" message="Your account has no current restaurant membership or branch assignment." /></div>
  return <RestaurantContextProvider contexts={context.data}><RestaurantShell><Outlet /></RestaurantShell></RestaurantContextProvider>
}

export function OwnerRoute() {
  const { selected } = useRestaurantContext()
  return selected.role === 'OWNER' ? <Outlet /> : <Navigate to="/restaurant/orders" replace />
}
