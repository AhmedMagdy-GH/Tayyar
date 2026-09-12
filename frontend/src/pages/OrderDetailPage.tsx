import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Banknote, MapPin, ReceiptText, RotateCw } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { safeErrorMessage } from '../api/errors'
import { orderApi } from '../api/orders'
import { queryKeys } from '../api/queryKeys'
import { AppHeader } from '../components/AppHeader'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { OrderTimeline, StatusBadge } from '../components/OrderStatus'
import { ReviewPanel } from '../components/ReviewPanel'
import { formatMoney } from '../utils/money'
import { isTerminalOrderStatus, statusLabel } from '../utils/orderStatus'

const date = (value: string) => new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))

export function OrderDetailPage() {
  const { orderId = '' } = useParams()
  const client = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const reason = 'Customer requested cancellation'
  const [message, setMessage] = useState('')
  const detail = useQuery({ queryKey: queryKeys.order(orderId), queryFn: () => orderApi.details(orderId), enabled: Boolean(orderId), retry: false, refetchInterval: (query) => query.state.data && !isTerminalOrderStatus(query.state.data.order.status) ? 20_000 : false })
  const cancel = useMutation({ mutationFn: () => orderApi.cancel(orderId, detail.data!.order.version, reason), onSuccess: async () => { setConfirming(false); setMessage('Your order was cancelled.'); await client.invalidateQueries({ queryKey: queryKeys.order(orderId) }); await client.invalidateQueries({ queryKey: ['customer', 'orders', 'list'] }) }, onError: async (error) => { setConfirming(false); if (error instanceof ApiError && error.status === 409) { await detail.refetch(); setMessage('The order changed before cancellation could complete. We reloaded its current status.') } else setMessage(safeErrorMessage(error, 'The order could not be cancelled.')) } })
  if (detail.isPending) return <div className="page-shell"><AppHeader /><main className="shell customer-page"><div className="session-loading" role="status"><span className="loader" /> Loading order…</div></main></div>
  if (detail.isError || !detail.data) return <div className="page-shell"><AppHeader /><main className="shell standalone-state"><div className="state-card" role="alert"><ReceiptText size={30} /><h1>We couldn’t load this order</h1><p>{safeErrorMessage(detail.error)}</p><button className="primary-button" type="button" onClick={() => void detail.refetch()}>Try again</button></div></main></div>
  const { order, items, deliveryAddress, payment, history } = detail.data
  const address = [deliveryAddress.street, `building ${deliveryAddress.building}`, deliveryAddress.floor && `floor ${deliveryAddress.floor}`, deliveryAddress.apartment && `apartment ${deliveryAddress.apartment}`, deliveryAddress.city].filter(Boolean).join(', ')
  return <div className="page-shell"><AppHeader /><main className="shell customer-page order-detail"><Link className="back-inline" to="/orders"><ArrowLeft size={16} /> All orders</Link><header className="order-detail__header"><div><span className="eyebrow">Order {order.id.slice(0, 8).toUpperCase()}</span><h1>{order.restaurant.name}</h1><p>{order.branch.name} · placed {date(order.createdAt)}</p></div><div><StatusBadge status={order.status} />{order.status === 'PLACED' && <button className="danger-outline" type="button" onClick={() => setConfirming(true)}>Cancel order</button>}</div></header>
    {message && <p className={cancel.isError ? 'form-error' : 'success-banner'} role="status" aria-live="polite">{message}</p>}
    <div className="order-detail__grid"><section className="order-detail-card timeline-card"><header><RotateCw size={19} /><div><h2>Order progress</h2><p>Status tracking refreshes about every 20 seconds while active. It is not live GPS tracking.</p></div></header><OrderTimeline status={order.status} history={history} /><div className="history-list"><h3>Status history</h3>{history.map((event, index) => <p key={`${event.occurredAt}-${index}`}><span>{statusLabel(event.newStatus)}</span><time dateTime={event.occurredAt}>{date(event.occurredAt)}</time>{event.reason && <small>{event.reason}</small>}</p>)}</div></section>
      <section className="order-detail-card items-card"><header><ReceiptText size={19} /><h2>Items</h2></header>{items.map((item, index) => <div className="order-line" key={`${item.menuItemId}-${index}`}><span><b>{item.quantity}×</b> {item.name}<small>{formatMoney(item.unitPrice, order.currency)} each</small></span><strong>{formatMoney(item.lineSubtotal, order.currency)}</strong></div>)}<div className="order-money"><p><span>Merchandise subtotal</span><strong>{formatMoney(order.merchandiseSubtotal, order.currency)}</strong></p>{order.discountTotal > 0 && <p><span>Discount</span><strong>− {formatMoney(order.discountTotal, order.currency)}</strong></p>}<p><span>Delivery fee</span><strong>{formatMoney(order.deliveryFee, order.currency)}</strong></p><p><span>Total</span><strong>{formatMoney(order.finalTotal, order.currency)}</strong></p></div></section>
      <section className="order-detail-card"><header><MapPin size={19} /><h2>Delivery address</h2></header><strong>{deliveryAddress.label}</strong><p>{address}</p>{deliveryAddress.landmark && <small>Landmark: {deliveryAddress.landmark}</small>}{deliveryAddress.instructions && <small>Instructions: {deliveryAddress.instructions}</small>}</section>
      <section className="order-detail-card"><header><Banknote size={19} /><h2>Payment</h2></header>{payment ? <><strong>{payment.method === 'CASH' ? 'Cash' : payment.method}</strong><p>Server payment status: <b>{payment.status}</b></p></> : <p>No customer payment record is available.</p>}</section>
    </div>{order.status === 'DELIVERED' && <ReviewPanel orderId={orderId} />}
    {confirming && <ConfirmDialog title="Cancel this order?" description="Cancellation is only possible while the order is still PLACED. The restaurant may accept it before this request completes." confirmLabel="Cancel order" pending={cancel.isPending} onCancel={() => setConfirming(false)} onConfirm={() => cancel.mutate()} />}
  </main></div>
}
