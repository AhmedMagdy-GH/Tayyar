# Reviews and Favorites

## Reviews

`POST`, `GET`, and `PATCH /api/v1/orders/{orderId}/review` are Customer-only. The
session supplies the Customer identity. Creation locks and reads the authoritative
Order and succeeds only when it belongs to that Customer and is `DELIVERED`.
Order, Customer, Restaurant, and Branch are copied from the Order; the API accepts
only a rating (1–5) and an optional, trimmed comment of at most 2,000 characters.

There is one Review per Order, enforced by `UNIQUE(order_id)`. A composite foreign
key also proves that all copied purchase context matches the Order. Purchase context
and creation time are immutable at database level, and Review rows cannot be
physically deleted. Customer withdrawal is intentionally deferred for v1.

Customers may update only rating and comment. Updates require `version`; successful
updates increment the shared version. Admin moderation uses
`PATCH /api/v1/admin/reviews/{reviewId}/moderation` with `VISIBLE` or `HIDDEN` and
the same version, so a concurrent edit or moderation action produces a controlled
409 instead of overwriting either operation. Moderation never changes content.

`GET /api/v1/discovery/restaurants/{restaurantId}/reviews` is anonymous, paginated
(default 20, maximum 100), ordered by `(created_at DESC, id DESC)`, and returns only
visible Reviews with anonymous reviewer presentation. Its rating summary is computed
from visible Reviews only. With no visible Reviews, count is zero and average is null.
No aggregate is denormalized and Discovery restaurant cards are unchanged.

## Favorites

Favorites are private Customer-to-Restaurant relations. `PUT` and `DELETE
/api/v1/favorites/{restaurantId}` are idempotent and return 204. Adding requires a
currently public Restaurant (active Restaurant with an active Branch). `GET
/api/v1/favorites` returns only the authenticated Customer's current, public
Restaurant summaries, paginated and deterministically ordered. The database primary
key `(customer_id, restaurant_id)` prevents duplicates. No Customer identifier,
favorite count, or personalized Discovery ranking is exposed.
