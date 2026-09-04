import { useEffect, useState } from 'react'
import { TopBar } from '../App'
import { api } from '../api'
import { Badge, Card, Empty } from '../components/Ui'
import { count, dateTime, statusClass, titleCase } from '../format'

interface TaskView {
  id: number; stepNo: number; action: string; state: string; attempt: number
  maxAttempts: number; reversible: boolean; lastError: string | null; claimedAt: string | null
}
interface JobView {
  id: number; idempotencyKey: string; jobType: string; targetRef: string; state: string
  requestedBy: string; taskTotal: number; taskSucceeded: number; taskFailed: number
  taskCompensated: number; startedAt: string | null; finishedAt: string | null
  failureReason: string | null; tasks: TaskView[]
}
interface RunView { id: number; counterpartyId: number; businessDate: string; counterpartyName: string }

function stepClass(state: string): string {
  switch (state) {
    case 'SUCCEEDED': return 'done'
    case 'FAILED': return 'failed'
    case 'COMPENSATED': return 'compensated'
    case 'ESCALATED': return 'escalated'
    default: return ''
  }
}

export default function Reprocess() {
  const [jobs, setJobs] = useState<JobView[]>([])
  const [selected, setSelected] = useState<JobView | null>(null)
  const [runs, setRuns] = useState<RunView[]>([])
  const [idempotencyKey, setIdempotencyKey] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = () => api.get<JobView[]>('/api/reprocess').then((found) => {
    setJobs(found)
    if (found.length > 0) setSelected((current) => found.find((job) => job.id === current?.id) ?? found[0])
  })

  useEffect(() => {
    load()
    api.get<RunView[]>('/api/recon/runs?size=1').then(setRuns)
    setIdempotencyKey(`rerun-${new Date().toISOString().slice(0, 19).replace(/[:T-]/g, '')}`)
  }, [])

  async function submit() {
    if (runs.length === 0) return
    setBusy(true)
    setMessage(null)
    const run = runs[0]
    try {
      const job = await api.post<JobView>('/api/reprocess', {
        jobType: 'RERUN_RECON',
        targetRef: `${run.counterpartyName} ${run.businessDate}`,
        steps: [{
          action: 'RERUN_RECON',
          payload: { counterpartyId: run.counterpartyId, businessDate: run.businessDate },
          compensation: { note: 'the previous run stays the reported one' },
        }],
      }, { 'Idempotency-Key': idempotencyKey })
      setMessage(`Job ${job.id} is ${titleCase(job.state)}.`)
      await load()
      setSelected(job)
    } catch (failure) {
      setMessage((failure as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <TopBar title="Reprocess"
              description="Every request carries an idempotency key. The same key never starts a second job." />
      <div className="content">
        <div className="filters">
          <label>Idempotency key</label>
          <input style={{ width: 260 }} value={idempotencyKey}
                 onChange={(event) => setIdempotencyKey(event.target.value)} />
          <button className="primary" onClick={submit} disabled={busy || runs.length === 0}>
            {busy ? 'Submitting' : 'Re-run the latest date'}
          </button>
          <span style={{ fontSize: 11.5, color: 'var(--faint)' }}>
            Submit twice with the same key: the second call returns the first job untouched.
          </span>
          {message && <span style={{ fontSize: 12, color: 'var(--sub)' }}>{message}</span>}
        </div>

        <div className="split">
          <Card title="Jobs" note={`${count(jobs.length)} most recent`}>
            <div className="table-wrap" style={{ maxHeight: 520, overflowY: 'auto' }}>
              <table>
                <thead>
                  <tr>
                    <th>Job</th><th>Type</th><th>Target</th><th>State</th>
                    <th className="num">Steps</th><th className="num">Done</th>
                    <th className="num">Rolled back</th><th>Requested by</th><th>Finished</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.length === 0 && <tr><td colSpan={9}><Empty>No jobs yet</Empty></td></tr>}
                  {jobs.map((job) => (
                    <tr key={job.id} className={`clickable ${selected?.id === job.id ? 'selected' : ''}`}
                        onClick={() => setSelected(job)}>
                      <td className="mono">#{job.id}</td>
                      <td>{titleCase(job.jobType)}</td>
                      <td className="dim">{job.targetRef}</td>
                      <td><Badge kind={statusClass(job.state)}>{titleCase(job.state)}</Badge></td>
                      <td className="num">{job.taskTotal}</td>
                      <td className="num">{job.taskSucceeded}</td>
                      <td className="num">{job.taskCompensated}</td>
                      <td className="dim">{job.requestedBy}</td>
                      <td className="mono dim">{dateTime(job.finishedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card title="Step timeline" note={selected ? `job #${selected.id}` : ''}>
            <div className="card-body">
              {!selected && <Empty>Select a job</Empty>}
              {selected && (
                <>
                  <dl className="kv" style={{ marginBottom: 14 }}>
                    <dt>Idempotency key</dt><dd>{selected.idempotencyKey}</dd>
                    <dt>State</dt>
                    <dd><Badge kind={statusClass(selected.state)}>{titleCase(selected.state)}</Badge></dd>
                    <dt>Started</dt><dd>{dateTime(selected.startedAt)}</dd>
                    <dt>Finished</dt><dd>{dateTime(selected.finishedAt)}</dd>
                  </dl>
                  {selected.failureReason && (
                    <p style={{ fontSize: 12, color: 'var(--critical)', marginBottom: 12 }}>
                      {selected.failureReason}
                    </p>
                  )}
                  <div className="timeline">
                    {selected.tasks.map((task) => (
                      <div key={task.id} className={`step ${stepClass(task.state)}`}>
                        <div className="title">
                          {task.stepNo}. {titleCase(task.action)}{' '}
                          <Badge kind={statusClass(task.state)}>{titleCase(task.state)}</Badge>
                        </div>
                        <div className="meta">
                          attempt {task.attempt}/{task.maxAttempts} ·{' '}
                          {task.reversible ? 'reversible' : 'not reversible'} ·{' '}
                          claimed {dateTime(task.claimedAt)}
                        </div>
                        {task.lastError && <div className="error">{task.lastError}</div>}
                      </div>
                    ))}
                  </div>
                  <p style={{ marginTop: 12, fontSize: 11.5, color: 'var(--faint)' }}>
                    Steps are claimed with a conditional update, so two workers reaching the
                    same step cannot both run it. A step that cannot be undone is escalated
                    rather than retried.
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
