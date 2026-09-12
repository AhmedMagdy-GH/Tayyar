import { mutate, request } from './client'
import type { OrderDetails, OrderStatus, OrderSummary, Page } from './contracts'

export type OrderFilters = { page?: number; size?: number; status?: OrderStatus }

function query(filters: OrderFilters) {
  const values = new URLSearchParams({ page: String(filters.page ?? 0), size: String(filters.size ?? 10) })
  if (filters.status) values.set('status', filters.status)
  return values.toString()
}

export const orderApi = {
  list(filters: OrderFilters = {}) { return request<Page<OrderSummary>>(`/orders?${query(filters)}`) },
  details(orderId: string) { return request<OrderDetails>(`/orders/${orderId}`) },
  cancel(orderId: string, version: number, reason: string) {
    return mutate<OrderSummary>(`/orders/${orderId}/cancel`, { method: 'POST', body: JSON.stringify({ version, reason }) })
  },
}
