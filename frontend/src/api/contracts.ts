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

export type DriverState = 'OFFLINE' | 'AVAILABLE' | 'BUSY'

export type DriverProfile = {
  driverId: string
  state: DriverState
  version: number
  updatedAt: string
}

export type DriverOrder = {
  orderId: string
  status: Extract<OrderStatus, 'READY_FOR_PICKUP' | 'OUT_FOR_DELIVERY'>
  orderVersion: number
  assignmentId: string
  assignmentVersion: number
  pickup: {
    restaurantId: string
    restaurantName: string
    branchId: string
    branchName: string
    addressLine1: string
    city: string
    phone: string | null
  }
  destination: {
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
  }
  paymentMethod: 'CASH' | 'CARD' | null
  cashAmountToCollect: number | null
  currency: string | null
  assignedAt: string
}

export type DriverCompletion = {
  orderId: string
  orderStatus: 'DELIVERED'
  orderVersion: number
  assignmentId: string
  assignmentStatus: 'COMPLETED'
  assignmentVersion: number
  driverState: 'AVAILABLE'
  driverVersion: number
  paymentMethod: 'CASH' | 'CARD'
  paymentStatus: 'PENDING' | 'PAID' | 'FAILED' | string
}

export type Favorite = {
  restaurantId: string
  name: string
  description: string
  favoritedAt: string
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

export type CheckoutInput = {
  cartId: string
  cartVersion: number
  savedAddressId: string
  paymentMethod: 'CASH'
  promotionCode?: string
}

export type CheckoutSummary = {
  orderId: string
  orderStatus: string
  paymentMethod: string
  paymentStatus: string
  merchandiseSubtotal: number
  deliveryFee: number
  discountTotal: number
  finalTotal: number
  currency: string
  createdAt: string
}

export type OrderDetails = {
  order: {
    id: string
    status: OrderStatus
    restaurant: { id: string; name: string }
    branch: { id: string; name: string }
    merchandiseSubtotal: number
    deliveryFee: number
    discountTotal: number
    finalTotal: number
    currency: string
    version: number
    createdAt: string
  }
  items: Array<{
    menuItemId: string
    name: string
    unitPrice: number
    quantity: number
    lineSubtotal: number
  }>
  deliveryAddress: {
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
    deliveryZoneName: string | null
    managedCityName: string | null
  }
  payment: { method: string; status: string } | null
  history: OrderHistoryEntry[]
}

export type OrderStatus = 'PENDING_PAYMENT' | 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'READY_FOR_PICKUP' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'PAYMENT_FAILED' | 'REJECTED' | 'CANCELLED'

export type OrderSummary = OrderDetails['order']

export type OrderHistoryEntry = {
  previousStatus: OrderStatus | null
  newStatus: OrderStatus
  reason: string | null
  occurredAt: string
}

export type Notification = {
  id: string
  type: 'ORDER_ACCEPTED' | 'ORDER_REJECTED' | 'ORDER_PREPARING' | 'ORDER_READY_FOR_PICKUP' | 'ORDER_OUT_FOR_DELIVERY' | 'ORDER_DELIVERED' | 'DELIVERY_ASSIGNED' | 'RESTAURANT_APPLICATION_APPROVED' | 'RESTAURANT_APPLICATION_REJECTED'
  channel: string
  title: string
  body: string
  relatedEntityType: 'ORDER' | 'DELIVERY_ASSIGNMENT' | 'RESTAURANT_APPLICATION' | null
  relatedEntityId: string | null
  read: boolean
  createdAt: string
  readAt: string | null
}

export type CustomerReview = {
  id: string
  orderId: string
  restaurantId: string
  branchId: string
  rating: number
  comment: string | null
  status: string
  version: number
  createdAt: string
  updatedAt: string
}

export type PublicReview = {
  id: string
  branchId: string
  rating: number
  comment: string | null
  createdAt: string
  updatedAt: string
}

export type PublicReviewPage = Page<PublicReview> & {
  ratingSummary: { averageRating: number | null; reviewCount: number }
}

export type RestaurantRole = 'OWNER' | 'STAFF'

export type RestaurantBranchContext = {
  branchId: string
  branchName: string
  branchStatus: string
  operationalState: string
}

export type RestaurantOperationsContext = {
  restaurantId: string
  restaurantName: string
  restaurantStatus: string
  role: RestaurantRole
  branches: RestaurantBranchContext[]
}

export type RestaurantView = {
  id: string
  name: string
  description: string
  status: string
  version: number
  createdAt: string
  updatedAt: string
}

export type BranchProfile = {
  name: string
  addressLine1: string
  addressLine2: string | null
  city: string
  region: string | null
  postalCode: string | null
  countryCode: string
  phone: string | null
  latitude: string | null
  longitude: string | null
  timezone: string
  deliveryModel: 'RESTAURANT_DELIVERY' | 'TAYYAR_DELIVERY'
}

export type ManagedBranch = {
  id: string
  restaurantId: string
  profile: BranchProfile
  status: string
  paused: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export type WeeklyHours = { weekday: number; opensAt: string; closesAt: string }
export type SpecialHours = { date: string; opensAt: string | null; closesAt: string | null }
export type BranchSchedule = { weekly: WeeklyHours[]; special: SpecialHours[]; version: number }

export type ManagedMenu = {
  id: string
  restaurantId: string
  name: string
  active: boolean
  currency: string
  version: number
  createdAt: string
  updatedAt: string
}

export type ManagedCategory = { id: string; name: string; description: string | null; active: boolean; position: number }
export type ManagedItem = { id: string; categoryId: string; name: string; description: string | null; basePrice: string; active: boolean; available: boolean; position: number }
export type EffectiveItem = ManagedItem & { priceOverride: string | null; availabilityOverride: boolean | null; effectivePrice: string; effectiveAvailable: boolean; currency: string }
export type VersionedPage<T> = Page<T> & { version: number }
export type Snapshot<T> = { data: T; version: number }

export type DeliveryRuleInput = { deliveryFee: string; minimumOrder: string; etaMinMinutes: number; etaMaxMinutes: number; enabled: boolean }
export type DeliveryRule = { id: string; branchId: string; deliveryZoneId: string; currency: string; rule: DeliveryRuleInput; version: number; createdAt: string; updatedAt: string }
export type Geography = { id: string; cityId: string | null; name: string; active: boolean; version: number; createdAt: string; updatedAt: string }

export type RestaurantStaff = {
  userId: string
  fullName: string
  email: string
  createdAt: string
  branches: Array<{ branchId: string; branchName: string; branchStatus: string }>
}

export type RestaurantOrderSummary = OrderSummary
export type RestaurantOrderDetails = OrderDetails

export type RestaurantApplication = {
  id: string
  applicantId: string
  status: string
  revision: number
  version: number
  name: string
  description: string
  restaurantId: string | null
  createdAt: string
  updatedAt: string
}

export type AdminUser = { id: string; fullName: string; email: string; status: 'ACTIVE' | 'SUSPENDED'; roles: string[]; createdAt: string; updatedAt: string }
export type AdminRestaurant = RestaurantView
export type AdminRestaurantDetail = AdminRestaurant & { branches: Array<{ id: string; name: string; status: string; paused: boolean; city: string }> }
export type AdminRestaurantHistory = { id: string; actorId: string; previousStatus: 'ACTIVE' | 'SUSPENDED'; newStatus: 'ACTIVE' | 'SUSPENDED'; reason: string; changedAt: string }
export type AdminApplicationSubmission = { revision: number; name: string; description: string; submittedAt: string }
export type AdminApplicationDecision = { revision: number; reviewerId: string; outcome: 'APPROVED' | 'REJECTED'; reason: string | null; restaurantId: string | null; decidedAt: string }
export type AdminOrder = { id: string; customerId: string; restaurantId: string; restaurantName: string; branchId: string; branchName: string; status: OrderStatus; version: number; finalTotal: number; currency: string; payment: { method: string; status: string; amount: number; currency: string } | null; assignment: { assignmentId: string; driverId: string; status: string; assignedAt: string } | null; createdAt: string }
export type AdminOrderDetails = { order: AdminOrder; items: Array<{ name: string; unitPrice: number; quantity: number; lineSubtotal: number }>; deliveryAddress: { label: string | null; street: string; building: string; floor: string | null; apartment: string | null; landmark: string | null; instructions: string | null; city: string; region: string | null; postalCode: string | null; countryCode: string } | null; history: Array<{ previousStatus: OrderStatus | null; newStatus: OrderStatus; actorKind: string; actorId: string | null; reason: string | null; occurredAt: string }> }
export type AdminDriver = { driverId: string; accountStatus: 'ACTIVE' | 'SUSPENDED'; state: DriverState; version: number; activeAssignment: { assignmentId: string; driverId: string; status: string; assignedAt: string } | null; createdAt: string; updatedAt: string }
export type AdminAuditRecord = { id: string; actorId: string; actionType: 'ACCOUNT_SUSPENDED' | 'ACCOUNT_REACTIVATED'; targetEntityType: 'USER'; targetEntityId: string; reason: string; beforeState: string; afterState: string; occurredAt: string }
export type AdminDriverProfile = { driverId: string; state: DriverState; version: number; updatedAt: string }
export type AdminDeliveryAssignment = { assignmentId: string; orderId: string; driverId: string; status: string; version: number; assignedAt: string; completedAt: string | null }
