import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { TopBar } from '../App'
import { api } from '../api'
import { Badge, Card, Empty, Stat } from '../components/Ui'
import { count, money, percent, statusClass, titleCase } from '../format'
import type { RunView } from './Runs'

interface EntryView {
  id: number; side: string; externalTxnId: string | null; approvalNo: string | null
  orderId: string | null; occurredAt: string; businessDate: string; grossMinor: number
  feeMinor: number; currency: string; txnStatus: string; paymentMethod: string | null
}

interface MatchView {
  id: number; matchType: string; matchedBy: string; confidence: number; matchKey: string
  ledgerGrossMinor: number; statementGrossMinor: number; grossDelta: number
  ledgerFeeMinor: number; statementFeeMinor: number; currency: string
  ledgerEntries: EntryView[]; statementEntries: EntryView[]
}

const PASS_LABEL: Record<string, string> = {
  A_EXACT_ID: 'A · id', B_COMPOSITE_KEY: 'B · key', C_FUZZY_AMOUNT_TIME: 'C · score', D_AGGREGATE: 'D · group',
}

export default function RunDetail() {
  const { id } = useParams()
  const [run, setRun] = useState<RunView | null>(null)
  const [matches, setMatches] = useState<MatchView[]>([])
  const [selected, setSelected] = useState<MatchView | null>(null)

  useEffect(() => {
    api.get<RunView>(`/api/recon/runs/${id}`).then(setRun)
    api.get<MatchView[]>(`/api/recon/runs/${id}/matches?size=60`).then((found) => {
      setMatches(found)
      setSelected(found.find((match) => match.matchedBy === 'D_AGGREGATE') ?? found[0] ?? null)
    })
  }, [id])

  if (!run) return <><TopBar title="Run" /><div className="content"><Empty>Loading</Empty></div></>

  const passes = [
    ['A · transaction id', run.passA],
    ['B · approval + amount', run.passB],
    ['C · amount and time', run.passC],
    ['D · aggregated payout', run.passD],
  ] as const
  const totalPasses = passes.reduce((sum, [, value]) => sum + value, 0) || 1

  return (
    <>
      <TopBar title={`Run #${run.id}`}
              description={`${run.counterpartyName} · ${run.businessDate} · sequence ${run.sequenceNo} · rules ${run.ruleVersion}`}>
        <Link className="btn" to="/runs">Back to runs</Link>
      </TopBar>
      <div className="content">
        <div className="grid cols-4" style={{ marginBottom: 14 }}>
          <Stat label="Match rate" value={percent(run.matchRate, 2)}
                sub={`${count(run.matchedCount)} rows in a group`} />
          <Stat label="Rows" value={count(run.ledgerCount + run.statementCount)}
                sub={`${count(run.ledgerCount)} ledger · ${count(run.statementCount)} statement`} />
          <Stat label="Differences" value={count(run.discrepancyCount)}
                sub={<Link to="/discrepancies" style={{ color: 'var(--accent)' }}>Open the queue</Link>} />
          <Stat label="Gross delta" small
                value={money(run.ledgerGrossMinor - run.statementGrossMinor, run.currency, true)}
                sub={`ours ${money(run.ledgerGrossMinor, run.currency)} · theirs ${money(run.statementGrossMinor, run.currency)}`} />
        </div>

        <div className="grid cols-3" style={{ marginBottom: 14 }}>
          <Card title="Passes" note={`${run.durationMillis ?? 0} ms`}>
            <div className="card-body">
              {passes.map(([label, value]) => (
                <div className="pass-row" key={label}>
                  <span className="name">{label}</span>
                  <span className="bar"><div style={{ width: `${(value / totalPasses) * 100}%` }} /></span>
                  <span className="count">{count(value)}</span>
                </div>
              ))}
            </div>
          </Card>
          <Card title="Status">
            <div className="card-body">
              <dl className="kv">
                <dt>Status</dt><dd><Badge kind={statusClass(run.status)}>{run.status}</Badge></dd>
                <dt>Rule version</dt><dd>{run.ruleVersion}</dd>
                <dt>Sequence</dt><dd>{run.sequenceNo}</dd>
                <dt>Duration</dt><dd>{run.durationMillis ?? '-'} ms</dd>
              </dl>
              <p style={{ marginTop: 10, fontSize: 11.5, color: 'var(--faint)' }}>
                The rule version is stored with the run so a result from months ago can still
                be explained by the rules that produced it.
              </p>
            </div>
          </Card>
          <Card title="Selected match">
            <div className="card-body">
              {selected ? (
                <dl className="kv">
                  <dt>Type</dt><dd>{titleCase(selected.matchType)}</dd>
                  <dt>Matched by</dt><dd>{PASS_LABEL[selected.matchedBy] ?? selected.matchedBy}</dd>
                  <dt>Confidence</dt><dd>{selected.confidence.toFixed(3)}</dd>
                  <dt>Reason</dt><dd style={{ fontFamily: 'inherit' }}>{selected.matchKey}</dd>
                  <dt>Gross delta</dt><dd>{money(selected.grossDelta, selected.currency)}</dd>
                </dl>
              ) : <Empty>Select a match</Empty>}
            </div>
          </Card>
        </div>

        <div className="split">
          <Card title="Match groups" note={`${count(matches.length)} shown`}>
            <div className="table-wrap" style={{ maxHeight: 460, overflowY: 'auto' }}>
              <table>
                <thead>
                  <tr>
                    <th>Group</th><th>Type</th><th>Pass</th><th className="num">Conf.</th>
                    <th className="num">Rows</th><th className="num">Ours</th>
                    <th className="num">Theirs</th><th className="num">Delta</th>
                  </tr>
                </thead>
                <tbody>
                  {matches.map((match) => (
                    <tr key={match.id}
                        className={`clickable ${selected?.id === match.id ? 'selected' : ''}`}
                        onClick={() => setSelected(match)}>
                      <td className="mono">#{match.id}</td>
                      <td><Badge kind={match.matchType === 'ONE_TO_ONE' ? 'plain' : 'info'}>
                        {titleCase(match.matchType)}</Badge></td>
                      <td className="mono dim">{PASS_LABEL[match.matchedBy] ?? match.matchedBy}</td>
                      <td className="num">{match.confidence.toFixed(2)}</td>
                      <td className="num dim">{match.ledgerEntries.length}:{match.statementEntries.length}</td>
                      <td className="num">{money(match.ledgerGrossMinor, match.currency)}</td>
                      <td className="num">{money(match.statementGrossMinor, match.currency)}</td>
                      <td className="num" style={{ color: match.grossDelta === 0 ? 'var(--faint)' : 'var(--critical)' }}>
                        {money(match.grossDelta, match.currency)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card title="Members" note={selected ? `group #${selected.id}` : ''}>
            <div className="card-body">
              {!selected && <Empty>Select a match group</Empty>}
              {selected && (
                <>
                  <p style={{ fontSize: 11.5, color: 'var(--faint)', marginBottom: 10 }}>
                    {selected.matchKey}
                  </p>
                  <MemberTable title="Our ledger" entries={selected.ledgerEntries} currency={selected.currency} />
                  <MemberTable title="Their statement" entries={selected.statementEntries} currency={selected.currency} />
                </>
              )}
            </div>
          </Card>
        </div>
      </div>
    </>
  )
}

function MemberTable({ title, entries, currency }:
  { title: string; entries: EntryView[]; currency: string }) {
  const total = entries.reduce((sum, entry) => sum + entry.grossMinor, 0)
  return (
    <div style={{ marginBottom: 14 }}>
      <h4 style={{ fontSize: 10.5, letterSpacing: '0.1em', textTransform: 'uppercase',
                   color: 'var(--faint)', marginBottom: 5 }}>
        {title} · {entries.length} row{entries.length === 1 ? '' : 's'}
      </h4>
      <table>
        <tbody>
          {entries.map((entry) => (
            <tr key={entry.id}>
              <td className="mono" style={{ fontSize: 11.5 }}>
                {entry.externalTxnId ?? entry.approvalNo ?? entry.orderId ?? `#${entry.id}`}
              </td>
              <td className="dim" style={{ fontSize: 11.5 }}>{entry.occurredAt.slice(11, 16)}</td>
              <td className="num">{money(entry.grossMinor, currency)}</td>
            </tr>
          ))}
          {entries.length > 1 && (
            <tr>
              <td colSpan={2} style={{ fontWeight: 600 }}>Sum</td>
              <td className="num" style={{ fontWeight: 600 }}>{money(total, currency)}</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
