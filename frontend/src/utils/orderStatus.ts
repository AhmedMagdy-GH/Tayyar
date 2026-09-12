import type { OrderStatus } from '../api/contracts'

export const orderStatusLabels: Record<OrderStatus, string> = {
  PENDING_PAYMENT: 'Pending payment', PLACED: 'Placed', ACCEPTED: 'Accepted', PREPARING: 'Preparing',
  READY_FOR_PICKUP: 'Ready for pickup', OUT_FOR_DELIVERY: 'Out for delivery', DELIVERED: 'Delivered',
  PAYMENT_FAILED: 'Payment failed', REJECTED: 'Rejected', CANCELLED: 'Cancelled',
}

const terminal = new Set<OrderStatus>(['DELIVERED', 'PAYMENT_FAILED', 'REJECTED', 'CANCELLED'])
export const statusLabel = (status: OrderStatus) => orderStatusLabels[status]
export const isTerminalOrderStatus = (status: OrderStatus) => terminal.has(status)
