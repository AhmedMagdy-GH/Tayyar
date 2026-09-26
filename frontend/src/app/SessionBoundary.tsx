import { Fragment, useEffect, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { watchSession } from '../api/sessionCache'
import { useCurrentUser } from '../hooks/useCustomer'

export function SessionBoundary({ children }: { children: ReactNode }) {
  const client = useQueryClient()
  const { data: user } = useCurrentUser()
  useEffect(() => watchSession(client), [client])
  // Discard private component/form state as well as Query cache state.
  const identity = user ? `${user.id}:${[...user.roles].sort().join(',')}:${user.status}` : 'anonymous'
  return <Fragment key={identity}>{children}</Fragment>
}
