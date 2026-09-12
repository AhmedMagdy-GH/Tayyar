import { useEffect, useRef } from 'react'
import { AlertTriangle, X } from 'lucide-react'
import { handleDialogKeyboard } from './dialogKeyboard'

export function ConfirmDialog({ title, description, confirmLabel, cancelLabel = 'Cancel', pending = false, onConfirm, onCancel }: { title: string; description: string; confirmLabel: string; cancelLabel?: string; pending?: boolean; onConfirm: () => void; onCancel: () => void }) {
  const cancelRef = useRef<HTMLButtonElement>(null)
  useEffect(() => { cancelRef.current?.focus() }, [])
  return <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target && !pending) onCancel() }}>
    <section className="confirm-dialog" role="alertdialog" aria-modal="true" aria-labelledby="confirm-title" aria-describedby="confirm-description" onKeyDown={(event) => handleDialogKeyboard(event, onCancel, pending)}>
      <button className="dialog-close" type="button" onClick={onCancel} disabled={pending} aria-label="Close dialog"><X size={18} /></button>
      <span className="dialog-icon"><AlertTriangle size={23} /></span><h2 id="confirm-title">{title}</h2><p id="confirm-description">{description}</p>
      <div className="dialog-actions"><button ref={cancelRef} type="button" onClick={onCancel} disabled={pending}>{cancelLabel}</button><button type="button" onClick={onConfirm} disabled={pending}>{pending ? 'Working…' : confirmLabel}</button></div>
    </section>
  </div>
}
