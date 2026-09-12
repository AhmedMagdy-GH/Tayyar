import { screen } from '@testing-library/react'
import type { Cart, CurrentUser } from '../api/contracts'
import { queryKeys } from '../api/queryKeys'
import { renderApp, testClient } from '../test/helpers'
import { CartPage } from './CartPage'

const user: CurrentUser = { id: 'u', fullName: 'Mona', email: 'mona@example.com', phone: null, status: 'ACTIVE', emailVerified: false, roles: ['CUSTOMER'] }
const cart: Cart = { id: 'c', branch: { id: 'b', restaurantId: 'r', name: 'Zamalek', restaurantName: 'Butcher’s Burger', state: 'ACTIVE', openNow: true }, items: [{ id: 'line', menuItemId: 'item', name: 'Double Smash', quantity: 1, acknowledgedUnitPrice: 180, currentUnitPrice: 195, priceChanged: true, currentlyAvailable: true, lineSubtotal: 195, version: 2 }], merchandiseSubtotal: 195, currency: 'EGP', version: 3 }

it('surfaces acknowledged/current price changes without silently accepting them', () => {
  const client = testClient()
  client.setQueryData(queryKeys.currentUser, user)
  client.setQueryData(queryKeys.addresses, { items: [], page: 0, size: 100, total: 0 })
  client.setQueryData(queryKeys.cart, cart)
  renderApp(<CartPage />, client, ['/cart'])
  expect(screen.getByText('One or more prices changed')).toBeInTheDocument()
  expect(screen.getByText('180 EGP')).toBeInTheDocument()
  expect(screen.getAllByText('195 EGP').length).toBeGreaterThan(0)
  expect(screen.getByRole('button', { name: 'Confirm current prices' })).toBeEnabled()
})
