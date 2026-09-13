import { fireEvent, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { App } from '../app/App'
import { ApiError } from '../api/client'
import type { CurrentUser, ManagedBranch, RestaurantOperationsContext, RestaurantOrderDetails, RestaurantStaff } from '../api/contracts'
import { queryKeys } from '../api/queryKeys'
import { restaurantOperationsApi } from '../api/restaurantOperations'
import { renderApp, testClient } from '../test/helpers'

const owner: CurrentUser = { id: 'owner', fullName: 'Owner', email: 'owner@example.com', phone: null, status: 'ACTIVE', emailVerified: true, roles: ['RESTAURANT_OWNER'] }
const staffUser: CurrentUser = { ...owner, id: 'staff', fullName: 'Staff', roles: ['RESTAURANT_STAFF'] }
const contexts: RestaurantOperationsContext[] = [
  { restaurantId: 'r1', restaurantName: 'Tayyar Grill', restaurantStatus: 'ACTIVE', role: 'OWNER', branches: [{ branchId: 'b1', branchName: 'Maadi', branchStatus: 'ACTIVE', operationalState: 'OPEN' }] },
  { restaurantId: 'r2', restaurantName: 'Tayyar Kitchen', restaurantStatus: 'ACTIVE', role: 'OWNER', branches: [{ branchId: 'b2', branchName: 'Zamalek', branchStatus: 'ACTIVE', operationalState: 'OPEN' }] },
]
const branch: ManagedBranch = { id: 'b1', restaurantId: 'r1', profile: { name: 'Maadi', addressLine1: 'Street 1', addressLine2: null, city: 'Cairo', region: null, postalCode: null, countryCode: 'EG', phone: null, latitude: null, longitude: null, timezone: 'Africa/Cairo', deliveryModel: 'RESTAURANT_DELIVERY' }, status: 'ACTIVE', paused: false, version: 3, createdAt: '', updatedAt: '' }
const member: RestaurantStaff = { userId: 'u1', fullName: 'Nour Ali', email: 'nour@example.com', createdAt: '2026-09-12T10:00:00Z', branches: [] }
const order: RestaurantOrderDetails = { order: { id: '12345678-1234-1234-1234-123456789012', status: 'PLACED', restaurant: { id: 'r1', name: 'Tayyar Grill' }, branch: { id: 'b1', name: 'Maadi' }, merchandiseSubtotal: 100, deliveryFee: 15, discountTotal: 0, finalTotal: 115, currency: 'EGP', version: 2, createdAt: '2026-09-12T10:00:00Z' }, items: [{ menuItemId: 'i1', name: 'Kofta', unitPrice: 100, quantity: 1, lineSubtotal: 100 }], deliveryAddress: { label: 'Home', street: 'Street', building: '1', floor: null, apartment: null, landmark: null, instructions: null, city: 'Cairo', region: null, postalCode: null, countryCode: 'EG', deliveryZoneName: 'Maadi', managedCityName: 'Cairo' }, payment: { method: 'CASH', status: 'PENDING' }, history: [] }

function client(user: CurrentUser, available = contexts) { const value = testClient(); value.setQueryData(queryKeys.currentUser, user); value.setQueryData(queryKeys.restaurantContext, available); return value }
afterEach(() => { window.localStorage.clear(); vi.restoreAllMocks() })

it('denies anonymous and Customer accounts', async () => {
  const anonymous = testClient(); anonymous.setQueryData(queryKeys.currentUser, null)
  const first = renderApp(<App />, anonymous, ['/restaurant'])
  expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
  first.unmount()
  const customer = client({ ...owner, roles: ['CUSTOMER'] }, [])
  renderApp(<App />, customer, ['/restaurant'])
  expect(await screen.findByRole('heading', { name: /Your favorites/i })).toBeInTheDocument()
})

it('permits Owner and switches multi-restaurant context', async () => {
  renderApp(<App />, client(owner), ['/restaurant/overview'])
  expect(await screen.findByText('Good work starts here, Tayyar Grill')).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Restaurant'), { target: { value: 'r2' } })
  expect(await screen.findByText('Good work starts here, Tayyar Kitchen')).toBeInTheDocument()
})

it('limits Staff navigation and branch choices to server context', async () => {
  const staffContext = [{ ...contexts[0], role: 'STAFF' as const, branches: [contexts[0].branches[0]] }]
  renderApp(<App />, client(staffUser, staffContext), ['/restaurant/orders'])
  expect(await screen.findByRole('heading', { name: 'Order queue' })).toBeInTheDocument()
  expect(screen.getByRole('option', { name: 'Maadi' })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Staff' })).not.toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Menu' })).not.toBeInTheDocument()
})

