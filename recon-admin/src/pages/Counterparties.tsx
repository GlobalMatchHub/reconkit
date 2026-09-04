import { useEffect, useState } from 'react'
import { TopBar } from '../App'
import { api } from '../api'
import { Badge, Card, Empty } from '../components/Ui'
import { money, titleCase } from '../format'

interface View {
  id: number; code: string; name: string; channelType: string; currency: string
  mappingProfile: string; feeRateBasisPoints: number; fixedFeeMinor: number
  feeVatBasisPoints: number; cycle: string; settleAfterDays: number
  holdbackBasisPoints: number; cutoffTime: string
  amountToleranceAbsolute: number; amountToleranceBasisPoints: number
  feeToleranceAbsolute: number; feeToleranceBasisPoints: number; matchWindowDays: number
}

const bp = (value: number) => `${(value / 100).toFixed(2)}%`

export default function Counterparties() {
  const [rows, setRows] = useState<View[]>([])
  useEffect(() => { api.get<View[]>('/api/counterparties').then(setRows) }, [])

  return (
    <>
      <TopBar title="Counterparties"
              description="The commercial contract in a form the engine can compute with." />
      <div className="content">
        <Card title="Terms">
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Counterparty</th><th>Channel</th><th>Profile</th><th className="num">Commission</th>
                  <th className="num">Flat fee</th><th className="num">Tax on fee</th><th>Cycle</th>
                  <th className="num">Payout</th><th className="num">Holdback</th><th className="num">Cutoff</th>
                  <th className="num">Amount tolerance</th><th className="num">Fee tolerance</th>
                  <th className="num">Match window</th>
                </tr>
              </thead>
              <tbody>
                {rows.length === 0 && <tr><td colSpan={13}><Empty>No counterparties</Empty></td></tr>}
                {rows.map((row) => (
                  <tr key={row.id}>
                    <td><b>{row.name}</b> <span className="dim mono">{row.code}</span></td>
                    <td><Badge kind="plain">{titleCase(row.channelType)}</Badge></td>
                    <td className="mono dim">{row.mappingProfile}</td>
                    <td className="num">{bp(row.feeRateBasisPoints)}</td>
                    <td className="num">{money(row.fixedFeeMinor, row.currency)}</td>
                    <td className="num">{bp(row.feeVatBasisPoints)}</td>
                    <td>{titleCase(row.cycle)}</td>
                    <td className="num">T+{row.settleAfterDays}</td>
                    <td className="num">{row.holdbackBasisPoints === 0 ? '-' : bp(row.holdbackBasisPoints)}</td>
                    <td className="num">{row.cutoffTime.slice(0, 5)}</td>
                    <td className="num">
                      {money(row.amountToleranceAbsolute, row.currency)} / {bp(row.amountToleranceBasisPoints)}
                    </td>
                    <td className="num">
                      {money(row.feeToleranceAbsolute, row.currency)} / {bp(row.feeToleranceBasisPoints)}
                    </td>
                    <td className="num">±{row.matchWindowDays}d</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>

        <div className="grid cols-2" style={{ marginTop: 14 }}>
          <Card title="Why the cutoff is a field">
            <div className="card-body">
              <p style={{ fontSize: 12.5, lineHeight: 1.7 }}>
                Our systems roll the day at local midnight. A gateway rolls it at its own
                cutoff, often half an hour earlier. Everything that happens in between is on
                our books for today and on theirs for tomorrow. Without this field the engine
                reports the same block of transactions missing every night, always at the
                same hour, always for amounts that add up.
              </p>
            </div>
          </Card>
          <Card title="Why tolerance has two numbers">
            <div className="card-body">
              <p style={{ fontSize: 12.5, lineHeight: 1.7 }}>
                An absolute floor absorbs the single unit of rounding every percentage fee
                produces. A relative band absorbs the drift that scales with the amount. One
                threshold cannot cover a 33 won transaction and a million won transaction
                without either flooding the queue or hiding real losses.
              </p>
            </div>
          </Card>
        </div>
      </div>
    </>
  )
}
