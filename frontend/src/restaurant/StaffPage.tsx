import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { restaurantOperationsApi } from '../api/restaurantOperations'
import { queryKeys } from '../api/queryKeys'
import type { RestaurantStaff } from '../api/contracts'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorState } from '../components/States'
import { useRestaurantContext } from './RestaurantContext'
import { formatDateTime, MutationMessage, PageHeader, Panel, Status } from './shared'

const staffSchema = z.object({ email: z.email('Enter a valid email address').transform((value) => value.trim().toLowerCase()) })
type StaffForm = z.infer<typeof staffSchema>

export function StaffManagementPage() {
  const { selected } = useRestaurantContext()
  const client = useQueryClient()
  const staff = useQuery({ queryKey: queryKeys.staff(selected.restaurantId), queryFn: () => restaurantOperationsApi.staff(selected.restaurantId) })
  const branches = useQuery({ queryKey: queryKeys.branches(selected.restaurantId), queryFn: () => restaurantOperationsApi.branches(selected.restaurantId) })
  const form = useForm<StaffForm>({ resolver: zodResolver(staffSchema), defaultValues: { email: '' } })
  const [removing, setRemoving] = useState<RestaurantStaff | null>(null)
  const add = useMutation({ mutationFn: (value: StaffForm) => restaurantOperationsApi.addStaff(selected.restaurantId, value.email), onSuccess: () => { form.reset(); void client.invalidateQueries({ queryKey: queryKeys.staff(selected.restaurantId) }) } })
  const remove = useMutation({ mutationFn: (member: RestaurantStaff) => restaurantOperationsApi.removeStaff(selected.restaurantId, member.userId), onSuccess: () => { setRemoving(null); void client.invalidateQueries({ queryKey: queryKeys.staff(selected.restaurantId) }) } })
  const assignment = useMutation({
    mutationFn: ({ member, branchId, assigned }: { member: RestaurantStaff; branchId: string; assigned: boolean }) => { const branch = branches.data!.items.find((item) => item.id === branchId)!; return assigned ? restaurantOperationsApi.unassignStaff(selected.restaurantId, branchId, member.userId, branch.version) : restaurantOperationsApi.assignStaff(selected.restaurantId, branchId, member.userId, branch.version) },
    onSuccess: () => { void client.invalidateQueries({ queryKey: queryKeys.staff(selected.restaurantId) }); void client.invalidateQueries({ queryKey: queryKeys.branches(selected.restaurantId) }) },
    onError: (error) => { if ((error as { status?: number }).status === 409) { void staff.refetch(); void branches.refetch() } },
  })
  return <><PageHeader eyebrow="Owner workspace" title="Restaurant staff" description="Membership grants access to this restaurant only; branch assignments define where Staff can operate." />
    <Panel title="Add existing user"><form className="ops-form inline-form" onSubmit={form.handleSubmit((value) => add.mutate(value))} noValidate><label>Email<input type="email" autoComplete="email" placeholder="staff@example.com" {...form.register('email')} /></label><button className="primary-button" disabled={add.isPending}>{add.isPending ? 'Adding…' : 'Add staff member'}</button>{form.formState.errors.email && <span className="field-error">{form.formState.errors.email.message}</span>}<MutationMessage error={add.error} success={add.isSuccess ? 'Staff membership created.' : undefined} /></form></Panel>
    {staff.isPending ? <Panel>Loading staff…</Panel> : staff.isError ? <ErrorState title="Staff list unavailable" onRetry={() => void staff.refetch()} /> : <div className="ops-card-list">{staff.data.items.map((member) => <Panel key={member.userId}><div className="staff-heading"><div><h2>{member.fullName}</h2><p>{member.email}</p><small>Member since {formatDateTime(member.createdAt)}</small></div><button className="danger-button" onClick={() => setRemoving(member)}>Remove membership</button></div><fieldset className="assignment-grid"><legend>Branch assignments</legend>{branches.data?.items.map((branch) => { const assigned = member.branches.some((item) => item.branchId === branch.id); return <label key={branch.id}><input type="checkbox" checked={assigned} onChange={() => assignment.mutate({ member, branchId: branch.id, assigned })} disabled={assignment.isPending} /><span>{branch.profile.name}</span><Status value={assigned ? 'ASSIGNED' : 'NOT ASSIGNED'} /></label>})}</fieldset><MutationMessage error={assignment.error} /></Panel>)}</div>}
    {removing && <ConfirmDialog title={`Remove ${removing.fullName}?`} description={`This removes access to ${selected.restaurantName} and its branch assignments. It does not remove access to any other restaurant.`} confirmLabel="Remove membership" pending={remove.isPending} onCancel={() => setRemoving(null)} onConfirm={() => remove.mutate(removing)} />}
  </>
}
