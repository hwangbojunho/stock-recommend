export function formatNumber(value, fractionDigits = 0) {
  if (value === null || value === undefined) return '-'
  return Number(value).toLocaleString('ko-KR', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })
}

export function formatWon(value) {
  if (value === null || value === undefined) return '-'
  return `${formatNumber(value)}원`
}

export function formatPercent(value) {
  if (value === null || value === undefined) return '-'
  const sign = value > 0 ? '+' : ''
  return `${sign}${formatNumber(value, 2)}%`
}

export function formatMultiple(value) {
  if (value === null || value === undefined) return '-'
  return `${formatNumber(value, 2)}배`
}

/**
 * marketCap, revenue 등 억원 단위 값을 조/억원 단위 문자열로 변환한다.
 */
export function formatEokToJo(value) {
  if (value === null || value === undefined) return '-'
  const jo = Math.floor(value / 10000)
  const eok = Math.round(value % 10000)
  if (jo > 0) {
    return eok > 0 ? `${formatNumber(jo)}조 ${formatNumber(eok)}억원` : `${formatNumber(jo)}조원`
  }
  return `${formatNumber(eok)}억원`
}
