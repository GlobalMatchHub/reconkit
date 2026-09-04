import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { TopBar } from '../App'
import { api } from '../api'
import { Badge, Card, Empty } from '../components/Ui'
import { count, dateTime, money, severityClass, statusClass, titleCase } from '../format'

interface View {
  id: number; runId: number; counterpartyId: number; counterpartyName: string; businessDate: string
  type: string; severity: string; status: string; deltaMinor: number; currency: string
  detail: string; assignee: string | null; resolutionNote: string | null
  resolvedBy: string | null; autoClosedByRunId: number | null; version: number
}
interface EntryView {
  id: number; side: string; externalTxnId: string | null; approvalNo: string | null
  orderId: string | null; occurredAt: string; businessDate: string; grossMinor: number
  feeMinor: number; currency: string; txnStatus: string; paymentMethod: string | null
}
interface Detail { discrepancy: View; ledgerEntry: EntryView | null; statementEntry: EntryView | null }
interface Listing { items: View[]; total: number; page: number; size: number }
interface Counterparty { id: number; name: string }

const TYPES = [
  'MISSING_IN_STATEMENT', 'MISSING_IN_LEDGER', 'DUPLICATE_STATEMENT', 'DUPLICATE_LEDGER',
  'AMOUNT_MISMATCH', 'FEE_MISMATCH', 'LATE_POSTING', 'STATUS_MISMATCH', 'CURRENCY_MISMATCH',
]

