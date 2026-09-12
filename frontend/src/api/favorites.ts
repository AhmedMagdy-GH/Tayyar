import type { Favorite, Page } from './contracts'
import { mutate, request } from './client'

export const favoriteApi = {
  list({ page = 0, size = 20 }: { page?: number; size?: number } = {}) {
    return request<Page<Favorite>>(`/favorites?page=${page}&size=${size}`)
  },
  add(restaurantId: string) {
    return mutate<void>(`/favorites/${restaurantId}`, { method: 'PUT' })
  },
  remove(restaurantId: string) {
    return mutate<void>(`/favorites/${restaurantId}`, { method: 'DELETE' })
  },
}
