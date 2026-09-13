import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { HomePage } from '../pages/HomePage'
import { RestaurantPage } from '../pages/RestaurantPage'
import { LoginPage, RegisterPage } from '../pages/AuthPage'
import { CartPage } from '../pages/CartPage'
import { AddressesPage } from '../pages/AddressesPage'
import { ProtectedRoute } from '../components/ProtectedRoute'
import { CheckoutPage } from '../pages/CheckoutPage'
import { OrderConfirmationPage } from '../pages/OrderConfirmationPage'
import { OrdersPage } from '../pages/OrdersPage'
import { OrderDetailPage } from '../pages/OrderDetailPage'
import { NotificationsPage } from '../pages/NotificationsPage'
import { FavoritesPage } from '../pages/FavoritesPage'
import { AccountPage } from '../pages/AccountPage'
import { RestaurantApplicationPage } from '../restaurant/RestaurantApplicationPage'

const DriverApp = lazy(() => import('../driver/DriverApp').then((module) => ({ default: module.DriverApp })))
const RestaurantApp = lazy(() => import('../restaurant/RestaurantApp').then((module) => ({ default: module.RestaurantApp })))

function RouteLoading({ label }: { label: string }) {
  return <div className="session-loading" role="status" aria-live="polite"><span className="loader" /> {label}</div>
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/restaurants/:restaurantId" element={<RestaurantPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/cart" element={<ProtectedRoute><CartPage /></ProtectedRoute>} />
      <Route path="/addresses" element={<ProtectedRoute><AddressesPage /></ProtectedRoute>} />
      <Route path="/checkout" element={<ProtectedRoute><CheckoutPage /></ProtectedRoute>} />
      <Route path="/orders/:orderId/confirmation" element={<ProtectedRoute><OrderConfirmationPage /></ProtectedRoute>} />
      <Route path="/orders" element={<ProtectedRoute><OrdersPage /></ProtectedRoute>} />
      <Route path="/orders/:orderId" element={<ProtectedRoute><OrderDetailPage /></ProtectedRoute>} />
      <Route path="/notifications" element={<ProtectedRoute><NotificationsPage /></ProtectedRoute>} />
      <Route path="/favorites" element={<ProtectedRoute><FavoritesPage /></ProtectedRoute>} />
      <Route path="/account" element={<ProtectedRoute><AccountPage /></ProtectedRoute>} />
      <Route path="/restaurant-application" element={<ProtectedRoute><RestaurantApplicationPage /></ProtectedRoute>} />
      <Route path="/restaurant/*" element={<Suspense fallback={<RouteLoading label="Loading restaurant operations…" />}><RestaurantApp /></Suspense>} />
      <Route path="/driver/*" element={<Suspense fallback={<RouteLoading label="Loading Driver operations…" />}><DriverApp /></Suspense>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
