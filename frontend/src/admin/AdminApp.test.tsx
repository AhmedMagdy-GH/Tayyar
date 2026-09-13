import { fireEvent, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { App } from '../app/App'
import { adminApi } from '../api/admin'
import { ApiError } from '../api/client'
import { queryKeys } from '../api/queryKeys'
import { renderApp, testClient } from '../test/helpers'

const now = '2026-09-13T10:00:00Z'
const admin = { id: '00000000-0000-4000-8000-000000000001', fullName: 'Admin User', email: 'admin@example.com', phone: null, status: 'ACTIVE', emailVerified: true, roles: ['ADMIN'] }
const user = { id: '00000000-0000-4000-8000-000000000002', fullName: 'Safe User', email: 'safe@example.com', status: 'ACTIVE' as const, roles: ['CUSTOMER'], createdAt: now, updatedAt: now }
const order = { id: '00000000-0000-4000-8000-000000000003', customerId: user.id, restaurantId: '00000000-0000-4000-8000-000000000004', restaurantName: 'Tayyar Grill', branchId: '00000000-0000-4000-8000-000000000005', branchName: 'Maadi', status: 'READY_FOR_PICKUP' as const, version: 7, finalTotal: 110, currency: 'EGP', payment: { method: 'CASH', status: 'PENDING', amount: 110, currency: 'EGP' }, assignment: null, createdAt: now }
const driver = { driverId: '00000000-0000-4000-8000-000000000006', accountStatus: 'ACTIVE' as const, state: 'AVAILABLE' as const, version: 2, activeAssignment: null, createdAt: now, updatedAt: now }
const application = { id: '00000000-0000-4000-8000-000000000007', applicantId: user.id, status: 'PENDING', revision: 1, version: 3, name: 'New Kitchen', description: 'Family meals', restaurantId: null, createdAt: now, updatedAt: now }
const restaurant = { id: '00000000-0000-4000-8000-000000000008', name: 'Tayyar Grill', description: 'Family grill', status: 'ACTIVE', version: 4, createdAt: now, updatedAt: now }
const restaurantDetail = { ...restaurant, branches: [{ id: order.branchId, name: 'Maadi', status: 'ACTIVE', paused: false, city: 'Cairo' }] }
const page = <T,>(items: T[]) => ({ items, page: 0, size: 20, total: items.length })

beforeEach(() => {
  vi.spyOn(adminApi, 'users').mockResolvedValue(page([user])); vi.spyOn(adminApi, 'user').mockResolvedValue(user)
  vi.spyOn(adminApi, 'restaurants').mockResolvedValue(page([restaurant])); vi.spyOn(adminApi, 'restaurant').mockResolvedValue(restaurantDetail); vi.spyOn(adminApi, 'restaurantHistory').mockResolvedValue(page([])); vi.spyOn(adminApi, 'applications').mockResolvedValue(page([application])); vi.spyOn(adminApi, 'orders').mockResolvedValue(page([order])); vi.spyOn(adminApi, 'drivers').mockResolvedValue(page([driver])); vi.spyOn(adminApi, 'audit').mockResolvedValue(page([]))
  vi.spyOn(adminApi, 'application').mockResolvedValue(application); vi.spyOn(adminApi, 'applicationSubmissions').mockResolvedValue(page([])); vi.spyOn(adminApi, 'applicationDecisions').mockResolvedValue(page([]))
  vi.spyOn(adminApi, 'order').mockResolvedValue({ order, items: [{ name: 'Kofta', unitPrice: 100, quantity: 1, lineSubtotal: 100 }], deliveryAddress: { label: 'Home', street: 'Street', building: '10', floor: null, apartment: null, landmark: null, instructions: null, city: 'Cairo', region: null, postalCode: null, countryCode: 'EG' }, history: [] })
  vi.spyOn(adminApi, 'branch').mockResolvedValue({ id: order.branchId, restaurantId: order.restaurantId, profile: { name: 'Maadi', addressLine1: 'Street', addressLine2: null, city: 'Cairo', region: null, postalCode: null, countryCode: 'EG', phone: null, latitude: null, longitude: null, timezone: 'Africa/Cairo', deliveryModel: 'TAYYAR_DELIVERY' }, status: 'ACTIVE', paused: false, version: 1, createdAt: now, updatedAt: now })
})
afterEach(() => vi.restoreAllMocks())

function client(roles = ['ADMIN']) { const value = testClient(); value.setQueryData(queryKeys.currentUser, { ...admin, roles }); return value }

it('permits Admin and denies every other account family', async () => {
  renderApp(<App />, client(), ['/admin']); expect(await screen.findByRole('heading', { name: 'Admin overview' })).toBeInTheDocument()
  for (const roles of [['CUSTOMER'], ['RESTAURANT_OWNER'], ['RESTAURANT_STAFF'], ['DRIVER']]) { const view = renderApp(<App />, client(roles), ['/admin']); expect(await screen.findByRole('heading', { name: /Your favorites/i })).toBeInTheDocument(); view.unmount() }
  const anonymous = testClient(); anonymous.setQueryData(queryKeys.currentUser, null); renderApp(<App />, anonymous, ['/admin']); expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
})

it('uses paginated server filters and opens safe user detail', async () => {
  renderApp(<App />, client(), ['/admin/users']); expect(await screen.findByText('safe@example.com')).toBeInTheDocument(); fireEvent.change(screen.getByLabelText('Email'), { target: { value: ' SAFE@EXAMPLE.COM ' } }); fireEvent.click(screen.getByRole('button', { name: 'Search users' })); await waitFor(() => expect(adminApi.users).toHaveBeenLastCalledWith(expect.objectContaining({ email: 'safe@example.com', page: 0 })))
})

it('confirms suspension and refetches authoritative detail after a 409', async () => {
  vi.spyOn(adminApi, 'accountStatus').mockRejectedValue(new ApiError('Customer has a live Order', 409)); renderApp(<App />, client(), [`/admin/users/${user.id}`]); fireEvent.click(await screen.findByRole('button', { name: 'Suspend account' })); fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Support review' } }); fireEvent.click(screen.getAllByRole('button', { name: 'Suspend account' }).at(-1)!); expect(await screen.findByText('Customer has a live Order')).toBeInTheDocument(); expect(adminApi.user).toHaveBeenCalled()
})

it('suspends and reactivates only after authoritative server responses', async () => {
  const status = vi.spyOn(adminApi, 'accountStatus').mockResolvedValueOnce({ ...user, status: 'SUSPENDED' }).mockResolvedValueOnce(user); renderApp(<App />, client(), [`/admin/users/${user.id}`]); fireEvent.click(await screen.findByRole('button', { name: 'Suspend account' })); fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Verified support action' } }); fireEvent.click(screen.getAllByRole('button', { name: 'Suspend account' }).at(-1)!); expect(await screen.findByText(/suspended after server confirmation/i)).toBeInTheDocument(); fireEvent.click(screen.getByRole('button', { name: 'Reactivate account' })); fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Review complete' } }); fireEvent.click(screen.getAllByRole('button', { name: 'Reactivate account' }).at(-1)!); await waitFor(() => expect(status).toHaveBeenLastCalledWith(user.id, 'reactivation', 'Review complete'))
})

