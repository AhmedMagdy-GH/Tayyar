import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Check, ChevronDown, Clock3, Heart, Info, MapPin, Minus, Plus, Share2, ShoppingBag, Star } from 'lucide-react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { cartApi, type AddCartItemInput } from '../api/cart'
import { discoveryApi } from '../api/discovery'
import type { Branch, Cart, Menu, MenuItem, Restaurant } from '../api/contracts'
import { safeErrorMessage } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import { AppHeader } from '../components/AppHeader'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { FoodArt } from '../components/FoodArt'
import { DemoNotice, ErrorState } from '../components/States'
import { demoBranch, demoMenu, demoPresentation, demoRestaurants } from '../demo/data'
import { useAddresses, useCart, useCurrentUser } from '../hooks/useCustomer'

function formatMoney(value: number, currency: string) {
  return `${Math.round(value).toLocaleString()} ${currency}`
}

function QuantityControl({ name, quantity, available, pending, demo, onChange }: { name: string; quantity: number; available: boolean; pending: boolean; demo: boolean; onChange: (value: number) => void }) {
  if (!available) return <span className="sold-out">Sold out</span>
  if (demo) return <button type="button" className="add-button add-button--disabled" disabled title="Demo menu items are not connected to the Cart API" aria-label={`${name} is preview-only`}><Plus size={19} /></button>
  if (!quantity) return <button type="button" className="add-button" disabled={pending} onClick={() => onChange(1)} aria-label={`Add ${name} to cart`}><Plus size={19} /></button>
  return <div className="quantity-control" aria-label={`${name} quantity ${quantity}`}><button type="button" disabled={pending} onClick={() => onChange(quantity - 1)} aria-label={`Decrease ${name} quantity`}><Minus size={16} /></button><strong>{quantity}</strong><button type="button" disabled={pending || quantity >= 99} onClick={() => onChange(quantity + 1)} aria-label={`Increase ${name} quantity`}><Plus size={16} /></button></div>
}

function MenuCard({ item, currency, quantity, pending, demo, onChange, index }: { item: MenuItem; currency: string; quantity: number; pending: boolean; demo: boolean; onChange: (value: number) => void; index: number }) {
  const art = (['burger', 'pizza', 'egyptian', 'breakfast'] as const)[index % 4]
  return <article className={`menu-card ${item.effectiveAvailability ? '' : 'menu-card--unavailable'}`}>
    <div className="menu-card__copy"><h3>{item.name}</h3><p>{item.description || 'Prepared fresh to order.'}</p><strong>{formatMoney(item.effectivePrice, currency)}</strong></div>
    <div className="menu-card__visual"><FoodArt art={art} label={`${item.name} fallback artwork`} /><QuantityControl name={item.name} quantity={quantity} available={item.effectiveAvailability} pending={pending} demo={demo} onChange={onChange} /></div>
  </article>
}

function OrderPreview({ cart, currentBranchId, authenticated }: { cart?: Cart; currentBranchId: string; authenticated: boolean }) {
  const count = cart?.items.reduce((total, item) => total + item.quantity, 0) ?? 0
  const matches = cart?.branch.id === currentBranchId
  return <aside className="order-preview"><span className="order-preview__icon"><ShoppingBag size={22} /></span><h2>{cart ? 'Your cart' : 'Your cart is empty'}</h2>{cart ? <>{!matches && <p>Items are currently from {cart.branch.restaurantName} · {cart.branch.name}.</p>}{matches && <p>{count} {count === 1 ? 'item' : 'items'} from this menu</p>}<div><span>Subtotal</span><strong>{formatMoney(cart.merchandiseSubtotal, cart.currency)}</strong></div><Link className="order-preview__link" to="/cart">Review cart</Link><small>Cart contents and prices are loaded from Tayyar.</small></> : <><p>Add something delicious to get started.</p><small>{authenticated ? 'Cart data is kept by the Tayyar server.' : 'Sign in is required before an item can be added.'}</small></>}</aside>
}

