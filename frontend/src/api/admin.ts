import { mutate, request } from './client'
import type { AdminApplicationDecision, AdminApplicationSubmission, AdminAuditRecord, AdminDeliveryAssignment, AdminDriver, AdminDriverProfile, AdminOrder, AdminOrderDetails, AdminRestaurant, AdminRestaurantDetail, AdminRestaurantHistory, AdminUser, ManagedBranch, Page, RestaurantApplication } from './contracts'

const json = (value: unknown) => JSON.stringify(value)
const url = (path: string, values: Record<string, string | number | undefined>) => { const params = new URLSearchParams(); Object.entries(values).forEach(([key, value]) => { if (value !== undefined && value !== '') params.set(key, String(value)) }); return `${path}?${params}` }

export const adminApi = {
  users: (filters: { email?: string; status?: string; role?: string; page: number; size?: number }) => request<Page<AdminUser>>(url('/admin/users', { ...filters, size: filters.size ?? 20 })),
  user: (id: string) => request<AdminUser>(`/admin/users/${id}`),
  accountStatus: (id: string, action: 'suspension' | 'reactivation', reason: string) => mutate<AdminUser>(`/admin/users/${id}/${action}`, { method: 'POST', body: json({ reason }) }),
  restaurants: (filters: { status?: string; page: number; size?: number }) => request<Page<AdminRestaurant>>(url('/admin/restaurants', { ...filters, size: filters.size ?? 20 })),
  restaurant: (id: string) => request<AdminRestaurantDetail>(`/admin/restaurants/${id}`),
  branch: (restaurantId: string, branchId: string) => request<ManagedBranch>(`/restaurants/${restaurantId}/branches/${branchId}`),
  restaurantHistory: (id: string, page: number) => request<Page<AdminRestaurantHistory>>(url(`/admin/restaurants/${id}/status-history`, { page, size: 20 })),
  restaurantStatus: (id: string, status: 'ACTIVE' | 'SUSPENDED', version: number, reason: string) => mutate<AdminRestaurant>(`/admin/restaurants/${id}/status`, { method: 'PUT', body: json({ status, version, reason }) }),
  applications: (status: string, page: number) => request<Page<RestaurantApplication>>(url('/admin/restaurant-applications', { status, page, size: 20 })),
  application: (id: string) => request<RestaurantApplication>(`/admin/restaurant-applications/${id}`),
  applicationSubmissions: (id: string, page: number) => request<Page<AdminApplicationSubmission>>(url(`/admin/restaurant-applications/${id}/submissions`, { page, size: 20 })),
  applicationDecisions: (id: string, page: number) => request<Page<AdminApplicationDecision>>(url(`/admin/restaurant-applications/${id}/decisions`, { page, size: 20 })),
  decideApplication: (id: string, outcome: 'APPROVED' | 'REJECTED', version: number, reason?: string) => mutate<RestaurantApplication>(`/admin/restaurant-applications/${id}/decisions`, { method: 'POST', body: json({ outcome, version, reason: reason || null }) }),
  orders: (filters: { status?: string; restaurantId?: string; branchId?: string; customerId?: string; page: number; size?: number }) => request<Page<AdminOrder>>(url('/admin/orders', { ...filters, size: filters.size ?? 20 })),
  order: (id: string) => request<AdminOrderDetails>(`/admin/orders/${id}`),
  drivers: (filters: { accountStatus?: string; state?: string; page: number; size?: number }) => request<Page<AdminDriver>>(url('/admin/drivers', { ...filters, size: filters.size ?? 20 })),
  provisionDriver: (userId: string) => mutate<AdminDriverProfile>('/admin/drivers', { method: 'POST', body: json({ userId }) }),
  assignDriver: (orderId: string, driverId: string, orderVersion: number) => mutate<AdminDeliveryAssignment>('/admin/delivery-assignments', { method: 'POST', body: json({ orderId, driverId, orderVersion }) }),
  audit: (filters: { action?: string; targetType?: string; targetId?: string; actorId?: string; page: number; size?: number }) => request<Page<AdminAuditRecord>>(url('/admin/audit-log', { ...filters, size: filters.size ?? 20 })),
}
