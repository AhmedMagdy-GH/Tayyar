import { ApiError, clearCsrfToken, mutate, request } from './client'
import type { CurrentUser, LoginInput, RegistrationInput } from './contracts'

export const authApi = {
  async currentUser(): Promise<CurrentUser | null> {
    try {
      return await request<CurrentUser>('/users/me')
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) return null
      throw error
    }
  },
  async login(input: LoginInput) {
    await mutate<void>('/auth/session', { method: 'POST', body: JSON.stringify(input) })
    clearCsrfToken()
  },
  register(input: RegistrationInput) {
    return mutate<CurrentUser>('/auth/registrations', { method: 'POST', body: JSON.stringify(input) })
  },
  async logout() {
    await mutate<void>('/auth/session', { method: 'DELETE' })
    clearCsrfToken()
  },
}
