import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { vi } from 'vitest'
import { ApiError } from '../api/client'
import type { CurrentUser, OrderDetails } from '../api/contracts'
import { orderApi } from '../api/orders'
import { queryKeys } from '../api/queryKeys'
import { reviewApi } from '../api/reviews'
import { renderApp, testClient } from '../test/helpers'
import { OrderDetailPage } from './OrderDetailPage'

const user: CurrentUser = { id: 'u', fullName: 'Mona', email: 'mona@example.com', phone: null, status: 'ACTIVE', emailVerified: false, roles: ['CUSTOMER'] }
const details: OrderDetails = { order: { id: 'o1', status: 'PLACED', restaurant: { id: 'r', name: 'Tayyar Grill' }, branch: { id: 'b', name: 'Maadi' }, merchandiseSubtotal: 100, deliveryFee: 15, discountTotal: 5, finalTotal: 110, currency: 'EGP', version: 3, createdAt: '2026-09-12T10:00:00Z' }, items: [{ menuItemId: 'i', name: 'Kofta Bowl', unitPrice: 100, quantity: 1, lineSubtotal: 100 }], deliveryAddress: { label: 'Home', street: 'Road 9', building: '2', floor: null, apartment: null, landmark: null, instructions: null, city: 'Cairo', region: null, postalCode: null, countryCode: 'EG', deliveryZoneName: null, managedCityName: 'Cairo' }, payment: { method: 'CASH', status: 'PENDING' }, history: [{ previousStatus: null, newStatus: 'PLACED', reason: null, occurredAt: '2026-09-12T10:00:00Z' }] }
function client() { const value = testClient(); value.setQueryData(queryKeys.currentUser, user); value.setQueryData(queryKeys.addresses, { items: [], page: 0, size: 100, total: 0 }); value.setQueryData(queryKeys.cart, undefined); return value }

afterEach(() => vi.restoreAllMocks())

it('renders snapshots and cancels only after confirmation', async () => {
  vi.spyOn(orderApi, 'details').mockResolvedValue(details)
  const cancel = vi.spyOn(orderApi, 'cancel').mockResolvedValue({ ...details.order, status: 'CANCELLED', version: 4 })
  renderApp(<Routes><Route path="/orders/:orderId" element={<OrderDetailPage />} /></Routes>, client(), ['/orders/o1'])
  expect(await screen.findByText('Kofta Bowl')).toBeInTheDocument()
  expect(screen.getByText(/Server payment status/)).toHaveTextContent('PENDING')
  fireEvent.click(screen.getByRole('button', { name: 'Cancel order' }))
  expect(screen.getByRole('alertdialog')).toBeInTheDocument()
  fireEvent.click(screen.getAllByRole('button', { name: 'Cancel order' })[1])
  await waitFor(() => expect(cancel).toHaveBeenCalledWith('o1', 3, 'Customer requested cancellation'))
})

it('refetches authoritative state after a cancellation conflict', async () => {
  const get = vi.spyOn(orderApi, 'details').mockResolvedValue(details)
  vi.spyOn(orderApi, 'cancel').mockRejectedValue(new ApiError('conflict', 409))
  renderApp(<Routes><Route path="/orders/:orderId" element={<OrderDetailPage />} /></Routes>, client(), ['/orders/o1'])
  fireEvent.click(await screen.findByRole('button', { name: 'Cancel order' }))
  fireEvent.click(screen.getAllByRole('button', { name: 'Cancel order' })[1])
  expect(await screen.findByText(/reloaded its current status/i)).toBeInTheDocument()
  expect(get.mock.calls.length).toBeGreaterThan(1)
})

it('shows review controls only for delivered orders and validates rating', async () => {
  vi.spyOn(orderApi, 'details').mockResolvedValue({ ...details, order: { ...details.order, status: 'DELIVERED' } })
  vi.spyOn(reviewApi, 'byOrder').mockRejectedValue(new ApiError('missing', 404))
  const create = vi.spyOn(reviewApi, 'create').mockResolvedValue({ id: 'rev', orderId: 'o1', restaurantId: 'r', branchId: 'b', rating: 5, comment: null, status: 'VISIBLE', version: 0, createdAt: '', updatedAt: '' })
  renderApp(<Routes><Route path="/orders/:orderId" element={<OrderDetailPage />} /></Routes>, client(), ['/orders/o1'])
  const submit = await screen.findByRole('button', { name: 'Submit review' })
  expect(submit).toBeDisabled()
  fireEvent.click(screen.getByRole('button', { name: '5 stars' }))
  fireEvent.click(submit)
  await waitFor(() => expect(create).toHaveBeenCalledWith('o1', 5, ''))
})
