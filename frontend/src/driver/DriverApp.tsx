import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, Banknote, Bike, Check, ChevronLeft, Clock3, LogOut, MapPin, Navigation, Phone, Power, RefreshCw, Store } from 'lucide-react'
import { Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { DriverCompletion, DriverOrder, DriverProfile, DriverState } from '../api/contracts'
import { driverOperationsApi } from '../api/driverOperations'
import { safeErrorMessage } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import { authApi } from '../api/auth'
import { BrandLogo } from '../components/BrandLogo'
import { ErrorState } from '../components/States'
import { useCurrentUser } from '../hooks/useCustomer'
import { formatMoney } from '../utils/money'

const stateCopy: Record<DriverState, { label: string; description: string }> = {
  OFFLINE: { label: 'Offline', description: 'Go available when you are ready to receive a delivery.' },
  AVAILABLE: { label: 'Available', description: 'You are ready for an assignment.' },
  BUSY: { label: 'On a delivery', description: 'Complete your active delivery before your status changes.' },
}

function pollingFor(profile?: DriverProfile, hasAssignment?: boolean) {
  if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return false
  if (profile?.state === 'OFFLINE') return false
  return hasAssignment ? 15_000 : 20_000
}

function sessionExpired(error: unknown) {
  return error instanceof ApiError && error.status === 401
}

function DriverGuard() {
  const session = useCurrentUser()
  const location = useLocation()
  if (session.isPending) return <div className="driver-session" role="status" aria-live="polite"><span className="loader" /> Checking Driver access…</div>
  if (session.isError) return <div className="driver-standalone"><ErrorState title="We couldn’t check your session" onRetry={() => void session.refetch()} /></div>
  if (!session.data) return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  if (!session.data.roles.includes('DRIVER')) return <div className="driver-standalone"><ErrorState title="This area is for Driver accounts" message="Your signed-in account does not have Driver access." /></div>
  return <DriverShell name={session.data.fullName}><Outlet /></DriverShell>
}

function DriverShell({ name, children }: { name: string; children: ReactNode }) {
  const client = useQueryClient()
  const navigate = useNavigate()
  const logout = useMutation({
    mutationFn: authApi.logout,
    onSuccess: () => {
      client.setQueryData(queryKeys.currentUser, null)
      client.removeQueries({ queryKey: ['driver-operations'] })
      navigate('/login', { replace: true })
    },
  })
  return (
    <div className="driver-app">
      <header className="driver-header">
        <div className="driver-container driver-header__inner">
          <BrandLogo />
          <div className="driver-header__account"><div className="driver-header__identity"><span>Driver operations</span><strong>{name}</strong></div><button type="button" onClick={() => logout.mutate()} disabled={logout.isPending} aria-label={logout.isError ? 'Sign out failed. Try again' : 'Sign out'} title="Sign out"><LogOut aria-hidden="true" /></button></div>
        </div>
      </header>
      <main className="driver-container driver-main">{children}</main>
    </div>
  )
}

function StatusControl({ profile }: { profile: DriverProfile }) {
  const client = useQueryClient()
  const [message, setMessage] = useState<string | null>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const mutation = useMutation({
    mutationFn: () => profile.state === 'OFFLINE' ? driverOperationsApi.available(profile.version) : driverOperationsApi.offline(profile.version),
    onSuccess: async (next) => {
      client.setQueryData(queryKeys.driverProfile, next)
      setMessage(`Status changed to ${stateCopy[next.state].label}.`)
      await client.invalidateQueries({ queryKey: queryKeys.driverOrders })
      heading.current?.focus()
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.status === 409) {
        await Promise.all([
          client.refetchQueries({ queryKey: queryKeys.driverProfile }),
          client.refetchQueries({ queryKey: queryKeys.driverOrders }),
        ])
        setMessage('Your status changed elsewhere. The latest Driver state is now shown.')
      } else {
        setMessage(safeErrorMessage(error, 'We couldn’t change your status. Check your connection and try again.'))
      }
    },
  })
  const copy = stateCopy[profile.state]
  return (
    <section className={`driver-status driver-status--${profile.state.toLowerCase()}`} aria-labelledby="driver-status-heading">
      <div className="driver-status__icon"><Power aria-hidden="true" /></div>
      <div className="driver-status__copy">
        <span>Current status</span>
        <h1 id="driver-status-heading" tabIndex={-1}>{copy.label}</h1>
        <p>{copy.description}</p>
      </div>
      {profile.state !== 'BUSY' && (
        <button className={profile.state === 'OFFLINE' ? 'driver-primary' : 'driver-secondary'} disabled={mutation.isPending} onClick={() => { setMessage(null); mutation.mutate() }}>
          {mutation.isPending ? <><RefreshCw className="spin" aria-hidden="true" /> Updating…</> : profile.state === 'OFFLINE' ? 'Go available' : 'Go offline'}
        </button>
      )}
      {message && <p className="driver-alert" role="alert">{message}</p>}
    </section>
  )
}

