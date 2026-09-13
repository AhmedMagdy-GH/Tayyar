import { Navigate, Route, Routes } from 'react-router-dom'
import { OwnerRoute, RestaurantRoute } from './RestaurantRoute'
import { RestaurantOverviewPage, RestaurantSettingsPage } from './OverviewSettingsPages'
import { BranchesPage, HoursPage } from './BranchesPages'
import { MenuManagementPage } from './MenuPage'
import { DeliveryManagementPage } from './DeliveryPage'
import { StaffManagementPage } from './StaffPage'
import { RestaurantOrderDetailPage, RestaurantOrdersPage } from './OrdersPages'

export function RestaurantApp() {
  return (
    <Routes>
      <Route element={<RestaurantRoute />}>
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
      <Route path="*" element={<Navigate to="/restaurant" replace />} />
    </Routes>
  )
}
