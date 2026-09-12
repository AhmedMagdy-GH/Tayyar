import { Bell, Home, ShoppingBag, UserRound } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'

export function MobileNav({ demo = false }: { demo?: boolean }) {
  const { pathname } = useLocation()
  const link = (to: string, label: string, icon: ReactNode) => <Link to={to} className={pathname === to || (to !== '/' && pathname.startsWith(to)) ? 'is-active' : ''} aria-current={pathname === to || (to !== '/' && pathname.startsWith(to)) ? 'page' : undefined}>{icon}<span>{label}</span></Link>
  return <nav className="mobile-nav" aria-label="Primary navigation">{link(demo ? '/?demo=true' : '/', 'Home', <Home size={20} />)}{link('/orders', 'Orders', <ShoppingBag size={20} />)}{link('/notifications', 'Alerts', <Bell size={20} />)}{link('/account', 'Account', <UserRound size={20} />)}</nav>
}
