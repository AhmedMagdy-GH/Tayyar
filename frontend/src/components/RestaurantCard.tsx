import { Clock3, Heart, Star } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Restaurant } from '../api/contracts'
import type { RestaurantPresentation } from '../demo/data'
import { FoodArt } from './FoodArt'

function money(value: number | null, currency: string) {
  if (value === null) return null
  return value === 0 ? 'Free delivery' : `${currency} ${value} delivery`
}

export function RestaurantCard({ restaurant, presentation, demo = false }: { restaurant: Restaurant; presentation?: RestaurantPresentation; demo?: boolean }) {
  const href = `/restaurants/${restaurant.id}${demo ? '?demo=true' : ''}`
  return (
    <article className="restaurant-card">
      <Link to={href} className="restaurant-card__image" aria-label={`View ${restaurant.name}`}>
        <FoodArt art={presentation?.art} label={`${restaurant.name} fallback artwork`} />
        {presentation?.badge && <span className="card-badge">{presentation.badge}</span>}
      </Link>
      <button type="button" className="favorite-button" aria-label={`Save ${restaurant.name}; requires sign in`} aria-pressed="false" title="Sign in to save favorites"><Heart size={18} /></button>
      <Link to={href} className="restaurant-card__body">
        <div className="restaurant-card__title-row">
          <h3>{restaurant.name}</h3>
          {presentation?.rating && <span className="rating"><Star size={14} fill="currentColor" /> {presentation.rating}</span>}
        </div>
        <p className="restaurant-card__cuisine">{presentation?.cuisine ?? restaurant.description}</p>
        <div className="restaurant-card__meta">
          <span><Clock3 size={15} /> {restaurant.minimumEtaMinutes ? `${restaurant.minimumEtaMinutes}–${restaurant.minimumEtaMinutes + 10} min` : 'ETA at checkout'}</span>
          {money(restaurant.minimumDeliveryFee, restaurant.currency) && <span>{money(restaurant.minimumDeliveryFee, restaurant.currency)}</span>}
        </div>
        {!restaurant.openNow && <span className="closed-note">Currently closed</span>}
      </Link>
    </article>
  )
}
