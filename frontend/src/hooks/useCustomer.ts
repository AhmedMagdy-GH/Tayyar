import { useQuery } from '@tanstack/react-query'
import { addressApi } from '../api/addresses'
import { authApi } from '../api/auth'
import { cartApi } from '../api/cart'
import { queryKeys } from '../api/queryKeys'

export function useCurrentUser() {
  return useQuery({ queryKey: queryKeys.currentUser, queryFn: authApi.currentUser, staleTime: 60_000, retry: false })
}

export function useCart(enabled = true) {
  return useQuery({ queryKey: queryKeys.cart, queryFn: cartApi.get, enabled, retry: false })
}

export function useAddresses(enabled = true) {
  return useQuery({ queryKey: queryKeys.addresses, queryFn: addressApi.list, enabled, retry: false })
}
