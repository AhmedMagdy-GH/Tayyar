import { useState, type FormEvent, type ReactNode } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Eye, EyeOff, LockKeyhole, Mail, UserRound } from 'lucide-react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { fieldErrors, safeErrorMessage } from '../api/errors'
import { queryKeys } from '../api/queryKeys'
import { BrandLogo } from '../components/BrandLogo'
import { FoodArt } from '../components/FoodArt'

type AuthLocationState = { from?: string; email?: string; registered?: boolean }

function intendedPath(value: unknown) {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : '/'
}

function Field({ label, name, type = 'text', value, onChange, autoComplete, error, icon, required = true }: { label: string; name: string; type?: string; value: string; onChange: (value: string) => void; autoComplete: string; error?: string; icon?: ReactNode; required?: boolean }) {
  return <label className="form-field"><span>{label}{!required && <small>Optional</small>}</span><span className="form-control">{icon}<input id={name} name={name} type={type} value={value} onChange={(event) => onChange(event.target.value)} autoComplete={autoComplete} required={required} aria-invalid={Boolean(error)} aria-describedby={error ? `${name}-error` : undefined} /></span>{error && <em id={`${name}-error`} role="alert">{error}</em>}</label>
}

export function LoginPage() {
  const location = useLocation()
  const state = (location.state ?? {}) as AuthLocationState
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [email, setEmail] = useState(state.email ?? '')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const login = useMutation({
    mutationFn: authApi.login,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.currentUser })
      navigate(intendedPath(state.from), { replace: true })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    const submittedPassword = password
    setPassword('')
    login.mutate({ email, password: submittedPassword })
  }

  return <main className="auth-layout">
    <section className="auth-panel">
      <div className="auth-panel__top"><BrandLogo /><Link to="/" className="back-inline"><ArrowLeft size={16} /> Back home</Link></div>
      <div className="auth-card">
        <span className="auth-icon"><LockKeyhole size={22} /></span>
        <h1>Welcome back</h1><p>Sign in to manage your cart, saved addresses, and orders.</p>
        {state.registered && <div className="success-banner" role="status">Account created. Sign in to continue.</div>}
        {login.isError && <div className="form-error" role="alert">{safeErrorMessage(login.error, 'Sign in failed. Please try again.')}</div>}
        <form onSubmit={submit} noValidate>
          <Field label="Email address" name="email" type="email" value={email} onChange={setEmail} autoComplete="email" icon={<Mail size={17} />} />
          <div className="password-field"><Field label="Password" name="password" type={showPassword ? 'text' : 'password'} value={password} onChange={setPassword} autoComplete="current-password" icon={<LockKeyhole size={17} />} /><button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? 'Hide password' : 'Show password'}>{showPassword ? <EyeOff size={17} /> : <Eye size={17} />}</button></div>
          <button className="auth-submit" type="submit" disabled={login.isPending}>{login.isPending ? 'Signing in…' : 'Sign in'}</button>
        </form>
        <p className="auth-switch">New to Tayyar? <Link to="/register" state={{ from: state.from }}>Create an account</Link></p>
      </div>
    </section>
    <aside className="auth-art"><div><span>Food you love.</span><h2>One secure sign-in away.</h2><FoodArt art="egyptian" /></div></aside>
  </main>
}

export function RegisterPage() {
  const location = useLocation()
  const state = (location.state ?? {}) as AuthLocationState
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const register = useMutation({
    mutationFn: authApi.register,
    onSuccess: () => navigate('/login', { replace: true, state: { from: state.from, email, registered: true } }),
  })
  const errors = fieldErrors(register.error)

  function submit(event: FormEvent) {
    event.preventDefault()
    const submittedPassword = password
    setPassword('')
    const normalizedPhone = phone.trim().replace(/^\+/, '')
    register.mutate({ fullName, email, phone: normalizedPhone ? `+${normalizedPhone}` : null, password: submittedPassword })
  }

  return <main className="auth-layout auth-layout--register">
    <section className="auth-panel">
      <div className="auth-panel__top"><BrandLogo /><Link to="/" className="back-inline"><ArrowLeft size={16} /> Back home</Link></div>
      <div className="auth-card">
        <span className="auth-icon"><UserRound size={22} /></span>
        <h1>Create your account</h1><p>Save addresses and keep your Tayyar cart with you.</p>
        {register.isError && <div className="form-error" role="alert">{safeErrorMessage(register.error, 'Registration failed. Check your details and try again.')}</div>}
        <form onSubmit={submit} noValidate>
          <Field label="Full name" name="fullName" value={fullName} onChange={setFullName} autoComplete="name" error={errors.fullName} icon={<UserRound size={17} />} />
          <Field label="Email address" name="email" type="email" value={email} onChange={setEmail} autoComplete="email" error={errors.email} icon={<Mail size={17} />} />
          <Field label="Phone number" name="phone" type="tel" value={phone} onChange={setPhone} autoComplete="tel" required={false} error={errors.phone} icon={<span className="field-prefix">+</span>} />
          <Field label="Password" name="password" type="password" value={password} onChange={setPassword} autoComplete="new-password" error={errors.password} icon={<LockKeyhole size={17} />} />
          <small className="password-help">Use a strong password up to 72 characters.</small>
          <button className="auth-submit" type="submit" disabled={register.isPending}>{register.isPending ? 'Creating account…' : 'Create account'}</button>
        </form>
        <p className="auth-switch">Already have an account? <Link to="/login" state={{ from: state.from }}>Sign in</Link></p>
      </div>
    </section>
    <aside className="auth-art"><div><span>Your next favourite is nearby.</span><h2>Fresh picks, delivered fast.</h2><FoodArt art="pizza" /></div></aside>
  </main>
}
