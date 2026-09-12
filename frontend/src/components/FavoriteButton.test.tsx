import { fireEvent, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { useLocation } from 'react-router-dom'
import type { CurrentUser, Restaurant } from '../api/contracts'
import { favoriteApi } from '../api/favorites'
import { queryKeys } from '../api/queryKeys'
import { renderApp, testClient } from '../test/helpers'
import { FavoriteButton } from './FavoriteButton'
import { RestaurantCard } from './RestaurantCard'

const user: CurrentUser = { id: 'u1', fullName: 'Mona', email: 'mona@example.com', phone: null, status: 'ACTIVE', emailVerified: false, roles: ['CUSTOMER'] }
const restaurant: Restaurant = { id: 'r1', name: 'Tayyar Grill', description: 'Fresh grills', branchCount: 1, openNow: true, serviceable: true, minimumDeliveryFee: 20, minimumOrder: 50, minimumEtaMinutes: 25, currency: 'EGP' }
const LocationProbe = () => <span data-testid="location">{useLocation().pathname}</span>

afterEach(() => vi.restoreAllMocks())

it('signposts authentication for an anonymous favorite click', () => {
  const client = testClient()
  client.setQueryData(queryKeys.currentUser, null)
  renderApp(<><FavoriteButton restaurantId="r1" restaurantName="Tayyar Grill" /><LocationProbe /></>, client, ['/'])
  fireEvent.click(screen.getByRole('button', { name: 'Save Tayyar Grill to favorites' }))
  expect(screen.getByTestId('location')).toHaveTextContent('/login')
})

it('adds and removes favorites with authoritative state', async () => {
  const client = testClient()
  client.setQueryData(queryKeys.currentUser, user)
  vi.spyOn(favoriteApi, 'list')
    .mockResolvedValueOnce({ items: [], page: 0, size: 100, total: 0 })
    .mockResolvedValueOnce({ items: [{ restaurantId: 'r1', name: 'Tayyar Grill', description: 'Fresh grills', favoritedAt: '2026-09-12T10:00:00Z' }], page: 0, size: 100, total: 1 })
    .mockResolvedValue({ items: [], page: 0, size: 100, total: 0 })
  const add = vi.spyOn(favoriteApi, 'add').mockResolvedValue()
  const remove = vi.spyOn(favoriteApi, 'remove').mockResolvedValue()
  renderApp(<FavoriteButton restaurantId="r1" restaurantName="Tayyar Grill" description="Fresh grills" />, client)
  const save = await screen.findByRole('button', { name: 'Save Tayyar Grill to favorites' })
  await waitFor(() => expect(save).toBeEnabled())
  fireEvent.click(save)
  await waitFor(() => expect(add).toHaveBeenCalledWith('r1'))
  const saved = await screen.findByRole('button', { name: 'Remove Tayyar Grill from favorites' })
  expect(saved).toHaveAttribute('aria-pressed', 'true')
  fireEvent.click(saved)
  await waitFor(() => expect(remove).toHaveBeenCalledWith('r1'))
})

it('uses one bounded favorites request for multiple discovery cards', async () => {
  const client = testClient()
  client.setQueryData(queryKeys.currentUser, user)
  const list = vi.spyOn(favoriteApi, 'list').mockResolvedValue({ items: [], page: 0, size: 100, total: 0 })
  renderApp(<>{[restaurant, { ...restaurant, id: 'r2', name: 'Pizza House' }, { ...restaurant, id: 'r3', name: 'Koshary Corner' }].map((item) => <RestaurantCard key={item.id} restaurant={item} />)}</>, client)
  await waitFor(() => expect(list).toHaveBeenCalledTimes(1))
  expect(list).toHaveBeenCalledWith({ page: 0, size: 100 })
})
