import { mutate, request } from './client'
import type { DriverCompletion, DriverOrder, DriverProfile, Page } from './contracts'

const json = (value: unknown) => JSON.stringify(value)

export const driverOperationsApi = {
  profile: () => request<DriverProfile>('/driver/profile'),
  available: (version: number) => mutate<DriverProfile>('/driver/profile/available', { method: 'POST', body: json({ version }) }),
  offline: (version: number) => mutate<DriverProfile>('/driver/profile/offline', { method: 'POST', body: json({ version }) }),
  activeOrders: () => request<Page<DriverOrder>>('/driver/orders?page=0&size=20'),
  order: (orderId: string) => request<DriverOrder>(`/driver/orders/${orderId}`),
  pickup: (order: DriverOrder) => mutate<DriverOrder>(`/driver/orders/${order.orderId}/pickup`, {
    method: 'POST',
    body: json({ orderVersion: order.orderVersion, assignmentVersion: order.assignmentVersion }),
  }),
  deliver: (order: DriverOrder) => mutate<DriverCompletion>(`/driver/orders/${order.orderId}/deliver`, {
    method: 'POST',
    body: json({ orderVersion: order.orderVersion, assignmentVersion: order.assignmentVersion }),
  }),
}
