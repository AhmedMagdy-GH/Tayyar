/* eslint-disable react-refresh/only-export-components */
import { useEffect, useRef, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { ApiError } from '../api/client'
import { handleDialogKeyboard } from '../components/dialogKeyboard'

export const formatDateTime = (value: string) => new Intl.DateTimeFormat('en-EG', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
export const shortId = (value: string) => value.slice(0, 8).toUpperCase()
export const readable = (value: string) => value.replaceAll('_', ' ')
export const conflictMessage = (error: unknown, fallback = 'The operation could not be completed.') => error instanceof ApiError && error.status === 409 && error.message.length <= 180 ? error.message : error instanceof ApiError && error.status === 404 ? 'The requested resource was not found.' : error instanceof ApiError && error.status === 403 ? 'You do not have permission to perform this action.' : error instanceof ApiError && error.status === 401 ? 'Your session expired. Sign in again.' : fallback

export function PageHeader({ title, description, actions }: { title: string; description: string; actions?: ReactNode }) { return <header className="admin-page-header"><div><p>Platform operations</p><h1>{title}</h1><span>{description}</span></div>{actions && <div>{actions}</div>}</header> }
export function Status({ value }: { value: string }) { return <span className={`admin-status admin-status--${value.toLowerCase()}`}><i aria-hidden="true" />{readable(value)}</span> }
export function Loading({ label = 'Loading…' }: { label?: string }) { return <div className="admin-loading" role="status" aria-live="polite"><span className="loader" />{label}</div> }
export function Message({ children, success = false }: { children?: ReactNode; success?: boolean }) { return children ? <p className={`admin-message ${success ? 'success' : ''}`} role={success ? 'status' : 'alert'}>{children}</p> : null }
export function Empty({ children = 'No matching records.' }: { children?: ReactNode }) { return <div className="admin-empty">{children}</div> }
export function Pager({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (page: number) => void }) { const pages = Math.max(1, Math.ceil(total / size)); return <nav className="admin-pager" aria-label="Pagination"><button disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</button><span>Page {page + 1} of {pages} · {total} records</span><button disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>Next</button></nav> }

export function ReasonDialog({ title, description, confirmLabel, pending, required = true, onCancel, onConfirm }: { title: string; description: string; confirmLabel: string; pending: boolean; required?: boolean; onCancel: () => void; onConfirm: (reason: string) => void }) {
  const first = useRef<HTMLTextAreaElement>(null)
  const schema = z.object({ reason: required ? z.string().trim().min(1, 'Enter a reason.').max(1000, 'Reason must be 1,000 characters or fewer.') : z.string().trim().max(1000, 'Reason must be 1,000 characters or fewer.') })
  const { register, handleSubmit, formState: { errors } } = useForm<{ reason: string }>({ resolver: zodResolver(schema), defaultValues: { reason: '' } })
  useEffect(() => { first.current?.focus() }, [])
  const field = register('reason')
  return <div className="dialog-backdrop" role="presentation"><form className="admin-dialog" role="alertdialog" aria-modal="true" aria-labelledby="admin-dialog-title" onKeyDown={(event) => handleDialogKeyboard(event, onCancel, pending)} onSubmit={handleSubmit((value) => onConfirm(value.reason.trim()))}>
    <h2 id="admin-dialog-title">{title}</h2><p>{description}</p><label>Reason{required ? '' : ' (optional)'}<textarea {...field} ref={(node) => { field.ref(node); first.current = node }} aria-invalid={Boolean(errors.reason)} aria-describedby={errors.reason ? 'reason-error' : undefined} maxLength={1000} /></label>{errors.reason && <span id="reason-error" className="field-error">{errors.reason.message}</span>}
    <div className="admin-dialog__actions"><button type="button" onClick={onCancel} disabled={pending}>Cancel</button><button className="danger" disabled={pending}>{pending ? 'Confirming…' : confirmLabel}</button></div>
  </form></div>
}

export function BackLink({ to, children }: { to: string; children: ReactNode }) { return <a className="admin-back" href={to}>← {children}</a> }
