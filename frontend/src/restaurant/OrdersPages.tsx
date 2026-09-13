import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { restaurantOperationsApi } from '../api/restaurantOperations'
import { queryKeys } from '../api/queryKeys'
import type { OrderStatus, RestaurantOrderDetails } from '../api/contracts'
import { ApiError } from '../api/client'
import { formatMoney } from '../utils/money'
import { ErrorState } from '../components/States'
import { useRestaurantContext } from './RestaurantContext'
import { formatDateTime, MutationMessage, PageHeader, Panel, Status } from './shared'

const activeStatuses: OrderStatus[] = ['PLACED', 'ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP']

export function RestaurantOrdersPage() {
  const { selected } = useRestaurantContext()
  const [branchId, setBranchId] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const allowedBranch = branchId && selected.branches.some((branch) => branch.branchId === branchId) ? branchId : null
  const orders = useQuery({
    queryKey: queryKeys.restaurantOrders(selected.restaurantId, allowedBranch, status || null, page),
    queryFn: () => restaurantOperationsApi.orders(selected.restaurantId, allowedBranch, status || null, page),
    refetchInterval: (query) => document.visibilityState === 'visible' && query.state.data?.items.some((item) => activeStatuses.includes(item.status)) ? 15_000 : false,
  })
  return <><PageHeader eyebrow="Live operations" title="Order queue" description="The queue refreshes every 15 seconds while visible and active orders are present." />
    <div className="ops-filter-bar"><label>Branch<select value={branchId} onChange={(event) => { setBranchId(event.target.value); setPage(0) }}><option value="">All available branches</option>{selected.branches.map((branch) => <option key={branch.branchId} value={branch.branchId}>{branch.branchName}</option>)}</select></label><label>Status<select value={status} onChange={(event) => { setStatus(event.target.value); setPage(0) }}><option value="">All states</option>{activeStatuses.map((value) => <option key={value}>{value}</option>)}<option>REJECTED</option><option>CANCELLED</option><option>DELIVERED</option></select></label><button onClick={() => void orders.refetch()}>Refresh now</button></div>
    {orders.isPending ? <Panel>Loading queue…</Panel> : orders.isError ? <ErrorState title="Order queue unavailable" onRetry={() => void orders.refetch()} /> : <Panel><div className="responsive-table"><table><thead><tr><th>Order</th><th>Branch</th><th>Status</th><th>Placed</th><th>Total</th><th><span className="sr-only">Open</span></th></tr></thead><tbody>{orders.data.items.map((order) => <tr key={order.id}><td data-label="Order"><strong>#{order.id.slice(0, 8)}</strong></td><td data-label="Branch">{order.branch.name}</td><td data-label="Status"><Status value={order.status} /></td><td data-label="Placed">{formatDateTime(order.createdAt)}</td><td data-label="Total">{formatMoney(order.finalTotal, order.currency)}</td><td><Link className="table-action" to={`/restaurant/orders/${order.id}`}>Open order</Link></td></tr>)}</tbody></table>{orders.data.items.length === 0 && <p className="empty-copy">No orders match these filters.</p>}</div><div className="pagination"><button disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button><span>Page {page + 1}</span><button disabled={(page + 1) * orders.data.size >= orders.data.total} onClick={() => setPage((value) => value + 1)}>Next</button></div></Panel>}
  </>
}

type Action = 'accept' | 'reject' | 'start-preparation' | 'ready-for-pickup'
const actions: Partial<Record<OrderStatus, Array<{ action: Action; label: string; danger?: boolean }>>> = {
  PLACED: [{ action: 'accept', label: 'Accept order' }, { action: 'reject', label: 'Reject order', danger: true }],
  ACCEPTED: [{ action: 'start-preparation', label: 'Start preparation' }],
  PREPARING: [{ action: 'ready-for-pickup', label: 'Mark ready for pickup' }],
}

export function RestaurantOrderDetailPage() {
  const { orderId = '' } = useParams()
  const { selected } = useRestaurantContext()
  const client = useQueryClient()
  const [reason, setReason] = useState('')
  const [conflict, setConflict] = useState('')
  const order = useQuery({ queryKey: queryKeys.restaurantOrder(selected.restaurantId, orderId), queryFn: () => restaurantOperationsApi.order(orderId), refetchInterval: (query) => document.visibilityState === 'visible' && query.state.data && activeStatuses.includes(query.state.data.order.status) ? 15_000 : false })
  const transition = useMutation({
    mutationFn: (action: Action) => restaurantOperationsApi.transitionOrder(orderId, action, order.data!.order.version, reason),
    onSuccess: (summary) => { setReason(''); setConflict(''); client.setQueryData<RestaurantOrderDetails>(queryKeys.restaurantOrder(selected.restaurantId, orderId), (current) => current ? { ...current, order: summary } : current); void client.invalidateQueries({ queryKey: ['restaurant-operations', selected.restaurantId, 'orders'] }) },
    onError: (error) => { if (error instanceof ApiError && error.status === 409) { setConflict('This order changed elsewhere. We refreshed the authoritative state; review it before trying another action.'); void order.refetch(); void client.invalidateQueries({ queryKey: ['restaurant-operations', selected.restaurantId, 'orders'] }) } },
  })
  if (order.isPending) return <><PageHeader title="Order" /><Panel>Loading order…</Panel></>
  if (order.isError) return <ErrorState title="Order unavailable" onRetry={() => void order.refetch()} />
  const details = order.data
  const nextActions = actions[details.order.status] ?? []
  return <><PageHeader eyebrow={<Link to="/restaurant/orders">← Order queue</Link>} title={`Order #${details.order.id.slice(0, 8)}`} description={`${details.order.branch.name} · ${formatDateTime(details.order.createdAt)}`} actions={<Status value={details.order.status} />} />
    {conflict && <p className="conflict-banner" role="alert">{conflict}</p>}<MutationMessage error={transition.error && !conflict ? transition.error : null} />
    {nextActions.length > 0 && <Panel title="Order actions"><div className="order-actions">{nextActions.map(({ action, label, danger }) => action === 'reject' ? <div className="reject-action" key={action}><label htmlFor="reject-reason">Rejection reason</label><input id="reject-reason" value={reason} maxLength={1000} onChange={(event) => setReason(event.target.value)} /><button className={danger ? 'danger-button' : 'primary-button'} disabled={!reason.trim() || transition.isPending} onClick={() => transition.mutate(action)}>{label}</button></div> : <button key={action} className="primary-button" disabled={transition.isPending} aria-label={`${label} for order ${details.order.id.slice(0, 8)}`} onClick={() => transition.mutate(action)}>{label}</button>)}</div></Panel>}
    <div className="ops-split"><Panel title="Items"><div className="order-items">{details.items.map((item) => <div key={item.menuItemId}><span>{item.quantity} × {item.name}</span><strong>{formatMoney(item.lineSubtotal, details.order.currency)}</strong></div>)}</div><dl className="totals"><div><dt>Subtotal</dt><dd>{formatMoney(details.order.merchandiseSubtotal, details.order.currency)}</dd></div><div><dt>Delivery</dt><dd>{formatMoney(details.order.deliveryFee, details.order.currency)}</dd></div><div><dt>Discount</dt><dd>− {formatMoney(details.order.discountTotal, details.order.currency)}</dd></div><div className="total-line"><dt>Total</dt><dd>{formatMoney(details.order.finalTotal, details.order.currency)}</dd></div></dl></Panel>
    <Panel title="Delivery snapshot"><address>{details.deliveryAddress.label}<br />{details.deliveryAddress.street}, {details.deliveryAddress.building}<br />{[details.deliveryAddress.floor && `Floor ${details.deliveryAddress.floor}`, details.deliveryAddress.apartment && `Apartment ${details.deliveryAddress.apartment}`].filter(Boolean).join(' · ')}<br />{details.deliveryAddress.city}{details.deliveryAddress.region ? `, ${details.deliveryAddress.region}` : ''}</address>{details.deliveryAddress.instructions && <p><strong>Instructions:</strong> {details.deliveryAddress.instructions}</p>}<p>Model: {details.payment?.method ?? '—'} · Payment {details.payment?.status ?? '—'}</p></Panel></div>
    <Panel title="Status history"><ol className="status-history">{details.history.map((entry, index) => <li key={`${entry.occurredAt}-${index}`}><Status value={entry.newStatus} /><span>{formatDateTime(entry.occurredAt)}</span>{entry.reason && <p>{entry.reason}</p>}</li>)}</ol></Panel>
  </>
}
