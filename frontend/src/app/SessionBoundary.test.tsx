import { useState } from 'react'
import { act, fireEvent, screen } from '@testing-library/react'
import { expect, it } from 'vitest'
import type { CurrentUser } from '../api/contracts'
import { queryKeys } from '../api/queryKeys'
import { renderApp, testClient } from '../test/helpers'
import { SessionBoundary } from './SessionBoundary'

function PrivateForm() {
  const [value, setValue] = useState('')
  return <input aria-label="Private draft" value={value} onChange={event => setValue(event.target.value)} />
}

it('discards mounted private form state when switching accounts', async () => {
  const client = testClient()
  const user: CurrentUser = { id: 'a', fullName: 'A', email: 'a@example.com', phone: null, status: 'ACTIVE', emailVerified: true, roles: ['CUSTOMER'] }
  client.setQueryData(queryKeys.currentUser, user)
  renderApp(<SessionBoundary><PrivateForm /></SessionBoundary>, client)
  fireEvent.change(screen.getByLabelText('Private draft'), { target: { value: 'Account A address' } })
  await act(async () => { client.setQueryData(queryKeys.currentUser, { ...user, id: 'b' }); await new Promise(resolve => setTimeout(resolve, 0)) })
  expect(screen.getByLabelText('Private draft')).toHaveValue('')
})
