# Tayyar frontend — Phase 1

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
- Cookie-session fetches (`credentials: include`) and reusable CSRF mutation preparation through `/api/v1/auth/csrf`
- Shared loading, error, empty, navigation, search, restaurant card, artwork, quantity, and cart-preview states
- Keyboard focus styling, semantic landmarks, labelled controls, and reduced-motion support

## Backend gaps represented as presentation fallbacks

Discovery DTOs do not currently provide imagery, cuisine tags, ratings, review counts, or promotional badges. Live API cards therefore use local fallback artwork and omit unsupported metadata. Preview mode supplies clearly labelled demo values for visual validation.

The basket in Phase 1 is a local interaction preview. Authenticated Cart and Favorite endpoints were audited, but writes are not wired until the authentication UI exists; this avoids presenting unsaved state as server-owned data. No JWTs or browser storage are used.

## Deliberately deferred

Checkout, orders, authentication forms, profile, account pages, and operational dashboards are outside this phase.