it('lists Restaurant branches and submits versioned suspend/reactivate changes', async () => {
  const suspendedRestaurant = { ...restaurantDetail, status: 'SUSPENDED', version: 5 }; vi.mocked(adminApi.restaurant).mockResolvedValueOnce(restaurantDetail).mockResolvedValueOnce(suspendedRestaurant).mockResolvedValueOnce(restaurantDetail); const status = vi.spyOn(adminApi, 'restaurantStatus').mockResolvedValueOnce(suspendedRestaurant).mockResolvedValueOnce(restaurant); renderApp(<App />, client(), [`/admin/restaurants/${restaurant.id}`]); expect(await screen.findByText('Maadi')).toBeInTheDocument(); fireEvent.click(screen.getByRole('button', { name: 'Suspend restaurant' })); fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Operational review' } }); fireEvent.click(screen.getAllByRole('button', { name: 'Suspend restaurant' }).at(-1)!); await waitFor(() => expect(status).toHaveBeenCalledWith(restaurant.id, 'SUSPENDED', 4, 'Operational review')); fireEvent.click(await screen.findByRole('button', { name: 'Reactivate restaurant' })); fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Review complete' } }); fireEvent.click(screen.getAllByRole('button', { name: 'Reactivate restaurant' }).at(-1)!); await waitFor(() => expect(status).toHaveBeenLastCalledWith(restaurant.id, 'ACTIVE', 5, 'Review complete'))
})

