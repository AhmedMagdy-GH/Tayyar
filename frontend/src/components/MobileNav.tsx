import { Heart, Home, Search, ShoppingBag } from 'lucide-react'
import { Link } from 'react-router-dom'

export function MobileNav({ demo = false }: { demo?: boolean }) {
  return <nav className="mobile-nav" aria-label="Primary navigation"><Link to={demo ? '/?demo=true' : '/'} className="is-active"><Home size={20} /><span>Home</span></Link><a href="#popular"><Search size={20} /><span>Browse</span></a><button type="button" title="Favorites coming soon"><Heart size={20} /><span>Saved</span></button><button type="button" title="Orders coming soon"><ShoppingBag size={20} /><span>Orders</span></button></nav>
}
