/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { restaurantOperationsApi } from '../api/restaurantOperations'
import { queryKeys } from '../api/queryKeys'
import type { RestaurantOperationsContext } from '../api/contracts'

type OperationsState = {
  contexts: RestaurantOperationsContext[]
  selected: RestaurantOperationsContext
  selectRestaurant: (restaurantId: string) => void
}

const Context = createContext<OperationsState | null>(null)

export function useRestaurantOperationsQuery(enabled = true) {
  return useQuery({ queryKey: queryKeys.restaurantContext, queryFn: restaurantOperationsApi.context, enabled, retry: false, staleTime: 30_000 })
}

export function RestaurantContextProvider({ contexts, children }: { contexts: RestaurantOperationsContext[]; children: ReactNode }) {
  const [selectedId, setSelectedId] = useState(() => window.localStorage.getItem('tayyar.restaurant.selected') ?? contexts[0]?.restaurantId)
  const selected = contexts.find((item) => item.restaurantId === selectedId) ?? contexts[0]

  useEffect(() => {
    if (selected) window.localStorage.setItem('tayyar.restaurant.selected', selected.restaurantId)
  }, [selected])

  const value = useMemo(() => ({ contexts, selected, selectRestaurant: setSelectedId }), [contexts, selected])
  return <Context.Provider value={value}>{children}</Context.Provider>
}

export function useRestaurantContext() {
  const value = useContext(Context)
  if (!value) throw new Error('Restaurant context is unavailable')
  return value
}
