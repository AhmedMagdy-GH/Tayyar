import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, ArrowLeft, Building2, Check, Edit3, Home, MapPin, Plus, Trash2, X } from 'lucide-react'
import { Link } from 'react-router-dom'
import { addressApi } from '../api/addresses'
import type { Address, AddressProfile } from '../api/contracts'
import { fieldErrors, safeErrorMessage } from '../api/errors'
import { discoveryApi } from '../api/discovery'
import { queryKeys } from '../api/queryKeys'
import { AppHeader } from '../components/AppHeader'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { handleDialogKeyboard } from '../components/dialogKeyboard'
import { ErrorState } from '../components/States'
import { useAddresses } from '../hooks/useCustomer'

type AddressDraft = Omit<AddressProfile, 'latitude' | 'longitude'> & { deliveryZoneId: string }
const emptyDraft: AddressDraft = { label: '', street: '', building: '', floor: '', apartment: '', landmark: '', instructions: '', city: '', region: '', postalCode: '', countryCode: 'EG', deliveryZoneId: '' }
const nullable = (value: string | null) => value?.trim() || null

function draftFrom(address?: Address): AddressDraft {
  if (!address) return emptyDraft
  return { ...address.profile, floor: address.profile.floor ?? '', apartment: address.profile.apartment ?? '', landmark: address.profile.landmark ?? '', instructions: address.profile.instructions ?? '', region: address.profile.region ?? '', postalCode: address.profile.postalCode ?? '', deliveryZoneId: address.deliveryZoneId ?? '' }
}

function profileFrom(draft: AddressDraft, existing?: Address): AddressProfile {
  return { label: draft.label, street: draft.street, building: draft.building, floor: nullable(draft.floor), apartment: nullable(draft.apartment), landmark: nullable(draft.landmark), instructions: nullable(draft.instructions), city: draft.city, region: nullable(draft.region), postalCode: nullable(draft.postalCode), countryCode: draft.countryCode.toUpperCase(), latitude: existing?.profile.latitude ?? null, longitude: existing?.profile.longitude ?? null }
}

function AddressForm({ address, zones, pending, error, onClose, onSubmit }: { address?: Address; zones: Array<{ id: string; name: string; cityName: string }>; pending: boolean; error?: unknown; onClose: () => void; onSubmit: (draft: AddressDraft) => void }) {
  const [draft, setDraft] = useState(() => draftFrom(address))
  const firstRef = useRef<HTMLInputElement>(null)
  const errors = fieldErrors(error)
  useEffect(() => { firstRef.current?.focus() }, [])
  const set = (field: keyof AddressDraft, value: string) => setDraft((current) => ({ ...current, [field]: value }))
  const input = (field: keyof AddressDraft, label: string, required = false, options: { maxLength?: number; autoComplete?: string } = {}) => <label className={`form-field ${field === 'instructions' ? 'address-field--wide' : ''}`}><span>{label}{!required && <small>Optional</small>}</span><span className="form-control"><input ref={field === 'label' ? firstRef : undefined} value={draft[field] ?? ''} onChange={(event) => set(field, event.target.value)} required={required} maxLength={options.maxLength} autoComplete={options.autoComplete} aria-invalid={Boolean(errors[field] || errors[`profile.${field}`])} /></span>{(errors[field] || errors[`profile.${field}`]) && <em role="alert">{errors[field] || errors[`profile.${field}`]}</em>}</label>

  return <div className="dialog-backdrop" role="presentation">
    <section className="address-dialog" role="dialog" aria-modal="true" aria-labelledby="address-dialog-title" onKeyDown={(event) => handleDialogKeyboard(event, onClose, pending)}>
      <header><div><span className="auth-icon"><MapPin size={20} /></span><div><h2 id="address-dialog-title">{address ? 'Edit address' : 'Add a new address'}</h2><p>Enter the structured address details used by Tayyar.</p></div></div><button type="button" onClick={onClose} disabled={pending} aria-label="Close address form"><X size={20} /></button></header>
      {Boolean(error) && <div className="form-error" role="alert">{safeErrorMessage(error, 'The address could not be saved.')}</div>}
      <form onSubmit={(event: FormEvent) => { event.preventDefault(); onSubmit(draft) }}>
        <div className="address-form-grid">
          {input('label', 'Label', true, { maxLength: 80, autoComplete: 'address-level1' })}
          {input('street', 'Street', true, { maxLength: 200, autoComplete: 'street-address' })}
          {input('building', 'Building', true, { maxLength: 80 })}
          {input('floor', 'Floor', false, { maxLength: 40 })}
          {input('apartment', 'Apartment', false, { maxLength: 40 })}
          {input('landmark', 'Landmark', false, { maxLength: 200 })}
          {input('city', 'City', true, { maxLength: 100, autoComplete: 'address-level2' })}
          {input('region', 'Region / governorate', false, { maxLength: 100, autoComplete: 'address-level1' })}
          {input('postalCode', 'Postal code', false, { maxLength: 20, autoComplete: 'postal-code' })}
          {input('countryCode', 'Country code', true, { maxLength: 2, autoComplete: 'country' })}
          <label className="form-field"><span>Delivery zone <small>Manual selection</small></span><span className="form-control"><select value={draft.deliveryZoneId} onChange={(event) => set('deliveryZoneId', event.target.value)}><option value="">No managed zone</option>{zones.map((zone) => <option key={zone.id} value={zone.id}>{zone.name}, {zone.cityName}</option>)}</select></span></label>
          {input('instructions', 'Delivery instructions', false, { maxLength: 1000 })}
        </div>
        <p className="location-honesty"><AlertCircle size={15} /> Zones are selected manually. Tayyar does not infer or verify your physical location.</p>
        <div className="address-dialog__actions"><button type="button" onClick={onClose} disabled={pending}>Cancel</button><button type="submit" disabled={pending}>{pending ? 'Saving…' : address ? 'Save changes' : 'Add address'}</button></div>
      </form>
    </section>
  </div>
}

