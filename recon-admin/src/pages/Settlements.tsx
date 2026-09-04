import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { TopBar } from '../App'
import { api } from '../api'
import { Badge, Card, Empty } from '../components/Ui'
import { count, money, statusClass, titleCase } from '../format'

export interface SettlementView {
  id: number; counterpartyId: number; counterpartyName: string; statementNo: string
  periodStart: string; periodEnd: string; payoutDate: string; status: string; currency: string
  grossMinor: number; refundMinor: number; feeMinor: number; feeVatMinor: number
  adjustmentMinor: number; holdbackMinor: number; holdbackReleaseMinor: number
  netPayableMinor: number; transactionCount: number; openDiscrepancyCount: number
  confirmedBy: string | null
  lines: Array<{ businessDate: string; transactionCount: number; grossMinor: number
                 refundMinor: number; feeMinor: number; feeVatMinor: number; netMinor: number }>
}
interface Counterparty { id: number; name: string }

export default function Settlements() {
  const [settlements, setSettlements] = useState<SettlementView[]>([])
  const [counterparties, setCounterparties] = useState<Counterparty[]>([])
  const [counterpartyId, setCounterpartyId] = useState('')
  const [periodStart, setPeriodStart] = useState('')
  const [periodEnd, setPeriodEnd] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const navigate = useNavigate()

  const load = () => api.get<SettlementView[]>('/api/settlements').then(setSettlements)

  useEffect(() => {
    load()
    api.get<Counterparty[]>('/api/counterparties').then((found) => {
      setCounterparties(found)
      if (found.length > 0) setCounterpartyId(String(found[0].id))
    })
  }, [])

  useEffect(() => {
    if (settlements.length > 0 && !periodStart) {
      setPeriodStart(settlements[0].periodStart)
      setPeriodEnd(settlements[0].periodEnd)
    }
  }, [settlements, periodStart])

  async function generate() {
    setMessage(null)
    try {
      const created = await api.post<SettlementView>('/api/settlements', {
        counterpartyId: Number(counterpartyId), periodStart, periodEnd,
      })
      setMessage(`${created.statementNo} generated, net payable ${money(created.netPayableMinor, created.currency, true)}.`)
      await load()
    } catch (failure) {
      setMessage((failure as Error).message)
    }
  }

  return (
    <>
      <TopBar title="Settlements" description="Generation runs inside a database row lock, so a period cannot be produced twice." />
      <div className="content">
        <div className="filters">
          <label>Counterparty</label>
          <select value={counterpartyId} onChange={(e) => setCounterpartyId(e.target.value)}>
            {counterparties.map((counterparty) => (
              <option key={counterparty.id} value={counterparty.id}>{counterparty.name}</option>
            ))}
          </select>
          <label>From</label>
          <input type="date" value={periodStart} onChange={(e) => setPeriodStart(e.target.value)} />
          <label>To</label>
          <input type="date" value={periodEnd} onChange={(e) => setPeriodEnd(e.target.value)} />
          <button className="primary" onClick={generate}>Generate statement</button>
          {message && <span style={{ fontSize: 12, color: 'var(--sub)' }}>{message}</span>}
        </div>

        <Card title="Statements" note={`${count(settlements.length)} total`}>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Statement</th><th>Counterparty</th><th>Period</th><th>Payout</th><th>Status</th>
                  <th className="num">Txns</th><th className="num">Gross</th><th className="num">Refunds</th>
                  <th className="num">Fee</th><th className="num">Holdback</th>
                  <th className="num">Net payable</th><th className="num">Open diffs</th>
                </tr>
              </thead>
              <tbody>
                {settlements.length === 0 && <tr><td colSpan={12}><Empty>No statements yet</Empty></td></tr>}
                {settlements.map((settlement) => (
                  <tr key={settlement.id} className="clickable"
                      onClick={() => navigate(`/settlements/${settlement.id}`)}>
                    <td className="mono">{settlement.statementNo}</td>
                    <td>{settlement.counterpartyName}</td>
                    <td className="mono dim">{settlement.periodStart} → {settlement.periodEnd}</td>
                    <td className="mono">{settlement.payoutDate}</td>
                    <td><Badge kind={statusClass(settlement.status)}>{titleCase(settlement.status)}</Badge></td>
                    <td className="num">{count(settlement.transactionCount)}</td>
                    <td className="num">{money(settlement.grossMinor, settlement.currency)}</td>
                    <td className="num">{money(-settlement.refundMinor, settlement.currency)}</td>
                    <td className="num">{money(-(settlement.feeMinor + settlement.feeVatMinor), settlement.currency)}</td>
                    <td className="num">{money(-settlement.holdbackMinor, settlement.currency)}</td>
                    <td className="num"><b>{money(settlement.netPayableMinor, settlement.currency)}</b></td>
                    <td className="num">
                      {settlement.openDiscrepancyCount === 0
                        ? <Badge kind="ok">clear</Badge>
                        : <Badge kind="medium">{settlement.openDiscrepancyCount}</Badge>}
                    </td>
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
