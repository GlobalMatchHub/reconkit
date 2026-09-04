import type { ReactNode } from 'react'

export function Card({ title, note, children, actions }:
  { title?: string; note?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && (
        <header className="card-head">
          {title && <h3>{title}</h3>}
          {note && <span className="note">{note}</span>}
          {actions}
        </header>
      )}
      {children}
    </section>
  )
}

export function Stat({ label, value, sub, small }:
  { label: string; value: ReactNode; sub?: ReactNode; small?: boolean }) {
  return (
    <section className="card stat">
      <div className="k">{label}</div>
      <div className={small ? 'v small mono' : 'v mono'}>{value}</div>
      {sub && <div className="sub">{sub}</div>}
    </section>
  )
}

export function Badge({ kind, children }: { kind: string; children: ReactNode }) {
  return <span className={`badge ${kind}`}>{children}</span>
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="empty">{children}</div>
}
