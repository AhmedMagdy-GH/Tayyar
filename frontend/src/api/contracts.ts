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
  correlationId?: string
  fieldErrors?: Array<{ field: string; message: string }>
}

export type CsrfResponse = {
  headerName: string
  token: string
}

export type CurrentUser = {
  id: string
  fullName: string
  email: string
  phone: string | null
  status: 'ACTIVE' | 'SUSPENDED' | string
  emailVerified: boolean
  roles: string[]
}

export type RegistrationInput = {
  fullName: string
  email: string
  phone: string | null
  password: string
}

export type LoginInput = { email: string; password: string }

export type CartLine = {
  id: string
  menuItemId: string
  name: string
  quantity: number
  acknowledgedUnitPrice: number
  currentUnitPrice: number
  priceChanged: boolean
  currentlyAvailable: boolean
  lineSubtotal: number
  version: number
}

export type Cart = {
  id: string
  branch: {
    id: string
    restaurantId: string
    name: string
    restaurantName: string
    state: string
    openNow: boolean
  }
  items: CartLine[]
  merchandiseSubtotal: number
  currency: string
  version: number
}

export type AddressProfile = {
  label: string
  street: string
  building: string
  floor: string | null
  apartment: string | null
  landmark: string | null
  instructions: string | null
  city: string
  region: string | null
  postalCode: string | null
  countryCode: string
  latitude: number | null
  longitude: number | null
}

export type Address = {
  id: string
  profile: AddressProfile
  deliveryZoneId: string | null
  isDefault: boolean
  version: number
  createdAt: string
  updatedAt: string
}
