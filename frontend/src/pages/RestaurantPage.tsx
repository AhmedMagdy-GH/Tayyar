import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Check, ChevronDown, Clock3, Heart, Info, MapPin, Minus, Plus, Share2, ShoppingBag, Star } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { discoveryApi } from '../api/discovery'
import type { Branch, Menu, MenuItem, Restaurant } from '../api/contracts'
import { AppHeader } from '../components/AppHeader'
import { FoodArt } from '../components/FoodArt'
import { DemoNotice, ErrorState } from '../components/States'
import { demoBranch, demoMenu, demoPresentation, demoRestaurants } from '../demo/data'

function formatMoney(value: number, currency: string) {
  return `${Math.round(value).toLocaleString()} ${currency}`
}

function QuantityControl({ quantity, available, onChange }: { quantity: number; available: boolean; onChange: (value: number) => void }) {
  if (!available) return <span className="sold-out">Sold out</span>
  if (!quantity) return <button type="button" className="add-button" onClick={() => onChange(1)} aria-label="Add item"><Plus size={19} /></button>
  return <div className="quantity-control" aria-label={`Quantity ${quantity}`}><button type="button" onClick={() => onChange(quantity - 1)} aria-label="Decrease quantity"><Minus size={16} /></button><strong>{quantity}</strong><button type="button" onClick={() => onChange(quantity + 1)} aria-label="Increase quantity"><Plus size={16} /></button></div>
}

function MenuCard({ item, currency, quantity, onChange, index }: { item: MenuItem; currency: string; quantity: number; onChange: (value: number) => void; index: number }) {
  const art = (['burger', 'pizza', 'egyptian', 'breakfast'] as const)[index % 4]
  return <article className={`menu-card ${item.effectiveAvailability ? '' : 'menu-card--unavailable'}`}>
    <div className="menu-card__copy"><h3>{item.name}</h3><p>{item.description || 'Prepared fresh to order.'}</p><strong>{formatMoney(item.effectivePrice, currency)}</strong></div>
    <div className="menu-card__visual"><FoodArt art={art} label={`${item.name} fallback artwork`} /><QuantityControl quantity={quantity} available={item.effectiveAvailability} onChange={onChange} /></div>
  </article>
}

function OrderPreview({ count, subtotal, currency }: { count: number; subtotal: number; currency: string }) {
  return <aside className="order-preview"><span className="order-preview__icon"><ShoppingBag size={22} /></span><h2>Your basket</h2>{count ? <><p>{count} {count === 1 ? 'item' : 'items'} ready to review</p><div><span>Subtotal</span><strong>{formatMoney(subtotal, currency)}</strong></div><button type="button" disabled>Checkout comes next</button><small>Basket changes are a local Phase 1 preview.</small></> : <><p>Your basket is waiting for something delicious.</p><small>Add an item to preview the cart state.</small></>}</aside>
}

