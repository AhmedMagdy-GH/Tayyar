import type { QueryClient } from '@tanstack/react-query'
import type { CurrentUser } from './contracts'
import { ApiError, invalidateSessionRequests } from './client'
import { queryKeys } from './queryKeys'

const isSession = (key: readonly unknown[]) => key[0] === 'session'

// Discovery queries can include a private saved-address filter, so evict those
// too. This is a security boundary, not routine mutation invalidation.
export function clearSessionData(client: QueryClient) {
  invalidateSessionRequests()
  void client.cancelQueries({ predicate: (query) => !isSession(query.queryKey) })
  client.removeQueries({ predicate: (query) => !isSession(query.queryKey) })
  client.getMutationCache().clear()
}

export function endSession(client: QueryClient) {
  void client.cancelQueries({ queryKey: queryKeys.currentUser })
  clearSessionData(client)
  client.setQueryData(queryKeys.currentUser, null)
}

const identity = (user: CurrentUser | null | undefined) => user ? `${user.id}:${[...user.roles].sort().join(',')}:${user.status}` : null

export function watchSession(client: QueryClient) {
  let previous = identity(client.getQueryData<CurrentUser | null>(queryKeys.currentUser))
  let clearing = false
  const expire = (error: unknown) => {
    if (clearing || !(error instanceof ApiError) || error.status !== 401 || error.body?.code === 'INVALID_CREDENTIALS') return
    clearing = true
    endSession(client)
    previous = null
    clearing = false
  }
  const queries = client.getQueryCache().subscribe((event) => {
    if (clearing || event.type !== 'updated') return
    if (isSession(event.query.queryKey) && event.action.type === 'success') {
      const next = identity(event.query.state.data as CurrentUser | null)
      if (next !== previous) {
        clearing = true
        clearSessionData(client)
        clearing = false
        previous = next
      }
    }
    if (event.action.type === 'error') expire(event.query.state.error)
  })
  const mutations = client.getMutationCache().subscribe((event) => {
    if (event.type === 'updated' && event.action.type === 'error') expire(event.mutation.state.error)
  })
  return () => { queries(); mutations() }
}
