import { mutate, request } from './client'
import type {
  BranchProfile, BranchSchedule, DeliveryRule, DeliveryRuleInput, EffectiveItem, Geography,
  ManagedBranch, ManagedCategory, ManagedItem, ManagedMenu, Page, RestaurantOperationsContext,
  RestaurantOrderDetails, RestaurantOrderSummary, RestaurantStaff, RestaurantView, Snapshot,
  VersionedPage, RestaurantApplication,
} from './contracts'

const json = (value: unknown) => JSON.stringify(value)
const page = '?page=0&size=100'

export const restaurantOperationsApi = {
  context: () => request<RestaurantOperationsContext[]>('/restaurant-operations/context'),
  applications: () => request<Page<RestaurantApplication>>('/restaurant-applications?page=0&size=20'),
  apply: (input: { name: string; description: string }) => mutate<RestaurantApplication>('/restaurant-applications', { method: 'POST', body: json(input) }),
  restaurant: (restaurantId: string) => request<RestaurantView>(`/restaurants/${restaurantId}`),
  updateRestaurant: (restaurantId: string, input: { name: string; description: string; version: number }) =>
    mutate<RestaurantView>(`/restaurants/${restaurantId}`, { method: 'PUT', body: json(input) }),

  branches: (restaurantId: string) => request<Page<ManagedBranch>>(`/restaurants/${restaurantId}/branches${page}`),
  createBranch: (restaurantId: string, profile: BranchProfile) =>
    mutate<ManagedBranch>(`/restaurants/${restaurantId}/branches`, { method: 'POST', body: json(profile) }),
  updateBranch: (restaurantId: string, branchId: string, profile: BranchProfile, version: number) =>
    mutate<ManagedBranch>(`/restaurants/${restaurantId}/branches/${branchId}`, { method: 'PUT', body: json({ profile, version }) }),
  updateBranchOperation: (restaurantId: string, branchId: string, status: string, paused: boolean, version: number) =>
    mutate<ManagedBranch>(`/restaurants/${restaurantId}/branches/${branchId}/operation`, { method: 'PUT', body: json({ status, paused, version }) }),
  hours: (restaurantId: string, branchId: string) => request<BranchSchedule>(`/restaurants/${restaurantId}/branches/${branchId}/hours`),
  updateHours: (restaurantId: string, branchId: string, schedule: BranchSchedule) =>
    mutate<BranchSchedule>(`/restaurants/${restaurantId}/branches/${branchId}/hours`, { method: 'PUT', body: json(schedule) }),

  menu: (restaurantId: string) => request<ManagedMenu>(`/restaurants/${restaurantId}/menu`),
  createMenu: (restaurantId: string, name: string) => mutate<ManagedMenu>(`/restaurants/${restaurantId}/menu`, { method: 'POST', body: json({ name }) }),
  updateMenu: (restaurantId: string, input: { name: string; active: boolean; version: number }) => mutate<{ id: string; version: number }>(`/restaurants/${restaurantId}/menu`, { method: 'PUT', body: json(input) }),
  categories: (restaurantId: string) => request<VersionedPage<ManagedCategory>>(`/restaurants/${restaurantId}/menu/categories${page}`),
  createCategory: (restaurantId: string, input: { name: string; description: string; version: number }) =>
    mutate<{ id: string; version: number }>(`/restaurants/${restaurantId}/menu/categories`, { method: 'POST', body: json(input) }),
  category: (restaurantId: string, categoryId: string) => request<Snapshot<ManagedCategory>>(`/restaurants/${restaurantId}/menu/categories/${categoryId}`),
  updateCategory: (restaurantId: string, categoryId: string, input: { name: string; description: string; active: boolean; version: number }) =>
    mutate<{ id: string; version: number }>(`/restaurants/${restaurantId}/menu/categories/${categoryId}`, { method: 'PUT', body: json(input) }),
  reorderCategories: (restaurantId: string, ids: string[], version: number) => mutate<{ id: string; version: number }>(`/restaurants/${restaurantId}/menu/categories/order`, { method: 'PUT', body: json({ ids, version }) }),
  items: (restaurantId: string, categoryId: string) => request<VersionedPage<ManagedItem>>(`/restaurants/${restaurantId}/menu/categories/${categoryId}/items${page}`),
  createItem: (restaurantId: string, categoryId: string, input: { name: string; description: string; basePrice: string; available: boolean; version: number }) =>
    mutate<{ id: string; version: number }>(`/restaurants/${restaurantId}/menu/categories/${categoryId}/items`, { method: 'POST', body: json(input) }),
  item: (restaurantId: string, itemId: string) => request<Snapshot<ManagedItem>>(`/restaurants/${restaurantId}/menu/items/${itemId}`),
  updateItem: (restaurantId: string, itemId: string, input: { name: string; description: string; basePrice: string; available: boolean; active: boolean; version: number }) =>
    mutate<{ id: string; version: number }>(`/restaurants/${restaurantId}/menu/items/${itemId}`, { method: 'PUT', body: json(input) }),
  reorderItems: (restaurantId: string, categoryId: string, ids: string[], version: number) => mutate<{ id: string; version: number }>(`/restaurants/${restaurantId}/menu/categories/${categoryId}/items/order`, { method: 'PUT', body: json({ ids, version }) }),
  effectiveItems: (restaurantId: string, branchId: string) => request<VersionedPage<EffectiveItem>>(`/restaurants/${restaurantId}/branches/${branchId}/menu/items${page}`),
  setOverride: (restaurantId: string, branchId: string, itemId: string, input: { available: boolean | null; price: string | null; version: number }) =>
    mutate<{ id: string; version: number }>(`/restaurants/${restaurantId}/branches/${branchId}/menu/items/${itemId}/override`, { method: 'PUT', body: json(input) }),
  removeOverride: (restaurantId: string, branchId: string, itemId: string, version: number) =>
    mutate<{ id: string; version: number }>(`/restaurants/${restaurantId}/branches/${branchId}/menu/items/${itemId}/override`, { method: 'DELETE', body: json({ version }) }),

  cities: () => request<Page<Geography>>('/geography/cities?page=0&size=100'),
  zones: (cityId: string) => request<Page<Geography>>(`/geography/delivery-zones?cityId=${encodeURIComponent(cityId)}&page=0&size=100`),
  deliveryRules: (restaurantId: string, branchId: string) => request<Page<DeliveryRule>>(`/restaurants/${restaurantId}/branches/${branchId}/delivery-zones${page}`),
  createDeliveryRule: (restaurantId: string, branchId: string, zoneId: string, input: DeliveryRuleInput) =>
    mutate<DeliveryRule>(`/restaurants/${restaurantId}/branches/${branchId}/delivery-zones/${zoneId}`, { method: 'POST', body: json(input) }),
  updateDeliveryRule: (restaurantId: string, branchId: string, zoneId: string, rule: DeliveryRuleInput, version: number) =>
    mutate<DeliveryRule>(`/restaurants/${restaurantId}/branches/${branchId}/delivery-zones/${zoneId}`, { method: 'PUT', body: json({ rule, version }) }),

  staff: (restaurantId: string) => request<Page<RestaurantStaff>>(`/restaurants/${restaurantId}/staff${page}`),
  addStaff: (restaurantId: string, email: string) => mutate<RestaurantStaff>(`/restaurants/${restaurantId}/staff`, { method: 'POST', body: json({ email }) }),
  removeStaff: (restaurantId: string, userId: string) => mutate<void>(`/restaurants/${restaurantId}/staff/${userId}`, { method: 'DELETE' }),
  assignStaff: (restaurantId: string, branchId: string, userId: string, version: number) =>
    mutate<ManagedBranch>(`/restaurants/${restaurantId}/branches/${branchId}/staff/${userId}`, { method: 'PUT', body: json({ version }) }),
  unassignStaff: (restaurantId: string, branchId: string, userId: string, version: number) =>
    mutate<ManagedBranch>(`/restaurants/${restaurantId}/branches/${branchId}/staff/${userId}`, { method: 'DELETE', body: json({ version }) }),

  orders: (restaurantId: string, branchId: string | null, status: string | null, orderPage = 0) => {
    const params = new URLSearchParams({ restaurantId, page: String(orderPage), size: '20' })
    if (branchId) params.set('branchId', branchId)
    if (status) params.set('status', status)
    return request<Page<RestaurantOrderSummary>>(`/restaurant-orders?${params}`)
  },
  order: (orderId: string) => request<RestaurantOrderDetails>(`/restaurant-orders/${orderId}`),
  transitionOrder: (orderId: string, action: 'accept' | 'reject' | 'start-preparation' | 'ready-for-pickup', version: number, reason?: string) =>
    mutate<RestaurantOrderSummary>(`/restaurant-orders/${orderId}/${action}`, { method: 'POST', body: json(action === 'reject' ? { version, reason } : { version }) }),
}
