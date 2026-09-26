import { QueryClient } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { ApiError } from './client'
import type { CurrentUser } from './contracts'
import { queryKeys } from './queryKeys'
import { endSession, watchSession } from './sessionCache'

const user: CurrentUser = { id: 'a', fullName: 'A', email: 'a@example.com', phone: null, status: 'ACTIVE', emailVerified: true, roles: ['ADMIN'] }
function setup() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  client.setQueryData(queryKeys.currentUser, user)
  const stop = watchSession(client)
  for (const key of [['customer', 'private'], ['restaurant-operations', 'private'], ['driver-operations', 'private'], ['admin', 'private'], ['discovery', 'saved-address']]) client.setQueryData(key, 'private')
  return { client, stop }
}
const expectEmpty = (client: QueryClient) => expect(client.getQueryCache().getAll().filter(query => query.queryKey[0] !== 'session')).toEqual([])

describe('session confidentiality boundary', () => {
  it.each([null, { ...user, id: 'b' }, { ...user, roles: ['CUSTOMER'] as CurrentUser['roles'] }, { ...user, status: 'SUSPENDED' as const }])('evicts every role when identity changes to %j', next => {
    const { client, stop } = setup()
    client.setQueryData(queryKeys.currentUser, next)
    expectEmpty(client)
    stop()
  })
  it('keeps caches during a same-identity session refresh', () => {
    const { client, stop } = setup()
    client.setQueryData(queryKeys.currentUser, { ...user })
    expect(client.getQueryData(['admin', 'private'])).toBe('private')
    stop()
  })
  it('expires all roles after a protected query returns 401', async () => {
    const { client, stop } = setup()
    await expect(client.fetchQuery({ queryKey: ['admin', 'expired'], queryFn: () => Promise.reject(new ApiError('Expired', 401)) })).rejects.toThrow('Expired')
    expectEmpty(client)
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull()
    stop()
  })
  it('expires all roles after a protected mutation returns 401', async () => {
    const { client, stop } = setup()
    const mutation = client.getMutationCache().build(client, { mutationFn: () => Promise.reject(new ApiError('Expired', 401)) })
    await expect(mutation.execute(undefined)).rejects.toThrow('Expired')
    expectEmpty(client)
    expect(client.getMutationCache().getAll()).toEqual([])
    stop()
  })
  it('does not repopulate private queries when an old request finishes after logout', async () => {
    const { client, stop } = setup()
    let resolve!: (value: string) => void
    const pending = client.fetchQuery({ queryKey: ['admin', 'pending'], queryFn: () => new Promise<string>(done => { resolve = done }) }).catch(() => undefined)
    endSession(client)
    resolve('old private result')
    await pending
    expectEmpty(client)
    expect(client.getQueryData(queryKeys.currentUser)).toBeNull()
    stop()
  })
})
