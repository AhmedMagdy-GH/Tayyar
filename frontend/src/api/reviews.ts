import { mutate, request } from './client'
import type { CustomerReview, PublicReviewPage } from './contracts'

export const reviewApi = {
  byOrder(orderId: string) { return request<CustomerReview>(`/orders/${orderId}/review`) },
  create(orderId: string, rating: number, comment: string) {
    return mutate<CustomerReview>(`/orders/${orderId}/review`, { method: 'POST', body: JSON.stringify({ rating, comment: comment || null }) })
  },
  update(orderId: string, rating: number, comment: string, version: number) {
    return mutate<CustomerReview>(`/orders/${orderId}/review`, { method: 'PATCH', body: JSON.stringify({ rating, comment: comment || null, version }) })
  },
  publicForRestaurant(restaurantId: string, page = 0, size = 5) {
    return request<PublicReviewPage>(`/discovery/restaurants/${restaurantId}/reviews?page=${page}&size=${size}`)
  },
}
