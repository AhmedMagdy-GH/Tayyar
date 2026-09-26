import { afterEach, expect, it, vi } from 'vitest'
import { invalidateSessionRequests, mutate, request } from './client'

afterEach(() => { vi.unstubAllGlobals(); invalidateSessionRequests() })

it('discards a private mutation response arriving after an account boundary', async () => {
  let resolve!: (response: Response) => void
  vi.stubGlobal('fetch', vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'csrf' })))
    .mockImplementationOnce(() => new Promise<Response>(done => { resolve = done })))
  const pending = mutate('/private', { method: 'POST' })
  await vi.waitFor(() => expect(resolve).toBeDefined())
  invalidateSessionRequests()
  resolve(new Response(JSON.stringify({ address: 'old account private address' })))
  await expect(pending).rejects.toThrow('The session changed')
})

it('discards a late session lookup instead of restoring the logged-out identity', async () => {
  let resolve!: (response: Response) => void
  vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(done => { resolve = done })))
  const pending = request('/users/me')
  invalidateSessionRequests()
  resolve(new Response(JSON.stringify({ id: 'previous-account' })))
  await expect(pending).rejects.toThrow('The session changed')
})

it('does not send a mutation if the session changed while acquiring CSRF', async () => {
  let resolve!: (response: Response) => void
  const fetchMock = vi.fn(() => new Promise<Response>(done => { resolve = done }))
  vi.stubGlobal('fetch', fetchMock)
  const pending = mutate('/private', { method: 'POST' })
  invalidateSessionRequests()
  resolve(new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'old' })))
  await expect(pending).rejects.toThrow('The session changed')
  expect(fetchMock).toHaveBeenCalledTimes(1)
})
