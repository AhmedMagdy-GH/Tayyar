import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { AppHeader } from '../components/AppHeader'
import { MobileNav } from '../components/MobileNav'
import { restaurantOperationsApi } from '../api/restaurantOperations'
import { queryKeys } from '../api/queryKeys'
import { MutationMessage, Status, formatDateTime } from './shared'

const schema = z.object({ name: z.string().trim().min(1).max(120), description: z.string().max(2000) })
type Form = z.infer<typeof schema>

export function RestaurantApplicationPage() {
  const client = useQueryClient()
  const applications = useQuery({ queryKey: queryKeys.restaurantApplications, queryFn: restaurantOperationsApi.applications })
  const form = useForm<Form>({ resolver: zodResolver(schema), defaultValues: { name: '', description: '' } })
  const apply = useMutation({ mutationFn: restaurantOperationsApi.apply, onSuccess: () => { form.reset(); void client.invalidateQueries({ queryKey: queryKeys.restaurantApplications }) } })
  return <div className="page-shell"><AppHeader /><main className="shell customer-page"><div className="customer-page__heading"><div><span className="eyebrow">Restaurant partner onboarding</span><h1>Bring your restaurant to Tayyar</h1><p>Application is separate from the active operations dashboard. Approval creates the Owner access needed there.</p></div></div>
    <div className="ops-split"><section className="ops-panel"><header><h2>Owner application</h2></header><form className="ops-form" onSubmit={form.handleSubmit((value) => apply.mutate(value))}><label>Restaurant name<input {...form.register('name')} /></label><label>Description<textarea rows={6} {...form.register('description')} /></label>{form.formState.errors.name && <span className="field-error">{form.formState.errors.name.message}</span>}<MutationMessage error={apply.error} success={apply.isSuccess ? 'Application submitted for review.' : undefined} /><button className="primary-button" disabled={apply.isPending}>{apply.isPending ? 'Submitting…' : 'Submit application'}</button></form></section>
    <section className="ops-panel"><header><h2>Your applications</h2></header><div className="ops-card-list">{applications.data?.items.length ? applications.data.items.map((item) => <article className="branch-context-card" key={item.id}><div><h3>{item.name}</h3><p>Revision {item.revision} · Updated {formatDateTime(item.updatedAt)}</p></div><Status value={item.status} /></article>) : <p className="empty-copy">No applications submitted yet.</p>}</div></section></div>
  </main><MobileNav /></div>
}
