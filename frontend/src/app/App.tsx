import { Navigate, Route, Routes } from 'react-router-dom'
import { HomePage } from '../pages/HomePage'
import { RestaurantPage } from '../pages/RestaurantPage'
import { LoginPage, RegisterPage } from '../pages/AuthPage'
import { CartPage } from '../pages/CartPage'
import { AddressesPage } from '../pages/AddressesPage'
import { ProtectedRoute } from '../components/ProtectedRoute'

export function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/restaurants/:restaurantId" element={<RestaurantPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/cart" element={<ProtectedRoute><CartPage /></ProtectedRoute>} />
      <Route path="/addresses" element={<ProtectedRoute><AddressesPage /></ProtectedRoute>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
