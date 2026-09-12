import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, ArrowLeft, CheckCircle2, Minus, Plus, RefreshCw, ShoppingBag, Trash2 } from 'lucide-react'
import { Link } from 'react-router-dom'
import { cartApi } from '../api/cart'
import type { Cart } from '../api/contracts'
import { safeErrorMessage } from '../api/errors'
import { formatMoney } from '../utils/money'
import { queryKeys } from '../api/queryKeys'
import { AppHeader } from '../components/AppHeader'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorState } from '../components/States'
import { useCart } from '../hooks/useCustomer'

export function CartPage() {
  const cartQuery = useCart()
  const cart = cartQuery.data
  const queryClient = useQueryClient()
  const [error, setError] = useState('')
  const [clearOpen, setClearOpen] = useState(false)
  const sync = (data: Cart | undefined) => { queryClient.setQueryData(queryKeys.cart, data); setError('') }
  const update = useMutation({
    mutationFn: ({ lineId, quantity, cart, version }: { lineId: string; quantity: number; cart: Cart; version: number }) => quantity === 0 ? cartApi.remove(lineId, { cartId: cart.id, cartVersion: cart.version, itemVersion: version }) : cartApi.update(lineId, { quantity, cartId: cart.id, cartVersion: cart.version, itemVersion: version }),
    onSuccess: sync,
    onError: (reason) => { setError(safeErrorMessage(reason, 'The cart could not be updated.')); void cartQuery.refetch() },
  })
  const clear = useMutation({ mutationFn: () => cartApi.clear(cart!.id, cart!.version), onSuccess: () => { sync(undefined); setClearOpen(false) }, onError: (reason) => { setError(safeErrorMessage(reason)); setClearOpen(false); void cartQuery.refetch() } })
  const reconfirm = useMutation({ mutationFn: () => cartApi.reconfirm(cart!.id, cart!.version), onSuccess: sync, onError: (reason) => { setError(safeErrorMessage(reason, 'Prices could not be confirmed. Review the cart and try again.')); void cartQuery.refetch() } })
  const priceChanged = cart?.items.some((item) => item.priceChanged) ?? false
  const unavailable = cart?.items.some((item) => !item.currentlyAvailable) ?? false

  return <div className="page-shell"><AppHeader /><main className="shell customer-page">
    <div className="customer-page__heading"><div><Link to="/" className="back-inline"><ArrowLeft size={16} /> Continue browsing</Link><h1>Your cart</h1><p>Server-synced items from one restaurant branch.</p></div>{cart && <button className="danger-link" type="button" onClick={() => setClearOpen(true)}><Trash2 size={16} /> Clear cart</button>}</div>
    {cartQuery.isPending && <div className="session-loading" role="status"><span className="loader" /> Loading your cart…</div>}
    {cartQuery.isError && <ErrorState title="We couldn’t load your cart" onRetry={() => void cartQuery.refetch()} />}
    {error && <div className="cart-alert cart-alert--error" role="alert"><AlertCircle size={19} /><span>{error}</span></div>}
    {!cartQuery.isPending && !cartQuery.isError && !cart && <div className="empty-cart"><span><ShoppingBag size={28} /></span><h2>Your cart is empty</h2><p>Browse a restaurant and add something delicious.</p><Link className="primary-button" to="/">Explore restaurants</Link></div>}
    {cart && <div className="cart-layout">
      <section className="cart-items" aria-labelledby="cart-branch"><header><div><small>Ordering from</small><h2 id="cart-branch">{cart.branch.restaurantName}</h2><p>{cart.branch.name} branch</p></div><span className={cart.branch.openNow ? 'status-open' : 'status-closed'}>{cart.branch.openNow ? 'Open now' : 'Closed'}</span></header>
        {priceChanged && <div className="cart-alert cart-alert--warning" role="alert"><AlertCircle size={20} /><div><strong>One or more prices changed</strong><p>Review the current prices below, then confirm them explicitly before checkout.</p></div></div>}
        {unavailable && <div className="cart-alert cart-alert--error" role="alert"><AlertCircle size={20} /><div><strong>An item is unavailable</strong><p>Remove unavailable items before confirming prices.</p></div></div>}
        <div className="cart-lines">{cart.items.map((line) => <article className="cart-line" key={line.id}><div className="cart-line__art" aria-hidden="true">🍽️</div><div className="cart-line__copy"><h3>{line.name}</h3>{line.priceChanged ? <p><del>{formatMoney(line.acknowledgedUnitPrice, cart.currency)}</del> <strong>{formatMoney(line.currentUnitPrice, cart.currency)}</strong> each</p> : <p>{formatMoney(line.currentUnitPrice, cart.currency)} each</p>}{!line.currentlyAvailable && <span>Currently unavailable</span>}</div><div className="cart-line__actions"><div className="quantity-control quantity-control--static"><button type="button" disabled={update.isPending} onClick={() => update.mutate({ lineId: line.id, quantity: line.quantity - 1, cart, version: line.version })} aria-label={line.quantity === 1 ? `Remove ${line.name}` : `Decrease ${line.name} quantity`}><Minus size={15} /></button><strong>{line.quantity}</strong><button type="button" disabled={update.isPending || line.quantity >= 99} onClick={() => update.mutate({ lineId: line.id, quantity: line.quantity + 1, cart, version: line.version })} aria-label={`Increase ${line.name} quantity`}><Plus size={15} /></button></div><strong>{formatMoney(line.lineSubtotal, cart.currency)}</strong></div></article>)}</div>
      </section>
      <aside className="cart-summary"><h2>Cart summary</h2><div><span>Items subtotal</span><strong>{formatMoney(cart.merchandiseSubtotal, cart.currency)}</strong></div>{priceChanged && <button className="reconfirm-button" type="button" disabled={reconfirm.isPending || unavailable} onClick={() => reconfirm.mutate()}><RefreshCw size={16} /> {reconfirm.isPending ? 'Confirming…' : 'Confirm current prices'}</button>}{!priceChanged && <p className="prices-current"><CheckCircle2 size={16} /> Prices are current</p>}{priceChanged || unavailable ? <button className="checkout-disabled" type="button" disabled>Review cart before checkout</button> : <Link className="order-preview__link" to="/checkout">Continue to checkout</Link>}<small>Delivery fees and promotions are calculated during checkout.</small></aside>
    </div>}
  </main>{clearOpen && <ConfirmDialog title="Clear your cart?" description="This removes every item from the active cart. This action cannot be undone." confirmLabel="Clear cart" pending={clear.isPending} onCancel={() => setClearOpen(false)} onConfirm={() => clear.mutate()} />}</div>
}
