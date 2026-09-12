import type { ApiErrorBody, CsrfResponse } from './contracts'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly body?: ApiErrorBody,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

let csrf: CsrfResponse | null = null

export function clearCsrfToken() {
  csrf = null
}

async function csrfHeaders(): Promise<Record<string, string>> {
  if (!csrf) {
    csrf = await request<CsrfResponse>('/auth/csrf')
  }
  return { [csrf.headerName]: csrf.token }
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
    credentials: 'include',
  })

  if (!response.ok) {
    let body: ApiErrorBody | undefined
    try {
      body = (await response.json()) as ApiErrorBody
    } catch {
      body = undefined
    }
    throw new ApiError(body?.message ?? `Request failed with status ${response.status}`, response.status, body)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export async function mutate<T>(path: string, init: RequestInit, retried = false): Promise<T> {
  const headers = new Headers(init.headers)
  Object.entries(await csrfHeaders()).forEach(([name, value]) => headers.set(name, value))
  try {
    return await request<T>(path, { ...init, headers })
  } catch (error) {
    if (error instanceof ApiError && error.status === 403 && error.body?.code === 'CSRF_INVALID' && !retried) {
      csrf = null
      return mutate<T>(path, init, true)
    }
    throw error
  }
}
