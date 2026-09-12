import { mutate, request } from './client'
import type { Cart } from './contracts'

export type AddCartItemInput = {
  branchId: string
  menuItemId: string
  quantity: number
  cartId?: string
  cartVersion?: number
}

export const cartApi = {
  async get() { return (await request<Cart | undefined>('/cart')) ?? null },
  add(input: AddCartItemInput) { return mutate<Cart>('/cart/items', { method: 'POST', body: JSON.stringify(input) }) },
  replace(input: AddCartItemInput & { cartId: string; cartVersion: number }) { return mutate<Cart>('/cart/replace', { method: 'POST', body: JSON.stringify(input) }) },
  update(lineId: string, input: { quantity: number; cartId: string; cartVersion: number; itemVersion: number }) {
    return mutate<Cart>(`/cart/items/${lineId}`, { method: 'PATCH', body: JSON.stringify(input) })
  },
  remove(lineId: string, input: { cartId: string; cartVersion: number; itemVersion: number }) {
    const query = new URLSearchParams({ cartId: input.cartId, cartVersion: String(input.cartVersion), itemVersion: String(input.itemVersion) })
    return mutate<Cart | undefined>(`/cart/items/${lineId}?${query}`, { method: 'DELETE' })
  },
  clear(cartId: string, version: number) {
    return mutate<void>(`/cart?${new URLSearchParams({ cartId, version: String(version) })}`, { method: 'DELETE' })
  },
  reconfirm(cartId: string, cartVersion: number) {
    return mutate<Cart>('/cart/reconfirm-prices', { method: 'POST', body: JSON.stringify({ cartId, cartVersion }) })
  },
}
