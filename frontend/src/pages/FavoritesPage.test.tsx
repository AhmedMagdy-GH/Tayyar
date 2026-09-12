import { fireEvent, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import type { CurrentUser, Favorite } from '../api/contracts'
import { favoriteApi } from '../api/favorites'
import { queryKeys } from '../api/queryKeys'
import { renderApp, testClient } from '../test/helpers'
import { FavoritesPage } from './FavoritesPage'

const user: CurrentUser = { id: 'u1', fullName: 'Mona Hassan', email: 'mona@example.com', phone: null, status: 'ACTIVE', emailVerified: true, roles: ['CUSTOMER'] }
const favorite: Favorite = { restaurantId: 'r1', name: 'Tayyar Grill', description: 'Egyptian comfort food', favoritedAt: '2026-09-12T10:00:00Z' }

function client() {
  const value = testClient()
  value.setQueryData(queryKeys.currentUser, user)
  value.setQueryData(queryKeys.addresses, { items: [], page: 0, size: 100, total: 0 })
  value.setQueryData(queryKeys.cart, null)
  value.setQueryData(queryKeys.notificationBadge, { items: [], page: 0, size: 1, total: 0 })
  return value
}

afterEach(() => vi.restoreAllMocks())

it('renders the server favorites list and removes a favorite', async () => {
  vi.spyOn(favoriteApi, 'list').mockResolvedValue({ items: [favorite], page: 0, size: 12, total: 1 })
  const remove = vi.spyOn(favoriteApi, 'remove').mockResolvedValue()
  renderApp(<FavoritesPage />, client(), ['/favorites'])
  expect(await screen.findByRole('heading', { name: 'Tayyar Grill' })).toBeInTheDocument()
  expect(screen.getByText('Egyptian comfort food')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Remove Tayyar Grill from favorites' }))
  await waitFor(() => expect(remove).toHaveBeenCalledWith('r1'))
})

it('renders an honest empty favorites state', async () => {
  vi.spyOn(favoriteApi, 'list').mockResolvedValue({ items: [], page: 0, size: 12, total: 0 })
  renderApp(<FavoritesPage />, client(), ['/favorites'])
  expect(await screen.findByRole('heading', { name: 'No favorites yet' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Browse restaurants' })).toHaveAttribute('href', '/')
})