function AssignmentCard({ order }: { order: DriverOrder }) {
  return (
    <article className="driver-assignment-card">
      <div className="driver-assignment-card__top">
        <span className="driver-kicker"><Bike aria-hidden="true" /> Active delivery</span>
        <span className="driver-order-ref">Order {order.orderId.slice(0, 8).toUpperCase()}</span>
      </div>
      <h2>{order.pickup.restaurantName}</h2>
      <p>{order.pickup.branchName} · {order.pickup.city}</p>
      <div className="driver-assignment-card__route" aria-label="Delivery route">
        <span><Store aria-hidden="true" /><small>Pickup</small><strong>{order.pickup.addressLine1}</strong></span>
        <ArrowRight aria-hidden="true" />
        <span><MapPin aria-hidden="true" /><small>Drop-off</small><strong>{order.destination.label}, {order.destination.street}</strong></span>
      </div>
      <Link className="driver-primary driver-primary--wide" to={`/driver/deliveries/${order.orderId}`}>Open delivery <ArrowRight aria-hidden="true" /></Link>
    </article>
  )
}

function DriverDashboard() {
  const profile = useQuery({ queryKey: queryKeys.driverProfile, queryFn: driverOperationsApi.profile, retry: false, refetchInterval: (query) => pollingFor(query.state.data) })
  const orders = useQuery({
    queryKey: queryKeys.driverOrders,
    queryFn: driverOperationsApi.activeOrders,
    enabled: Boolean(profile.data),
    retry: false,
    refetchInterval: (query) => pollingFor(profile.data, Boolean(query.state.data?.items.length)),
  })
  if (profile.isPending) return <DriverLoading label="Loading your Driver status…" />
  if (profile.isError && sessionExpired(profile.error)) return <Navigate to="/login" replace state={{ from: '/driver' }} />
  if (profile.isError) return <ErrorState title="We couldn’t load your Driver profile" message={safeErrorMessage(profile.error)} onRetry={() => void profile.refetch()} />
  return (
    <>
      <StatusControl profile={profile.data} />
      <section className="driver-section" aria-labelledby="assignment-heading">
        <div className="driver-section__heading"><div><span className="driver-kicker">Today</span><h2 id="assignment-heading">Your assignment</h2></div>{orders.isFetching && !orders.isPending && <span className="driver-refresh" role="status"><RefreshCw className="spin" aria-hidden="true" /> Refreshing</span>}</div>
        {orders.isPending && <DriverLoading label="Checking for an active assignment…" />}
        {orders.isError && <ErrorState title="We couldn’t check your assignment" message={safeErrorMessage(orders.error)} onRetry={() => void orders.refetch()} />}
        {orders.data?.items[0] ? <AssignmentCard order={orders.data.items[0]} /> : orders.data && (
          <div className="driver-empty"><span><Clock3 aria-hidden="true" /></span><h3>No active assignment</h3><p>{profile.data.state === 'AVAILABLE' ? 'Stay ready. We’ll check for a new assignment every 20 seconds while this page is open.' : 'Go available when you are ready to receive a delivery.'}</p></div>
        )}
      </section>
    </>
  )
}

function DriverLoading({ label }: { label: string }) {
  return <div className="driver-loading" role="status" aria-live="polite"><span className="loader" /> {label}</div>
}

function Timeline({ status }: { status: DriverOrder['status'] | 'DELIVERED' }) {
  const steps = ['READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED'] as const
  const labels = { READY_FOR_PICKUP: 'Ready for pickup', OUT_FOR_DELIVERY: 'Out for delivery', DELIVERED: 'Delivered' }
  const current = steps.indexOf(status)
  return <ol className="driver-timeline" aria-label="Delivery progress">{steps.map((step, index) => <li key={step} className={index <= current ? 'is-complete' : ''}><span>{index < current ? <Check aria-hidden="true" /> : index + 1}</span><strong>{labels[step]}</strong></li>)}</ol>
}

