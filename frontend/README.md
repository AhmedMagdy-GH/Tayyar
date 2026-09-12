# Tayyar frontend — Phase 2

Responsive customer discovery and restaurant menu experience built from the approved Stitch direction. The Stitch presentation shell was intentionally excluded; the implementation is a native React application.

## Run locally

```bash
npm install
npm run dev
```

The Vite development server proxies `/api` to `http://localhost:8080`. Override the backend origin with `TAYYAR_API_ORIGIN` or the browser-facing prefix with `VITE_API_BASE_URL`.

- Live API: `http://localhost:5173/`
- Explicit visual preview: `http://localhost:5173/?demo=true`

The preview mode is intentionally URL-gated and displays a persistent notice so local presentation content cannot be mistaken for server data.

## Implemented scope

- React 19, TypeScript, Vite, Tailwind CSS v4, React Router, and TanStack Query
- Responsive homepage at 390, 768, 1024, and 1440px
- Responsive restaurant details and paginated-menu presentation
- Typed Discovery API integration for restaurants, branches, menus, and delivery zones
- Customer registration, login, current-session recovery, and real server logout using HttpOnly cookie sessions
- Cookie-session fetches (`credentials: include`) and reusable CSRF mutation preparation through `/api/v1/auth/csrf`
- Server-owned cart reads and versioned mutations, explicit cross-branch replacement, availability warnings, and price reconfirmation
- Structured saved-address create, edit, delete, default selection, and manual delivery-zone assignment
- Address-aware restaurant and branch discovery using only backend-supported `addressId` and `zoneId` filters
- Shared loading, safe error, empty, navigation, search, restaurant card, artwork, and quantity states
- Keyboard focus styling, semantic landmarks, labelled controls, and reduced-motion support

Customer routes are `/login`, `/register`, `/cart`, and `/addresses`. Cart and address screens require a real customer session and preserve a safe intended route through login.

## Backend gaps represented as presentation fallbacks

Discovery DTOs do not currently provide imagery, cuisine tags, ratings, review counts, or promotional badges. Live API cards therefore use local fallback artwork and omit unsupported metadata. Preview mode supplies clearly labelled demo values for visual validation.

Demo mode remains visual-only for unsupported discovery metadata. It does not fake authentication, cart ownership, saved addresses, or checkout. No JWTs or browser storage are used for authentication or cart state.

## Deliberately deferred

Checkout, order history, favorites, notifications, the full profile experience, and operational dashboards remain outside this phase.
