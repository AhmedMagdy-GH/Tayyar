import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { vi } from 'vitest'
import { ApiError } from '../api/client'
import { authApi } from '../api/auth'
import { renderApp } from '../test/helpers'
import { LoginPage, RegisterPage } from './AuthPage'

describe('LoginPage', () => {
  it('submits the backend login contract and clears the password field', async () => {
    const login = vi.spyOn(authApi, 'login').mockResolvedValue()
    renderApp(<Routes><Route path="/login" element={<LoginPage />} /><Route path="/cart" element={<div>Returned to cart</div>} /></Routes>, undefined, [{ pathname: '/login', state: { from: '/cart' } } as never])
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'mona@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'a-secret' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    await waitFor(() => expect(login.mock.calls[0]?.[0]).toEqual({ email: 'mona@example.com', password: 'a-secret' }))
    expect(screen.getByText('Returned to cart')).toBeInTheDocument()
  })

  it('renders the generic invalid-credentials response safely', async () => {
    vi.spyOn(authApi, 'login').mockRejectedValue(new ApiError('Invalid email or password', 401, { code: 'INVALID_CREDENTIALS' }))
    renderApp(<LoginPage />, undefined, ['/login'])
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'wrong@example.com' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'wrong' } })
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The email or password is incorrect.')
    expect(screen.getByLabelText('Password')).toHaveValue('')
  })
})

describe('RegisterPage', () => {
  it('submits only the backend registration fields and routes to login', async () => {
    const register = vi.spyOn(authApi, 'register').mockResolvedValue({ id: 'u', fullName: 'Mona Hassan', email: 'mona@example.com', phone: '+201000000000', status: 'ACTIVE', emailVerified: false, roles: ['CUSTOMER'] })
    renderApp(<Routes><Route path="/register" element={<RegisterPage />} /><Route path="/login" element={<LoginPage />} /></Routes>, undefined, ['/register'])
    fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'Mona Hassan' } })
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'mona@example.com' } })
    fireEvent.change(screen.getByLabelText(/Phone number/), { target: { value: '201000000000' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'A secure password!' } })
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
    await waitFor(() => expect(register.mock.calls[0]?.[0]).toEqual({ fullName: 'Mona Hassan', email: 'mona@example.com', phone: '+201000000000', password: 'A secure password!' }))
    expect(await screen.findByText('Account created. Sign in to continue.')).toBeInTheDocument()
  })
})
