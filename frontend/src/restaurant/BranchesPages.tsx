import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { restaurantOperationsApi } from '../api/restaurantOperations'
import { queryKeys } from '../api/queryKeys'
import type { BranchProfile, ManagedBranch, WeeklyHours } from '../api/contracts'
import { ErrorState } from '../components/States'
import { useRestaurantContext } from './RestaurantContext'
import { MutationMessage, PageHeader, Panel, Status } from './shared'

const branchSchema = z.object({
  name: z.string().trim().min(1).max(120), addressLine1: z.string().trim().min(1).max(200), addressLine2: z.string().max(200),
  city: z.string().trim().min(1).max(100), region: z.string().max(100), postalCode: z.string().max(20), countryCode: z.string().regex(/^[A-Z]{2}$/),
  phone: z.string().refine((value) => !value || /^\+[1-9][0-9]{7,14}$/.test(value), 'Use international format, for example +201234567890'),
  latitude: z.string().refine((value) => !value || !Number.isNaN(Number(value)), 'Enter a valid latitude'), longitude: z.string().refine((value) => !value || !Number.isNaN(Number(value)), 'Enter a valid longitude'),
  timezone: z.string().trim().min(1).max(100), deliveryModel: z.enum(['RESTAURANT_DELIVERY', 'PLATFORM_DELIVERY']),
})
type BranchForm = z.infer<typeof branchSchema>
const emptyBranch: BranchForm = { name: '', addressLine1: '', addressLine2: '', city: '', region: '', postalCode: '', countryCode: 'EG', phone: '', latitude: '', longitude: '', timezone: 'Africa/Cairo', deliveryModel: 'RESTAURANT_DELIVERY' }
const toProfile = (value: BranchForm): BranchProfile => ({ ...value, addressLine2: value.addressLine2 || null, region: value.region || null, postalCode: value.postalCode || null, phone: value.phone || null, latitude: value.latitude || null, longitude: value.longitude || null })
const toForm = (branch: ManagedBranch): BranchForm => ({ ...branch.profile, addressLine2: branch.profile.addressLine2 ?? '', region: branch.profile.region ?? '', postalCode: branch.profile.postalCode ?? '', phone: branch.profile.phone ?? '', latitude: branch.profile.latitude ?? '', longitude: branch.profile.longitude ?? '' })

