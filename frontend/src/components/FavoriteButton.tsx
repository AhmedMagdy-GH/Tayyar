import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Heart } from 'lucide-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import type { Favorite, Page } from '../api/contracts'
import { favoriteApi } from '../api/favorites'
import { queryKeys } from '../api/queryKeys'
import { useCurrentUser } from '../hooks/useCustomer'

type Props = {
  restaurantId: string
  restaurantName: string
  description?: string
  className?: string
  demo?: boolean
}

export function FavoriteButton({ restaurantId, restaurantName, description = '', className = 'favorite-button', demo = false }: Props) {
  const session = useCurrentUser()
  const client = useQueryClient()
  const navigate = useNavigate()
  const location = useLocation()
  const authenticated = Boolean(session.data)
  const favorites = useQuery({
    queryKey: queryKeys.favoritesAll,
    queryFn: () => favoriteApi.list({ page: 0, size: 100 }),
    enabled: authenticated && !demo,
    staleTime: 30_000,
    retry: false,
  })
  const saved = favorites.data?.items.some((item) => item.restaurantId === restaurantId) ?? false

  useEffect(() => {
    if (favorites.error instanceof ApiError && favorites.error.status === 401) {
      client.setQueryData(queryKeys.currentUser, null)
      client.removeQueries({ queryKey: ['customer'] })
    }
  }, [client, favorites.error])

  const mutation = useMutation({
    mutationFn: () => saved ? favoriteApi.remove(restaurantId) : favoriteApi.add(restaurantId),
    onSuccess: () => {
      client.setQueryData<Page<Favorite>>(queryKeys.favoritesAll, (current) => {
        if (!current) return current
        if (saved) return { ...current, items: current.items.filter((item) => item.restaurantId !== restaurantId), total: Math.max(0, current.total - 1) }
        if (current.items.some((item) => item.restaurantId === restaurantId)) return current
        return { ...current, items: [{ restaurantId, name: restaurantName, description, favoritedAt: new Date().toISOString() }, ...current.items].slice(0, current.size), total: current.total + 1 }
      })
      void client.invalidateQueries({ queryKey: queryKeys.favorites })
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 401) {
        client.setQueryData(queryKeys.currentUser, null)
        client.removeQueries({ queryKey: ['customer'] })
        navigate('/login', { state: { from: `${location.pathname}${location.search}` } })
      }
    },
  })

  const label = saved ? `Remove ${restaurantName} from favorites` : `Save ${restaurantName} to favorites`
  const click = () => {
    if (demo) return
    if (!authenticated) {
      navigate('/login', { state: { from: `${location.pathname}${location.search}` } })
      return
    }
    mutation.mutate()
  }

  return <button type="button" className={`${className}${saved ? ' is-favorite' : ''}`} aria-label={demo ? `${restaurantName} is preview-only and cannot be saved` : label} aria-pressed={saved} title={demo ? 'Preview restaurants cannot be saved' : label} disabled={mutation.isPending || (authenticated && favorites.isPending) || demo} onClick={click}><Heart size={18} fill={saved ? 'currentColor' : 'none'} /></button>
}
