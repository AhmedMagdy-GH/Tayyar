import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { vi } from 'vitest'
import type { CurrentUser, Notification } from '../api/contracts'
import { notificationApi } from '../api/notifications'
import { queryKeys } from '../api/queryKeys'
import { renderApp, testClient } from '../test/helpers'
import { NotificationsPage } from './NotificationsPage'

const user: CurrentUser = { id: 'u', fullName: 'Mona', email: 'mona@example.com', phone: null, status: 'ACTIVE', emailVerified: false, roles: ['CUSTOMER'] }
const item: Notification = { id: 'n1', type: 'ORDER_ACCEPTED', channel: 'IN_APP', title: 'Order accepted', body: 'Your order is accepted.', relatedEntityType: 'ORDER', relatedEntityId: 'o1', read: false, createdAt: '2026-09-12T10:00:00Z', readAt: null }
function client() { const value = testClient(); value.setQueryData(queryKeys.currentUser, user); value.setQueryData(queryKeys.addresses, { items: [], page: 0, size: 100, total: 0 }); value.setQueryData(queryKeys.cart, undefined); return value }

afterEach(() => vi.restoreAllMocks())

it('renders unread state, marks one read, and uses controlled order navigation', async () => {
  vi.spyOn(notificationApi, 'list').mockResolvedValue({ items: [item], page: 0, size: 10, total: 1 })
  const mark = vi.spyOn(notificationApi, 'markRead').mockResolvedValue({ ...item, read: true, readAt: '2026-09-12T10:01:00Z' })
  renderApp(<Routes><Route path="/notifications" element={<NotificationsPage />} /></Routes>, client(), ['/notifications'])
  expect(await screen.findByText('Order accepted')).toBeInTheDocument()
  expect(screen.getByText('Unread notification')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: /View order/ })).toHaveAttribute('href', '/orders/o1')
  fireEvent.click(screen.getByRole('button', { name: 'Mark as read' }))
  await waitFor(() => expect(mark).toHaveBeenCalledWith('n1'))
})

it('marks all notifications read', async () => {
  vi.spyOn(notificationApi, 'list').mockResolvedValue({ items: [item], page: 0, size: 10, total: 1 })
  const markAll = vi.spyOn(notificationApi, 'markAllRead').mockResolvedValue({ markedRead: 1 })
  renderApp(<Routes><Route path="/notifications" element={<NotificationsPage />} /></Routes>, client(), ['/notifications'])
  await screen.findByText('Order accepted')
  fireEvent.click(screen.getByRole('button', { name: /Mark all read/ }))
  await waitFor(() => expect(markAll).toHaveBeenCalledOnce())
})
