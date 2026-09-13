import { fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { CurrentUser, DriverCompletion, DriverOrder, DriverProfile } from '../api/contracts'
import { driverOperationsApi } from '../api/driverOperations'
import { queryKeys } from '../api/queryKeys'
import { App } from '../app/App'
import { renderApp, testClient } from '../test/helpers'

const driverUser: CurrentUser = { id: 'driver-1', fullName: 'Nour Hassan', email: 'nour@example.com', phone: null, status: 'ACTIVE', emailVerified: true, roles: ['DRIVER'] }
const profile = (state: DriverProfile['state'], version = 2): DriverProfile => ({ driverId: 'driver-1', state, version, updatedAt: '2026-09-13T08:00:00Z' })
const readyOrder: DriverOrder = {
  orderId: '11111111-2222-3333-4444-555555555555', status: 'READY_FOR_PICKUP', orderVersion: 7,
  assignmentId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', assignmentVersion: 0,
  pickup: { restaurantId: 'restaurant-1', restaurantName: 'Cairo Kitchen', branchId: 'branch-1', branchName: 'Zamalek', addressLine1: '12 Brazil Street', city: 'Cairo', phone: '+201000000000' },
  destination: { label: 'Home', street: 'Tahrir Street', building: '8', floor: '3', apartment: '7', landmark: 'Museum gate', instructions: 'Ring once', city: 'Cairo', region: 'Downtown', postalCode: null, countryCode: 'EG' },
  paymentMethod: 'CASH', cashAmountToCollect: 245, currency: 'EGP', assignedAt: '2026-09-13T08:05:00Z',
}
const outOrder: DriverOrder = { ...readyOrder, status: 'OUT_FOR_DELIVERY', orderVersion: 8 }
const completion: DriverCompletion = { orderId: readyOrder.orderId, orderStatus: 'DELIVERED', orderVersion: 9, assignmentId: readyOrder.assignmentId, assignmentStatus: 'COMPLETED', assignmentVersion: 1, driverState: 'AVAILABLE', driverVersion: 4, paymentMethod: 'CASH', paymentStatus: 'PAID' }

function driverClient() {
  const client = testClient()
  client.setQueryData(queryKeys.currentUser, driverUser)
  return client
}

function mockDashboard(state: DriverProfile['state'], orders: DriverOrder[] = []) {
  vi.spyOn(driverOperationsApi, 'profile').mockResolvedValue(profile(state))
  vi.spyOn(driverOperationsApi, 'activeOrders').mockResolvedValue({ items: orders, page: 0, size: 20, total: orders.length })
}

afterEach(() => vi.restoreAllMocks())

describe('Driver routing and operational state', () => {
  it('redirects an anonymous visitor to login', async () => {
    const client = testClient()
    client.setQueryData(queryKeys.currentUser, null)
    renderApp(<App />, client, ['/driver'])
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
  })

  it('denies a Customer account', async () => {
    const client = testClient()
    client.setQueryData(queryKeys.currentUser, { ...driverUser, roles: ['CUSTOMER'] })
    renderApp(<App />, client, ['/driver'])
    expect(await screen.findByText('This area is for Driver accounts')).toBeInTheDocument()
  })

  it('returns a revoked Driver session to sign-in', async () => {
    vi.spyOn(driverOperationsApi, 'profile').mockRejectedValue(new ApiError('expired', 401))
    renderApp(<App />, driverClient(), ['/driver'])
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(screen.getByText('Sign in to continue to Driver operations.')).toBeInTheDocument()
  })

  it.each([
    ['OFFLINE', 'Go available'],
    ['AVAILABLE', 'Go offline'],
    ['BUSY', null],
  ] as const)('shows only the valid %s action', async (state, action) => {
    mockDashboard(state)
    renderApp(<App />, driverClient(), ['/driver'])
    expect(await screen.findByRole('heading', { name: state === 'BUSY' ? 'On a delivery' : state[0] + state.slice(1).toLowerCase() })).toBeInTheDocument()
    if (action) expect(screen.getByRole('button', { name: action })).toBeInTheDocument()
    else {
      expect(screen.queryByRole('button', { name: 'Go available' })).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Go offline' })).not.toBeInTheDocument()
    }
  })

  it('shows the no-assignment state without requesting IDs', async () => {
    mockDashboard('AVAILABLE')
    renderApp(<App />, driverClient(), ['/driver'])
    expect(await screen.findByText('No active assignment')).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
  })
})

describe('Driver delivery transitions', () => {
  function mockDetail(order = readyOrder) {
    vi.spyOn(driverOperationsApi, 'profile').mockResolvedValue(profile('BUSY'))
    vi.spyOn(driverOperationsApi, 'activeOrders').mockResolvedValue({ items: [order], page: 0, size: 20, total: 1 })
    return vi.spyOn(driverOperationsApi, 'order').mockResolvedValue(order)
  }

  it('shows only backend-provided, privacy-safe operational fields', async () => {
    mockDetail()
    renderApp(<App />, driverClient(), [`/driver/deliveries/${readyOrder.orderId}`])
    expect(await screen.findByRole('heading', { name: 'Cairo Kitchen' })).toBeInTheDocument()
    expect(screen.getByText('245 EGP')).toBeInTheDocument()
    expect(screen.getByText('Ring once')).toBeInTheDocument()
    expect(screen.queryByText(/@/)).not.toBeInTheDocument()
  })

  it('uses returned versions for pickup and renders the authoritative next action', async () => {
    const orderRead = mockDetail()
    orderRead.mockResolvedValue(outOrder).mockResolvedValueOnce(readyOrder)
    const pickup = vi.spyOn(driverOperationsApi, 'pickup').mockResolvedValue(outOrder)
    renderApp(<App />, driverClient(), [`/driver/deliveries/${readyOrder.orderId}`])
    fireEvent.click(await screen.findByRole('button', { name: 'Order picked up' }))
    await waitFor(() => expect(pickup).toHaveBeenCalledWith(readyOrder))
    expect(await screen.findByRole('button', { name: 'Complete delivery' })).toBeInTheDocument()
  })

  it('shows server-confirmed CASH completion and Driver release', async () => {
    mockDetail(outOrder)
    vi.spyOn(driverOperationsApi, 'deliver').mockResolvedValue(completion)
    renderApp(<App />, driverClient(), [`/driver/deliveries/${readyOrder.orderId}`])
    fireEvent.click(await screen.findByRole('button', { name: 'Complete delivery' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, complete delivery' }))
    expect(await screen.findByRole('heading', { name: 'Delivery complete' })).toBeInTheDocument()
    expect(screen.getByText('Cash · Paid')).toBeInTheDocument()
    expect(screen.getByText('Available')).toBeInTheDocument()
  })

  it('does not retry a stale pickup and reports the refreshed state', async () => {
    mockDetail()
    const pickup = vi.spyOn(driverOperationsApi, 'pickup').mockRejectedValue(new ApiError('stale', 409))
    renderApp(<App />, driverClient(), [`/driver/deliveries/${readyOrder.orderId}`])
    fireEvent.click(await screen.findByRole('button', { name: 'Order picked up' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('no action was retried')
    expect(pickup).toHaveBeenCalledTimes(1)
  })

  it('treats an uncertain network result as unconfirmed', async () => {
    mockDetail(outOrder)
    vi.spyOn(driverOperationsApi, 'deliver').mockRejectedValue(new TypeError('Network error'))
    renderApp(<App />, driverClient(), [`/driver/deliveries/${readyOrder.orderId}`])
    fireEvent.click(await screen.findByRole('button', { name: 'Complete delivery' }))
    fireEvent.click(screen.getByRole('button', { name: 'Yes, complete delivery' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('couldn’t confirm the result')
    expect(screen.queryByRole('heading', { name: 'Delivery complete' })).not.toBeInTheDocument()
  })

  it('handles a terminal or foreign delivery as unavailable', async () => {
    vi.spyOn(driverOperationsApi, 'profile').mockResolvedValue(profile('AVAILABLE'))
    vi.spyOn(driverOperationsApi, 'order').mockRejectedValue(new ApiError('not found', 404))
    renderApp(<App />, driverClient(), [`/driver/deliveries/${readyOrder.orderId}`])
    expect(await screen.findByText('This delivery is no longer active')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /complete/i })).not.toBeInTheDocument()
  })
})
