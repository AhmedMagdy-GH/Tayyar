import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { vi } from 'vitest'
import { ApiError } from '../api/client'
import { cartApi } from '../api/cart'
import type { Branch, Cart, CurrentUser, Menu, Restaurant } from '../api/contracts'
import { queryKeys } from '../api/queryKeys'
import { renderApp, testClient } from '../test/helpers'
import { RestaurantPage } from './RestaurantPage'

const user: CurrentUser = { id: 'u', fullName: 'Mona', email: 'mona@example.com', phone: null, status: 'ACTIVE', emailVerified: false, roles: ['CUSTOMER'] }
const restaurant: Restaurant = { id: 'r2', name: 'New Place', description: 'Burgers', branchCount: 1, openNow: true, serviceable: true, minimumDeliveryFee: 10, minimumOrder: 50, minimumEtaMinutes: 20, currency: 'EGP' }
const branch: Branch = { id: 'b2', name: 'Maadi', addressLine1: 'Road 9', city: 'Cairo', timezone: 'Africa/Cairo', openNow: true, state: 'ACTIVE', serviceable: true, deliveryFee: 10, minimumOrder: 50, etaMinMinutes: 20, etaMaxMinutes: 30, currency: 'EGP' }
const menu: Menu = { id: 'm2', branchId: 'b2', name: 'Menu', currency: 'EGP', categories: { items: [{ id: 'cat', name: 'Popular', description: '', items: { items: [{ id: 'i2', name: 'New Burger', description: 'Fresh', effectivePrice: 120, effectiveAvailability: true }], page: 0, size: 100, total: 1 } }], page: 0, size: 100, total: 1 } }
const oldCart: Cart = { id: 'c1', branch: { id: 'b1', restaurantId: 'r1', name: 'Zamalek', restaurantName: 'Old Place', state: 'ACTIVE', openNow: true }, items: [{ id: 'l1', menuItemId: 'i1', name: 'Old item', quantity: 1, acknowledgedUnitPrice: 90, currentUnitPrice: 90, priceChanged: false, currentlyAvailable: true, lineSubtotal: 90, version: 1 }], merchandiseSubtotal: 90, currency: 'EGP', version: 6 }

it('requires explicit confirmation before replacing a cross-branch cart', async () => {
  const client = testClient()
  client.setQueryData(queryKeys.currentUser, user)
  client.setQueryData(queryKeys.cart, oldCart)
  client.setQueryData(queryKeys.addresses, { items: [], page: 0, size: 100, total: 0 })
  client.setQueryData(['restaurant', 'r2'], restaurant)
  client.setQueryData(['restaurant-branches', 'r2', undefined], { items: [branch], page: 0, size: 100, total: 1 })
  client.setQueryData(['restaurant-menu', 'r2', 'b2'], menu)
  vi.spyOn(cartApi, 'add').mockRejectedValue(new ApiError('Cart belongs to another branch; explicitly replace it to continue', 409, { code: 'CONFLICT', message: 'Cart belongs to another branch; explicitly replace it to continue' }))
  vi.spyOn(cartApi, 'replace').mockResolvedValue({ ...oldCart, branch: { ...oldCart.branch, id: 'b2', restaurantId: 'r2', restaurantName: 'New Place', name: 'Maadi' }, version: 0 })
  renderApp(<Routes><Route path="/restaurants/:restaurantId" element={<RestaurantPage />} /></Routes>, client, ['/restaurants/r2'])
  fireEvent.click(screen.getByRole('button', { name: 'Add New Burger to cart' }))
  expect(await screen.findByRole('alertdialog', { name: 'Replace your current cart?' })).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Replace cart' }))
  await waitFor(() => expect(cartApi.replace).toHaveBeenCalledWith({ branchId: 'b2', menuItemId: 'i2', quantity: 1, cartId: 'c1', cartVersion: 6 }))
})
