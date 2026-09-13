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
import { OwnerRoute, RestaurantRoute } from '../restaurant/RestaurantRoute'
import { RestaurantOverviewPage, RestaurantSettingsPage } from '../restaurant/OverviewSettingsPages'
import { BranchesPage, HoursPage } from '../restaurant/BranchesPages'
import { MenuManagementPage } from '../restaurant/MenuPage'
import { DeliveryManagementPage } from '../restaurant/DeliveryPage'
import { StaffManagementPage } from '../restaurant/StaffPage'
import { RestaurantOrderDetailPage, RestaurantOrdersPage } from '../restaurant/OrdersPages'
import { RestaurantApplicationPage } from '../restaurant/RestaurantApplicationPage'

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
      <Route path="/restaurant" element={<RestaurantRoute />}>
        <Route index element={<Navigate to="overview" replace />} />
        <Route path="overview" element={<RestaurantOverviewPage />} />
        <Route path="orders" element={<RestaurantOrdersPage />} />
        <Route path="orders/:orderId" element={<RestaurantOrderDetailPage />} />
        <Route element={<OwnerRoute />}>
          <Route path="branches" element={<BranchesPage />} />
          <Route path="hours" element={<HoursPage />} />
          <Route path="menu" element={<MenuManagementPage />} />
          <Route path="delivery" element={<DeliveryManagementPage />} />
          <Route path="staff" element={<StaffManagementPage />} />
          <Route path="settings" element={<RestaurantSettingsPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