export default function Discrepancies() {
  const [listing, setListing] = useState<Listing | null>(null)
  const [counterparties, setCounterparties] = useState<Counterparty[]>([])
  const [detail, setDetail] = useState<Detail | null>(null)
  const [status, setStatus] = useState('OPEN')
  const [severity, setSeverity] = useState('')
  const [type, setType] = useState('')
  const [counterpartyId, setCounterpartyId] = useState('')
  const [note, setNote] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  // The selected row lives in the URL so an operator can send a colleague the exact
  // difference they are looking at rather than a description of it.
  const [params, setParams] = useSearchParams()

  const load = useCallback(() => {
    const query = new URLSearchParams({ size: '60' })
    if (status) query.set('status', status)
    if (severity) query.set('severity', severity)
    if (type) query.set('type', type)
    if (counterpartyId) query.set('counterpartyId', counterpartyId)
    return api.get<Listing>(`/api/discrepancies?${query}`).then(setListing)
  }, [status, severity, type, counterpartyId])

  useEffect(() => { load() }, [load])
  useEffect(() => { api.get<Counterparty[]>('/api/counterparties').then(setCounterparties) }, [])

  const open = useCallback((id: number) => {
    setMessage(null)
    setNote('')
    setParams((current) => {
      const next = new URLSearchParams(current)
      next.set('selected', String(id))
      return next
    }, { replace: true })
    api.get<Detail>(`/api/discrepancies/${id}`).then(setDetail)
  }, [setParams])

  useEffect(() => {
    const selected = params.get('selected')
    if (selected && detail?.discrepancy.id !== Number(selected)) {
      api.get<Detail>(`/api/discrepancies/${selected}`).then(setDetail)
    }
  }, [params, detail])

  async function resolve(outcome: string) {
    if (!detail) return
    try {
      await api.post(`/api/discrepancies/${detail.discrepancy.id}/resolve`, {
        status: outcome, note, version: detail.discrepancy.version,
      })
      setMessage(`Marked ${titleCase(outcome)}.`)
      await load()
      open(detail.discrepancy.id)
    } catch (failure) {
      setMessage((failure as Error).message)
    }
  }

  return (
    <>
      <TopBar title="Differences"
              description="Ledger minus statement. Positive means we booked more than they reported." />
      <div className="content">
        <div className="filters">
          <label>Status</label>
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">Any</option>
            {['OPEN', 'INVESTIGATING', 'RESOLVED', 'WRITTEN_OFF', 'ACCEPTED'].map((value) => (
              <option key={value} value={value}>{titleCase(value)}</option>
            ))}
          </select>
          <label>Severity</label>
          <select value={severity} onChange={(e) => setSeverity(e.target.value)}>
            <option value="">Any</option>
            {['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((value) => (
              <option key={value} value={value}>{titleCase(value)}</option>
            ))}
          </select>
          <label>Type</label>
          <select value={type} onChange={(e) => setType(e.target.value)}>
            <option value="">Any</option>
            {TYPES.map((value) => <option key={value} value={value}>{titleCase(value)}</option>)}
          </select>
          <label>Counterparty</label>
          <select value={counterpartyId} onChange={(e) => setCounterpartyId(e.target.value)}>
            <option value="">All</option>
            {counterparties.map((counterparty) => (
              <option key={counterparty.id} value={counterparty.id}>{counterparty.name}</option>
            ))}
          </select>
        </div>

        <div className="split">
          <Card title="Queue" note={listing ? `${count(listing.total)} matching` : ''}>
            <div className="table-wrap" style={{ maxHeight: 'calc(100vh - 214px)', overflowY: 'auto' }}>
              <table>
                <thead>
                  <tr>
                    <th>Id</th><th>Date</th><th>Counterparty</th><th>Type</th>
                    <th>Severity</th><th className="num">Delta</th><th>Detail</th><th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {listing?.items.length === 0 && (
                    <tr><td colSpan={8}><Empty>Nothing matches these filters</Empty></td></tr>
                  )}
                  {listing?.items.map((row) => (
                    <tr key={row.id}
                        className={`clickable ${detail?.discrepancy.id === row.id ? 'selected' : ''}`}
                        onClick={() => open(row.id)}>
                      <td className="mono">#{row.id}</td>
                      <td className="mono">{row.businessDate}</td>
                      <td>{row.counterpartyName}</td>
                      <td>{titleCase(row.type)}</td>
                      <td><Badge kind={severityClass(row.severity)}>{row.severity}</Badge></td>
                      <td className="num">{money(row.deltaMinor, row.currency)}</td>
                      <td className="dim" style={{ maxWidth: 260, overflow: 'hidden',
                                                   textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {row.detail}
                      </td>
                      <td><Badge kind={statusClass(row.status)}>{titleCase(row.status)}</Badge></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card title="Investigation" note={detail ? `#${detail.discrepancy.id}` : ''}>
            <div className="card-body">
              {!detail && <Empty>Select a difference</Empty>}
              {detail && (
                <>
                  <p style={{ marginBottom: 12 }}>{detail.discrepancy.detail}</p>
                  <div className="diff" style={{ marginBottom: 12 }}>
                    <div>
                      <h4>Our ledger</h4>
                      <EntrySide entry={detail.ledgerEntry} other={detail.statementEntry} />
                    </div>
                    <div>
                      <h4>Their statement</h4>
                      <EntrySide entry={detail.statementEntry} other={detail.ledgerEntry} />
                    </div>
                  </div>

                  <dl className="kv" style={{ marginBottom: 12 }}>
                    <dt>Run</dt><dd>#{detail.discrepancy.runId}</dd>
                    <dt>Delta</dt>
                    <dd>{money(detail.discrepancy.deltaMinor, detail.discrepancy.currency, true)}</dd>
                    <dt>Version</dt><dd>{detail.discrepancy.version}</dd>
                    {detail.discrepancy.resolvedBy && (<>
                      <dt>Resolved by</dt><dd>{detail.discrepancy.resolvedBy}</dd>
                    </>)}
                    {detail.discrepancy.autoClosedByRunId && (<>
                      <dt>Closed by run</dt><dd>#{detail.discrepancy.autoClosedByRunId}</dd>
                    </>)}
                  </dl>

                  <label style={{ fontSize: 11, letterSpacing: '0.08em', textTransform: 'uppercase',
                                  color: 'var(--faint)' }}>
                    Resolution note
                  </label>
                  <input style={{ width: '100%', margin: '4px 0 10px' }} value={note}
                         placeholder="What did you find?"
                         onChange={(event) => setNote(event.target.value)} />
                  <div style={{ display: 'flex', gap: 7, flexWrap: 'wrap' }}>
                    <button onClick={() => resolve('INVESTIGATING')}>Investigating</button>
                    <button className="primary" onClick={() => resolve('RESOLVED')}>Resolved</button>
                    <button onClick={() => resolve('ACCEPTED')}>Accept as timing</button>
                    <button onClick={() => resolve('WRITTEN_OFF')}>Write off</button>
                  </div>
                  {message && <p style={{ marginTop: 9, fontSize: 12, color: 'var(--sub)' }}>{message}</p>}
                  <p style={{ marginTop: 10, fontSize: 11.5, color: 'var(--faint)' }}>
                    The version read above is sent with the decision. If somebody else has
                    changed this row since, the save is refused rather than overwriting them.
                  </p>
                </>
              )}
            </div>
          </Card>
        </div>
      </div>
    </>
  )
}

function EntrySide({ entry, other }: { entry: EntryView | null; other: EntryView | null }) {
  if (!entry) return <p style={{ fontSize: 12, color: 'var(--faint)' }}>No matching record</p>
  const rows: Array<[string, string, boolean]> = [
    ['Txn id', entry.externalTxnId ?? '-', false],
    ['Approval', entry.approvalNo ?? '-', false],
    ['Occurred', dateTime(entry.occurredAt), false],
    ['Business date', entry.businessDate, other ? entry.businessDate !== other.businessDate : false],
    ['Gross', money(entry.grossMinor, entry.currency), other ? entry.grossMinor !== other.grossMinor : false],
    ['Fee', money(entry.feeMinor, entry.currency), other ? entry.feeMinor !== other.feeMinor : false],
    ['Status', titleCase(entry.txnStatus), other ? entry.txnStatus !== other.txnStatus : false],
  ]
  return (
    <>
      {rows.map(([label, value, mismatch]) => (
        <div className={`row ${mismatch ? 'mismatch' : ''}`} key={label}>
          <span>{label}</span><span>{value}</span>
        </div>
      ))}
    </>
  )
}
