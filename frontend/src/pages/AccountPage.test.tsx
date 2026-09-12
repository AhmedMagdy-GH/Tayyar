import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { vi } from 'vitest'
import type { CurrentUser } from '../api/contracts'
import { authApi } from '../api/auth'
import { queryKeys } from '../api/queryKeys'
import { renderApp, testClient } from '../test/helpers'
import { AccountPage } from './AccountPage'

const user: CurrentUser = { id: 'u1', fullName: 'Mona Hassan', email: 'mona@example.com', phone: '+201234567890', status: 'ACTIVE', emailVerified: true, roles: ['CUSTOMER'] }

function client() {
  const value = testClient()
  value.setQueryData(queryKeys.currentUser, user)
  value.setQueryData(queryKeys.addresses, { items: [], page: 0, size: 100, total: 0 })
  value.setQueryData(queryKeys.cart, null)
  value.setQueryData(queryKeys.notificationBadge, { items: [], page: 0, size: 1, total: 0 })
  return value
}

afterEach(() => vi.restoreAllMocks())

it('shows only supported account fields and no fake editing controls', () => {
  renderApp(<AccountPage />, client(), ['/account'])
  expect(screen.getByRole('heading', { name: 'Mona Hassan' })).toBeInTheDocument()
  expect(screen.getByText('mona@example.com')).toBeInTheDocument()
  expect(screen.getByText('+201234567890')).toBeInTheDocument()
  expect(screen.getByText('Profile editing is not available yet')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /save/i })).not.toBeInTheDocument()
  const hub = screen.getByRole('region', { name: 'Customer shortcuts' })
  for (const name of ['Orders', 'Saved addresses', 'Favorites', 'Notifications']) expect(within(hub).getByRole('link', { name: new RegExp(`^${name}`) })).toBeInTheDocument()
})

it('logs out from account and clears customer-scoped cache', async () => {
  const value = client()
  value.setQueryData(queryKeys.favoritesAll, { items: [], page: 0, size: 100, total: 0 })
  const logout = vi.spyOn(authApi, 'logout').mockResolvedValue()
  renderApp(<AccountPage />, value, ['/account'])
  fireEvent.click(screen.getByRole('button', { name: 'Sign out' }))
  await waitFor(() => expect(logout).toHaveBeenCalledOnce())
  expect(value.getQueryData(queryKeys.currentUser)).toBeNull()
  expect(value.getQueryData(queryKeys.favoritesAll)).toBeUndefined()
})
