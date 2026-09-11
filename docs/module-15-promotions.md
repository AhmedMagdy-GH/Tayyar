# Module 15: Promotions

## V1 scope

Promotions are restaurant-scoped and code-based. A Checkout may select at most one code. V1 does
not include platform-wide or automatic promotions, stacking, branch/item/category targeting,
BOGO, loyalty, referrals, subscriptions, or a customer preview endpoint. Checkout is the sole
customer validation surface so that an earlier informational result cannot be mistaken for a
reservation.

Restaurant owners can create, list, read, and update promotions for restaurants they own. Admins
can manage restaurant promotions through the same restaurant-scoped routes. Staff, customers,
and drivers cannot use management APIs. Promotions are retained and deactivated rather than
deleted after use.

## Codes and money

Codes are trimmed, uppercased with `Locale.ROOT`, and restricted to 3–32 ASCII letters, digits,
underscores, or hyphens. Uniqueness is `(restaurant_id, code)`, so `WELCOME20`, `welcome20`, and
` Welcome20 ` resolve identically within a restaurant.

Money uses `BigDecimal` and PostgreSQL `NUMERIC(12,2)` in EGP. Percentage discounts use
`HALF_UP` rounding to two decimal places. An optional percentage cap is applied before the final
merchandise-subtotal cap. Fixed discounts are also capped at merchandise subtotal. Delivery fees
are never discounted:

`finalTotal = merchandiseSubtotal - discountTotal + deliveryFee`

Minimum-purchase eligibility uses the authoritative merchandise subtotal before delivery.

## Eligibility, redemption, and concurrency

Checkout revalidates the normalized code, restaurant scope, configured active flag, server time
window, minimum subtotal, and total/per-customer usage limits. `ends_at` is exclusive. A promotion
that yields a zero discount is ineligible.

The promotion row is locked with `SELECT ... FOR UPDATE` before usage counts are checked. This
serializes final-slot decisions in PostgreSQL for both campaign and per-customer limits. A
redemption and immutable order-promotion snapshot are inserted only after Order creation and in
the same transaction as Payment, Cart consumption, and the Checkout receipt. Any failure rolls
back every write. An idempotent retry finds its durable receipt before evaluation and cannot
consume another use; normalized promotion selection is part of the receipt identity.

Targeted indexes cover restaurant/active management reads, campaign redemption counts,
campaign-and-customer redemption counts, and unique Order-linked redemption lookup.
