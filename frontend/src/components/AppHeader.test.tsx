import { fireEvent, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { authApi } from '../api/auth'
import { queryKeys } from '../api/queryKeys'
import type { CurrentUser } from '../api/contracts'
import { renderApp, testClient } from '../test/helpers'
import { AppHeader } from './AppHeader'

const user: CurrentUser = { id: 'user-1', fullName: 'Mona Hassan', email: 'mona@example.com', phone: null, status: 'ACTIVE', emailVerified: false, roles: ['CUSTOMER'] }

describe('AppHeader session states', () => {
  it('shows login and registration actions for an anonymous visitor', () => {
    const client = testClient()
    client.setQueryData(queryKeys.currentUser, null)
    renderApp(<AppHeader />, client)
    expect(screen.getByRole('link', { name: 'Log in' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Create account' })).toBeInTheDocument()
  })

  it('shows customer cart and clears sensitive cache after server logout', async () => {
    const client = testClient()
    client.setQueryData(queryKeys.currentUser, user)
    client.setQueryData(queryKeys.addresses, { items: [], page: 0, size: 100, total: 0 })
    client.setQueryData(queryKeys.cart, { id: 'cart-1', branch: { id: 'branch-1', restaurantId: 'r-1', name: 'Zamalek', restaurantName: 'Tayyar Grill', state: 'ACTIVE', openNow: true }, items: [{ id: 'line-1', menuItemId: 'item-1', name: 'Burger', quantity: 2, acknowledgedUnitPrice: 100, currentUnitPrice: 100, priceChanged: false, currentlyAvailable: true, lineSubtotal: 200, version: 0 }], merchandiseSubtotal: 200, currency: 'EGP', version: 0 })
    const logout = vi.spyOn(authApi, 'logout').mockResolvedValue()
    renderApp(<AppHeader />, client)
    expect(screen.getByRole('link', { name: 'Cart with 2 items' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Open account menu' }))
    fireEvent.click(screen.getAllByRole('menuitem', { name: 'Sign out' })[0])
    await waitFor(() => expect(logout).toHaveBeenCalledOnce())
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull()
    expect(client.getQueryData(queryKeys.cart)).toBeUndefined()
  })
})
