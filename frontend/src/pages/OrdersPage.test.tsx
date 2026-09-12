import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { vi } from 'vitest'
import { orderApi } from '../api/orders'
import { queryKeys } from '../api/queryKeys'
import type { CurrentUser, OrderSummary } from '../api/contracts'
import { renderApp, testClient } from '../test/helpers'
import { OrdersPage } from './OrdersPage'

const user: CurrentUser = { id: 'u', fullName: 'Mona', email: 'mona@example.com', phone: null, status: 'ACTIVE', emailVerified: false, roles: ['CUSTOMER'] }
const order: OrderSummary = { id: '12345678-1234-1234-1234-123456789012', status: 'OUT_FOR_DELIVERY', restaurant: { id: 'r', name: 'Tayyar Grill' }, branch: { id: 'b', name: 'Maadi' }, merchandiseSubtotal: 100, deliveryFee: 15, discountTotal: 0, finalTotal: 115, currency: 'EGP', version: 2, createdAt: '2026-09-12T10:00:00Z' }

function client() { const value = testClient(); value.setQueryData(queryKeys.currentUser, user); value.setQueryData(queryKeys.addresses, { items: [], page: 0, size: 100, total: 0 }); value.setQueryData(queryKeys.cart, undefined); return value }

afterEach(() => vi.restoreAllMocks())

it('renders the customer order list with server totals and status', async () => {
  vi.spyOn(orderApi, 'list').mockResolvedValue({ items: [order], page: 0, size: 10, total: 1 })
  renderApp(<Routes><Route path="/orders" element={<OrdersPage />} /></Routes>, client(), ['/orders'])
  expect(await screen.findByText('Tayyar Grill')).toBeInTheDocument()
  expect(screen.getByText('Out for delivery')).toBeInTheDocument()
  expect(screen.getByText('115 EGP')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /View order/ })).toHaveAttribute('href', `/orders/${order.id}`)
})

it('renders an empty order history', async () => {
  vi.spyOn(orderApi, 'list').mockResolvedValue({ items: [], page: 0, size: 10, total: 0 })
  renderApp(<Routes><Route path="/orders" element={<OrdersPage />} /></Routes>, client(), ['/orders'])
  expect(await screen.findByRole('heading', { name: 'Your first order starts here' })).toBeInTheDocument()
})
