import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Bar, BarChart, CartesianGrid, Line, ComposedChart, ResponsiveContainer,
  Tooltip, XAxis, YAxis,
} from 'recharts'
import { TopBar } from '../App'
import { api } from '../api'
import { Badge, Card, Empty, Stat } from '../components/Ui'
import { count, money, percent, shortDate, titleCase } from '../format'

interface DailyPoint { date: string; matched: number; unmatched: number; discrepancies: number; matchRate: number }
interface ChannelSummary {
  counterpartyId: number; code: string; name: string; channelType: string
  latestDate: string | null; matchRate: number; openDiscrepancies: number
  ledgerGrossMinor: number; statementGrossMinor: number; currency: string
}
interface TypeCount { type: string; count: number; absoluteAmountMinor: number }
interface Summary {
  from: string; to: string; runCount: number; ledgerRows: number; statementRows: number
  matchedRows: number; matchRate: number; openDiscrepancies: number; unexplainedMinor: number
  bySeverity: Record<string, number>; byType: TypeCount[]; daily: DailyPoint[]
  channels: ChannelSummary[]; byPass: Record<string, number>
}

const PASS_LABELS: Record<string, string> = {
  A_EXACT_ID: 'A · transaction id',
  B_COMPOSITE_KEY: 'B · approval + amount',
  C_FUZZY_AMOUNT_TIME: 'C · amount and time',
  D_AGGREGATE: 'D · aggregated payout',
}

