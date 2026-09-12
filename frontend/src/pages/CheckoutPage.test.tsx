import { act, fireEvent, screen, waitFor } from '@testing-library/react'
import { vi } from 'vitest'
import { ApiError } from '../api/client'
import { checkoutApi } from '../api/checkout'
import { cartApi } from '../api/cart'
import type { Address, Cart, CheckoutSummary, CurrentUser } from '../api/contracts'
import { queryKeys } from '../api/queryKeys'
import { renderApp, testClient } from '../test/helpers'
import { CheckoutPage } from './CheckoutPage'

const user: CurrentUser = { id: 'u', fullName: 'Mona', email: 'mona@example.com', phone: null, status: 'ACTIVE', emailVerified: false, roles: ['CUSTOMER'] }
const address = (id: string, isDefault = false): Address => ({ id, profile: { label: id === 'a1' ? 'Home' : 'Work', street: 'Tahrir Street', building: '12', floor: null, apartment: null, landmark: null, instructions: null, city: 'Cairo', region: null, postalCode: null, countryCode: 'EG', latitude: null, longitude: null }, deliveryZoneId: 'z1', isDefault, version: 1, createdAt: '', updatedAt: '' })
const cart: Cart = { id: 'c1', branch: { id: 'b1', restaurantId: 'r1', name: 'Zamalek', restaurantName: 'Tayyar Grill', state: 'ACTIVE', openNow: true }, items: [{ id: 'line1', menuItemId: 'i1', name: 'Kofta Bowl', quantity: 2, acknowledgedUnitPrice: 90, currentUnitPrice: 90, priceChanged: false, currentlyAvailable: true, lineSubtotal: 180, version: 2 }], merchandiseSubtotal: 180, currency: 'EGP', version: 7 }
const summary: CheckoutSummary = { orderId: 'o1', orderStatus: 'PLACED', paymentMethod: 'CASH', paymentStatus: 'PENDING', merchandiseSubtotal: 180, deliveryFee: 20, discountTotal: 0, finalTotal: 200, currency: 'EGP', createdAt: '2026-09-12T10:00:00Z' }

function setup(activeCart: Cart | null = cart, savedAddresses = [address('a1', true), address('a2')]) {
  const client = testClient()
  client.setQueryData(queryKeys.currentUser, user)
  client.setQueryData(queryKeys.cart, activeCart)
  client.setQueryData(queryKeys.addresses, { items: savedAddresses, page: 0, size: 100, total: savedAddresses.length })
  if (!activeCart) vi.spyOn(cartApi, 'get').mockResolvedValue(null)
  return renderApp(<CheckoutPage />, client, ['/checkout'])
}

