import type { ReactElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { render } from '@testing-library/react'

export function testClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } })
}

export function renderApp(ui: ReactElement, client = testClient(), initialEntries = ['/']) {
  return { client, ...render(<QueryClientProvider client={client}><MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter></QueryClientProvider>) }
}
