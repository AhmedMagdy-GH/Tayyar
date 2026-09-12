import { fireEvent, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { checkoutApi } from '../api/checkout'
import { orderApi } from '../api/orders'
import type { Address, Cart, CheckoutSummary, CurrentUser } from '../api/contracts'
import { queryKeys } from '../api/queryKeys'
import { renderApp, testClient } from '../test/helpers'
import { App } from './App'

const user: CurrentUser = { id: 'u', fullName: 'Mona', email: 'mona@example.com', phone: null, status: 'ACTIVE', emailVerified: false, roles: ['CUSTOMER'] }
const cart: Cart = { id: 'c1', branch: { id: 'b1', restaurantId: 'r1', name: 'Zamalek', restaurantName: 'Tayyar Grill', state: 'ACTIVE', openNow: true }, items: [{ id: 'line1', menuItemId: 'i1', name: 'Kofta Bowl', quantity: 2, acknowledgedUnitPrice: 90, currentUnitPrice: 90, priceChanged: false, currentlyAvailable: true, lineSubtotal: 180, version: 2 }], merchandiseSubtotal: 180, currency: 'EGP', version: 7 }
const address: Address = { id: 'a1', profile: { label: 'Home', street: 'Tahrir Street', building: '12', floor: null, apartment: null, landmark: null, instructions: null, city: 'Cairo', region: null, postalCode: null, countryCode: 'EG', latitude: null, longitude: null }, deliveryZoneId: 'z1', isDefault: true, version: 1, createdAt: '', updatedAt: '' }
const summary: CheckoutSummary = { orderId: 'o1', orderStatus: 'PLACED', paymentMethod: 'CASH', paymentStatus: 'PENDING', merchandiseSubtotal: 180, deliveryFee: 20, discountTotal: 0, finalTotal: 200, currency: 'EGP', createdAt: '2026-09-12T10:00:00Z' }

afterEach(() => vi.restoreAllMocks())

it('requires authentication for checkout', async () => {
  const client = testClient()
  client.setQueryData(queryKeys.currentUser, null)
  renderApp(<App />, client, ['/checkout'])
  expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
})

it('navigates a successful checkout to confirmation and keeps the current user', async () => {
  vi.spyOn(checkoutApi, 'place').mockResolvedValue(summary)
  vi.spyOn(orderApi, 'details').mockReturnValue(new Promise(() => {}))
  const client = testClient()
  client.setQueryData(queryKeys.currentUser, user)
  client.setQueryData(queryKeys.cart, cart)
  client.setQueryData(queryKeys.addresses, { items: [address], page: 0, size: 100, total: 1 })
  renderApp(<App />, client, ['/checkout'])
  fireEvent.click(screen.getByRole('button', { name: 'Place order · Cash' }))
  expect(await screen.findByText('Order confirmed')).toBeInTheDocument()
  expect(screen.getByText('o1')).toBeInTheDocument()
  await waitFor(() => expect(client.getQueryData(queryKeys.cart)).toBeNull())
  expect(client.getQueryData(queryKeys.currentUser)).toEqual(user)
})