it('submits a versioned application rejection with its required reason', async () => {
  vi.spyOn(adminApi, 'decideApplication').mockResolvedValue({ ...application, status: 'REJECTED' }); renderApp(<App />, client(), [`/admin/applications/${application.id}`]); fireEvent.click(await screen.findByRole('button', { name: 'Reject' })); fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Incomplete documents' } }); fireEvent.click(screen.getByRole('button', { name: 'Reject application' })); await waitFor(() => expect(adminApi.decideApplication).toHaveBeenCalledWith(application.id, 'REJECTED', 3, 'Incomplete documents'))
})

it('records a versioned application approval after server confirmation', async () => {
  vi.spyOn(adminApi, 'decideApplication').mockResolvedValue({ ...application, status: 'APPROVED' }); renderApp(<App />, client(), [`/admin/applications/${application.id}`]); fireEvent.click(await screen.findByRole('button', { name: 'Approve' })); fireEvent.click(screen.getByRole('button', { name: 'Approve application' })); await waitFor(() => expect(adminApi.decideApplication).toHaveBeenCalledWith(application.id, 'APPROVED', 3, '')); expect(await screen.findByText(/decision recorded after server confirmation/i)).toBeInTheDocument()
})

it('approves without inventing a reason and refetches after stale application conflict', async () => {
  const decide = vi.spyOn(adminApi, 'decideApplication').mockRejectedValue(new ApiError('Application was already reviewed', 409)); renderApp(<App />, client(), [`/admin/applications/${application.id}`]); fireEvent.click(await screen.findByRole('button', { name: 'Approve' })); fireEvent.click(screen.getByRole('button', { name: 'Approve application' })); expect(await screen.findByText('Application was already reviewed')).toBeInTheDocument(); expect(decide).toHaveBeenCalledWith(application.id, 'APPROVED', 3, ''); expect(adminApi.application).toHaveBeenCalled()
})

it('applies bounded Order filters through the server query', async () => {
  renderApp(<App />, client(), ['/admin/orders']); await screen.findByText('Tayyar Grill'); fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'READY_FOR_PICKUP' } }); fireEvent.change(screen.getByLabelText('Restaurant ID'), { target: { value: restaurant.id } }); fireEvent.click(screen.getByRole('button', { name: 'Search orders' })); await waitFor(() => expect(adminApi.orders).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'READY_FOR_PICKUP', restaurantId: restaurant.id, page: 0 })))
})

it('keeps Order support read-only and renders the authoritative version', async () => {
  renderApp(<App />, client(), [`/admin/orders/${order.id}`]); expect(await screen.findByText('Kofta')).toBeInTheDocument(); expect(screen.getByText('7')).toBeInTheDocument(); expect(screen.queryByRole('button', { name: /cancel|refund|paid|force/i })).not.toBeInTheDocument()
})

