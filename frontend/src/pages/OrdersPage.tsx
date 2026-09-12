import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { ArrowRight, Clock3, PackageOpen, ReceiptText } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { OrderStatus } from '../api/contracts'
import { safeErrorMessage } from '../api/errors'
import { orderApi } from '../api/orders'
import { queryKeys } from '../api/queryKeys'
import { AppHeader } from '../components/AppHeader'
import { MobileNav } from '../components/MobileNav'
import { StatusBadge } from '../components/OrderStatus'
import { formatMoney } from '../utils/money'

const filters: Array<{ value: '' | OrderStatus; label: string }> = [{ value: '', label: 'All orders' }, { value: 'PLACED', label: 'Placed' }, { value: 'PREPARING', label: 'Preparing' }, { value: 'OUT_FOR_DELIVERY', label: 'On the way' }, { value: 'DELIVERED', label: 'Delivered' }, { value: 'CANCELLED', label: 'Cancelled' }]
const date = (value: string) => new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))

export function OrdersPage() {
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<'' | OrderStatus>('')
  const orders = useQuery({ queryKey: queryKeys.orders(page, status || undefined), queryFn: () => orderApi.list({ page, size: 10, status: status || undefined }), placeholderData: keepPreviousData, retry: false })
  const pageCount = Math.ceil((orders.data?.total ?? 0) / (orders.data?.size ?? 10))
  return <div className="page-shell"><AppHeader /><main className="shell customer-page orders-page"><div className="customer-page__heading"><div><span className="eyebrow">Your meals, in one place</span><h1>Orders</h1><p>Track active orders and revisit the details from past deliveries.</p></div></div>
    <div className="order-filters" role="group" aria-label="Filter orders by status">{filters.map((filter) => <button key={filter.value} type="button" aria-pressed={status === filter.value} onClick={() => { setStatus(filter.value); setPage(0) }}>{filter.label}</button>)}</div>
    {orders.isPending && <div className="session-loading" role="status"><span className="loader" /> Loading your orders…</div>}
    {orders.isError && <div className="state-card" role="alert"><ReceiptText size={30} /><h2>We couldn’t load your orders</h2><p>{safeErrorMessage(orders.error)}</p><button className="primary-button" type="button" onClick={() => void orders.refetch()}>Try again</button></div>}
    {orders.data?.items.length === 0 && <div className="state-card"><PackageOpen size={31} /><h2>{status ? 'No orders match this filter' : 'Your first order starts here'}</h2><p>{status ? 'Choose another status to see more orders.' : 'Once you check out, your order history and progress will appear here.'}</p><Link className="primary-button" to="/">Browse restaurants</Link></div>}
    {orders.data && orders.data.items.length > 0 && <div className="orders-list" aria-busy={orders.isFetching}>{orders.data.items.map((order) => <article className="order-card" key={order.id}><div className="order-card__icon"><ReceiptText size={22} /></div><div className="order-card__main"><div><span className="order-reference">Order {order.id.slice(0, 8).toUpperCase()}</span><h2>{order.restaurant.name}</h2><p>{order.branch.name} · <Clock3 size={13} /> {date(order.createdAt)}</p></div><StatusBadge status={order.status} /></div><div className="order-card__total"><span>Total</span><strong>{formatMoney(order.finalTotal, order.currency)}</strong></div><Link to={`/orders/${order.id}`} aria-label={`View order ${order.id}`}><span>View details</span><ArrowRight size={17} /></Link></article>)}</div>}
    {pageCount > 1 && <nav className="pagination" aria-label="Order pages"><button type="button" disabled={page === 0 || orders.isFetching} onClick={() => setPage((value) => value - 1)}>Previous</button><span>Page {page + 1} of {pageCount}</span><button type="button" disabled={page + 1 >= pageCount || orders.isFetching} onClick={() => setPage((value) => value + 1)}>Next</button></nav>}
  </main><MobileNav /></div>
}
