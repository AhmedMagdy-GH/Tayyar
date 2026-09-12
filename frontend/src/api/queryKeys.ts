export const queryKeys = {
  currentUser: ['session', 'current-user'] as const,
  cart: ['customer', 'cart'] as const,
  addresses: ['customer', 'addresses'] as const,
  order: (orderId: string) => ['customer', 'orders', orderId] as const,
  orders: (page: number, status?: string) => ['customer', 'orders', 'list', { page, status: status ?? null }] as const,
  notifications: (page: number, read?: boolean) => ['customer', 'notifications', { page, read: read ?? null }] as const,
  notificationBadge: ['customer', 'notifications', 'unread-count'] as const,
  review: (orderId: string) => ['customer', 'reviews', orderId] as const,
  publicReviews: (restaurantId: string, page: number) => ['discovery', 'restaurants', restaurantId, 'reviews', page] as const,
  zones: ['discovery', 'delivery-zones'] as const,
}