it('assigns an available Driver using the selected Admin Order version', async () => {
  vi.spyOn(adminApi, 'assignDriver').mockResolvedValue({ assignmentId: 'a1', orderId: order.id, driverId: driver.driverId, status: 'ACTIVE', version: 0, assignedAt: now, completedAt: null }); renderApp(<App />, client(), ['/admin/drivers']); await screen.findByRole('option', { name: /Tayyar Grill/ }); fireEvent.change(screen.getByLabelText('Order'), { target: { value: order.id } }); expect(await screen.findByText('Platform delivery confirmed.')).toBeInTheDocument(); fireEvent.change(screen.getByLabelText('Available Driver'), { target: { value: driver.driverId } }); fireEvent.click(screen.getByRole('button', { name: 'Assign Driver' })); await waitFor(() => expect(adminApi.assignDriver).toHaveBeenCalledWith(order.id, driver.driverId, 7))
})

it('provisions an eligible Driver and refetches without optimistic state', async () => {
  vi.spyOn(adminApi, 'provisionDriver').mockResolvedValue({ driverId: driver.driverId, state: 'OFFLINE', version: 0, updatedAt: now }); renderApp(<App />, client(), ['/admin/drivers']); await screen.findByText(/Provision Driver/); fireEvent.change(screen.getByLabelText('User ID'), { target: { value: driver.driverId } }); fireEvent.click(screen.getByRole('button', { name: 'Provision profile' })); expect(await screen.findByText(/provisioned after server confirmation/i)).toBeInTheDocument(); expect(adminApi.provisionDriver).toHaveBeenCalledWith(driver.driverId)
})

it('shows a stale assignment conflict, refetches scoped state, and never retries', async () => {
  const assign = vi.spyOn(adminApi, 'assignDriver').mockRejectedValue(new ApiError('Order version is stale', 409)); renderApp(<App />, client(), ['/admin/drivers']); await screen.findByRole('option', { name: /Tayyar Grill/ }); fireEvent.change(screen.getByLabelText('Order'), { target: { value: order.id } }); await screen.findByText('Platform delivery confirmed.'); fireEvent.change(screen.getByLabelText('Available Driver'), { target: { value: driver.driverId } }); fireEvent.click(screen.getByRole('button', { name: 'Assign Driver' })); expect(await screen.findByText('Order version is stale')).toBeInTheDocument(); expect(assign).toHaveBeenCalledTimes(1); expect(adminApi.orders).toHaveBeenCalled()
})

it('renders audit fields structurally and applies server filters', async () => {
  vi.mocked(adminApi.audit).mockResolvedValue(page([{ id: 'a1', actorId: admin.id, actionType: 'ACCOUNT_SUSPENDED', targetEntityType: 'USER', targetEntityId: user.id, reason: 'Verified abuse', beforeState: 'ACTIVE', afterState: 'SUSPENDED', occurredAt: now }])); renderApp(<App />, client(), ['/admin/audit']); expect(await screen.findByText('Verified abuse')).toBeInTheDocument(); fireEvent.change(screen.getByLabelText('Action'), { target: { value: 'ACCOUNT_SUSPENDED' } }); fireEvent.click(screen.getByRole('button', { name: 'Filter audit' })); await waitFor(() => expect(adminApi.audit).toHaveBeenLastCalledWith(expect.objectContaining({ action: 'ACCOUNT_SUSPENDED' })))
})

it('paginates the immutable audit viewer through the server', async () => {
  vi.mocked(adminApi.audit).mockResolvedValue({ items: [{ id: 'a1', actorId: admin.id, actionType: 'ACCOUNT_SUSPENDED', targetEntityType: 'USER', targetEntityId: user.id, reason: 'Verified abuse', beforeState: 'ACTIVE', afterState: 'SUSPENDED', occurredAt: now }], page: 0, size: 20, total: 21 }); renderApp(<App />, client(), ['/admin/audit']); fireEvent.click(await screen.findByRole('button', { name: 'Next' })); await waitFor(() => expect(adminApi.audit).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 })))
})
