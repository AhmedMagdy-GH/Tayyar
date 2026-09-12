import type { RestaurantPresentation } from '../demo/data'

type Art = RestaurantPresentation['art']

const emoji: Record<Art, string> = { burger: '🍔', pizza: '🍕', egyptian: '🥙', breakfast: '🥞' }

export function FoodArt({ art = 'burger', className = '', label }: { art?: Art; className?: string; label?: string }) {
  return (
    <div className={`food-art food-art--${art} ${className}`} role="img" aria-label={label ?? `${art} illustration`}>
      <span className="food-art__blob" aria-hidden="true" />
      <span className="food-art__emoji" aria-hidden="true">{emoji[art]}</span>
      <span className="food-art__spark food-art__spark--one" aria-hidden="true">✦</span>
      <span className="food-art__spark food-art__spark--two" aria-hidden="true">●</span>
    </div>
  )
}
