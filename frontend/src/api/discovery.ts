import { request } from './client'
import type { Branch, Menu, Page, Restaurant, Zone } from './contracts'

function params(values: Record<string, string | number | boolean | undefined>) {
  const query = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== '') query.set(key, String(value))
  })
  return query.toString()
}

export type RestaurantFilters = {
  query?: string
  zoneId?: string
  addressId?: string
  page?: number
  size?: number
  openNow?: boolean
}

export const discoveryApi = {
  restaurants(filters: RestaurantFilters = {}) {
    return request<Page<Restaurant>>(`/discovery/restaurants?${params({ page: 0, size: 20, ...filters })}`)
  },
  restaurant(id: string) {
    return request<Restaurant>(`/discovery/restaurants/${id}`)
  },
  branches(restaurantId: string, location: { zoneId?: string; addressId?: string } = {}) {
    return request<Page<Branch>>(
      `/discovery/restaurants/${restaurantId}/branches?${params({ page: 0, size: 100, ...location })}`,
    )
  },
  menu(restaurantId: string, branchId: string) {
    return request<Menu>(
      `/discovery/restaurants/${restaurantId}/branches/${branchId}/menu?${params({
        page: 0,
        size: 100,
        itemPage: 0,
        itemSize: 100,
        availableOnly: false,
      })}`,
    )
  },
  zones() {
    return request<Page<Zone>>('/discovery/delivery-zones?page=0&size=100')
  },
}
