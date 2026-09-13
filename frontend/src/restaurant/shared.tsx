/* eslint-disable react-refresh/only-export-components */
import type { ReactNode } from 'react'
import { ApiError } from '../api/client'

export function PageHeader({ eyebrow, title, description, actions }: { eyebrow?: ReactNode; title: string; description?: string; actions?: ReactNode }) {
  return <header className="ops-page-header"><div>{eyebrow && <span className="ops-eyebrow">{eyebrow}</span>}<h1>{title}</h1>{description && <p>{description}</p>}</div>{actions && <div className="ops-header-actions">{actions}</div>}</header>
}

export function Panel({ title, children, actions }: { title?: string; children: ReactNode; actions?: ReactNode }) {
  return <section className="ops-panel">{(title || actions) && <header>{title && <h2>{title}</h2>}{actions}</header>}{children}</section>
}

export function Status({ value }: { value: string }) {
  return <span className={`ops-status ops-status-${value.toLowerCase().replaceAll('_', '-')}`}><span aria-hidden="true" />{value.replaceAll('_', ' ')}</span>
}

export function MutationMessage({ error, success }: { error: unknown; success?: string }) {
  if (error) {
    const api = error instanceof ApiError ? error : null
    const field = api?.body?.fieldErrors?.map((item) => `${item.field}: ${item.message}`).join(', ')
    return <p className="form-message error" role="alert">{field || api?.message || 'The request could not be completed.'}</p>
  }
  return success ? <p className="form-message success" role="status">{success}</p> : null
}

export const formatDateTime = (value: string) => new Intl.DateTimeFormat('en-EG', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