export function AddressesPage() {
  const addresses = useAddresses()
  const zones = useQuery({ queryKey: queryKeys.zones, queryFn: discoveryApi.zones })
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<Address | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Address | null>(null)
  const [notice, setNotice] = useState('')
  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.addresses })
  const save = useMutation({
    mutationFn: async (draft: AddressDraft) => {
      const current = editing === 'new' ? undefined : editing ?? undefined
      let saved = current ? await addressApi.edit(current.id, profileFrom(draft, current), current.version) : await addressApi.create(profileFrom(draft))
      const desiredZone = draft.deliveryZoneId || null
      if (saved.deliveryZoneId !== desiredZone) saved = await addressApi.setZone(saved.id, desiredZone, saved.version)
      return saved
    },
    onSuccess: async () => { await refresh(); setEditing(null); setNotice('Address saved successfully.') },
  })
  const makeDefault = useMutation({ mutationFn: (address: Address) => addressApi.setDefault(address.id, address.version), onSuccess: async () => { await refresh(); setNotice('Default address updated.') }, onError: () => void addresses.refetch() })
  const remove = useMutation({ mutationFn: (address: Address) => addressApi.remove(address.id, address.version), onSuccess: async () => { await refresh(); setDeleting(null); setNotice('Address deleted.') }, onError: () => { setDeleting(null); void addresses.refetch() } })

  return <div className="page-shell"><AppHeader /><main className="shell customer-page addresses-page">
    <div className="customer-page__heading"><div><Link to="/" className="back-inline"><ArrowLeft size={16} /> Back home</Link><h1>Saved addresses</h1><p>Manage structured delivery details and your default location.</p></div><button className="primary-button" type="button" onClick={() => { save.reset(); setEditing('new') }}><Plus size={17} /> Add address</button></div>
    {notice && <div className="success-banner" role="status" onClick={() => setNotice('')}>{notice}</div>}
    {addresses.isPending && <div className="session-loading" role="status"><span className="loader" /> Loading addresses…</div>}
    {addresses.isError && <ErrorState title="We couldn’t load your addresses" onRetry={() => void addresses.refetch()} />}
    {addresses.data?.items.length === 0 && <div className="empty-cart"><span><Home size={28} /></span><h2>No saved addresses yet</h2><p>Add your first address. It will become the default automatically.</p><button className="primary-button" type="button" onClick={() => setEditing('new')}><Plus size={17} /> Add address</button></div>}
    <div className="address-grid">{addresses.data?.items.map((address) => <article className={`address-card ${address.isDefault ? 'address-card--default' : ''}`} key={address.id}><header><span><Building2 size={19} /></span><div><h2>{address.profile.label}</h2>{address.isDefault && <em><Check size={12} /> Default</em>}</div></header><p>{address.profile.street}, Building {address.profile.building}{address.profile.floor ? `, Floor ${address.profile.floor}` : ''}{address.profile.apartment ? `, Apt ${address.profile.apartment}` : ''}</p><p>{address.profile.city}{address.profile.region ? `, ${address.profile.region}` : ''} · {address.profile.countryCode}</p><small>{address.deliveryZoneId ? `Managed zone: ${zones.data?.items.find((zone) => zone.id === address.deliveryZoneId)?.name ?? 'Assigned'}` : 'No managed delivery zone selected'}</small><footer>{!address.isDefault && <button type="button" onClick={() => makeDefault.mutate(address)} disabled={makeDefault.isPending}>Make default</button>}<button type="button" onClick={() => { save.reset(); setEditing(address) }}><Edit3 size={15} /> Edit</button><button type="button" className="danger-link" onClick={() => setDeleting(address)}><Trash2 size={15} /> Delete</button></footer></article>)}</div>
  </main>{editing && <AddressForm address={editing === 'new' ? undefined : editing} zones={zones.data?.items ?? []} pending={save.isPending} error={save.error} onClose={() => setEditing(null)} onSubmit={(draft) => save.mutate(draft)} />}{deleting && <ConfirmDialog title={`Delete ${deleting.profile.label}?`} description="This saved address will be removed from your account. Other addresses are not affected." confirmLabel="Delete address" pending={remove.isPending} onCancel={() => setDeleting(null)} onConfirm={() => remove.mutate(deleting)} />}</div>
}
