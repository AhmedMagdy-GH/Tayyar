import { useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Banknote, Check, MapPin, ShoppingBag } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'
import type { CheckoutSummary } from '../api/contracts'
import { orderApi } from '../api/orders'
import { queryKeys } from '../api/queryKeys'
import { AppHeader } from '../components/AppHeader'
import { ErrorState } from '../components/States'
import { formatMoney } from '../utils/money'

export function OrderConfirmationPage() {
  const { orderId = '' } = useParams()
  const location = useLocation()
  const summary = (location.state as { checkoutSummary?: CheckoutSummary } | null)?.checkoutSummary
  const headingRef = useRef<HTMLHeadingElement>(null)
  const details = useQuery({ queryKey: queryKeys.order(orderId), queryFn: () => orderApi.details(orderId), enabled: Boolean(orderId), retry: false })
  useEffect(() => { headingRef.current?.focus() }, [])
  const order = details.data?.order
  const currency = order?.currency ?? summary?.currency ?? 'EGP'
  const money = {
    subtotal: order?.merchandiseSubtotal ?? summary?.merchandiseSubtotal,
    discount: order?.discountTotal ?? summary?.discountTotal,
    delivery: order?.deliveryFee ?? summary?.deliveryFee,
    total: order?.finalTotal ?? summary?.finalTotal,
  }
  if (!summary && details.isPending) return <div className="page-shell"><AppHeader /><main className="shell customer-page"><div className="session-loading" role="status"><span className="loader" /> Loading order confirmation…</div></main></div>
  if (!summary && details.isError) return <div className="page-shell"><AppHeader /><main className="shell standalone-state"><ErrorState title="We couldn’t load this order confirmation" onRetry={() => void details.refetch()} /></main></div>
  return <div className="page-shell confirmation-page"><AppHeader /><main className="shell confirmation-shell"><section className="confirmation-hero"><span><Check size={34} /></span><p>Order confirmed</p><h1 ref={headingRef} tabIndex={-1}>Thanks — we’ve received your order.</h1><small>Order reference</small><code>{orderId}</code><div className="confirmation-status"><b>{order?.status ?? summary?.orderStatus ?? 'PLACED'}</b><span>Cash payment · {details.data?.payment?.status ?? summary?.paymentStatus ?? 'PENDING'}</span></div></section>
    <div className="confirmation-grid"><section className="confirmation-card"><header><ShoppingBag size={19} /><h2>Order details</h2></header>{order && <p className="confirmation-place"><strong>{order.restaurant.name}</strong><span>{order.branch.name} branch</span></p>}{details.data?.items.map((item) => <div className="confirmation-item" key={item.menuItemId}><span>{item.quantity}× {item.name}</span><strong>{formatMoney(item.lineSubtotal, currency)}</strong></div>)}{details.isPending && <p className="field-help" role="status">Loading item details…</p>}{details.isError && summary && <p className="field-help">The order was placed, but extra details could not be loaded right now.</p>}</section>
      <aside className="confirmation-card confirmation-totals"><header><Banknote size={19} /><h2>Confirmed total</h2></header>{money.subtotal != null && <div><span>Subtotal</span><strong>{formatMoney(money.subtotal, currency)}</strong></div>}{money.discount != null && <div><span>Discount</span><strong>− {formatMoney(money.discount, currency)}</strong></div>}{money.delivery != null && <div><span>Delivery</span><strong>{formatMoney(money.delivery, currency)}</strong></div>}{money.total != null && <div className="confirmation-total"><span>Total</span><strong>{formatMoney(money.total, currency)}</strong></div>}<p><Banknote size={15} /> Cash on Delivery</p></aside>
      {details.data?.deliveryAddress && <section className="confirmation-card confirmation-address"><header><MapPin size={19} /><h2>Delivering to</h2></header><strong>{details.data.deliveryAddress.label}</strong><p>{details.data.deliveryAddress.street}, building {details.data.deliveryAddress.building}<br />{details.data.deliveryAddress.city}</p></section>}
    </div><Link to="/" className="continue-browsing">Continue browsing <ArrowRight size={17} /></Link></main></div>
}
