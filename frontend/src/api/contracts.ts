export type Page<T> = {
  items: T[]
  page: number
  size: number
  total: number
}

export type Restaurant = {
  id: string
  name: string
  description: string
  branchCount: number
  openNow: boolean
  serviceable: boolean | null
  minimumDeliveryFee: number | null
  minimumOrder: number | null
  minimumEtaMinutes: number | null
  currency: string
}

export type Branch = {
  id: string
  name: string
  addressLine1: string
  city: string
  timezone: string
  openNow: boolean
  state: string
  serviceable: boolean | null
  deliveryFee: number | null
  minimumOrder: number | null
  etaMinMinutes: number | null
  etaMaxMinutes: number | null
  currency: string
}

export type MenuItem = {
  id: string
  name: string
  description: string
  effectivePrice: number
  effectiveAvailability: boolean
}

export type MenuCategory = {
  id: string
  name: string
  description: string
  items: Page<MenuItem>
}

export type Menu = {
  id: string
  branchId: string
  name: string
  currency: string
  categories: Page<MenuCategory>
}

export type Zone = {
  id: string
  name: string
  cityId: string
  cityName: string
}

export type ApiErrorBody = {
  code?: string
  message?: string
  timestamp?: string
  path?: string
}

export type CsrfResponse = {
  headerName: string
  token: string
}
