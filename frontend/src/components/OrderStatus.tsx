import { Check, Circle, CircleX, Clock3 } from 'lucide-react'
import type { OrderHistoryEntry, OrderStatus } from '../api/contracts'
import { orderStatusLabels as labels } from '../utils/orderStatus'

const active: OrderStatus[] = ['PLACED', 'ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED']
const failed = new Set<OrderStatus>(['PAYMENT_FAILED', 'REJECTED', 'CANCELLED'])

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`order-status order-status--${failed.has(status) ? 'failed' : status === 'DELIVERED' ? 'done' : 'active'}`}>{failed.has(status) ? <CircleX size={13} /> : status === 'DELIVERED' ? <Check size={13} /> : <Clock3 size={13} />}{labels[status]}</span>
}

export function OrderTimeline({ status, history }: { status: OrderStatus; history: OrderHistoryEntry[] }) {
  if (failed.has(status)) {
    const event = [...history].reverse().find((entry) => entry.newStatus === status)
    return <div className="order-terminal" role="status"><CircleX size={22} /><div><strong>{labels[status]}</strong><p>This order ended at this state{event?.reason ? `: ${event.reason}` : '.'}</p></div></div>
  }
  const reached = new Set(history.map((entry) => entry.newStatus))
  reached.add(status)
  return <ol className="order-timeline" aria-label={`Order progress. Current status: ${labels[status]}`}>
    {active.map((step, index) => { const done = reached.has(step) || active.indexOf(status) >= index; const current = step === status; return <li key={step} className={done ? 'is-done' : ''} aria-current={current ? 'step' : undefined}><span>{done ? <Check size={14} /> : <Circle size={13} />}</span><div><strong>{labels[step]}</strong>{current && <small>Current status</small>}</div></li> })}
  </ol>
}
