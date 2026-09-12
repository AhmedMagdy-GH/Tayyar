# Frontend Phase 3 backend contracts

Audited from the backend source before frontend implementation.

## Checkout

- `POST /api/v1/checkout`
- Authentication: active user with `CUSTOMER` role.
- Cookies: session cookie; frontend requests use `credentials: include`.
- CSRF: mutating request, using the existing token/header from `GET /api/v1/auth/csrf`.
- Required header: exactly one `Idempotency-Key` value. It must match `[A-Za-z0-9_-]{16,128}`. A browser UUID is valid.
- Request body:

```json
{
  "cartId": "UUID",
  "cartVersion": 0,
  "savedAddressId": "UUID",
  "paymentMethod": "CASH",
  "promotionCode": "optional string, max 64 characters"
}
```

`promotionCode` is normalized and evaluated by Checkout. Promotion-management endpoints are not customer preview endpoints. There is no customer promotion-preview API.

Success is `201 Created`, `Cache-Control: no-store`, with:

```json
{
  "orderId": "UUID",
  "orderStatus": "PLACED",
  "paymentMethod": "CASH",
  "paymentStatus": "PENDING",
  "merchandiseSubtotal": "decimal",
  "deliveryFee": "decimal",
  "discountTotal": "decimal",
  "finalTotal": "decimal",
  "currency": "string",
  "createdAt": "instant"
}
```

Checkout creates the order and a CASH payment, consumes the active cart, and records the successful result against `(customer, Idempotency-Key)` in the same transaction. Reusing the same key and identical normalized selections returns the stored successful summary; reusing it with different selections returns `409 IDEMPOTENCY_MISMATCH`.

Supported/observed Checkout outcomes:

- `400 INVALID_IDEMPOTENCY_KEY`, `INVALID_REQUEST`, or validation errors.
- `401` when no valid session exists.
- `403 FORBIDDEN` for a non-customer/inactive account; CSRF failures use the shared `CSRF_INVALID` response.
- `404 NOT_FOUND` when the submitted Cart or saved Address is not available to the Customer.
- `409 PAYMENT_METHOD_UNAVAILABLE` for CARD.
- `409 STALE_CART`, `EMPTY_CART`, `BRANCH_NOT_ACCEPTING`, `ITEM_UNAVAILABLE`, `PRICE_RECONFIRMATION_REQUIRED`, `ADDRESS_ZONE_REQUIRED`, `NOT_SERVICEABLE`, `MINIMUM_ORDER_NOT_MET`, `TOTAL_OUT_OF_RANGE`, `IDEMPOTENCY_MISMATCH`, or `CONCURRENT_CHANGE`.
- Promotion failures reach Checkout as `409 PROMOTION_INELIGIBLE` (invalid/wrong restaurant, inactive, not started, expired, below minimum, total limit, customer limit, or zero discount).
- `429 RATE_LIMITED`, with `Retry-After: 900` from the current limiter.
- `5xx` or a fetch/network failure has an uncertain result: the request may have committed even if the browser did not receive it.

## Cart

- `GET /api/v1/cart`: `200` with the active Cart view or `204 No Content`; it does not create a Cart.
- Cart view fields: `id`, `branch`, `items`, `merchandiseSubtotal`, `currency`, `version`.
- Each item includes `priceChanged` and `currentlyAvailable`.
- `POST /api/v1/cart/reconfirm-prices` body: `{ "cartId": "UUID", "cartVersion": number }`; success returns the current Cart view. Checkout must use the refetched/current `id` and `version` and must not proceed while any item has `priceChanged`.

## Saved addresses

- `GET /api/v1/users/me/addresses?page=0&size=100` returns `{ items, page, size, total }`.
- Each item provides `id`, `profile`, nullable `deliveryZoneId`, `isDefault`, `version`, `createdAt`, and `updatedAt`.
- Checkout submits the selected saved Address's `id`. Existence or even a selected zone does not guarantee serviceability; Checkout validates the branch/zone configuration.

## Customer order detail used by confirmation

- `GET /api/v1/orders/{orderId}`; authenticated `CUSTOMER`; ownership is enforced.
- Response fields:
  - `order`: `id`, `status`, `restaurant { id, name }`, `branch { id, name }`, `merchandiseSubtotal`, `deliveryFee`, `discountTotal`, `finalTotal`, `currency`, `version`, `createdAt`.
  - `items[]`: `menuItemId`, `name`, `unitPrice`, `quantity`, `lineSubtotal`.
  - `deliveryAddress`: saved snapshot fields including label/street/building/city and delivery-zone names.
  - nullable `payment`: `method`, `status`.
  - `history[]`, which is not needed for Phase 3 confirmation.

The Phase 3 confirmation screen may safely use Checkout's summary immediately and fetch this detail endpoint for restaurant, items, and delivery-address snapshot. It does not expose order history/tracking UX.
