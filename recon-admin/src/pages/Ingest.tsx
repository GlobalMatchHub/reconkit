import { useEffect, useRef, useState } from 'react'
import { TopBar } from '../App'
import { api } from '../api'
import { Badge, Card, Empty } from '../components/Ui'
import { count, dateTime, statusClass, titleCase } from '../format'

interface BatchView {
  id: number; counterpartyId: number; counterpartyName: string; side: string; sourceName: string
  contentHash: string; businessDate: string; status: string; rowCount: number
  rejectedCount: number; rejectReason: string | null; loadedAt: string | null
}
interface UploadResponse {
  batchId: number; duplicate: boolean; loaded: number; rejected: number; rejectionSamples: string[]
}
interface Counterparty { id: number; name: string; mappingProfile: string }

export default function Ingest() {
  const [batches, setBatches] = useState<BatchView[]>([])
  const [counterparties, setCounterparties] = useState<Counterparty[]>([])
  const [counterpartyId, setCounterpartyId] = useState('')
  const [side, setSide] = useState('STATEMENT')
  const [result, setResult] = useState<UploadResponse | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [fileName, setFileName] = useState<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  const load = () => api.get<BatchView[]>('/api/ingest/batches').then(setBatches)

  useEffect(() => {
    load()
    api.get<Counterparty[]>('/api/counterparties').then((found) => {
      setCounterparties(found)
      if (found.length > 0) setCounterpartyId(String(found[0].id))
    })
  }, [])

  async function upload() {
    const file = fileInput.current?.files?.[0]
    if (!file) return
    setMessage(null)
    const form = new FormData()
    form.append('counterpartyId', counterpartyId)
    form.append('side', side)
    form.append('file', file)
    try {
      const response = await api.upload<UploadResponse>('/api/ingest', form)
      setResult(response)
      setMessage(response.duplicate
        ? `This exact file was already loaded as batch ${response.batchId}. Nothing was inserted.`
        : `Loaded ${response.loaded} rows into batch ${response.batchId}.`)
      await load()
    } catch (failure) {
      setMessage((failure as Error).message)
    }
  }

  return (
    <>
      <TopBar title="Ingest"
              description="Files are hashed before they are parsed, so the same file loaded twice is a no-op." />
      <div className="content">
        <div className="filters">
          <label>Counterparty</label>
          <select value={counterpartyId} onChange={(e) => setCounterpartyId(e.target.value)}>
            {counterparties.map((counterparty) => (
              <option key={counterparty.id} value={counterparty.id}>
                {counterparty.name} · {counterparty.mappingProfile}
              </option>
            ))}
          </select>
          <label>Side</label>
          <select value={side} onChange={(e) => setSide(e.target.value)}>
            <option value="STATEMENT">Their statement</option>
            <option value="LEDGER">Our ledger</option>
          </select>
          <span className="file-pick">
            <label className="pick" htmlFor="csv">Choose file</label>
            <input id="csv" type="file" accept=".csv,text/csv" ref={fileInput}
                   onChange={(event) => setFileName(event.target.files?.[0]?.name ?? null)} />
            <span className="name">{fileName ?? 'no file selected'}</span>
          </span>
          <button className="primary" onClick={upload}>Upload</button>
          {message && <span style={{ fontSize: 12, color: 'var(--sub)' }}>{message}</span>}
        </div>

        {result && result.rejectionSamples.length > 0 && (
          <Card title="Rejected rows" note={`${result.rejected} of ${result.loaded + result.rejected}`}>
            <div className="card-body">
              <p style={{ fontSize: 11.5, color: 'var(--faint)', marginBottom: 8 }}>
                Bad rows are recorded with their line number and reason. The rest of the file
                is still loaded: a settlement file with three bad lines out of forty thousand
                still has to go in tonight.
              </p>
              <ul style={{ listStyle: 'none' }}>
                {result.rejectionSamples.map((sample) => (
                  <li key={sample} className="mono" style={{ fontSize: 11.5, padding: '2px 0' }}>{sample}</li>
                ))}
              </ul>
            </div>
          </Card>
        )}

        <Card title="Load history" note={`${count(batches.length)} most recent`}>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Batch</th><th>Counterparty</th><th>Side</th><th>Source</th>
                  <th>Business date</th><th>Status</th><th className="num">Rows</th>
                  <th className="num">Rejected</th><th>Content hash</th><th>Loaded</th>
                </tr>
              </thead>
              <tbody>
                {batches.length === 0 && <tr><td colSpan={10}><Empty>No loads yet</Empty></td></tr>}
                {batches.map((batch) => (
                  <tr key={batch.id}>
                    <td className="mono">#{batch.id}</td>
                    <td>{batch.counterpartyName}</td>
                    <td><Badge kind="plain">{titleCase(batch.side)}</Badge></td>
                    <td className="mono dim">{batch.sourceName}</td>
                    <td className="mono">{batch.businessDate}</td>
                    <td><Badge kind={statusClass(batch.status)}>{titleCase(batch.status)}</Badge></td>
                    <td className="num">{count(batch.rowCount)}</td>
                    <td className="num">{batch.rejectedCount}</td>
                    <td className="mono dim" style={{ fontSize: 11 }}>{batch.contentHash.slice(0, 12)}…</td>
                    <td className="mono dim">{dateTime(batch.loadedAt)}</td>
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
