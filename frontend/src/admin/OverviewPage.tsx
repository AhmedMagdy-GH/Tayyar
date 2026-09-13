import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { adminApi } from '../api/admin'
import { queryKeys } from '../api/queryKeys'
import { Loading, PageHeader } from './shared'

export function AdminOverviewPage() {
  const users = useQuery({ queryKey: queryKeys.adminUsers({ page: 0, size: 1 }), queryFn: () => adminApi.users({ page: 0, size: 1 }) })
  const applications = useQuery({ queryKey: queryKeys.adminApplications('PENDING', 0), queryFn: () => adminApi.applications('PENDING', 0) })
  const ready = useQuery({ queryKey: queryKeys.adminOrders({ status: 'READY_FOR_PICKUP', page: 0, size: 1 }), queryFn: () => adminApi.orders({ status: 'READY_FOR_PICKUP', page: 0, size: 1 }) })
  const drivers = useQuery({ queryKey: queryKeys.adminDrivers({ state: 'AVAILABLE', page: 0, size: 1 }), queryFn: () => adminApi.drivers({ state: 'AVAILABLE', page: 0, size: 1 }) })
  if ([users, applications, ready, drivers].some((query) => query.isPending)) return <Loading label="Loading operational summary…" />
  const cards = [['User support', users.data?.total, '/admin/users'], ['Pending applications', applications.data?.total, '/admin/applications'], ['Ready orders', ready.data?.total, '/admin/orders'], ['Available drivers', drivers.data?.total, '/admin/drivers']] as const
  return <><PageHeader title="Admin overview" description="Bounded operational tools backed by current platform data." /><section className="admin-overview" aria-label="Operational summary">{cards.map(([label, count, to]) => <Link key={label} to={to}><span>{label}</span><strong>{count ?? 'Unavailable'}</strong><small>Open workspace →</small></Link>)}</section><section className="admin-callout"><h2>Support, with guardrails</h2><p>Account state, restaurant review, read-only order support, Driver operations, and audit visibility are intentionally bounded. Business rules and authorization remain server-enforced.</p></section></>
}
