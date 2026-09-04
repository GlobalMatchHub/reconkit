import { useState, type FormEvent } from 'react'
import { useAuth } from '../auth'
import { IconMark } from '../components/Icons'

export default function Login() {
  const { signIn } = useAuth()
  const [email, setEmail] = useState('operator@reconkit.dev')
  const [password, setPassword] = useState('reconkit')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await signIn(email, password)
    } catch (failure) {
      setError((failure as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login">
      <form className="box" onSubmit={submit}>
        <h1><IconMark /> Reconkit</h1>
        <p className="lede">Multi channel settlement reconciliation</p>
        {error && <div className="error">{error}</div>}
        <label htmlFor="email">Email</label>
        <input id="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" />
        <label htmlFor="password">Password</label>
        <input id="password" type="password" value={password}
               onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
        <button className="primary" disabled={busy}>{busy ? 'Signing in' : 'Sign in'}</button>
        <div className="hint">
          Demo accounts: <span className="mono">admin@reconkit.dev</span> and{' '}
          <span className="mono">operator@reconkit.dev</span>, password <span className="mono">reconkit</span>.
        </div>
      </form>
    </div>
  )
}
