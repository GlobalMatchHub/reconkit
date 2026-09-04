import { accessToken } from './auth'

let refreshHook: (() => Promise<string | null>) | null = null
let signOutHook: (() => void) | null = null

export function wireAuth(refresh: () => Promise<string | null>, signOut: () => void) {
  refreshHook = refresh
  signOutHook = signOut
}

async function call<T>(path: string, init: RequestInit, retry = true): Promise<T> {
  const token = accessToken()
  const headers = new Headers(init.headers)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')

  const response = await fetch(path, { ...init, headers })
  if (response.status === 401 && retry && refreshHook) {
    const refreshed = await refreshHook()
    if (refreshed) return call<T>(path, init, false)
    signOutHook?.()
    throw new Error('session expired')
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.message ?? `${response.status} ${response.statusText}`)
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

export const api = {
  get: <T,>(path: string) => call<T>(path, { method: 'GET' }),
  post: <T,>(path: string, body?: unknown, headers?: Record<string, string>) =>
    call<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body), headers }),
  upload: <T,>(path: string, form: FormData) => call<T>(path, { method: 'POST', body: form }),
}