function AddressBlock({ icon, title, children }: { icon: ReactNode; title: string; children: ReactNode }) {
  return <section className="driver-address"><div className="driver-address__icon">{icon}</div><div><span>{title}</span>{children}</div></section>
}

function DriverDeliveryPage() {
  const { orderId = '' } = useParams()
  const location = useLocation()
  const client = useQueryClient()
  const [completion, setCompletion] = useState<DriverCompletion | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const resultHeading = useRef<HTMLHeadingElement>(null)
  const profile = useQuery({ queryKey: queryKeys.driverProfile, queryFn: driverOperationsApi.profile, retry: false, refetchInterval: completion ? false : 15_000 })
  const order = useQuery({ queryKey: queryKeys.driverOrder(orderId), queryFn: () => driverOperationsApi.order(orderId), retry: false, refetchInterval: completion || (typeof document !== 'undefined' && document.visibilityState === 'hidden') ? false : 15_000 })

  const refreshAuthoritative = async () => {
    await Promise.all([
      client.refetchQueries({ queryKey: queryKeys.driverProfile }),
      client.refetchQueries({ queryKey: queryKeys.driverOrders }),
      client.refetchQueries({ queryKey: queryKeys.driverOrder(orderId) }),
    ])
  }
  const transition = useMutation({
    mutationFn: async (action: 'pickup' | 'deliver') => {
      if (!order.data) throw new Error('Delivery is unavailable')
      return action === 'pickup' ? driverOperationsApi.pickup(order.data) : driverOperationsApi.deliver(order.data)
    },
    onSuccess: async (result, action) => {
      setMessage(null)
      setConfirming(false)
      if (action === 'pickup') client.setQueryData(queryKeys.driverOrder(orderId), result as DriverOrder)
      else setCompletion(result as DriverCompletion)
      await Promise.all([
        client.invalidateQueries({ queryKey: queryKeys.driverProfile }),
        client.invalidateQueries({ queryKey: queryKeys.driverOrders }),
        client.invalidateQueries({ queryKey: queryKeys.driverOrder(orderId) }),
      ])
      resultHeading.current?.focus()
    },
    onError: async (error) => {
      await refreshAuthoritative()
      if (error instanceof ApiError && error.status === 409) setMessage('This delivery changed elsewhere. The latest server state is now shown; no action was retried.')
      else if (error instanceof ApiError && error.status === 404) setMessage('This is no longer your active assignment. Your assignment list has been refreshed.')
      else setMessage('We couldn’t confirm the result. The delivery was refreshed from the server before you try again.')
    },
  })

  useEffect(() => {
    if (completion) resultHeading.current?.focus()
  }, [completion])

  if (completion) return (
    <section className="driver-complete">
      <div className="driver-complete__mark"><Check aria-hidden="true" /></div>
      <span className="driver-kicker">Server confirmed</span>
      <h1 ref={resultHeading} tabIndex={-1}>Delivery complete</h1>
      <p>Order {completion.orderId.slice(0, 8).toUpperCase()} is delivered and the assignment is completed.</p>
      <Timeline status="DELIVERED" />
      <dl><div><dt>Driver status</dt><dd>Available</dd></div><div><dt>Payment</dt><dd>{completion.paymentMethod === 'CASH' ? `Cash · ${completion.paymentStatus === 'PAID' ? 'Paid' : completion.paymentStatus}` : `${completion.paymentMethod} · ${completion.paymentStatus}`}</dd></div></dl>
      <Link className="driver-primary driver-primary--wide" to="/driver">Back to dashboard</Link>
    </section>
  )
  if (order.isPending || profile.isPending) return <DriverLoading label="Loading active delivery…" />
  if ((order.isError && sessionExpired(order.error)) || (profile.isError && sessionExpired(profile.error))) return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  if (order.isError) {
    const missing = order.error instanceof ApiError && order.error.status === 404
    return <div className="driver-detail-error"><ErrorState title={missing ? 'This delivery is no longer active' : 'We couldn’t load this delivery'} message={missing ? 'It may be completed, reassigned, or unavailable to this Driver account.' : safeErrorMessage(order.error)} onRetry={missing ? undefined : () => void order.refetch()} /><Link className="driver-secondary" to="/driver">Back to dashboard</Link></div>
  }
  if (profile.isError) return <ErrorState title="We couldn’t load your Driver status" message={safeErrorMessage(profile.error)} onRetry={() => void profile.refetch()} />
  const delivery = order.data
  const destinationLines = [delivery.destination.street, `Building ${delivery.destination.building}`, delivery.destination.floor && `Floor ${delivery.destination.floor}`, delivery.destination.apartment && `Apartment ${delivery.destination.apartment}`].filter(Boolean)
  return (
    <div className="driver-delivery">
      <Link className="driver-back" to="/driver"><ChevronLeft aria-hidden="true" /> Dashboard</Link>
      <header className="driver-delivery__header"><div><span className="driver-kicker">Active delivery</span><h1 ref={resultHeading} tabIndex={-1}>{delivery.pickup.restaurantName}</h1><p>Order {delivery.orderId.slice(0, 8).toUpperCase()}</p></div><span className="driver-state-pill">{delivery.status === 'READY_FOR_PICKUP' ? 'Ready for pickup' : 'Out for delivery'}</span></header>
      <Timeline status={delivery.status} />
      <div className="driver-delivery__grid">
        <div className="driver-delivery__details">
          <AddressBlock icon={<Store aria-hidden="true" />} title="Pickup from"><h2>{delivery.pickup.branchName}</h2><p>{delivery.pickup.addressLine1}, {delivery.pickup.city}</p>{delivery.pickup.phone && <a href={`tel:${delivery.pickup.phone}`}><Phone aria-hidden="true" /> Call branch</a>}</AddressBlock>
          <AddressBlock icon={<Navigation aria-hidden="true" />} title="Deliver to"><h2>{delivery.destination.label}</h2><p>{destinationLines.join(' · ')}<br />{[delivery.destination.city, delivery.destination.region, delivery.destination.postalCode].filter(Boolean).join(', ')}</p>{delivery.destination.landmark && <p><strong>Landmark:</strong> {delivery.destination.landmark}</p>}{delivery.destination.instructions && <div className="driver-note"><strong>Delivery instructions</strong><p>{delivery.destination.instructions}</p></div>}</AddressBlock>
          {delivery.paymentMethod === 'CASH' && <section className="driver-cash"><Banknote aria-hidden="true" /><div><span>Collect on delivery</span><strong>{delivery.cashAmountToCollect == null ? 'Amount unavailable' : formatMoney(delivery.cashAmountToCollect, delivery.currency ?? 'EGP')}</strong><p>Confirm delivery only after receiving the cash. Payment is updated by the server.</p></div></section>}
          {delivery.paymentMethod === 'CARD' && <section className="driver-cash driver-cash--card"><Banknote aria-hidden="true" /><div><span>Payment method</span><strong>Card</strong><p>No payment details or payment action are required here.</p></div></section>}
        </div>
        <aside className="driver-action-card" aria-labelledby="next-action-heading">
          <span className="driver-kicker">Next action</span>
          <h2 id="next-action-heading">{delivery.status === 'READY_FOR_PICKUP' ? 'Confirm pickup' : 'Complete delivery'}</h2>
          <p>{delivery.status === 'READY_FOR_PICKUP' ? 'Only confirm after the order has been collected from the branch.' : delivery.paymentMethod === 'CASH' ? 'Confirm that the order was handed over and cash was collected.' : 'Confirm that the order was handed to the customer.'}</p>
          {message && <p className="driver-alert" role="alert">{message}</p>}
          {delivery.status === 'READY_FOR_PICKUP' ? <button className="driver-primary driver-primary--wide" disabled={transition.isPending} onClick={() => transition.mutate('pickup')}>{transition.isPending ? 'Confirming with server…' : 'Order picked up'}</button> : confirming ? <div className="driver-confirm" role="group" aria-label="Confirm delivery"><p>This action completes the delivery{delivery.paymentMethod === 'CASH' ? ' and confirms cash collection' : ''}.</p><button className="driver-primary driver-primary--wide" disabled={transition.isPending} onClick={() => transition.mutate('deliver')}>{transition.isPending ? 'Confirming with server…' : 'Yes, complete delivery'}</button><button className="driver-text-button" disabled={transition.isPending} onClick={() => setConfirming(false)}>Not yet</button></div> : <button className="driver-primary driver-primary--wide" onClick={() => setConfirming(true)}>Complete delivery</button>}
          <p className="driver-action-card__foot">Actions are confirmed by Tayyar before this screen updates.</p>
        </aside>
      </div>
    </div>
  )
}

export function DriverApp() {
  return <Routes><Route element={<DriverGuard />}><Route index element={<DriverDashboard />} /><Route path="deliveries" element={<DriverDashboard />} /><Route path="deliveries/:orderId" element={<DriverDeliveryPage />} /></Route><Route path="*" element={<Navigate to="/driver" replace />} /></Routes>
}
