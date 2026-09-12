import { screen } from '@testing-library/react'
import { vi } from 'vitest'
import { Route, Routes } from 'react-router-dom'
import { orderApi } from '../api/orders'
import type { CurrentUser } from '../api/contracts'
import { queryKeys } from '../api/queryKeys'
import { renderApp, testClient } from '../test/helpers'
import { OrderConfirmationPage } from './OrderConfirmationPage'

const user: CurrentUser = { id: 'u', fullName: 'Mona', email: 'mona@example.com', phone: null, status: 'ACTIVE', emailVerified: false, roles: ['CUSTOMER'] }

it('shows the real customer order detail without tracking UI', async () => {
  vi.spyOn(orderApi, 'details').mockResolvedValue({ order: { id: 'o1', status: 'PLACED', restaurant: { id: 'r1', name: 'Tayyar Grill' }, branch: { id: 'b1', name: 'Zamalek' }, merchandiseSubtotal: 180, deliveryFee: 20, discountTotal: 10, finalTotal: 190, currency: 'EGP', version: 0, createdAt: '' }, items: [{ menuItemId: 'i1', name: 'Kofta Bowl', unitPrice: 90, quantity: 2, lineSubtotal: 180 }], deliveryAddress: { label: 'Home', street: 'Tahrir Street', building: '12', floor: null, apartment: null, landmark: null, instructions: null, city: 'Cairo', region: null, postalCode: null, countryCode: 'EG', deliveryZoneName: 'Downtown', managedCityName: 'Cairo' }, payment: { method: 'CASH', status: 'PENDING' }, history: [] })
  const client = testClient()
  client.setQueryData(queryKeys.currentUser, user)
  client.setQueryData(queryKeys.cart, undefined)
  client.setQueryData(queryKeys.addresses, { items: [], page: 0, size: 100, total: 0 })
  renderApp(<Routes><Route path="/orders/:orderId/confirmation" element={<OrderConfirmationPage />} /></Routes>, client, ['/orders/o1/confirmation'])
  expect(await screen.findByText('Tayyar Grill')).toBeInTheDocument()
  expect(screen.getByText(/Kofta Bowl/)).toBeInTheDocument()
  expect(screen.getByText('190 EGP')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /Continue browsing/ })).toBeInTheDocument()
  expect(screen.queryByText(/tracking/i)).not.toBeInTheDocument()
})