export function RestaurantPage() {
  const { restaurantId = '' } = useParams()
  const demo = new URLSearchParams(window.location.search).get('demo') === 'true'
  const [chosenBranchId, setChosenBranchId] = useState('')
  const [quantities, setQuantities] = useState<Record<string, number>>({})

  const restaurantQuery = useQuery({ queryKey: ['restaurant', restaurantId], queryFn: () => discoveryApi.restaurant(restaurantId), enabled: !demo && Boolean(restaurantId) })
  const branchesQuery = useQuery({ queryKey: ['restaurant-branches', restaurantId], queryFn: () => discoveryApi.branches(restaurantId), enabled: !demo && Boolean(restaurantId) })
  const restaurant: Restaurant | undefined = demo ? (demoRestaurants.find((entry) => entry.id === restaurantId) ?? demoRestaurants[0]) : restaurantQuery.data
  const branches: Branch[] = demo ? [demoBranch] : (branchesQuery.data?.items ?? [])
  const branchId = chosenBranchId || branches[0]?.id || ''
  const branch = branches.find((entry) => entry.id === branchId)
  const menuQuery = useQuery({ queryKey: ['restaurant-menu', restaurantId, branchId], queryFn: () => discoveryApi.menu(restaurantId, branchId), enabled: !demo && Boolean(branchId) })
  const menu: Menu | undefined = demo ? demoMenu : menuQuery.data
  const presentation = demo ? demoPresentation[restaurant?.id ?? ''] : undefined

  const totals = useMemo(() => {
    const allItems = menu?.categories.items.flatMap((category) => category.items.items) ?? []
    return allItems.reduce((result, item) => {
      const quantity = quantities[item.id] ?? 0
      return { count: result.count + quantity, subtotal: result.subtotal + quantity * item.effectivePrice }
    }, { count: 0, subtotal: 0 })
  }, [menu, quantities])

  if (!demo && (restaurantQuery.isPending || branchesQuery.isPending)) return <div className="restaurant-loading"><span className="loader" /><p>Preparing the menu…</p></div>
  if (!demo && (restaurantQuery.isError || branchesQuery.isError)) return <div className="shell standalone-state"><ErrorState title="We couldn’t load this restaurant" onRetry={() => { void restaurantQuery.refetch(); void branchesQuery.refetch() }} /></div>
  if (!restaurant) return <div className="shell standalone-state"><ErrorState title="Restaurant not found" /></div>

  return <div className="page-shell restaurant-page">
    <AppHeader demo={demo} />
    <main>
      {demo && <div className="shell notice-wrap"><DemoNotice /></div>}
      <section className="restaurant-hero">
        <div className="shell restaurant-hero__canvas">
          <Link className="back-link" to={demo ? '/?demo=true' : '/'}><ArrowLeft size={17} /> All restaurants</Link>
          <FoodArt art={presentation?.art} label={`${restaurant.name} fallback hero artwork`} />
          <div className="restaurant-hero__actions"><button type="button" aria-label="Share restaurant"><Share2 size={18} /></button><button type="button" aria-label="Save restaurant; requires sign in"><Heart size={18} /></button></div>
        </div>
      </section>

      <section className="restaurant-summary shell" aria-labelledby="restaurant-name">
        <div className="restaurant-summary__main">
          <div className="restaurant-summary__title"><div><span className="open-badge"><Check size={13} /> {restaurant.openNow ? 'Open now' : 'Closed'}</span><h1 id="restaurant-name">{restaurant.name}</h1><p>{presentation?.cuisine ?? restaurant.description}</p></div>{presentation?.rating && <div className="large-rating"><Star size={19} fill="currentColor" /><strong>{presentation.rating}</strong><span>{presentation.ratingCount} ratings</span></div>}</div>
          <div className="restaurant-facts">
            <span><Clock3 size={18} /><strong>{branch?.etaMinMinutes && branch?.etaMaxMinutes ? `${branch.etaMinMinutes}–${branch.etaMaxMinutes} min` : restaurant.minimumEtaMinutes ? `${restaurant.minimumEtaMinutes}+ min` : 'ETA at checkout'}</strong><small>Delivery time</small></span>
            <span><ShoppingBag size={18} /><strong>{branch?.deliveryFee === 0 ? 'Free' : branch?.deliveryFee != null ? formatMoney(branch.deliveryFee, branch.currency) : restaurant.minimumDeliveryFee != null ? formatMoney(restaurant.minimumDeliveryFee, restaurant.currency) : 'At checkout'}</strong><small>Delivery fee</small></span>
            <span><Info size={18} /><strong>{branch?.minimumOrder != null ? formatMoney(branch.minimumOrder, branch.currency) : restaurant.minimumOrder != null ? formatMoney(restaurant.minimumOrder, restaurant.currency) : 'No minimum'}</strong><small>Minimum order</small></span>
          </div>
        </div>
        <label className="branch-selector"><MapPin size={19} /><span><small>Ordering from</small><select value={branchId} onChange={(event) => setChosenBranchId(event.target.value)} disabled={branches.length < 2}>{branches.length ? branches.map((entry) => <option key={entry.id} value={entry.id}>{entry.name} · {entry.city}</option>) : <option>No branch available</option>}</select></span><ChevronDown size={17} /></label>
      </section>

      {menu && menu.categories.items.length > 0 && <nav className="menu-tabs" aria-label="Menu categories"><div className="shell">{menu.categories.items.map((category, index) => <a key={category.id} href={`#category-${category.id}`} className={index === 0 ? 'is-active' : ''}>{category.name}</a>)}</div></nav>}

      <div className="shell menu-layout">
        <div className="menu-content">
          {!demo && menuQuery.isPending && <div className="menu-loading"><span className="loader" /><p>Loading menu…</p></div>}
          {!demo && menuQuery.isError && <ErrorState title="We couldn’t load this menu" onRetry={() => void menuQuery.refetch()} />}
          {!branch && <div className="state-card"><MapPin size={28} /><h2>No branch is available</h2><p>This restaurant has no discoverable branches yet.</p></div>}
          {menu?.categories.items.map((category, categoryIndex) => <section className="menu-section" key={category.id} id={`category-${category.id}`}><div className="menu-section__heading"><span>{String(categoryIndex + 1).padStart(2, '0')}</span><div><h2>{category.name}</h2>{category.description && <p>{category.description}</p>}</div></div><div className="menu-grid">{category.items.items.map((item, itemIndex) => <MenuCard key={item.id} item={item} currency={menu.currency} index={itemIndex + categoryIndex} quantity={quantities[item.id] ?? 0} onChange={(value) => setQuantities((current) => ({ ...current, [item.id]: Math.max(0, Math.min(99, value)) }))} />)}</div></section>)}
        </div>
        <OrderPreview count={totals.count} subtotal={totals.subtotal} currency={menu?.currency ?? restaurant.currency} />
      </div>
    </main>
    {totals.count > 0 && <div className="floating-cart"><span><b>{totals.count}</b><span>View basket</span></span><strong>{formatMoney(totals.subtotal, menu?.currency ?? restaurant.currency)}</strong></div>}
  </div>
}
