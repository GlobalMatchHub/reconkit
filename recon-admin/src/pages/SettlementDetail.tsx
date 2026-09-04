import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { TopBar } from '../App'
import { api } from '../api'
import { Badge, Card, Empty, Stat } from '../components/Ui'
import { count, money, statusClass, titleCase } from '../format'
import type { SettlementView } from './Settlements'

export default function SettlementDetail() {
  const { id } = useParams()
  const [settlement, setSettlement] = useState<SettlementView | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  const load = () => api.get<SettlementView>(`/api/settlements/${id}`).then(setSettlement)
  useEffect(() => { load() }, [id])

  async function confirm() {
    setMessage(null)
    try {
      setSettlement(await api.post<SettlementView>(`/api/settlements/${id}/confirm`))
      setMessage('Confirmed. From here the statement is handed to payments and can no longer be regenerated.')
    } catch (failure) {
      setMessage((failure as Error).message)
    }
  }

  if (!settlement) return <><TopBar title="Statement" /><div className="content"><Empty>Loading</Empty></div></>

  const currency = settlement.currency
  const rows: Array<[string, number, boolean?]> = [
    ['Gross sales', settlement.grossMinor],
    ['Refunds', -settlement.refundMinor],
    ['Commission', -settlement.feeMinor],
    ['Tax on commission', -settlement.feeVatMinor],
    ['Adjustments', settlement.adjustmentMinor],
    ['Holdback withheld', -settlement.holdbackMinor],
    ['Holdback released', settlement.holdbackReleaseMinor],
    ['Net payable', settlement.netPayableMinor, true],
  ]

  return (
    <>
      <TopBar title={settlement.statementNo}
              description={`${settlement.counterpartyName} · ${settlement.periodStart} to ${settlement.periodEnd}`}>
        <Link className="btn" to="/settlements">Back</Link>
        {settlement.status === 'DRAFT' && <button className="primary" onClick={confirm}>Confirm</button>}
      </TopBar>
      <div className="content">
        {message && <p style={{ marginBottom: 12, fontSize: 12, color: 'var(--sub)' }}>{message}</p>}
        <div className="grid cols-4" style={{ marginBottom: 14 }}>
          <Stat label="Net payable" value={money(settlement.netPayableMinor, currency, true)} small
                sub={`Payout on ${settlement.payoutDate}`} />
          <Stat label="Transactions" value={count(settlement.transactionCount)}
                sub={`${settlement.lines.length} settlement days`} />
          <Stat label="Total fee" small
                value={money(settlement.feeMinor + settlement.feeVatMinor, currency, true)}
                sub="Commission plus tax, summed per transaction" />
          <Stat label="Open differences" value={count(settlement.openDiscrepancyCount)}
                sub={settlement.openDiscrepancyCount === 0
                  ? 'Period fully explained'
                  : 'Carried onto the statement deliberately'} />
        </div>

        <div className="split">
          <Card title="Daily breakdown" note={`${settlement.lines.length} days`}>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Business date</th><th className="num">Txns</th><th className="num">Gross</th>
                    <th className="num">Refunds</th><th className="num">Commission</th>
                    <th className="num">Tax</th><th className="num">Net</th>
                  </tr>
                </thead>
                <tbody>
                  {settlement.lines.map((line) => (
                    <tr key={line.businessDate}>
                      <td className="mono">{line.businessDate}</td>
                      <td className="num">{count(line.transactionCount)}</td>
                      <td className="num">{money(line.grossMinor, currency)}</td>
                      <td className="num">{money(-line.refundMinor, currency)}</td>
                      <td className="num">{money(-line.feeMinor, currency)}</td>
                      <td className="num">{money(-line.feeVatMinor, currency)}</td>
                      <td className="num">{money(line.netMinor, currency)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card title="Statement">
            <div className="card-body">
              <table>
                <tbody>
                  {rows.map(([label, value, strong]) => (
                    <tr key={label}>
                      <td style={{ fontWeight: strong ? 700 : 400 }}>{label}</td>
                      <td className="num" style={{ fontWeight: strong ? 700 : 400 }}>
                        {money(value, currency)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <dl className="kv" style={{ marginTop: 14 }}>
                <dt>Status</dt>
                <dd><Badge kind={statusClass(settlement.status)}>{titleCase(settlement.status)}</Badge></dd>
                <dt>Payout date</dt><dd>{settlement.payoutDate}</dd>
                <dt>Confirmed by</dt><dd>{settlement.confirmedBy ?? '-'}</dd>
              </dl>
              <p style={{ marginTop: 10, fontSize: 11.5, color: 'var(--faint)' }}>
                The holdback withheld and any earlier holdback released are separate lines.
                Netting them into one figure makes the month a merchant was held back look
                the same as the month they were paid back.
              </p>
            </div>
          </Card>
        </div>
      </div>
    </>
  )
}
