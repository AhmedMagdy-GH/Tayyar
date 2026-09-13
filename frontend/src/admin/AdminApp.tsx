import type { ReactNode } from 'react'
import { ClipboardCheck, ClipboardList, LayoutDashboard, LogOut, ShieldCheck, Store, Truck, Users } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { NavLink, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { queryKeys } from '../api/queryKeys'
import { BrandLogo } from '../components/BrandLogo'
import { ErrorState } from '../components/States'
import { useCurrentUser } from '../hooks/useCustomer'
import { AdminOverviewPage } from './OverviewPage'
import { AdminUsersPage, AdminUserPage } from './UsersPages'
import { AdminRestaurantsPage, AdminRestaurantPage } from './RestaurantsPages'
import { AdminApplicationsPage, AdminApplicationPage } from './ApplicationsPages'
import { AdminOrdersPage, AdminOrderPage } from './OrdersPages'
import { AdminDriversPage } from './DriversPage'
import { AdminAuditPage } from './AuditPage'

const links = [['Overview', '/admin', LayoutDashboard], ['Users', '/admin/users', Users], ['Restaurants', '/admin/restaurants', Store], ['Applications', '/admin/applications', ClipboardCheck], ['Orders', '/admin/orders', ClipboardList], ['Drivers', '/admin/drivers', Truck], ['Audit log', '/admin/audit', ShieldCheck]] as const

function AdminGuard() {
  const session = useCurrentUser(); const location = useLocation()
  if (session.isPending) return <div className="session-loading" role="status"><span className="loader" />Checking Admin access…</div>
  if (session.isError) return <div className="shell standalone-state"><ErrorState title="We couldn’t check your session" onRetry={() => void session.refetch()} /></div>
  if (!session.data) return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  if (!session.data.roles.includes('ADMIN')) return <Navigate to="/" replace />
  return <AdminShell name={session.data.fullName}><Outlet /></AdminShell>
}

function AdminShell({ name, children }: { name: string; children: ReactNode }) {
  const client = useQueryClient(); const navigate = useNavigate()
  const logout = useMutation({ mutationFn: authApi.logout, onSuccess: () => { client.setQueryData(queryKeys.currentUser, null); client.removeQueries({ queryKey: ['admin'] }); navigate('/login', { replace: true }) } })
  return <div className="admin-layout"><aside className="admin-sidebar"><BrandLogo /><div className="admin-identity"><span>Administrator</span><strong>{name}</strong></div><nav aria-label="Admin operations">{links.map(([label, to, Icon]) => <NavLink key={to} to={to} end={to === '/admin'}><Icon aria-hidden="true" />{label}</NavLink>)}</nav><button className="admin-signout" onClick={() => logout.mutate()} disabled={logout.isPending}><LogOut aria-hidden="true" />Sign out</button></aside><main className="admin-main">{children}</main></div>
}

export function AdminApp() { return <Routes><Route element={<AdminGuard />}><Route index element={<AdminOverviewPage />} /><Route path="users" element={<AdminUsersPage />} /><Route path="users/:userId" element={<AdminUserPage />} /><Route path="restaurants" element={<AdminRestaurantsPage />} /><Route path="restaurants/:restaurantId" element={<AdminRestaurantPage />} /><Route path="applications" element={<AdminApplicationsPage />} /><Route path="applications/:applicationId" element={<AdminApplicationPage />} /><Route path="orders" element={<AdminOrdersPage />} /><Route path="orders/:orderId" element={<AdminOrderPage />} /><Route path="drivers" element={<AdminDriversPage />} /><Route path="audit" element={<AdminAuditPage />} /></Route><Route path="*" element={<Navigate to="/admin" replace />} /></Routes> }
