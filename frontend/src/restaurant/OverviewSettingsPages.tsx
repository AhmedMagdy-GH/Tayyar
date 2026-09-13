import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { restaurantOperationsApi } from '../api/restaurantOperations'
import { ApiError } from '../api/client'
import { queryKeys } from '../api/queryKeys'
import { ErrorState } from '../components/States'
import { useRestaurantContext } from './RestaurantContext'
import { MutationMessage, PageHeader, Panel, Status } from './shared'

export function RestaurantOverviewPage() {
  const { selected } = useRestaurantContext()
  const restaurant = useQuery({ queryKey: queryKeys.restaurant(selected.restaurantId), queryFn: () => restaurantOperationsApi.restaurant(selected.restaurantId), enabled: selected.role === 'OWNER' })
  return <>
    <PageHeader eyebrow="Restaurant operations" title={`Good work starts here, ${selected.restaurantName}`} description={selected.role === 'OWNER' ? 'A live view of your restaurant and branch operating context.' : 'Your queue is limited to the branches assigned to you.'} />
    <div className="ops-metric-grid">
      <Panel><span className="metric-label">Restaurant state</span><strong>{selected.restaurantStatus}</strong><Status value={selected.restaurantStatus} /></Panel>
      <Panel><span className="metric-label">Available branches</span><strong>{selected.branches.length}</strong><span>{selected.role === 'STAFF' ? 'Assigned to you' : 'In operational context'}</span></Panel>
      <Panel><span className="metric-label">Access</span><strong>{selected.role === 'OWNER' ? 'Management + orders' : 'Assigned operations'}</strong><span>Server-scoped</span></Panel>
    </div>
    <Panel title="Branch readiness"><div className="ops-card-list">{selected.branches.length ? selected.branches.map((branch) => <article className="branch-context-card" key={branch.branchId}><div><h3>{branch.branchName}</h3><p>{branch.operationalState.replaceAll('_', ' ')}</p></div><Status value={branch.branchStatus} /></article>) : <p className="empty-copy">No branches are available in this context.</p>}</div></Panel>
    {selected.role === 'OWNER' && restaurant.isError && <ErrorState title="Restaurant details are unavailable" onRetry={() => void restaurant.refetch()} />}
  </>
}

const settingsSchema = z.object({ name: z.string().trim().min(1).max(120), description: z.string().max(2000) })
type SettingsForm = z.infer<typeof settingsSchema>

export function RestaurantSettingsPage() {
  const { selected } = useRestaurantContext()
  const client = useQueryClient()
  const restaurant = useQuery({ queryKey: queryKeys.restaurant(selected.restaurantId), queryFn: () => restaurantOperationsApi.restaurant(selected.restaurantId) })
  const { register, handleSubmit, reset, formState: { errors } } = useForm<SettingsForm>({ resolver: zodResolver(settingsSchema), defaultValues: { name: '', description: '' } })
  useEffect(() => { if (restaurant.data) reset({ name: restaurant.data.name, description: restaurant.data.description }) }, [restaurant.data, reset])
  const update = useMutation({
    mutationFn: (values: SettingsForm) => restaurantOperationsApi.updateRestaurant(selected.restaurantId, { ...values, version: restaurant.data!.version }),
    onSuccess: (data) => { client.setQueryData(queryKeys.restaurant(selected.restaurantId), data); void client.invalidateQueries({ queryKey: queryKeys.restaurantContext }) },
    onError: (error) => { if (error instanceof ApiError && error.status === 409) void restaurant.refetch() },
  })
  return <><PageHeader eyebrow="Owner settings" title="Restaurant profile" description="Changes use the latest server version. Conflicts refresh the authoritative profile." />
    <Panel>{restaurant.isPending ? <p>Loading settings…</p> : restaurant.isError ? <ErrorState onRetry={() => void restaurant.refetch()} /> : <form className="ops-form" onSubmit={handleSubmit((values) => update.mutate(values))} noValidate>
      <label>Name<input {...register('name')} aria-invalid={Boolean(errors.name)} /></label>{errors.name && <span className="field-error">{errors.name.message}</span>}
      <label>Description<textarea rows={5} {...register('description')} aria-invalid={Boolean(errors.description)} /></label>{errors.description && <span className="field-error">{errors.description.message}</span>}
      <MutationMessage error={update.error} success={update.isSuccess ? 'Restaurant profile updated.' : undefined} />
      <button className="primary-button" disabled={update.isPending}>{update.isPending ? 'Saving…' : 'Save profile'}</button>
    </form>}</Panel>
  </>
}
