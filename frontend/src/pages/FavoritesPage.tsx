import { useEffect, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, Heart, HeartOff } from 'lucide-react'
import { Link } from 'react-router-dom'
import { ApiError } from '../api/client'
import { favoriteApi } from '../api/favorites'
import { safeErrorMessage } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import { AppHeader } from '../components/AppHeader'
import { MobileNav } from '../components/MobileNav'

const PAGE_SIZE = 12

export function FavoritesPage() {
  const [page, setPage] = useState(0)
  const [message, setMessage] = useState('')
  const client = useQueryClient()
  const favorites = useQuery({
    queryKey: queryKeys.favoritesPage(page, PAGE_SIZE),
    queryFn: () => favoriteApi.list({ page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
    retry: false,
  })
  const remove = useMutation({
    mutationFn: (restaurantId: string) => favoriteApi.remove(restaurantId),
    onSuccess: async () => {
      setMessage('Restaurant removed from favorites.')
      await client.invalidateQueries({ queryKey: queryKeys.favorites })
    },
    onError: (error) => setMessage(safeErrorMessage(error, 'This favorite could not be removed.')),
  })
  const pageCount = Math.ceil((favorites.data?.total ?? 0) / (favorites.data?.size ?? PAGE_SIZE))

  useEffect(() => {
    if (favorites.error instanceof ApiError && favorites.error.status === 401) {
      client.setQueryData(queryKeys.currentUser, null)
      client.removeQueries({ queryKey: ['customer'] })
    }
  }, [client, favorites.error])

  return <div className="page-shell"><AppHeader /><main className="shell customer-page favorites-page"><div className="customer-page__heading"><div><span className="eyebrow">Saved for later</span><h1>Favorites</h1><p>Your saved restaurants, loaded from your Tayyar account.</p></div></div>
    {message && <p className={remove.isError ? 'form-error' : 'success-banner'} role="status" aria-live="polite">{message}</p>}
    {favorites.isPending && <div className="session-loading" role="status"><span className="loader" /> Loading favorites…</div>}
    {favorites.isError && <div className="state-card" role="alert"><HeartOff size={31} /><h2>We couldn’t load your favorites</h2><p>{safeErrorMessage(favorites.error)}</p><button className="primary-button" type="button" onClick={() => void favorites.refetch()}>Try again</button></div>}
    {favorites.data?.items.length === 0 && <div className="state-card"><Heart size={31} /><h2>No favorites yet</h2><p>Save a restaurant from discovery or its menu, and it will appear here.</p><Link className="primary-button" to="/">Browse restaurants</Link></div>}
    {favorites.data && favorites.data.items.length > 0 && <div className="favorite-grid" aria-busy={favorites.isFetching}>{favorites.data.items.map((favorite) => <article className="favorite-card" key={favorite.restaurantId}><span className="favorite-card__icon"><Heart size={22} fill="currentColor" /></span><div><h2>{favorite.name}</h2><p>{favorite.description || 'Open this restaurant to explore its current menu.'}</p><small>Saved {new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(favorite.favoritedAt))}</small></div><footer><button type="button" aria-label={`Remove ${favorite.name} from favorites`} disabled={remove.isPending} onClick={() => remove.mutate(favorite.restaurantId)}><HeartOff size={16} /> Remove</button><Link to={`/restaurants/${favorite.restaurantId}`}>View restaurant <ArrowRight size={16} /></Link></footer></article>)}</div>}
    {pageCount > 1 && <nav className="pagination" aria-label="Favorite pages"><button type="button" disabled={page === 0 || favorites.isFetching} onClick={() => setPage((value) => value - 1)}>Previous</button><span>Page {page + 1} of {pageCount}</span><button type="button" disabled={page + 1 >= pageCount || favorites.isFetching} onClick={() => setPage((value) => value + 1)}>Next</button></nav>}
  </main><MobileNav /></div>
}
