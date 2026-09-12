import { request } from './client'
import type { OrderDetails } from './contracts'

export const orderApi = {
  details(orderId: string) { return request<OrderDetails>(`/orders/${orderId}`) },
}