it('adds staff by normalized email and refreshes the owner list', async () => {
  vi.spyOn(restaurantOperationsApi, 'addStaff').mockResolvedValue(member)
  const value = client(owner, contexts.slice(0, 1)); value.setQueryData(queryKeys.staff('r1'), { items: [], page: 0, size: 100, total: 0 }); value.setQueryData(queryKeys.branches('r1'), { items: [branch], page: 0, size: 100, total: 1 })
  renderApp(<App />, value, ['/restaurant/staff'])
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: '  NOUR@EXAMPLE.COM ' } })
  fireEvent.click(screen.getByRole('button', { name: 'Add staff member' }))
  await waitFor(() => expect(restaurantOperationsApi.addStaff).toHaveBeenCalledWith('r1', 'nour@example.com'))
})

it('keeps duplicate membership conflict visible', async () => {
  vi.spyOn(restaurantOperationsApi, 'addStaff').mockRejectedValue(new ApiError('User is already restaurant staff', 409))
  const value = client(owner, contexts.slice(0, 1)); value.setQueryData(queryKeys.staff('r1'), { items: [], page: 0, size: 100, total: 0 }); value.setQueryData(queryKeys.branches('r1'), { items: [branch], page: 0, size: 100, total: 1 })
  renderApp(<App />, value, ['/restaurant/staff'])
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'nour@example.com' } }); fireEvent.click(screen.getByRole('button', { name: 'Add staff member' }))
  expect(await screen.findByText('User is already restaurant staff')).toBeInTheDocument()
})

it('assigns a member to a branch using the authoritative branch version', async () => {
  vi.spyOn(restaurantOperationsApi, 'assignStaff').mockResolvedValue({ ...branch, version: 4 })
  const value = client(owner, contexts.slice(0, 1)); value.setQueryData(queryKeys.staff('r1'), { items: [member], page: 0, size: 100, total: 1 }); value.setQueryData(queryKeys.branches('r1'), { items: [branch], page: 0, size: 100, total: 1 })
  renderApp(<App />, value, ['/restaurant/staff'])
  fireEvent.click(await screen.findByRole('checkbox', { name: /Maadi/ }))
  await waitFor(() => expect(restaurantOperationsApi.assignStaff).toHaveBeenCalledWith('r1', 'b1', 'u1', 3))
})

it('confirms membership removal and explains restaurant-local impact', async () => {
  vi.spyOn(restaurantOperationsApi, 'removeStaff').mockResolvedValue()
  const value = client(owner, contexts.slice(0, 1)); value.setQueryData(queryKeys.staff('r1'), { items: [member], page: 0, size: 100, total: 1 }); value.setQueryData(queryKeys.branches('r1'), { items: [branch], page: 0, size: 100, total: 1 })
  renderApp(<App />, value, ['/restaurant/staff'])
  fireEvent.click(await screen.findByRole('button', { name: 'Remove membership' }))
  expect(screen.getByText(/does not remove access to any other restaurant/i)).toBeInTheDocument()
  fireEvent.click(screen.getAllByRole('button', { name: 'Remove membership' }).at(-1)!)
  await waitFor(() => expect(restaurantOperationsApi.removeStaff).toHaveBeenCalledWith('r1', 'u1'))
})

it('renders categories, items, and unambiguous branch override values', async () => {
  const value = client(owner, contexts.slice(0, 1))
  value.setQueryData(queryKeys.menu('r1'), { id: 'm1', restaurantId: 'r1', name: 'Main menu', active: true, currency: 'EGP', version: 1, createdAt: '', updatedAt: '' })
  value.setQueryData(queryKeys.categories('r1'), { items: [{ id: 'c1', name: 'Mains', description: 'Lunch', active: true, position: 0 }], page: 0, size: 100, total: 1, version: 2 })
  value.setQueryData(queryKeys.items('r1', 'c1'), { items: [{ id: 'i1', categoryId: 'c1', name: 'Kofta', description: 'Grilled', basePrice: '100.00', active: true, available: true, position: 0 }], page: 0, size: 100, total: 1, version: 2 })
  value.setQueryData(queryKeys.branchOverrides('r1', 'b1'), { items: [{ id: 'i1', categoryId: 'c1', name: 'Kofta', description: 'Grilled', basePrice: '100.00', active: true, available: true, position: 0, priceOverride: '110.00', availabilityOverride: false, effectivePrice: '110.00', effectiveAvailable: false, currency: 'EGP' }], page: 0, size: 100, total: 1, version: 3 })
  renderApp(<App />, value, ['/restaurant/menu'])
  expect(await screen.findAllByText('Kofta')).toHaveLength(2)
  expect(screen.getByText(/Restaurant default: 100 EGP · available/)).toBeInTheDocument()
  expect(screen.getByText(/Branch override: 110 EGP · unavailable/)).toBeInTheDocument()
})