export default function Dashboard() {
  const [data, setData] = useState<Summary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.get<Summary>('/api/dashboard').then(setData).catch((failure) => setError(failure.message))
  }, [])

  if (error) return <><TopBar title="Dashboard" /><div className="content"><Empty>{error}</Empty></div></>
  if (!data) return <><TopBar title="Dashboard" /><div className="content"><Empty>Loading</Empty></div></>

  const totalPasses = Object.values(data.byPass).reduce((sum, value) => sum + value, 0) || 1
  const chart = data.daily.map((point) => ({
    date: shortDate(point.date),
    matchRate: Number((point.matchRate * 100).toFixed(2)),
    differences: point.discrepancies,
  }))

  return (
    <>
      <TopBar title="Dashboard" description={`${data.from} to ${data.to} · ${data.runCount} runs`} />
      <div className="content">
        <div className="grid cols-4" style={{ marginBottom: 14 }}>
          <Stat label="Match rate" value={percent(data.matchRate, 2)}
                sub={`${count(data.matchedRows)} of ${count(data.ledgerRows + data.statementRows)} rows`} />
          <Stat label="Rows reconciled" value={count(data.ledgerRows + data.statementRows)}
                sub={`${count(data.ledgerRows)} ledger · ${count(data.statementRows)} statement`} />
          <Stat label="Open differences" value={count(data.openDiscrepancies)}
                sub={<Link to="/discrepancies" style={{ color: 'var(--accent)' }}>Work the queue</Link>} />
          <Stat label="Unexplained" value={money(data.unexplainedMinor, 'KRW', true)} small
                sub="Absolute value of open differences" />
        </div>

        <div className="grid cols-2" style={{ marginBottom: 14 }}>
          <div className="card span-2">
            <header className="card-head">
              <h3>Daily match rate and differences</h3>
              <span className="note">Latest run of each counterparty and date</span>
            </header>
            <div className="card-body" style={{ height: 232 }}>
              <ResponsiveContainer width="100%" height="100%">
                <ComposedChart data={chart} margin={{ top: 4, right: 8, bottom: 0, left: -18 }}>
                  <CartesianGrid stroke="#eef0f3" vertical={false} />
                  <XAxis dataKey="date" tick={{ fontSize: 10.5, fill: '#8a9096' }}
                         tickLine={false} axisLine={{ stroke: '#e3e6ea' }} interval={2} />
                  <YAxis yAxisId="rate"
                         domain={[(min: number) => Math.max(0, Math.floor(min * 2) / 2 - 0.5), 100]}
                         tick={{ fontSize: 10.5, fill: '#8a9096' }}
                         tickLine={false} axisLine={false} unit="%" width={60} />
                  <YAxis yAxisId="diff" orientation="right" tick={{ fontSize: 10.5, fill: '#8a9096' }}
                         tickLine={false} axisLine={false} width={30} />
                  <Tooltip contentStyle={{ fontSize: 12, borderRadius: 2, border: '1px solid #d2d6da' }} />
                  <Bar yAxisId="diff" dataKey="differences" fill="#f0c6ad" barSize={9} name="Differences" />
                  <Line yAxisId="rate" type="monotone" dataKey="matchRate" stroke="#1f44c8"
                        strokeWidth={1.8} dot={false} name="Match rate %" />
                </ComposedChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>

        <div className="grid cols-3" style={{ marginBottom: 14 }}>
          <Card title="Which pass matched it" note={`${count(totalPasses)} groups`}>
            <div className="card-body">
              {Object.entries(PASS_LABELS).map(([key, label]) => {
                const value = data.byPass[key] ?? 0
                return (
                  <div className="pass-row" key={key}>
                    <span className="name">{label}</span>
                    <span className="bar"><div style={{ width: `${(value / totalPasses) * 100}%` }} /></span>
                    <span className="count">{count(value)}</span>
                  </div>
                )
              })}
              <p style={{ marginTop: 10, fontSize: 11.5, color: 'var(--faint)' }}>
                Cheap and certain first. A row claimed by an earlier pass is never
                reconsidered by a later one.
              </p>
            </div>
          </Card>

          <Card title="Open differences by severity">
            <div className="card-body" style={{ height: 196 }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((severity) => ({
                  severity: titleCase(severity), value: data.bySeverity[severity] ?? 0,
                }))} layout="vertical" margin={{ top: 2, right: 16, bottom: 0, left: 8 }}>
                  <CartesianGrid stroke="#eef0f3" horizontal={false} />
                  <XAxis type="number" tick={{ fontSize: 10.5, fill: '#8a9096' }} tickLine={false} axisLine={false} />
                  <YAxis type="category" dataKey="severity" width={62}
                         tick={{ fontSize: 11, fill: '#5a6066' }} tickLine={false} axisLine={false} />
                  <Tooltip contentStyle={{ fontSize: 12, borderRadius: 2, border: '1px solid #d2d6da' }} />
                  <Bar dataKey="value" fill="#1f44c8" barSize={14} radius={[0, 1, 1, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </Card>

          <Card title="Open differences by type">
            <div className="table-wrap" style={{ maxHeight: 262, overflowY: 'auto' }}>
              <table>
                <tbody>
                  {data.byType.length === 0 && <tr><td className="dim">Nothing open</td></tr>}
                  {data.byType.map((row) => (
                    <tr key={row.type}>
                      <td>{titleCase(row.type)}</td>
                      <td className="num">{count(row.count)}</td>
                      <td className="num dim">{money(row.absoluteAmountMinor)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>

        <Card title="Channels" note="Match rate over the selected period">
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Counterparty</th><th>Channel</th><th>Latest date</th>
                  <th className="num">Our gross</th><th className="num">Their gross</th>
                  <th className="num">Difference</th><th className="num">Match rate</th>
                  <th className="num">Open</th>
                </tr>
              </thead>
              <tbody>
                {data.channels.map((channel) => {
                  const delta = channel.ledgerGrossMinor - channel.statementGrossMinor
                  return (
                    <tr key={channel.counterpartyId}>
                      <td><b>{channel.name}</b> <span className="dim mono">{channel.code}</span></td>
                      <td><Badge kind="plain">{titleCase(channel.channelType)}</Badge></td>
                      <td className="mono dim">{channel.latestDate ?? '-'}</td>
                      <td className="num">{money(channel.ledgerGrossMinor, channel.currency)}</td>
                      <td className="num">{money(channel.statementGrossMinor, channel.currency)}</td>
                      <td className="num" style={{ color: delta === 0 ? 'var(--faint)' : 'var(--critical)' }}>
                        {money(delta, channel.currency)}
                      </td>
                      <td className="num">{percent(channel.matchRate, 2)}</td>
                      <td className="num">
                        {channel.openDiscrepancies === 0
                          ? <Badge kind="ok">clear</Badge>
                          : <Badge kind={channel.openDiscrepancies > 30 ? 'high' : 'medium'}>
                              {count(channel.openDiscrepancies)}
                            </Badge>}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </Card>
      </div>
    </>
  )
}