describe('CheckoutPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('shows a safe empty-cart state without creating a cart', () => {
    const add = vi.spyOn(cartApi, 'add')
    setup(null)
    expect(screen.getByText('Your cart is empty')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Explore restaurants' })).toBeInTheDocument()
    expect(add).not.toHaveBeenCalled()
  })

  it('selects the default address and lets the customer choose another', async () => {
    setup()
    expect(await screen.findByRole('radio', { name: /Home/ })).toBeChecked()
    fireEvent.click(screen.getByRole('radio', { name: /Work/ }))
    expect(screen.getByRole('radio', { name: /Work/ })).toBeChecked()
  })

  it('renders CASH as selected and CARD as unavailable without card fields', () => {
    setup()
    expect(screen.getByRole('radio', { name: /Cash on Delivery/ })).toBeChecked()
    expect(screen.getByText('Coming soon — unavailable')).toBeInTheDocument()
    expect(screen.queryByLabelText(/card number/i)).not.toBeInTheDocument()
  })

  it('submits promotion, current cart identity/version, selected address and CASH', async () => {
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('11111111-1111-4111-8111-111111111111')
    const place = vi.spyOn(checkoutApi, 'place').mockResolvedValue(summary)
    setup()
    fireEvent.change(screen.getByPlaceholderText('Enter promotion code'), { target: { value: ' save20 ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Place order · Cash' }))
    await waitFor(() => expect(place).toHaveBeenCalledWith({ cartId: 'c1', cartVersion: 7, savedAddressId: 'a1', paymentMethod: 'CASH', promotionCode: 'SAVE20' }, '11111111-1111-4111-8111-111111111111'))
  })

  it('blocks checkout until changed prices are explicitly reconfirmed and refetched', async () => {
    const changed = { ...cart, items: [{ ...cart.items[0], priceChanged: true, currentUnitPrice: 95, lineSubtotal: 190 }], merchandiseSubtotal: 190 }
    vi.spyOn(cartApi, 'reconfirm').mockResolvedValue({ ...changed, items: [{ ...changed.items[0], priceChanged: false }], version: 8 })
    vi.spyOn(cartApi, 'get').mockResolvedValue({ ...changed, items: [{ ...changed.items[0], priceChanged: false }], version: 8 })
    setup(changed)
    expect(screen.getByRole('button', { name: 'Place order · Cash' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Confirm prices' }))
    await waitFor(() => expect(cartApi.reconfirm).toHaveBeenCalledWith('c1', 7))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Place order · Cash' })).toBeEnabled())
  })

  it('allows only one in-flight mutation across repeated submit events', async () => {
    let resolve!: (value: CheckoutSummary) => void
    const place = vi.spyOn(checkoutApi, 'place').mockReturnValue(new Promise((done) => { resolve = done }))
    setup()
    const button = screen.getByRole('button', { name: 'Place order · Cash' })
    fireEvent.click(button)
    fireEvent.click(button)
    fireEvent.submit(document.getElementById('checkout-form')!)
    await waitFor(() => expect(place).toHaveBeenCalledTimes(1))
    await act(async () => { resolve(summary) })
  })

  it('reuses the same idempotency key after an uncertain network result', async () => {
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('22222222-2222-4222-8222-222222222222')
    const place = vi.spyOn(checkoutApi, 'place').mockRejectedValueOnce(new TypeError('network')).mockResolvedValueOnce(summary)
    setup()
    fireEvent.click(screen.getByRole('button', { name: 'Place order · Cash' }))
    expect(await screen.findByText('Order result needs confirmation')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Retry same order attempt' }))
    await waitFor(() => expect(place).toHaveBeenCalledTimes(2))
    expect(place.mock.calls[0][1]).toBe(place.mock.calls[1][1])
  })

  it.each([
    ['STALE_CART', 'Your cart changed. Review the refreshed cart before placing your order.'],
    ['PROMOTION_INELIGIBLE', 'That promotion cannot be applied to this order. Check the code or place the order without it.'],
  ])('handles %s safely', async (code, expected) => {
    vi.spyOn(checkoutApi, 'place').mockRejectedValue(new ApiError('internal detail', 409, { code }))
    vi.spyOn(cartApi, 'get').mockResolvedValue(cart)
    setup()
    fireEvent.click(screen.getByRole('button', { name: 'Place order · Cash' }))
    expect(await screen.findByText(expected)).toBeInTheDocument()
    expect(screen.queryByText('internal detail')).not.toBeInTheDocument()
  })

  it('honors rate-limit retry information without automatically retrying', async () => {
    const place = vi.spyOn(checkoutApi, 'place').mockRejectedValue(new ApiError('rate', 429, { code: 'RATE_LIMITED' }, 900))
    setup()
    fireEvent.click(screen.getByRole('button', { name: 'Place order · Cash' }))
    expect(await screen.findByText('Too many checkout attempts. Please wait about 15 minutes.')).toBeInTheDocument()
    expect(place).toHaveBeenCalledTimes(1)
  })

  it('clears the active cart cache after successful checkout', async () => {
    vi.spyOn(checkoutApi, 'place').mockResolvedValue(summary)
    const { client } = setup()
    fireEvent.click(screen.getByRole('button', { name: 'Place order · Cash' }))
    await waitFor(() => expect(client.getQueryData(queryKeys.cart)).toBeNull())
  })
})
