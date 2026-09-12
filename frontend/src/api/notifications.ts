import { mutate, request } from './client'
import type { Notification, Page } from './contracts'

export type NotificationFilters = { page?: number; size?: number; read?: boolean }

function query(filters: NotificationFilters) {
  const values = new URLSearchParams()
  values.set('page', String(filters.page ?? 0))
  values.set('size', String(filters.size ?? 10))
  if (filters.read !== undefined) values.set('read', String(filters.read))
  return values.toString()
}

export const notificationApi = {
  list(filters: NotificationFilters = {}) { return request<Page<Notification>>(`/notifications?${query(filters)}`) },
  details(id: string) { return request<Notification>(`/notifications/${id}`) },
  markRead(id: string) { return mutate<Notification>(`/notifications/${id}/read`, { method: 'POST' }) },
  markAllRead() { return mutate<{ markedRead: number }>('/notifications/read-all', { method: 'POST' }) },
}
