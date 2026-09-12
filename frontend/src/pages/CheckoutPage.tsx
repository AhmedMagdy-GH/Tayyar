import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, ArrowLeft, Banknote, CheckCircle2, CreditCard, MapPin, RefreshCw, ShieldCheck, ShoppingBag, Tag } from 'lucide-react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { checkoutApi } from '../api/checkout'
import { cartApi } from '../api/cart'
import type { CheckoutInput } from '../api/contracts'
import { queryKeys } from '../api/queryKeys'
import { AppHeader } from '../components/AppHeader'
import { ErrorState } from '../components/States'
import { useAddresses, useCart } from '../hooks/useCustomer'
import { formatMoney } from '../utils/money'

type Attempt = { key: string; input: CheckoutInput }

function newIdempotencyKey() {
  return crypto.randomUUID()
}

function checkoutError(error: unknown) {
  if (!(error instanceof ApiError)) return { message: 'We could not confirm whether your order was received. Retry below to safely check using the same order attempt.', uncertain: true }
  const code = error.body?.code
  if (error.status >= 500) return { message: 'We could not confirm whether your order was received. Retry below to safely check using the same order attempt.', uncertain: true }
  if (error.status === 429) {
    const wait = error.retryAfterSeconds ? ` Please wait about ${Math.ceil(error.retryAfterSeconds / 60)} minutes.` : ' Please wait before trying again.'
    return { message: `Too many checkout attempts.${wait}`, uncertain: false }
  }
  if (error.status === 401) return { message: 'Your session expired. Sign in again to continue.', uncertain: false, sessionExpired: true }
  if (error.status === 403) return { message: 'Checkout could not be authorized. Refresh the page and try again.', uncertain: false }
  if (error.status === 404) return { message: 'Your cart or selected address is no longer available. Review your checkout details.', uncertain: false, refresh: true }
  if (code === 'STALE_CART' || code === 'CONCURRENT_CHANGE') return { message: 'Your cart changed. Review the refreshed cart before placing your order.', uncertain: false, refresh: true }
  if (code === 'PRICE_RECONFIRMATION_REQUIRED') return { message: 'A price changed. Review and explicitly confirm the current prices before checkout.', uncertain: false, refresh: true }
  if (code === 'PROMOTION_INELIGIBLE') return { message: 'That promotion cannot be applied to this order. Check the code or place the order without it.', uncertain: false }
  if (code === 'ADDRESS_ZONE_REQUIRED') return { message: 'Choose a managed delivery zone for this saved address before checkout.', uncertain: false }
  if (code === 'NOT_SERVICEABLE') return { message: 'This restaurant cannot deliver to the selected address. Choose another saved address.', uncertain: false }
  if (code === 'MINIMUM_ORDER_NOT_MET') return { message: 'This order is below the delivery minimum. Add more items to continue.', uncertain: false, refresh: true }
  if (code === 'BRANCH_NOT_ACCEPTING') return { message: 'This branch is not accepting orders right now. Your cart is still saved.', uncertain: false }
  if (code === 'ITEM_UNAVAILABLE') return { message: 'An item is no longer available. Review your cart before checkout.', uncertain: false, refresh: true }
  if (code === 'PAYMENT_METHOD_UNAVAILABLE') return { message: 'That payment method is unavailable. Select Cash on Delivery.', uncertain: false }
  if (code === 'IDEMPOTENCY_MISMATCH') return { message: 'This order attempt no longer matches your selections. Refresh checkout before trying again.', uncertain: false }
  return { message: error.status === 400 ? 'Review your checkout selections and try again.' : 'Checkout could not be completed. Your cart is still saved.', uncertain: false }
}

