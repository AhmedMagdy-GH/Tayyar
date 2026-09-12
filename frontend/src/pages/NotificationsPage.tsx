import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, CheckCheck, ChevronRight, MailOpen } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Notification } from '../api/contracts'
import { safeErrorMessage } from '../api/errors'
import { notificationApi } from '../api/notifications'
import { queryKeys } from '../api/queryKeys'
import { AppHeader } from '../components/AppHeader'
import { MobileNav } from '../components/MobileNav'

const date = (value: string) => new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))

export function NotificationsPage() {
  const client = useQueryClient()
  const [page, setPage] = useState(0)
  const [filter, setFilter] = useState<'all' | 'unread'>('all')
  const [message, setMessage] = useState('')
  const read = filter === 'unread' ? false : undefined
  const inbox = useQuery({ queryKey: queryKeys.notifications(page, read), queryFn: () => notificationApi.list({ page, size: 10, read }), placeholderData: keepPreviousData, retry: false })
  const refresh = async () => { await client.invalidateQueries({ queryKey: ['customer', 'notifications'] }) }
  const markOne = useMutation({ mutationFn: (id: string) => notificationApi.markRead(id), onSuccess: async () => { setMessage('Notification marked as read.'); await refresh() }, onError: (error) => setMessage(safeErrorMessage(error)) })
  const markAll = useMutation({ mutationFn: () => notificationApi.markAllRead(), onSuccess: async (result) => { setMessage(result.markedRead ? `${result.markedRead} notification${result.markedRead === 1 ? '' : 's'} marked as read.` : 'You are all caught up.'); await refresh() }, onError: (error) => setMessage(safeErrorMessage(error)) })
  const pages = Math.ceil((inbox.data?.total ?? 0) / (inbox.data?.size ?? 10))
  const openOrder = (item: Notification) => item.relatedEntityType === 'ORDER' && item.relatedEntityId
  return <div className="page-shell"><AppHeader /><main className="shell customer-page notifications-page"><div className="customer-page__heading"><div><span className="eyebrow">Updates that matter</span><h1>Notifications</h1><p>Order updates and account messages from Tayyar.</p></div><button className="secondary-button" type="button" disabled={markAll.isPending || !inbox.data?.items.some((item) => !item.read)} onClick={() => markAll.mutate()}><CheckCheck size={17} /> {markAll.isPending ? 'Marking…' : 'Mark all read'}</button></div>
    <div className="notification-filters" role="group" aria-label="Filter notifications"><button type="button" aria-pressed={filter === 'all'} onClick={() => { setFilter('all'); setPage(0) }}>All</button><button type="button" aria-pressed={filter === 'unread'} onClick={() => { setFilter('unread'); setPage(0) }}>Unread</button></div>
    {message && <p className={markOne.isError || markAll.isError ? 'form-error' : 'success-banner'} role="status" aria-live="polite">{message}</p>}
    {inbox.isPending && <div className="session-loading" role="status"><span className="loader" /> Loading notifications…</div>}
    {inbox.isError && <div className="state-card" role="alert"><Bell size={30} /><h2>We couldn’t load notifications</h2><p>{safeErrorMessage(inbox.error)}</p><button className="primary-button" type="button" onClick={() => void inbox.refetch()}>Try again</button></div>}
    {inbox.data?.items.length === 0 && <div className="state-card"><MailOpen size={31} /><h2>{filter === 'unread' ? 'No unread notifications' : 'Nothing here yet'}</h2><p>{filter === 'unread' ? 'You are all caught up.' : 'Updates about your orders will appear here.'}</p></div>}
    {inbox.data && inbox.data.items.length > 0 && <div className="notifications-list" aria-busy={inbox.isFetching}>{inbox.data.items.map((item) => <article key={item.id} className={`notification-card ${item.read ? '' : 'notification-card--unread'}`}><span className="notification-card__icon"><Bell size={18} /><span className="sr-only">{item.read ? 'Read notification' : 'Unread notification'}</span></span><div><h2>{item.title}</h2><p>{item.body}</p><time dateTime={item.createdAt}>{date(item.createdAt)}</time></div><div className="notification-card__actions">{!item.read && <button type="button" disabled={markOne.isPending} onClick={() => markOne.mutate(item.id)}>Mark as read</button>}{openOrder(item) && <Link to={`/orders/${item.relatedEntityId}`}>View order <ChevronRight size={15} /></Link>}</div></article>)}</div>}
    {pages > 1 && <nav className="pagination" aria-label="Notification pages"><button type="button" disabled={page === 0 || inbox.isFetching} onClick={() => setPage((value) => value - 1)}>Previous</button><span>Page {page + 1} of {pages}</span><button type="button" disabled={page + 1 >= pages || inbox.isFetching} onClick={() => setPage((value) => value + 1)}>Next</button></nav>}
  </main><MobileNav /></div>
}
