import { useDeferredValue, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight, ChevronRight, Sparkles } from 'lucide-react'
import { AppHeader } from '../components/AppHeader'
import { DemoNotice, EmptyState, ErrorState, LoadingCards } from '../components/States'
import { FoodArt } from '../components/FoodArt'
import { MobileNav } from '../components/MobileNav'
import { RestaurantCard } from '../components/RestaurantCard'
import { discoveryApi } from '../api/discovery'
import { queryKeys } from '../api/queryKeys'
import { demoPresentation, demoRestaurants, demoZones } from '../demo/data'
import { useAddresses, useCurrentUser } from '../hooks/useCustomer'

const categories = [
  ['Burgers', '🍔'], ['Pizza', '🍕'], ['Chicken', '🍗'], ['Shawarma', '🥙'],
  ['Egyptian', '🧆'], ['Healthy', '🥗'], ['Desserts', '🍰'], ['Breakfast', '🥞'],
] as const

export function HomePage() {
  const demo = new URLSearchParams(window.location.search).get('demo') === 'true'
  const [search, setSearch] = useState('')
  const [zoneId, setZoneId] = useState('')
  const deferredSearch = useDeferredValue(search.trim())
  const session = useCurrentUser()
  const addresses = useAddresses(Boolean(session.data))
  const defaultAddressId = addresses.data?.items.find((address) => address.isDefault)?.id

  const zonesQuery = useQuery({
    queryKey: queryKeys.zones,
    queryFn: discoveryApi.zones,
    enabled: !demo,
  })
  const restaurantsQuery = useQuery({
    queryKey: ['restaurants', deferredSearch, zoneId, defaultAddressId],
    queryFn: () => discoveryApi.restaurants({ query: deferredSearch, addressId: defaultAddressId, zoneId: defaultAddressId ? undefined : (zoneId || undefined) }),
    enabled: !demo,
  })

  const zones = demo ? demoZones : (zonesQuery.data?.items ?? [])
  const baseRestaurants = demo ? demoRestaurants : (restaurantsQuery.data?.items ?? [])
  const restaurants = demo
    ? baseRestaurants.filter((restaurant) => `${restaurant.name} ${restaurant.description} ${demoPresentation[restaurant.id]?.cuisine ?? ''}`.toLowerCase().includes(deferredSearch.toLowerCase()))
    : baseRestaurants

  function pickCategory(name: string) {
    setSearch(name)
    document.getElementById('popular')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <div className="page-shell">
      <AppHeader demo={demo} zones={zones} zoneId={zoneId} onZoneChange={setZoneId} search={search} onSearch={setSearch} />
      <main>
        {demo && <div className="shell notice-wrap"><DemoNotice /></div>}
        <section className="shell hero" aria-labelledby="hero-title">
          <div className="hero__copy">
            <span className="eyebrow"><Sparkles size={15} /> Made for your neighbourhood</span>
            <h1 id="hero-title">Your favorites, <em>delivered fast.</em></h1>
            <p>Discover restaurants and meals available near you, from local classics to new cravings.</p>
            <a className="primary-button" href="#popular">Explore restaurants <ArrowRight size={18} /></a>
          </div>
          <div className="hero__visual" aria-hidden="true">
            <span className="hero__ring hero__ring--one" />
            <span className="hero__ring hero__ring--two" />
            <FoodArt art="burger" />
            <span className="hero__delivery-card"><strong>25 min</strong><small>to your door</small></span>
          </div>
        </section>

        <section className="shell category-section" aria-labelledby="category-title">
          <div className="section-heading">
            <div><h2 id="category-title">Craving something delicious?</h2><p>Pick a category and start exploring.</p></div>
            <a href="#popular">View restaurants <ChevronRight size={17} /></a>
          </div>
          <div className="category-rail">
            {categories.map(([name, icon]) => (
              <button type="button" key={name} onClick={() => pickCategory(name)} aria-label={`Search ${name}`}>
                <span>{icon}</span><strong>{name}</strong>
              </button>
            ))}
          </div>
        </section>

        <section className="shell restaurants-section" id="popular" aria-labelledby="popular-title">
          <div className="section-heading">
            <div><h2 id="popular-title">{search ? `Results for “${search}”` : 'Popular near you'}</h2><p>Restaurants available near your selected location.</p></div>
            {search && <button className="text-button" type="button" onClick={() => setSearch('')}>Clear search</button>}
          </div>
          {!demo && restaurantsQuery.isPending && <LoadingCards />}
          {!demo && restaurantsQuery.isError && <ErrorState onRetry={() => void restaurantsQuery.refetch()} />}
          {(demo || restaurantsQuery.isSuccess) && restaurants.length === 0 && <EmptyState search={search} />}
          {(demo || restaurantsQuery.isSuccess) && restaurants.length > 0 && (
            <div className="restaurant-grid">
              {restaurants.map((restaurant) => <RestaurantCard key={restaurant.id} restaurant={restaurant} presentation={demo ? demoPresentation[restaurant.id] : undefined} demo={demo} />)}
            </div>
          )}
        </section>
      </main>
      <footer className="site-footer"><div className="shell"><strong>tayyar</strong><span>Good food should never feel far away.</span><small>© 2026 Tayyar</small></div></footer>
      <MobileNav demo={demo} />
    </div>
  )
}
