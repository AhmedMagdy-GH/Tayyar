import { Heart, MapPin, Menu, Search, ShoppingBag, UserRound } from 'lucide-react'
import { BrandLogo } from './BrandLogo'
import type { Zone } from '../api/contracts'

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
  return (
    <header className="app-header">
      <div className="shell app-header__inner">
        <BrandLogo demo={demo} />
        <div className="address-picker">
          <MapPin size={18} aria-hidden="true" />
          <label>
            <span>Deliver to</span>
            <select aria-label="Delivery area" value={zoneId ?? ''} onChange={(event) => onZoneChange?.(event.target.value)} disabled={!onZoneChange}>
              <option value="">Choose delivery area</option>
              {zones.map((zone) => <option key={zone.id} value={zone.id}>{zone.name}, {zone.cityName}</option>)}
            </select>
          </label>
        </div>
        {onSearch && (
          <label className="header-search">
            <Search size={18} aria-hidden="true" />
            <span className="sr-only">Search restaurants</span>
            <input value={search} onChange={(event) => onSearch(event.target.value)} placeholder={searchPlaceholder} />
          </label>
        )}
        <nav className="header-actions" aria-label="Account shortcuts">
          <button className="icon-button header-actions__secondary" type="button" aria-label="Favorites, coming in a later phase" title="Favorites coming soon"><Heart size={19} /></button>
          <button className="header-orders" type="button" title="Orders coming soon"><ShoppingBag size={18} /> Orders</button>
          <button className="cart-pill" type="button" title="Cart integration follows authentication"><ShoppingBag size={18} /> Cart</button>
          <button className="avatar-button" type="button" aria-label="Account, coming in a later phase" title="Account coming soon"><UserRound size={17} /></button>
          <button className="icon-button mobile-menu" type="button" aria-label="Open navigation"><Menu size={21} /></button>
        </nav>
      </div>
      {onSearch && (
        <div className="shell mobile-search-wrap">
          <label className="header-search mobile-search">
            <Search size={18} aria-hidden="true" />
            <span className="sr-only">Search restaurants</span>
            <input value={search} onChange={(event) => onSearch(event.target.value)} placeholder="Search restaurants or food" />
          </label>
        </div>
      )}
    </header>
  )
}
