import { useState } from 'react'
import { Bell, Heart, LogOut, Mail, MapPin, Phone, ReceiptText, ShieldCheck, UserRound } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { authApi } from '../api/auth'
import { safeErrorMessage } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import { AppHeader } from '../components/AppHeader'
import { MobileNav } from '../components/MobileNav'
import { useCurrentUser } from '../hooks/useCustomer'

const links = [
  { to: '/orders', label: 'Orders', detail: 'Track current orders and revisit past ones.', icon: ReceiptText },
  { to: '/addresses', label: 'Saved addresses', detail: 'Manage the delivery addresses already supported by Tayyar.', icon: MapPin },
  { to: '/favorites', label: 'Favorites', detail: 'Return to restaurants you have saved.', icon: Heart },
  { to: '/notifications', label: 'Notifications', detail: 'Read order and account updates.', icon: Bell },
]

export function AccountPage() {
  const session = useCurrentUser()
  const client = useQueryClient()
  const navigate = useNavigate()
  const [message, setMessage] = useState('')
  const logout = useMutation({
    mutationFn: authApi.logout,
    onSuccess: () => {
      client.setQueryData(queryKeys.currentUser, null)
      client.removeQueries({ queryKey: ['customer'] })
      navigate('/', { replace: true })
    },
    onError: (error) => setMessage(safeErrorMessage(error, 'Sign out could not be completed.')),
  })
  const user = session.data
  if (!user) return null

  return <div className="page-shell"><AppHeader /><main className="shell customer-page account-page"><div className="customer-page__heading"><div><span className="eyebrow">Your Tayyar account</span><h1>Account</h1><p>Profile details and shortcuts for your customer experience.</p></div></div>
    {message && <p className="form-error" role="alert">{message}</p>}
    <div className="account-layout"><section className="profile-overview" aria-labelledby="profile-heading"><header><span><UserRound size={25} /></span><div><h2 id="profile-heading">{user.fullName}</h2><p>Customer account overview</p></div></header><dl><div><dt><Mail size={16} /> Email</dt><dd>{user.email}</dd></div><div><dt><Phone size={16} /> Phone</dt><dd>{user.phone || 'Not provided'}</dd></div><div><dt><ShieldCheck size={16} /> Account status</dt><dd><span className={`account-status account-status--${user.status.toLowerCase()}`}>{user.status === 'ACTIVE' ? 'Active' : user.status}</span></dd></div><div><dt><ShieldCheck size={16} /> Email status</dt><dd>{user.emailVerified ? 'Verified' : 'Not verified'}</dd></div></dl><aside className="account-readonly" role="note"><strong>Profile editing is not available yet</strong><p>Tayyar currently provides a read-only account API. Your details are shown exactly as stored; there is no unsupported save action on this page.</p></aside><button className="danger-outline account-logout" type="button" disabled={logout.isPending} onClick={() => logout.mutate()}><LogOut size={17} /> {logout.isPending ? 'Signing out…' : 'Sign out'}</button></section>
      <section className="account-hub" aria-labelledby="account-links-heading"><h2 id="account-links-heading">Customer shortcuts</h2><div>{links.map(({ to, label, detail, icon: Icon }) => <Link key={to} to={to}><span><Icon size={21} /></span><div><strong>{label}</strong><small>{detail}</small></div></Link>)}</div></section></div>
  </main><MobileNav /></div>
}
