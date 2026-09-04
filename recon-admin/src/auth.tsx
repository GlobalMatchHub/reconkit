import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'

export interface Session {
  accessToken: string
  refreshToken: string
  email: string
  displayName: string
  role: string
  tenantId: string
}

interface AuthValue {
  session: Session | null
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => void
  refresh: () => Promise<string | null>
}

const STORAGE_KEY = 'reconkit.session'
const AuthContext = createContext<AuthValue | null>(null)

function read(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(read)

  const store = useCallback((next: Session | null) => {
    setSession(next)
    try {
      if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
      else localStorage.removeItem(STORAGE_KEY)
    } catch {
      /* a browser with storage disabled still works, it just forgets on reload */
    }
  }, [])

  const signIn = useCallback(
    async (email: string, password: string) => {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      })
      if (!response.ok) {
        const body = await response.json().catch(() => ({ message: 'sign in failed' }))
        throw new Error(body.message ?? 'sign in failed')
      }
      store((await response.json()) as Session)
    },
    [store],
  )

  // The access token is short lived on purpose, so a 401 is expected traffic rather than
  // an error. One refresh is attempted and the original call is replayed; a second failure
  // signs the operator out rather than looping.
  const refresh = useCallback(async () => {
    const current = read()
    if (!current) return null
    const response = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: current.refreshToken }),
    })
    if (!response.ok) {
      store(null)
      return null
    }
    const next = (await response.json()) as Session
    store(next)
    return next.accessToken
  }, [store])

  const value = useMemo(
    () => ({ session, signIn, signOut: () => store(null), refresh }),
    [session, signIn, store, refresh],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth used outside AuthProvider')
  return value
}

export function accessToken(): string | null {
  return read()?.accessToken ?? null
}
