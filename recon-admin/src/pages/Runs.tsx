import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { TopBar } from '../App'
import { api } from '../api'
import { Badge, Card, Empty } from '../components/Ui'
import { count, money, percent, statusClass } from '../format'

export interface RunView {
  id: number; counterpartyId: number; counterpartyName: string; businessDate: string
  sequenceNo: number; status: string; ruleVersion: string
  ledgerCount: number; statementCount: number; matchedCount: number; discrepancyCount: number
  matchRate: number; passA: number; passB: number; passC: number; passD: number
  ledgerGrossMinor: number; statementGrossMinor: number; durationMillis: number | null; currency: string
}

interface Counterparty { id: number; name: string; code: string }

export default function Runs() {
  const [runs, setRuns] = useState<RunView[]>([])
  const [counterparties, setCounterparties] = useState<Counterparty[]>([])
  const [counterpartyId, setCounterpartyId] = useState('')
  const [businessDate, setBusinessDate] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const navigate = useNavigate()

  const load = () => api.get<RunView[]>('/api/recon/runs?size=80').then(setRuns)

  useEffect(() => {
    load()
    api.get<Counterparty[]>('/api/counterparties').then((found) => {
      setCounterparties(found)
      if (found.length > 0) setCounterpartyId(String(found[0].id))
    })
  }, [])

  useEffect(() => {
    if (runs.length > 0 && !businessDate) setBusinessDate(runs[0].businessDate)
  }, [runs, businessDate])

  async function trigger() {
    setBusy(true)
    setMessage(null)
    try {
      const created = await api.post<RunView>('/api/recon/runs', {
        counterpartyId: Number(counterpartyId), businessDate,
      })
      setMessage(`Run ${created.id} finished as sequence ${created.sequenceNo} for ${created.businessDate}.`)
      await load()
    } catch (failure) {
      setMessage((failure as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <TopBar title="Reconciliation runs" description="Immutable. Re-running a date adds a sequence, it does not edit one." />
      <div className="content">
        <div className="filters">
          <label>Counterparty</label>
          <select value={counterpartyId} onChange={(e) => setCounterpartyId(e.target.value)}>
            {counterparties.map((counterparty) => (
              <option key={counterparty.id} value={counterparty.id}>{counterparty.name}</option>
            ))}
          </select>
          <label>Business date</label>
          <input type="date" value={businessDate} onChange={(e) => setBusinessDate(e.target.value)} />
          <button className="primary" onClick={trigger} disabled={busy || !counterpartyId || !businessDate}>
            {busy ? 'Running' : 'Run reconciliation'}
          </button>
          {message && <span style={{ fontSize: 12, color: 'var(--sub)' }}>{message}</span>}
        </div>

        <Card title="Runs" note={`${count(runs.length)} most recent`}>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Run</th><th>Counterparty</th><th>Date</th><th>Seq</th><th>Status</th>
                  <th className="num">Ledger</th><th className="num">Statement</th>
                  <th className="num">Matched</th><th className="num">Match rate</th>
                  <th className="num">Differences</th><th className="num">Gross delta</th>
                  <th className="num">Took</th>
                </tr>
              </thead>
              <tbody>
                {runs.length === 0 && <tr><td colSpan={12}><Empty>No runs yet</Empty></td></tr>}
                {runs.map((run) => (
                  <tr key={run.id} className="clickable" onClick={() => navigate(`/runs/${run.id}`)}>
                    <td className="mono">#{run.id}</td>
                    <td>{run.counterpartyName}</td>
                    <td className="mono">{run.businessDate}</td>
                    <td className="num">{run.sequenceNo}</td>
                    <td><Badge kind={statusClass(run.status)}>{run.status}</Badge></td>
                    <td className="num">{count(run.ledgerCount)}</td>
                    <td className="num">{count(run.statementCount)}</td>
                    <td className="num">{count(run.matchedCount)}</td>
                    <td className="num">{percent(run.matchRate, 2)}</td>
                    <td className="num">
                      {run.discrepancyCount === 0
                        ? <span className="dim">0</span>
                        : <Badge kind={run.discrepancyCount > 8 ? 'high' : 'medium'}>{run.discrepancyCount}</Badge>}
                    </td>
                    <td className="num">{money(run.ledgerGrossMinor - run.statementGrossMinor, run.currency)}</td>
                    <td className="num dim">{run.durationMillis ? `${run.durationMillis} ms` : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      </div>
    </>
  )
}
