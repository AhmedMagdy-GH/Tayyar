import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { restaurantOperationsApi } from '../api/restaurantOperations'
import { queryKeys } from '../api/queryKeys'
import type { DeliveryRule, DeliveryRuleInput } from '../api/contracts'
import { formatMoney } from '../utils/money'
import { useRestaurantContext } from './RestaurantContext'
import { MutationMessage, PageHeader, Panel, Status } from './shared'

const schema = z.object({ zoneId: z.string().min(1), deliveryFee: z.string().regex(/^\d{1,10}(\.\d{1,2})?$/), minimumOrder: z.string().regex(/^\d{1,10}(\.\d{1,2})?$/), etaMinMinutes: z.number().int().positive(), etaMaxMinutes: z.number().int().positive(), enabled: z.boolean() }).refine((data) => data.etaMaxMinutes >= data.etaMinMinutes, { path: ['etaMaxMinutes'], message: 'Maximum ETA must be at least the minimum' })
type DeliveryForm = z.infer<typeof schema>
const emptyDeliveryForm: DeliveryForm = { zoneId: '', deliveryFee: '0.00', minimumOrder: '0.00', etaMinMinutes: 20, etaMaxMinutes: 35, enabled: true }

export function DeliveryManagementPage() {
  const { selected } = useRestaurantContext()
  const client = useQueryClient()
  const [branchId, setBranchId] = useState(selected.branches[0]?.branchId ?? '')
  const [cityId, setCityId] = useState('')
  const [editing, setEditing] = useState<DeliveryRule | null>(null)
  const cities = useQuery({ queryKey: ['geography', 'cities'], queryFn: restaurantOperationsApi.cities })
  const activeCity = cityId || cities.data?.items[0]?.id || ''
  const zones = useQuery({ queryKey: ['geography', 'zones', activeCity], queryFn: () => restaurantOperationsApi.zones(activeCity), enabled: Boolean(activeCity) })
  const rules = useQuery({ queryKey: queryKeys.deliveryRules(selected.restaurantId, branchId), queryFn: () => restaurantOperationsApi.deliveryRules(selected.restaurantId, branchId), enabled: Boolean(branchId) })
  const form = useForm<DeliveryForm>({ resolver: zodResolver(schema), defaultValues: emptyDeliveryForm })
  useEffect(() => { if (editing) form.reset({ zoneId: editing.deliveryZoneId, ...editing.rule, deliveryFee: String(editing.rule.deliveryFee), minimumOrder: String(editing.rule.minimumOrder) }) }, [editing, form])
  useEffect(() => { setBranchId(selected.branches[0]?.branchId ?? ''); setEditing(null); form.reset(emptyDeliveryForm) }, [selected.restaurantId, selected.branches, form])
  useEffect(() => { setEditing(null); form.reset(emptyDeliveryForm) }, [branchId, form])
  const save = useMutation({
    mutationFn: (value: DeliveryForm) => { const rule: DeliveryRuleInput = { deliveryFee: value.deliveryFee, minimumOrder: value.minimumOrder, etaMinMinutes: value.etaMinMinutes, etaMaxMinutes: value.etaMaxMinutes, enabled: value.enabled }; return editing ? restaurantOperationsApi.updateDeliveryRule(selected.restaurantId, branchId, value.zoneId, rule, editing.version) : restaurantOperationsApi.createDeliveryRule(selected.restaurantId, branchId, value.zoneId, rule) },
    onSuccess: () => { setEditing(null); form.reset(emptyDeliveryForm); void client.invalidateQueries({ queryKey: queryKeys.deliveryRules(selected.restaurantId, branchId) }) },
    onError: async (error) => { if ((error as { status?: number }).status === 409 && editing) { const refreshed = await rules.refetch(); setEditing(refreshed.data?.items.find((rule) => rule.id === editing.id) ?? null) } },
  })
  return <><PageHeader eyebrow="Owner workspace" title="Delivery zones" description="Manage explicit City and Zone rules—fees, minimums, ETA, and availability—without inferred map geometry." />
    <div className="ops-filter-bar"><label>Branch<select value={branchId} onChange={(event) => setBranchId(event.target.value)}>{selected.branches.map((branch) => <option key={branch.branchId} value={branch.branchId}>{branch.branchName}</option>)}</select></label><label>City<select value={activeCity} onChange={(event) => setCityId(event.target.value)}>{cities.data?.items.map((city) => <option key={city.id} value={city.id}>{city.name}</option>)}</select></label></div>
    <div className="ops-split"><Panel title={editing ? 'Edit zone rule' : 'Add zone rule'}><form className="ops-form" onSubmit={form.handleSubmit((value) => save.mutate(value))} noValidate><label>Zone<select {...form.register('zoneId')} disabled={Boolean(editing)} aria-invalid={Boolean(form.formState.errors.zoneId)} aria-describedby={form.formState.errors.zoneId ? 'delivery-zone-error' : undefined}><option value="">Choose zone</option>{zones.data?.items.map((zone) => <option key={zone.id} value={zone.id}>{zone.name}</option>)}</select></label>{form.formState.errors.zoneId && <span id="delivery-zone-error" className="field-error">Choose a delivery zone.</span>}<label>Delivery fee (EGP)<input inputMode="decimal" {...form.register('deliveryFee')} aria-invalid={Boolean(form.formState.errors.deliveryFee)} aria-describedby={form.formState.errors.deliveryFee ? 'delivery-fee-error' : undefined} /></label>{form.formState.errors.deliveryFee && <span id="delivery-fee-error" className="field-error">Enter a non-negative amount with up to 2 decimals.</span>}<label>Minimum order (EGP)<input inputMode="decimal" {...form.register('minimumOrder')} aria-invalid={Boolean(form.formState.errors.minimumOrder)} aria-describedby={form.formState.errors.minimumOrder ? 'minimum-order-error' : undefined} /></label>{form.formState.errors.minimumOrder && <span id="minimum-order-error" className="field-error">Enter a non-negative amount with up to 2 decimals.</span>}<label>ETA minimum (minutes)<input type="number" {...form.register('etaMinMinutes', { valueAsNumber: true })} aria-invalid={Boolean(form.formState.errors.etaMinMinutes)} /></label><label>ETA maximum (minutes)<input type="number" {...form.register('etaMaxMinutes', { valueAsNumber: true })} aria-invalid={Boolean(form.formState.errors.etaMaxMinutes)} aria-describedby={form.formState.errors.etaMaxMinutes ? 'eta-max-error' : undefined} /></label><label className="check-label"><input type="checkbox" {...form.register('enabled')} /> Rule enabled</label>{form.formState.errors.etaMaxMinutes && <span id="eta-max-error" className="field-error">{form.formState.errors.etaMaxMinutes.message}</span>}<MutationMessage error={save.error} /><button className="primary-button" disabled={save.isPending || !branchId || (Boolean(editing) && editing?.version == null)}>{editing ? 'Save rule' : 'Add rule'}</button>{editing && <button type="button" onClick={() => { setEditing(null); form.reset(emptyDeliveryForm) }}>Cancel</button>}</form></Panel>
    <Panel title="Configured rules"><div className="ops-card-list">{rules.data?.items.length ? rules.data.items.map((rule) => <article className="delivery-rule" key={rule.id}><div><h3>{zones.data?.items.find((zone) => zone.id === rule.deliveryZoneId)?.name ?? 'Delivery zone'}</h3><p>{formatMoney(rule.rule.deliveryFee)} fee · {formatMoney(rule.rule.minimumOrder)} minimum</p><p>{rule.rule.etaMinMinutes}–{rule.rule.etaMaxMinutes} min</p></div><div><Status value={rule.rule.enabled ? 'ENABLED' : 'DISABLED'} /><button onClick={() => setEditing(rule)}>Edit</button></div></article>) : <p className="empty-copy">No rules configured for this branch.</p>}</div></Panel></div>
  </>
}
