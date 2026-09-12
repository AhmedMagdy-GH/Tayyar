import { screen } from '@testing-library/react'
import { render } from '@testing-library/react'
import { OrderTimeline } from './OrderStatus'

it('shows the active sequence and current semantic step', () => {
  render(<OrderTimeline status="PREPARING" history={[{ previousStatus: null, newStatus: 'PLACED', reason: null, occurredAt: '2026-09-12T10:00:00Z' }, { previousStatus: 'PLACED', newStatus: 'ACCEPTED', reason: null, occurredAt: '2026-09-12T10:02:00Z' }, { previousStatus: 'ACCEPTED', newStatus: 'PREPARING', reason: null, occurredAt: '2026-09-12T10:04:00Z' }]} />)
  expect(screen.getByRole('list', { name: /Current status: Preparing/ })).toBeInTheDocument()
  expect(screen.getByText('Current status')).toBeInTheDocument()
})

it.each(['REJECTED', 'CANCELLED'] as const)('treats %s as its own terminal state', (status) => {
  render(<OrderTimeline status={status} history={[{ previousStatus: 'PLACED', newStatus: status, reason: 'Restaurant could not fulfill it', occurredAt: '2026-09-12T10:00:00Z' }]} />)
  expect(screen.getByRole('status')).toHaveTextContent(status === 'REJECTED' ? 'Rejected' : 'Cancelled')
  expect(screen.getByText(/Restaurant could not fulfill it/)).toBeInTheDocument()
  expect(screen.queryByRole('list')).not.toBeInTheDocument()
})