export function CheckoutPage() {
  const cartQuery = useCart()
  const addressesQuery = useAddresses()
  const cart = cartQuery.data
  const addresses = useMemo(() => addressesQuery.data?.items ?? [], [addressesQuery.data?.items])
  const [selectedAddressId, setSelectedAddressId] = useState(() => {
    const initial = addressesQuery.data?.items ?? []
    return (initial.find((address) => address.isDefault) ?? initial[0])?.id ?? ''
  })
  const [promotionCode, setPromotionCode] = useState('')
  const [message, setMessage] = useState('')
  const [uncertain, setUncertain] = useState(false)
  const attemptRef = useRef<Attempt | null>(null)
  const submittingRef = useRef(false)
  const alertRef = useRef<HTMLDivElement>(null)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    if (!selectedAddressId && addresses.length) setSelectedAddressId((addresses.find((address) => address.isDefault) ?? addresses[0]).id)
  }, [addresses, selectedAddressId])

  useEffect(() => { if (message) alertRef.current?.focus() }, [message])

  const checkout = useMutation({
    mutationFn: ({ input, key }: Attempt) => checkoutApi.place(input, key),
    onSuccess: (summary) => {
      queryClient.setQueryData(queryKeys.cart, null)
      void queryClient.invalidateQueries({ queryKey: queryKeys.order(summary.orderId) })
      attemptRef.current = null
      navigate(`/orders/${summary.orderId}/confirmation`, { replace: true, state: { checkoutSummary: summary } })
    },
    onError: async (error) => {
      const result = checkoutError(error)
      setMessage(result.message)
      setUncertain(result.uncertain)
      if (!result.uncertain) attemptRef.current = null
      if (result.refresh) await cartQuery.refetch()
      if (result.sessionExpired) {
        queryClient.setQueryData(queryKeys.currentUser, undefined)
        navigate('/login', { replace: true, state: { from: `${location.pathname}${location.search}` } })
      }
    },
    onSettled: () => { submittingRef.current = false },
  })

  const reconfirm = useMutation({
    mutationFn: () => cartApi.reconfirm(cart!.id, cart!.version),
    onSuccess: async () => {
      setMessage('Prices confirmed. Review the current cart and place your order when ready.')
      await queryClient.invalidateQueries({ queryKey: queryKeys.cart })
    },
    onError: async () => {
      setMessage('Prices could not be confirmed. Review the refreshed cart and try again.')
      await cartQuery.refetch()
    },
  })

  const priceChanged = cart?.items.some((item) => item.priceChanged) ?? false
  const unavailable = cart?.items.some((item) => !item.currentlyAvailable) ?? false
  const canSubmit = Boolean(cart && selectedAddressId && !priceChanged && !unavailable && !checkout.isPending)

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!canSubmit || submittingRef.current || !cart) return
    submittingRef.current = true
    setMessage('')
    const normalizedPromotion = promotionCode.trim()
    const input: CheckoutInput = {
      cartId: cart.id,
      cartVersion: cart.version,
      savedAddressId: selectedAddressId,
      paymentMethod: 'CASH',
      ...(normalizedPromotion ? { promotionCode: normalizedPromotion } : {}),
    }
    const attempt = uncertain && attemptRef.current ? attemptRef.current : { key: newIdempotencyKey(), input }
    attemptRef.current = attempt
    checkout.mutate(attempt)
  }

  const loading = cartQuery.isPending || addressesQuery.isPending
  const failed = cartQuery.isError || addressesQuery.isError

  return <div className="page-shell checkout-page"><AppHeader /><main className="shell customer-page">
    <div className="customer-page__heading"><div><Link to="/cart" className="back-inline"><ArrowLeft size={16} /> Back to cart</Link><h1>Checkout</h1><p>Confirm delivery and payment. Final amounts are calculated by Tayyar.</p></div><span className="secure-checkout"><ShieldCheck size={16} /> Secure checkout</span></div>
    {loading && <div className="session-loading" role="status"><span className="loader" /> Loading checkout…</div>}
    {failed && <ErrorState title="We couldn’t load checkout" onRetry={() => { void cartQuery.refetch(); void addressesQuery.refetch() }} />}
    {!loading && !failed && !cart && <div className="empty-cart"><span><ShoppingBag size={28} /></span><h2>Your cart is empty</h2><p>Add items from a restaurant before checking out.</p><Link className="primary-button" to="/">Explore restaurants</Link></div>}
    {!loading && !failed && cart && <form id="checkout-form" className="checkout-layout" onSubmit={submit}>
      <div className="checkout-content">
        {message && <div ref={alertRef} tabIndex={-1} className={`checkout-notice ${uncertain ? 'checkout-notice--uncertain' : ''}`} role="alert"><AlertCircle size={20} /><div><strong>{uncertain ? 'Order result needs confirmation' : 'Checkout needs attention'}</strong><p>{message}</p></div></div>}
        <section className="checkout-section" aria-labelledby="delivery-heading"><header><span><MapPin size={18} /></span><div><small>Step 1</small><h2 id="delivery-heading">Delivery address</h2></div><Link to="/addresses">Manage addresses</Link></header>
          {!addresses.length ? <div className="checkout-empty"><p>You don’t have a saved delivery address yet.</p><Link className="secondary-button" to="/addresses">Add an address</Link></div> : <fieldset className="address-options" disabled={checkout.isPending || uncertain}><legend className="sr-only">Choose a saved delivery address</legend>{addresses.map((address) => <label className={`address-option ${selectedAddressId === address.id ? 'is-selected' : ''}`} key={address.id}><input type="radio" name="savedAddress" value={address.id} checked={selectedAddressId === address.id} onChange={() => setSelectedAddressId(address.id)} /><span className="address-option__marker"><CheckCircle2 size={18} /></span><span><strong>{address.profile.label} {address.isDefault && <em>Default</em>}</strong><small>{address.profile.street}, building {address.profile.building}</small><small>{address.profile.city}{address.profile.region ? `, ${address.profile.region}` : ''}</small>{!address.deliveryZoneId && <b>Delivery zone needs attention</b>}</span></label>)}</fieldset>}
        </section>
        <section className="checkout-section" aria-labelledby="items-heading"><header><span><ShoppingBag size={18} /></span><div><small>Step 2</small><h2 id="items-heading">Order items</h2></div><Link to="/cart">Edit cart</Link></header><div className="checkout-items">{cart.items.map((item) => <div key={item.id}><span><b>{item.quantity}×</b> {item.name}</span><strong>{formatMoney(item.lineSubtotal, cart.currency)}</strong></div>)}</div>{priceChanged && <div className="inline-warning" role="alert"><AlertCircle size={17} /><span>Prices changed and must be confirmed before checkout.</span><button type="button" disabled={reconfirm.isPending || unavailable} onClick={() => reconfirm.mutate()}><RefreshCw size={15} /> {reconfirm.isPending ? 'Confirming…' : 'Confirm prices'}</button></div>}</section>
        <section className="checkout-section" aria-labelledby="promotion-heading"><header><span><Tag size={18} /></span><div><small>Step 3</small><h2 id="promotion-heading">Promotion code</h2></div><em>Optional</em></header><label className="promotion-field"><span className="sr-only">Promotion code</span><input value={promotionCode} maxLength={64} disabled={checkout.isPending || uncertain} onChange={(event) => setPromotionCode(event.target.value.toUpperCase())} placeholder="Enter promotion code" aria-describedby="promotion-help" /></label><p id="promotion-help" className="field-help">Your code is not applied yet. Tayyar will validate it and confirm any discount when you place the order.</p></section>
        <section className="checkout-section" aria-labelledby="payment-heading"><header><span><Banknote size={18} /></span><div><small>Step 4</small><h2 id="payment-heading">Payment method</h2></div></header><fieldset className="payment-options"><legend className="sr-only">Choose a payment method</legend><label className="payment-option is-selected"><input type="radio" name="paymentMethod" value="CASH" checked readOnly /><Banknote size={20} /><span><strong>Cash on Delivery</strong><small>Pay in cash when your order arrives</small></span><CheckCircle2 size={18} /></label><div className="payment-option is-disabled" aria-disabled="true"><CreditCard size={20} /><span><strong>Card payment</strong><small>Coming soon — unavailable</small></span></div></fieldset></section>
      </div>
      <aside className="checkout-summary"><h2>Order summary</h2><div><span>Subtotal</span><strong>{formatMoney(cart.merchandiseSubtotal, cart.currency)}</strong></div><div><span>Discount</span><span>Confirmed after order</span></div><div><span>Delivery</span><span>Confirmed after order</span></div><div className="checkout-summary__total"><span>Total</span><strong>Calculated by Tayyar</strong></div><p>No client-calculated fees or discounts are submitted.</p><button className="place-order-button" type="submit" disabled={!canSubmit}>{checkout.isPending ? <><span className="loader" /> Placing order…</> : uncertain ? 'Retry same order attempt' : 'Place order · Cash'}</button></aside>
    </form>}
  </main>{cart && <div className="mobile-checkout-action"><button type="submit" form="checkout-form" disabled={!canSubmit} aria-label={uncertain ? 'Retry same order attempt (mobile action)' : 'Place order · Cash (mobile action)'}>{checkout.isPending ? 'Placing order…' : uncertain ? 'Retry same order attempt' : 'Place order · Cash'}</button></div>}</div>
}
