import { AlertCircle, RefreshCw, SearchX } from 'lucide-react'

export function LoadingCards() {
  return <div className="restaurant-grid" aria-label="Loading restaurants">{Array.from({ length: 4 }, (_, index) => <div className="skeleton-card" key={index}><span /><i /><i /></div>)}</div>
}

export function ErrorState({ title = 'We couldn’t load this right now', onRetry }: { title?: string; onRetry?: () => void }) {
  return <div className="state-card" role="alert"><AlertCircle size={30} /><h2>{title}</h2><p>Check that the Tayyar API is running, then try again.</p>{onRetry && <button className="primary-button" type="button" onClick={onRetry}><RefreshCw size={17} /> Try again</button>}</div>
}

export function EmptyState({ search }: { search?: string }) {
  return <div className="state-card"><SearchX size={30} /><h2>No restaurants found</h2><p>{search ? `Nothing matched “${search}”. Try another search.` : 'There are no restaurants available in this area yet.'}</p></div>
}

export function DemoNotice() {
  return <div className="demo-notice" role="status"><span>Visual preview</span> Restaurant names, artwork, ratings, cuisine labels, and menu content on this page are local demo data—not API results.</div>
}
