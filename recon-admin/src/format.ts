/**
 * Amounts arrive as whole minor units and are formatted here, never parsed back into a
 * number for arithmetic. The browser's number type cannot hold a large won figure exactly,
 * and a dashboard that rounds is a dashboard that disagrees with the statement.
 */
const SCALE: Record<string, number> = { KRW: 0, JPY: 0, USD: 2, EUR: 2, GBP: 2 }

export function money(minorUnits: number, currency = 'KRW', withSymbol = false): string {
  const scale = SCALE[currency] ?? 2
  const negative = minorUnits < 0
  const absolute = Math.abs(minorUnits)
  const whole = Math.floor(absolute / 10 ** scale)
  const fraction = absolute % 10 ** scale
  const text =
    scale === 0
      ? whole.toLocaleString()
      : `${whole.toLocaleString()}.${String(fraction).padStart(scale, '0')}`
  const symbol = withSymbol ? (currency === 'KRW' ? '₩' : `${currency} `) : ''
  return `${negative ? '-' : ''}${symbol}${text}`
}

export function percent(value: number, digits = 1): string {
  return `${(value * 100).toFixed(digits)}%`
}

export function count(value: number): string {
  return value.toLocaleString()
}

export function shortDate(value: string): string {
  return value.slice(5)
}

export function dateTime(value?: string | null): string {
  if (!value) return '-'
  const date = new Date(value)
  return date.toLocaleString('en-GB', { hour12: false }).replace(',', '')
}

export function titleCase(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export function severityClass(severity: string): string {
  return severity.toLowerCase()
}

export function statusClass(status: string): string {
  switch (status) {
    case 'RESOLVED':
    case 'COMPLETED':
    case 'SUCCEEDED':
    case 'CONFIRMED':
    case 'PAID':
    case 'LOADED':
      return 'ok'
    case 'OPEN':
    case 'PENDING':
    case 'DRAFT':
      return 'info'
    case 'FAILED':
    case 'ESCALATED':
    case 'REJECTED':
      return 'critical'
    case 'COMPENSATED':
    case 'INVESTIGATING':
    case 'RUNNING':
      return 'medium'
    default:
      return 'plain'
  }
}
