import type { KeyboardEvent } from 'react'

const focusableSelector = 'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])'

export function handleDialogKeyboard(event: KeyboardEvent<HTMLElement>, onEscape: () => void, escapeDisabled = false) {
  if (event.key === 'Escape') {
    if (!escapeDisabled) onEscape()
    return
  }
  if (event.key !== 'Tab') return

  const focusable = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(focusableSelector)).filter((element) => !element.hidden)
  if (focusable.length === 0) {
    event.preventDefault()
    return
  }

  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}
