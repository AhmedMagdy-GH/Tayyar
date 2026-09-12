import { Navigate, Route, Routes } from 'react-router-dom'
import { HomePage } from '../pages/HomePage'
import { RestaurantPage } from '../pages/RestaurantPage'

export function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/restaurants/:restaurantId" element={<RestaurantPage />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
