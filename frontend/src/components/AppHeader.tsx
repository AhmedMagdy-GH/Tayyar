import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Heart, LogOut, MapPin, Menu, Search, ShoppingBag, UserRound } from 'lucide-react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { addressApi } from '../api/addresses'
import { authApi } from '../api/auth'
import { queryKeys } from '../api/queryKeys'
import type { Zone } from '../api/contracts'
import { useAddresses, useCart, useCurrentUser } from '../hooks/useCustomer'
import { BrandLogo } from './BrandLogo'

type Props = {
  demo?: boolean
  zones?: Zone[]
  zoneId?: string
  onZoneChange?: (id: string) => void
  search?: string
  onSearch?: (value: string) => void
  searchPlaceholder?: string
}

export function AppHeader({ demo = false, zones = [], zoneId, onZoneChange, search, onSearch, searchPlaceholder = 'Search restaurants, dishes, or cuisines' }: Props) {
  const session = useCurrentUser()
  const authenticated = Boolean(session.data)
  const addresses = useAddresses(authenticated)
  const cart = useCart(authenticated)
  const defaultAddress = addresses.data?.items.find((address) => address.isDefault)
  const [menuOpen, setMenuOpen] = useState(false)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const returnPath = `${location.pathname}${location.search}`
  const defaultMutation = useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) => addressApi.setDefault(id, version),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.addresses }),
  })
  const logout = useMutation({
    mutationFn: authApi.logout,
    onSuccess: () => {
      queryClient.setQueryData(queryKeys.currentUser, null)
      queryClient.removeQueries({ queryKey: ['customer'] })
      setMenuOpen(false)
      navigate('/', { replace: true })
    },
  })
  const cartCount = cart.data?.items.reduce((total, item) => total + item.quantity, 0) ?? 0

  return <header className="app-header">
    <div className="shell app-header__inner">
      <BrandLogo demo={demo} />
      <div className="address-picker"><MapPin size={18} aria-hidden="true" />
        {authenticated ? <label><span>Deliver to</span><select aria-label="Saved delivery address" value={defaultAddress?.id ?? ''} onChange={(event) => { const selected = addresses.data?.items.find((entry) => entry.id === event.target.value); if (selected && !selected.isDefault) defaultMutation.mutate({ id: selected.id, version: selected.version }) }} disabled={addresses.isPending || defaultMutation.isPending}><option value="">{addresses.isPending ? 'Loading addresses…' : 'Choose saved address'}</option>{addresses.data?.items.map((address) => <option key={address.id} value={address.id}>{address.profile.label} · {address.profile.city}</option>)}</select></label>
          : <label><span>Deliver to</span><select aria-label="Delivery area" value={zoneId ?? ''} onChange={(event) => onZoneChange?.(event.target.value)} disabled={!onZoneChange}><option value="">Choose delivery area</option>{zones.map((zone) => <option key={zone.id} value={zone.id}>{zone.name}, {zone.cityName}</option>)}</select></label>}
      </div>
      {onSearch && <label className="header-search"><Search size={18} aria-hidden="true" /><span className="sr-only">Search restaurants</span><input value={search} onChange={(event) => onSearch(event.target.value)} placeholder={searchPlaceholder} /></label>}
      <nav className="header-actions" aria-label="Account shortcuts">
        {session.isPending ? <span className="header-session-loading" role="status">Checking session…</span> : session.isError ? <button className="header-session-error" type="button" onClick={() => void session.refetch()}>Session unavailable · retry</button> : authenticated ? <>
          <button className="icon-button header-actions__secondary" type="button" aria-label="Favorites, coming in a later phase" title="Favorites coming soon"><Heart size={19} /></button>
          <button className="header-orders" type="button" title="Orders coming in a later phase"><ShoppingBag size={18} /> Orders</button>
          <Link className="cart-pill" to="/cart" aria-label={`Cart with ${cartCount} items`}><ShoppingBag size={18} /> Cart{cartCount ? ` (${cartCount})` : ''}</Link>
          <div className="account-menu-wrap"><button className="avatar-button" type="button" aria-label="Open account menu" aria-expanded={menuOpen} onClick={() => setMenuOpen((value) => !value)}>{session.data?.fullName.slice(0, 1).toUpperCase() ?? <UserRound size={17} />}</button>{menuOpen && <div className="account-menu" role="menu"><div><strong>{session.data?.fullName}</strong><span>{session.data?.email}</span></div><Link role="menuitem" to="/addresses" onClick={() => setMenuOpen(false)}><MapPin size={16} /> Saved addresses</Link><button role="menuitem" type="button" onClick={() => logout.mutate()} disabled={logout.isPending}><LogOut size={16} /> {logout.isPending ? 'Signing out…' : 'Sign out'}</button></div>}</div>
        </> : <div className="guest-actions"><Link to="/login" state={{ from: returnPath }}>Log in</Link><Link to="/register" state={{ from: returnPath }}>Create account</Link></div>}
        <button className="icon-button mobile-menu" type="button" aria-label="Open account navigation" aria-expanded={menuOpen} onClick={() => setMenuOpen((value) => !value)}><Menu size={21} /></button>
      </nav>
      {menuOpen && <div className="mobile-account-menu" role="menu">{authenticated ? <><div><strong>{session.data?.fullName}</strong><span>{session.data?.email}</span></div><Link role="menuitem" to="/cart"><ShoppingBag size={16} /> Cart{cartCount ? ` (${cartCount})` : ''}</Link><Link role="menuitem" to="/addresses"><MapPin size={16} /> Saved addresses</Link><button role="menuitem" type="button" onClick={() => logout.mutate()}><LogOut size={16} /> Sign out</button></> : <><Link role="menuitem" to="/login" state={{ from: returnPath }}>Log in</Link><Link role="menuitem" to="/register" state={{ from: returnPath }}>Create account</Link></>}</div>}
    </div>
    {onSearch && <div className="shell mobile-search-wrap"><label className="header-search mobile-search"><Search size={18} aria-hidden="true" /><span className="sr-only">Search restaurants</span><input value={search} onChange={(event) => onSearch(event.target.value)} placeholder="Search restaurants or food" /></label></div>}
  </header>
}