export function BranchesPage() {
  const { selected } = useRestaurantContext()
  const client = useQueryClient()
  const branches = useQuery({ queryKey: queryKeys.branches(selected.restaurantId), queryFn: () => restaurantOperationsApi.branches(selected.restaurantId) })
  const [editing, setEditing] = useState<ManagedBranch | null>(null)
  const [showForm, setShowForm] = useState(false)
  const { register, handleSubmit, reset, formState: { errors } } = useForm<BranchForm>({ resolver: zodResolver(branchSchema), defaultValues: emptyBranch })
  useEffect(() => reset(editing ? toForm(editing) : emptyBranch), [editing, reset])
  const save = useMutation({
    mutationFn: (values: BranchForm) => editing ? restaurantOperationsApi.updateBranch(selected.restaurantId, editing.id, toProfile(values), editing.version) : restaurantOperationsApi.createBranch(selected.restaurantId, toProfile(values)),
    onSuccess: () => { setShowForm(false); setEditing(null); void client.invalidateQueries({ queryKey: queryKeys.branches(selected.restaurantId) }); void client.invalidateQueries({ queryKey: queryKeys.restaurantContext }) },
    onError: (error) => { if ((error as { status?: number }).status === 409) void branches.refetch() },
  })
  const operation = useMutation({ mutationFn: ({ branch, status, paused }: { branch: ManagedBranch; status: string; paused: boolean }) => restaurantOperationsApi.updateBranchOperation(selected.restaurantId, branch.id, status, paused, branch.version), onSuccess: () => void client.invalidateQueries({ queryKey: queryKeys.branches(selected.restaurantId) }), onError: (error) => { if ((error as { status?: number }).status === 409) void branches.refetch() } })
  return <><PageHeader eyebrow="Owner workspace" title="Branches" description="Manage branch profiles, delivery models, and live operating state." actions={<button className="primary-button" onClick={() => { setEditing(null); setShowForm((value) => !value) }}>{showForm ? 'Close editor' : 'Add branch'}</button>} />
    {showForm && <Panel title={editing ? `Edit ${editing.profile.name}` : 'New branch'}><form className="ops-form ops-form-grid" onSubmit={handleSubmit((values) => save.mutate(values))} noValidate>
      <label>Name<input {...register('name')} /></label><label>Address line 1<input {...register('addressLine1')} /></label><label>Address line 2<input {...register('addressLine2')} /></label><label>City<input {...register('city')} /></label><label>Region<input {...register('region')} /></label><label>Postal code<input {...register('postalCode')} /></label><label>Country code<input maxLength={2} {...register('countryCode')} /></label><label>Phone<input placeholder="+201234567890" {...register('phone')} /></label><label>Latitude<input inputMode="decimal" {...register('latitude')} /></label><label>Longitude<input inputMode="decimal" {...register('longitude')} /></label><label>Timezone<input {...register('timezone')} /></label><label>Delivery model<select {...register('deliveryModel')}><option value="RESTAURANT_DELIVERY">Restaurant delivery</option><option value="PLATFORM_DELIVERY">Platform delivery</option></select></label>
      {Object.values(errors)[0]?.message && <span className="field-error form-wide">{Object.values(errors)[0]?.message}</span>}<MutationMessage error={save.error} success={save.isSuccess ? 'Branch saved.' : undefined} /><button className="primary-button form-wide" disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save branch'}</button>
    </form></Panel>}
    {branches.isPending ? <Panel>Loading branches…</Panel> : branches.isError ? <ErrorState onRetry={() => void branches.refetch()} /> : <div className="ops-card-list">{branches.data.items.map((branch) => <Panel key={branch.id}><div className="branch-card-heading"><div><h2>{branch.profile.name}</h2><p>{branch.profile.addressLine1}, {branch.profile.city}</p></div><Status value={branch.paused ? 'PAUSED' : branch.status} /></div><dl className="compact-details"><div><dt>Delivery</dt><dd>{branch.profile.deliveryModel.replaceAll('_', ' ')}</dd></div><div><dt>Timezone</dt><dd>{branch.profile.timezone}</dd></div></dl><div className="ops-row-actions"><button onClick={() => { setEditing(branch); setShowForm(true) }}>Edit profile</button><button onClick={() => operation.mutate({ branch, status: branch.status, paused: !branch.paused })}>{branch.paused ? 'Resume' : 'Pause'}</button><button onClick={() => operation.mutate({ branch, status: branch.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE', paused: branch.paused })}>Set {branch.status === 'ACTIVE' ? 'inactive' : 'active'}</button></div></Panel>)}</div>}
  </>
}

const days = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
export function HoursPage() {
  const { selected } = useRestaurantContext()
  const client = useQueryClient()
  const branches = useQuery({ queryKey: queryKeys.branches(selected.restaurantId), queryFn: () => restaurantOperationsApi.branches(selected.restaurantId) })
  const [branchId, setBranchId] = useState('')
  const activeBranch = branchId || branches.data?.items[0]?.id || ''
  const schedule = useQuery({ queryKey: queryKeys.branchHours(selected.restaurantId, activeBranch), queryFn: () => restaurantOperationsApi.hours(selected.restaurantId, activeBranch), enabled: Boolean(activeBranch) })
  const [weekly, setWeekly] = useState<WeeklyHours[]>([])
  useEffect(() => { if (schedule.data) setWeekly(schedule.data.weekly) }, [schedule.data])
  const save = useMutation({ mutationFn: () => restaurantOperationsApi.updateHours(selected.restaurantId, activeBranch, { weekly, special: schedule.data!.special, version: schedule.data!.version }), onSuccess: (data) => client.setQueryData(queryKeys.branchHours(selected.restaurantId, activeBranch), data), onError: (error) => { if ((error as { status?: number }).status === 409) void schedule.refetch() } })
  const setDay = (weekday: number, enabled: boolean, field?: 'opensAt' | 'closesAt', value?: string) => setWeekly((current) => enabled ? current.some((row) => row.weekday === weekday) ? current.map((row) => row.weekday === weekday && field ? { ...row, [field]: value } : row) : [...current, { weekday, opensAt: '09:00', closesAt: '22:00' }] : current.filter((row) => row.weekday !== weekday))
  return <><PageHeader eyebrow="Owner workspace" title="Opening hours" description="One same-day interval per weekday, matching the backend schedule model." />
    <Panel><label className="compact-select">Branch<select value={activeBranch} onChange={(event) => setBranchId(event.target.value)}>{branches.data?.items.map((branch) => <option key={branch.id} value={branch.id}>{branch.profile.name}</option>)}</select></label></Panel>
    <Panel title="Weekly schedule">{schedule.isPending ? <p>Loading schedule…</p> : schedule.isError ? <ErrorState onRetry={() => void schedule.refetch()} /> : <form className="hours-editor" onSubmit={(event) => { event.preventDefault(); save.mutate() }}>{days.map((day, index) => { const row = weekly.find((item) => item.weekday === index + 1); return <div className="hours-row" key={day}><label><input type="checkbox" checked={Boolean(row)} onChange={(event) => setDay(index + 1, event.target.checked)} /> {day}</label>{row ? <><label>Opens <input type="time" value={row.opensAt.slice(0, 5)} onChange={(event) => setDay(index + 1, true, 'opensAt', event.target.value)} /></label><label>Closes <input type="time" value={row.closesAt.slice(0, 5)} onChange={(event) => setDay(index + 1, true, 'closesAt', event.target.value)} /></label></> : <span>Closed</span>}</div>})}<MutationMessage error={save.error} success={save.isSuccess ? 'Hours updated.' : undefined} /><button className="primary-button" disabled={save.isPending}>Save schedule</button></form>}</Panel>
  </>
}