it('renders a real delivery Zone rule for editing', async () => {
  const value = client(owner, contexts.slice(0, 1))
  value.setQueryData(['geography', 'cities'], { items: [{ id: 'city1', cityId: null, name: 'Cairo', active: true, version: 0, createdAt: '', updatedAt: '' }], page: 0, size: 100, total: 1 })
  value.setQueryData(['geography', 'zones', 'city1'], { items: [{ id: 'z1', cityId: 'city1', name: 'Maadi Zone', active: true, version: 0, createdAt: '', updatedAt: '' }], page: 0, size: 100, total: 1 })
  value.setQueryData(queryKeys.deliveryRules('r1', 'b1'), { items: [{ id: 'd1', branchId: 'b1', deliveryZoneId: 'z1', currency: 'EGP', rule: { deliveryFee: '15.00', minimumOrder: '75.00', etaMinMinutes: 20, etaMaxMinutes: 35, enabled: true }, version: 2, createdAt: '', updatedAt: '' }], page: 0, size: 100, total: 1 })
  renderApp(<App />, value, ['/restaurant/delivery'])
  expect(await screen.findAllByText('Maadi Zone')).toHaveLength(2)
  expect(screen.getByText(/15 EGP fee · 75 EGP minimum/)).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
  expect(screen.getByRole('heading', { name: 'Edit zone rule' })).toBeInTheDocument()
})

it('refetches rather than retrying an order transition after a stale 409', async () => {
  vi.spyOn(restaurantOperationsApi, 'transitionOrder').mockRejectedValue(new ApiError('Order changed', 409))
  vi.spyOn(restaurantOperationsApi, 'order').mockResolvedValue(order)
  const value = client(owner, contexts.slice(0, 1)); value.setQueryData(queryKeys.restaurantOrder('r1', order.order.id), order)
  renderApp(<App />, value, [`/restaurant/orders/${order.order.id}`])
  fireEvent.click(await screen.findByRole('button', { name: /Accept order for order/ }))
  expect(await screen.findByText(/changed elsewhere/i)).toBeInTheDocument()
  expect(restaurantOperationsApi.transitionOrder).toHaveBeenCalledTimes(1)
  await waitFor(() => expect(restaurantOperationsApi.order).toHaveBeenCalled())
})

it.each([
  ['PLACED', 'Accept order', 'accept'], ['PLACED', 'Reject order', 'reject'],
  ['ACCEPTED', 'Start preparation', 'start-preparation'], ['PREPARING', 'Mark ready for pickup', 'ready-for-pickup'],
] as const)('uses the explicit %s transition action %s', async (status, label, action) => {
  const details = { ...order, order: { ...order.order, status } }
  vi.spyOn(restaurantOperationsApi, 'transitionOrder').mockResolvedValue({ ...details.order, status: action === 'accept' ? 'ACCEPTED' : action === 'reject' ? 'REJECTED' : action === 'start-preparation' ? 'PREPARING' : 'READY_FOR_PICKUP' })
  const value = client(owner, contexts.slice(0, 1)); value.setQueryData(queryKeys.restaurantOrder('r1', order.order.id), details)
  renderApp(<App />, value, [`/restaurant/orders/${order.order.id}`])
  if (action === 'reject') fireEvent.change(await screen.findByLabelText('Rejection reason'), { target: { value: 'Sold out' } })
  fireEvent.click(await screen.findByRole('button', { name: new RegExp(label) }))
  await waitFor(() => expect(restaurantOperationsApi.transitionOrder).toHaveBeenCalledWith(order.order.id, action, 2, action === 'reject' ? 'Sold out' : ''))
})
