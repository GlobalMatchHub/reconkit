import { useEffect } from 'react'
import { NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { wireAuth } from './api'
import { useAuth } from './auth'
import {
  IconAudit, IconChannel, IconDashboard, IconDiff, IconIngest,
  IconMark, IconReprocess, IconRuns, IconSettlement,
} from './components/Icons'
import Audit from './pages/Audit'
import Counterparties from './pages/Counterparties'
import Dashboard from './pages/Dashboard'
import Discrepancies from './pages/Discrepancies'
import Ingest from './pages/Ingest'
import Login from './pages/Login'
import Reprocess from './pages/Reprocess'
import RunDetail from './pages/RunDetail'
import Runs from './pages/Runs'
import SettlementDetail from './pages/SettlementDetail'
import Settlements from './pages/Settlements'

const PAGES = [
  { to: '/', label: 'Dashboard', icon: <IconDashboard />, group: 'Reconciliation' },
  { to: '/runs', label: 'Runs', icon: <IconRuns />, group: 'Reconciliation' },
  { to: '/discrepancies', label: 'Differences', icon: <IconDiff />, group: 'Reconciliation' },
  { to: '/settlements', label: 'Settlements', icon: <IconSettlement />, group: 'Money out' },
  { to: '/reprocess', label: 'Reprocess', icon: <IconReprocess />, group: 'Money out' },
  { to: '/ingest', label: 'Ingest', icon: <IconIngest />, group: 'Operations' },
  { to: '/counterparties', label: 'Counterparties', icon: <IconChannel />, group: 'Operations' },
  { to: '/audit', label: 'Audit log', icon: <IconAudit />, group: 'Operations' },
]

export default function App() {
  const { session, signOut, refresh } = useAuth()
  const location = useLocation()

  useEffect(() => { wireAuth(refresh, signOut) }, [refresh, signOut])

  if (!session) {
    return location.pathname === '/login' ? <Login /> : <Navigate to="/login" replace />
  }

  const groups = [...new Set(PAGES.map((page) => page.group))]

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <h1><IconMark /> Reconkit</h1>
          <span>Settlement reconciliation</span>
        </div>
        <nav className="nav">
          {groups.map((group) => (
            <div key={group}>
              <div className="nav-group">{group}</div>
              {PAGES.filter((page) => page.group === group).map((page) => (
                <NavLink key={page.to} to={page.to} end={page.to === '/'}>
                  {page.icon}
                  {page.label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-foot">
          <b>{session.displayName}</b>
          {session.email}
          <span className="mono who">{session.tenantId} · {session.role}</span>
          <button onClick={signOut}>Sign out</button>
        </div>
      </aside>
      <main className="main">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/runs" element={<Runs />} />
          <Route path="/runs/:id" element={<RunDetail />} />
          <Route path="/discrepancies" element={<Discrepancies />} />
          <Route path="/settlements" element={<Settlements />} />
          <Route path="/settlements/:id" element={<SettlementDetail />} />
          <Route path="/reprocess" element={<Reprocess />} />
          <Route path="/ingest" element={<Ingest />} />
          <Route path="/counterparties" element={<Counterparties />} />
          <Route path="/audit" element={<Audit />} />
          <Route path="/login" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  )
}

export function TopBar({ title, description, children }:
  { title: string; description?: string; children?: React.ReactNode }) {
  return (
    <header className="topbar">
      <h2>{title}</h2>
      {description && <span className="desc">{description}</span>}
      <span className="spacer" />
      {children}
    </header>
  )
}
