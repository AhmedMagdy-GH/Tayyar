import { fireEvent, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { addressApi } from '../api/addresses'
import type { Address, CurrentUser } from '../api/contracts'
import { queryKeys } from '../api/queryKeys'
import { renderApp, testClient } from '../test/helpers'
import { AddressesPage } from './AddressesPage'

const user: CurrentUser = { id: 'u', fullName: 'Mona', email: 'mona@example.com', phone: null, status: 'ACTIVE', emailVerified: false, roles: ['CUSTOMER'] }
const address: Address = { id: 'a1', profile: { label: 'Work', street: 'Tahrir Street', building: '12', floor: null, apartment: null, landmark: null, instructions: null, city: 'Cairo', region: null, postalCode: null, countryCode: 'EG', latitude: null, longitude: null }, deliveryZoneId: null, isDefault: false, version: 4, createdAt: '', updatedAt: '' }

it('uses the address version when setting a real default address', async () => {
  const client = testClient()
  client.setQueryData(queryKeys.currentUser, user)
  client.setQueryData(queryKeys.cart, undefined)
  client.setQueryData(queryKeys.addresses, { items: [address], page: 0, size: 100, total: 1 })
  client.setQueryData(queryKeys.zones, { items: [], page: 0, size: 100, total: 0 })
  vi.spyOn(addressApi, 'setDefault').mockResolvedValue({ ...address, isDefault: true, version: 5 })
  vi.spyOn(addressApi, 'list').mockResolvedValue({ items: [{ ...address, isDefault: true, version: 5 }], page: 0, size: 100, total: 1 })
  renderApp(<AddressesPage />, client, ['/addresses'])
  fireEvent.click(screen.getByRole('button', { name: 'Make default' }))
  await waitFor(() => expect(addressApi.setDefault).toHaveBeenCalledWith('a1', 4))
  expect(await screen.findByText('Default address updated.')).toBeInTheDocument()
})
