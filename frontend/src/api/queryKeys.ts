export const queryKeys = {
  currentUser: ['session', 'current-user'] as const,
  cart: ['customer', 'cart'] as const,
  addresses: ['customer', 'addresses'] as const,
  order: (orderId: string) => ['customer', 'orders', orderId] as const,
  zones: ['discovery', 'delivery-zones'] as const,
}