export function RestaurantPage() {
  const { restaurantId = '' } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const demo = new URLSearchParams(window.location.search).get('demo') === 'true'
  const [chosenBranchId, setChosenBranchId] = useState('')
  const [actionError, setActionError] = useState('')
  const [replacement, setReplacement] = useState<AddCartItemInput | null>(null)
  const session = useCurrentUser()
  const addresses = useAddresses(Boolean(session.data))
  const defaultAddressId = addresses.data?.items.find((address) => address.isDefault)?.id
  const cartQuery = useCart(Boolean(session.data))
  const cart = cartQuery.data

  const restaurantQuery = useQuery({ queryKey: ['restaurant', restaurantId], queryFn: () => discoveryApi.restaurant(restaurantId), enabled: !demo && Boolean(restaurantId) })
  const branchesQuery = useQuery({ queryKey: ['restaurant-branches', restaurantId, defaultAddressId], queryFn: () => discoveryApi.branches(restaurantId, { addressId: defaultAddressId }), enabled: !demo && Boolean(restaurantId) })
  const restaurant: Restaurant | undefined = demo ? (demoRestaurants.find((entry) => entry.id === restaurantId) ?? demoRestaurants[0]) : restaurantQuery.data
  const branches: Branch[] = demo ? [demoBranch] : (branchesQuery.data?.items ?? [])
  const branchId = chosenBranchId || branches[0]?.id || ''
  const branch = branches.find((entry) => entry.id === branchId)
  const menuQuery = useQuery({ queryKey: ['restaurant-menu', restaurantId, branchId], queryFn: () => discoveryApi.menu(restaurantId, branchId), enabled: !demo && Boolean(branchId) })
  const menu: Menu | undefined = demo ? demoMenu : menuQuery.data
  const presentation = demo ? demoPresentation[restaurant?.id ?? ''] : undefined

  function storeCart(next: Cart | undefined) {
    queryClient.setQueryData(queryKeys.cart, next)
    setActionError('')
  }

  const add = useMutation({
    mutationFn: (input: AddCartItemInput) => cartApi.add(input),
    onSuccess: storeCart,
    onError: (error, input) => {
      const branchConflict = error instanceof ApiError && error.status === 409 && (cart?.branch.id !== input.branchId || error.body?.message?.includes('another branch'))
      if (branchConflict && cart) setReplacement(input)
      else { setActionError(safeErrorMessage(error, 'This item could not be added.')); void cartQuery.refetch() }
    },
  })
  const replace = useMutation({
    mutationFn: (input: AddCartItemInput) => cartApi.replace({ ...input, cartId: cart!.id, cartVersion: cart!.version }),
    onSuccess: (next) => { storeCart(next); setReplacement(null) },
    onError: (error) => { setActionError(safeErrorMessage(error, 'The cart could not be replaced.')); setReplacement(null); void cartQuery.refetch() },
  })
  const update = useMutation({
    mutationFn: ({ lineId, quantity, itemVersion }: { lineId: string; quantity: number; itemVersion: number }) => quantity === 0 ? cartApi.remove(lineId, { cartId: cart!.id, cartVersion: cart!.version, itemVersion }) : cartApi.update(lineId, { quantity, cartId: cart!.id, cartVersion: cart!.version, itemVersion }),
    onSuccess: storeCart,
    onError: (error) => { setActionError(safeErrorMessage(error, 'The cart changed. It has been refreshed.')); void cartQuery.refetch() },
  })

  function changeItem(item: MenuItem, quantity: number) {
    if (demo) return
    if (!session.data) { navigate('/login', { state: { from: `${location.pathname}${location.search}` } }); return }
    const line = cart?.branch.id === branchId ? cart.items.find((entry) => entry.menuItemId === item.id) : undefined
    if (line) update.mutate({ lineId: line.id, quantity, itemVersion: line.version })
    else add.mutate({ branchId, menuItemId: item.id, quantity, cartId: cart?.id, cartVersion: cart?.version })
  }

  if (!demo && (restaurantQuery.isPending || branchesQuery.isPending)) return <div className="restaurant-loading"><span className="loader" /><p>Preparing the menu…</p></div>
  if (!demo && (restaurantQuery.isError || branchesQuery.isError)) return <div className="shell standalone-state"><ErrorState title="We couldn’t load this restaurant" onRetry={() => { void restaurantQuery.refetch(); void branchesQuery.refetch() }} /></div>
  if (!restaurant) return <div className="shell standalone-state"><ErrorState title="Restaurant not found" /></div>

  const cartCount = cart?.items.reduce((total, item) => total + item.quantity, 0) ?? 0
  return <div className="page-shell restaurant-page">
    <AppHeader demo={demo} />
    <main>
      {demo && <div className="shell notice-wrap"><DemoNotice /></div>}
      <section className="restaurant-hero"><div className="shell restaurant-hero__canvas"><Link className="back-link" to={demo ? '/?demo=true' : '/'}><ArrowLeft size={17} /> All restaurants</Link><FoodArt art={presentation?.art} label={`${restaurant.name} fallback hero artwork`} /><div className="restaurant-hero__actions"><button type="button" aria-label="Share restaurant"><Share2 size={18} /></button><button type="button" aria-label="Save restaurant; requires sign in"><Heart size={18} /></button></div></div></section>
      <section className="restaurant-summary shell" aria-labelledby="restaurant-name"><div className="restaurant-summary__main"><div className="restaurant-summary__title"><div><span className="open-badge"><Check size={13} /> {restaurant.openNow ? 'Open now' : 'Closed'}</span><h1 id="restaurant-name">{restaurant.name}</h1><p>{presentation?.cuisine ?? restaurant.description}</p></div>{presentation?.rating && <div className="large-rating"><Star size={19} fill="currentColor" /><strong>{presentation.rating}</strong><span>{presentation.ratingCount} ratings</span></div>}</div><div className="restaurant-facts"><span><Clock3 size={18} /><strong>{branch?.etaMinMinutes && branch?.etaMaxMinutes ? `${branch.etaMinMinutes}–${branch.etaMaxMinutes} min` : restaurant.minimumEtaMinutes ? `${restaurant.minimumEtaMinutes}+ min` : 'ETA at checkout'}</strong><small>Delivery time</small></span><span><ShoppingBag size={18} /><strong>{branch?.deliveryFee === 0 ? 'Free' : branch?.deliveryFee != null ? formatMoney(branch.deliveryFee, branch.currency) : restaurant.minimumDeliveryFee != null ? formatMoney(restaurant.minimumDeliveryFee, restaurant.currency) : 'At checkout'}</strong><small>Delivery fee</small></span><span><Info size={18} /><strong>{branch?.minimumOrder != null ? formatMoney(branch.minimumOrder, branch.currency) : restaurant.minimumOrder != null ? formatMoney(restaurant.minimumOrder, restaurant.currency) : 'No minimum'}</strong><small>Minimum order</small></span></div></div><label className="branch-selector"><MapPin size={19} /><span><small>Ordering from</small><select value={branchId} onChange={(event) => setChosenBranchId(event.target.value)} disabled={branches.length < 2}>{branches.length ? branches.map((entry) => <option key={entry.id} value={entry.id}>{entry.name} · {entry.city}</option>) : <option>No branch available</option>}</select></span><ChevronDown size={17} /></label></section>
      {menu && menu.categories.items.length > 0 && <nav className="menu-tabs" aria-label="Menu categories"><div className="shell">{menu.categories.items.map((category, index) => <a key={category.id} href={`#category-${category.id}`} className={index === 0 ? 'is-active' : ''}>{category.name}</a>)}</div></nav>}
      <div className="shell menu-layout"><div className="menu-content">
        {actionError && <div className="cart-alert cart-alert--error" role="alert"><Info size={18} /> {actionError}</div>}
        {!demo && menuQuery.isPending && <div className="menu-loading"><span className="loader" /><p>Loading menu…</p></div>}
        {!demo && menuQuery.isError && <ErrorState title="We couldn’t load this menu" onRetry={() => void menuQuery.refetch()} />}
        {!branch && <div className="state-card"><MapPin size={28} /><h2>No branch is available</h2><p>This restaurant has no discoverable branches yet.</p></div>}
        {menu?.categories.items.map((category, categoryIndex) => <section className="menu-section" key={category.id} id={`category-${category.id}`}><div className="menu-section__heading"><span>{String(categoryIndex + 1).padStart(2, '0')}</span><div><h2>{category.name}</h2>{category.description && <p>{category.description}</p>}</div></div><div className="menu-grid">{category.items.items.map((item, itemIndex) => { const line = cart?.branch.id === branchId ? cart.items.find((entry) => entry.menuItemId === item.id) : undefined; return <MenuCard key={item.id} item={item} currency={menu.currency} index={itemIndex + categoryIndex} quantity={line?.quantity ?? 0} pending={add.isPending || update.isPending || replace.isPending} demo={demo} onChange={(value) => changeItem(item, value)} /> })}</div></section>)}
      </div><OrderPreview cart={session.data ? cart : undefined} currentBranchId={branchId} authenticated={Boolean(session.data)} /></div>
    </main>
    {!demo && cart && cartCount > 0 && <Link className="floating-cart" to="/cart"><span><b>{cartCount}</b><span>View cart</span></span><strong>{formatMoney(cart.merchandiseSubtotal, cart.currency)}</strong></Link>}
    {replacement && cart && <ConfirmDialog title="Replace your current cart?" description={`Your cart contains items from ${cart.branch.restaurantName} · ${cart.branch.name}. Replacing it will discard those items and start a cart from this branch.`} confirmLabel="Replace cart" cancelLabel="Keep current cart" pending={replace.isPending} onCancel={() => setReplacement(null)} onConfirm={() => replace.mutate(replacement)} />}
  </div>
}
