import { ApiError } from './client'

export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {}
  return Object.fromEntries((error.body?.fieldErrors ?? []).map((entry) => [entry.field, entry.message]))
}

export function safeErrorMessage(error: unknown, fallback = 'Something went wrong. Please try again.') {
  if (!(error instanceof ApiError)) return fallback
  if (error.status === 429) return 'Too many attempts. Please wait before trying again.'
  if (error.body?.code === 'INVALID_CREDENTIALS') return 'The email or password is incorrect.'
  if (error.body?.code === 'REGISTRATION_CONFLICT') return 'Registration could not be completed. Try signing in or use different details.'
  if (error.body?.code === 'SESSION_INVALID' || error.status === 401) return 'Your session has expired. Please sign in again.'
  if (error.status === 403) return 'You do not have permission to perform this action.'
  if (error.status === 404) return 'The requested item could not be found.'
  if (error.status === 409) return 'This information changed. Refresh and try again.'
  if (error.status >= 500) return 'Tayyar is temporarily unavailable. Please try again shortly.'
  return error.body?.message && error.body.message.length <= 180 ? error.body.message : fallback
}
