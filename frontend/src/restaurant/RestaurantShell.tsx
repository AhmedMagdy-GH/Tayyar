import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { Building2, ClipboardList, Clock3, LayoutDashboard, MapPinned, MenuSquare, Settings2, Users } from 'lucide-react'
import { BrandLogo } from '../components/BrandLogo'
import { useRestaurantContext } from './RestaurantContext'

const ownerLinks = [
  ['Overview', '/restaurant/overview', LayoutDashboard], ['Orders', '/restaurant/orders', ClipboardList],
  ['Menu', '/restaurant/menu', MenuSquare], ['Branches', '/restaurant/branches', Building2],
  ['Hours', '/restaurant/hours', Clock3], ['Delivery', '/restaurant/delivery', MapPinned],
  ['Staff', '/restaurant/staff', Users], ['Settings', '/restaurant/settings', Settings2],
] as const

export function RestaurantShell({ children }: { children: ReactNode }) {
  const { contexts, selected, selectRestaurant } = useRestaurantContext()
  const links = selected.role === 'OWNER' ? ownerLinks : ownerLinks.filter(([name]) => name === 'Overview' || name === 'Orders')
  return <div className="ops-layout">
    <aside className="ops-sidebar">
      <div className="ops-brand"><BrandLogo /></div>
      <div className="ops-context">
        <label htmlFor="restaurant-context">Restaurant</label>
        {contexts.length > 1 ? <select id="restaurant-context" value={selected.restaurantId} onChange={(event) => selectRestaurant(event.target.value)}>{contexts.map((item) => <option key={item.restaurantId} value={item.restaurantId}>{item.restaurantName}</option>)}</select> : <strong>{selected.restaurantName}</strong>}
        <span className="role-chip">{selected.role === 'OWNER' ? 'Owner' : 'Staff'} · {selected.restaurantStatus}</span>
      </div>
      <nav className="ops-nav" aria-label="Restaurant operations">{links.map(([name, to, Icon]) => <NavLink key={to} to={to}><Icon aria-hidden="true" size={19} /><span>{name}</span></NavLink>)}</nav>
      <NavLink className="customer-return" to="/">← Customer app</NavLink>
    </aside>
    <main className="ops-main">{children}</main>
  </div>
}
