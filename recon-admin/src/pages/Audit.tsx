import { useEffect, useState } from 'react'
import { TopBar } from '../App'
import { api } from '../api'
import { Badge, Card, Empty } from '../components/Ui'
import { count, dateTime, titleCase } from '../format'

interface View {
  id: number; actor: string; action: string; entityType: string; entityId: string | null
  beforeJson: string | null; afterJson: string | null; requestId: string; occurredAt: string
}
interface Listing { items: View[]; total: number }

function pretty(json: string | null): string {
  if (!json) return '-'
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

export default function Audit() {
  const [listing, setListing] = useState<Listing | null>(null)
  const [selected, setSelected] = useState<View | null>(null)

  useEffect(() => {
    api.get<Listing>('/api/audit?size=60').then((found) => {
      setListing(found)
      setSelected(found.items.find((row) => row.beforeJson) ?? found.items[0] ?? null)
    })
  }, [])

  return (
    <>
      <TopBar title="Audit log"
              description="Append only. Every row carries the request id that produced it." />
      <div className="content">
        <div className="split">
          <Card title="Events" note={listing ? `${count(listing.total)} total` : ''}>
            <div className="table-wrap" style={{ maxHeight: 560, overflowY: 'auto' }}>
              <table>
                <thead>
                  <tr><th>When</th><th>Actor</th><th>Action</th><th>Entity</th><th>Request</th></tr>
                </thead>
                <tbody>
                  {listing?.items.length === 0 && <tr><td colSpan={5}><Empty>Nothing recorded</Empty></td></tr>}
                  {listing?.items.map((row) => (
                    <tr key={row.id} className={`clickable ${selected?.id === row.id ? 'selected' : ''}`}
                        onClick={() => setSelected(row)}>
                      <td className="mono dim">{dateTime(row.occurredAt)}</td>
                      <td>{row.actor}</td>
                      <td><Badge kind="info">{titleCase(row.action)}</Badge></td>
                      <td className="mono">{row.entityType} #{row.entityId ?? '-'}</td>
                      <td className="mono dim" style={{ fontSize: 11 }}>{row.requestId}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card title="Change" note={selected ? `#${selected.id}` : ''}>
            <div className="card-body">
              {!selected && <Empty>Select an event</Empty>}
              {selected && (
                <>
                  <dl className="kv" style={{ marginBottom: 12 }}>
                    <dt>Actor</dt><dd>{selected.actor}</dd>
                    <dt>Action</dt><dd>{selected.action}</dd>
                    <dt>Entity</dt><dd>{selected.entityType} #{selected.entityId ?? '-'}</dd>
                    <dt>Request id</dt><dd>{selected.requestId}</dd>
                    <dt>Occurred</dt><dd>{dateTime(selected.occurredAt)}</dd>
                  </dl>
                  <h4 style={{ fontSize: 10.5, letterSpacing: '0.1em', textTransform: 'uppercase',
                               color: 'var(--faint)', marginBottom: 5 }}>Before</h4>
                  <pre className="mono" style={{ fontSize: 11.5, background: 'var(--ground)',
                                                 padding: 9, borderRadius: 2, marginBottom: 12,
                                                 overflowX: 'auto' }}>{pretty(selected.beforeJson)}</pre>
                  <h4 style={{ fontSize: 10.5, letterSpacing: '0.1em', textTransform: 'uppercase',
                               color: 'var(--faint)', marginBottom: 5 }}>After</h4>
                  <pre className="mono" style={{ fontSize: 11.5, background: 'var(--ground)',
                                                 padding: 9, borderRadius: 2,
                                                 overflowX: 'auto' }}>{pretty(selected.afterJson)}</pre>
                  <p style={{ marginTop: 10, fontSize: 11.5, color: 'var(--faint)' }}>
                    Images are stored as JSON rather than a rendered message, because the
                    question asked six months later is never the one the message was written for.
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
