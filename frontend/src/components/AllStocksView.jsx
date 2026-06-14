import { useEffect, useMemo, useState } from 'react'
import { fetchAllStocks } from '../api/stockApi'
import { formatEokToJo, formatMultiple, formatNumber, formatPercent, formatWon } from '../utils/format'
import StockAnalysisDetail from './StockAnalysisDetail'

const COLUMNS = [
  { key: 'name', label: '종목명', align: 'left' },
  { key: 'total', label: '총점' },
  { key: 'valuationScore', label: '밸류에이션' },
  { key: 'qualityScore', label: '퀄리티' },
  { key: 'growthScore', label: '성장성' },
  { key: 'momentumScore', label: '모멘텀' },
  { key: 'financialHealthScore', label: '재무건전성' },
  { key: 'dividendScore', label: '배당' },
  { key: 'price', label: '현재가' },
  { key: 'changeRate', label: '등락률' },
  { key: 'marketCap', label: '시가총액' },
  { key: 'per', label: 'PER' },
  { key: 'pbr', label: 'PBR' },
  { key: 'psr', label: 'PSR' },
  { key: 'roe', label: 'ROE' },
  { key: 'operatingMargin', label: '영업이익률' },
  { key: 'netMargin', label: '순이익률' },
  { key: 'debtRatio', label: '부채비율' },
  { key: 'dividendYield', label: '배당수익률' },
  { key: 'forwardPer', label: '추정PER' },
  { key: 'forwardEps', label: '추정EPS' },
  { key: 'forwardNetIncome', label: '추정순이익' },
]

const SCORE_KEYS = new Set([
  'valuationScore',
  'qualityScore',
  'growthScore',
  'momentumScore',
  'financialHealthScore',
  'dividendScore',
])

function formatValue(key, value) {
  if (key === 'total') {
    return value ?? '-'
  }
  if (SCORE_KEYS.has(key)) {
    return formatNumber(value, 1)
  }
  switch (key) {
    case 'price':
      return formatWon(value)
    case 'changeRate':
    case 'roe':
    case 'operatingMargin':
    case 'netMargin':
    case 'debtRatio':
    case 'dividendYield':
      return formatPercent(value)
    case 'marketCap':
    case 'forwardNetIncome':
      return formatEokToJo(value)
    case 'per':
    case 'pbr':
    case 'psr':
    case 'forwardPer':
      return formatMultiple(value)
    case 'forwardEps':
      return formatWon(value)
    default:
      return value ?? '-'
  }
}

function changeClass(changeRate) {
  if (changeRate === null || changeRate === undefined || changeRate === 0) return ''
  return changeRate > 0 ? 'up' : 'down'
}

function flatten(analysis) {
  const { metrics, score } = analysis
  return {
    ...metrics,
    total: score.total,
    valuationScore: score.valuationScore,
    qualityScore: score.qualityScore,
    growthScore: score.growthScore,
    momentumScore: score.momentumScore,
    financialHealthScore: score.financialHealthScore,
    dividendScore: score.dividendScore,
    _analysis: analysis,
  }
}

function AllStocksView() {
  const [stocks, setStocks] = useState([])
  const [updatedAt, setUpdatedAt] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState({ key: 'total', direction: 'desc' })
  const [selectedAnalysis, setSelectedAnalysis] = useState(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const data = await fetchAllStocks()
        if (!cancelled) {
          setStocks((data.stocks ?? []).map(flatten))
          setUpdatedAt(data.updatedAt ?? null)
          setError('')
        }
      } catch (err) {
        if (!cancelled) setError(err.message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    load()
    return () => {
      cancelled = true
    }
  }, [])

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    let result = stocks
    if (keyword) {
      result = result.filter(
        (s) => s.name?.toLowerCase().includes(keyword) || s.code?.includes(keyword)
      )
    }
    const { key, direction } = sort
    const sorted = [...result].sort((a, b) => {
      const av = a[key]
      const bv = b[key]
      if (av == null && bv == null) return 0
      if (av == null) return 1
      if (bv == null) return -1
      if (typeof av === 'string') {
        return direction === 'asc' ? av.localeCompare(bv) : bv.localeCompare(av)
      }
      return direction === 'asc' ? av - bv : bv - av
    })
    return sorted
  }, [stocks, query, sort])

  function handleSort(key) {
    setSort((prev) =>
      prev.key === key ? { key, direction: prev.direction === 'asc' ? 'desc' : 'asc' } : { key, direction: 'desc' }
    )
  }

  if (loading) {
    return <p className="empty">불러오는 중...</p>
  }
  if (error) {
    return <p className="error-text">{error}</p>
  }
  if (stocks.length === 0) {
    return <p className="empty">전체 종목 데이터를 아직 준비 중입니다. 잠시 후 다시 확인해주세요. (서버 기동 후 첫 갱신에 수 분 정도 걸릴 수 있습니다)</p>
  }

  if (selectedAnalysis) {
    return (
      <div>
        <button className="back-button" onClick={() => setSelectedAnalysis(null)}>
          ← 목록으로
        </button>
        <StockAnalysisDetail analysis={selectedAnalysis} />
      </div>
    )
  }

  return (
    <div>
      <div className="all-stocks-toolbar">
        <input
          type="text"
          placeholder="종목명 또는 코드 검색"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <span className="all-stocks-meta">
          총 {filtered.length}개 종목
          {updatedAt && ` · 마지막 갱신: ${new Date(updatedAt).toLocaleString('ko-KR')}`}
        </span>
      </div>
      <div className="table-scroll all-stocks-scroll">
        <table className="stock-table all-stocks-table">
          <thead>
            <tr>
              {COLUMNS.map((col) => (
                <th
                  key={col.key}
                  className={col.align === 'left' ? 'name-cell' : ''}
                  onClick={() => handleSort(col.key)}
                >
                  {col.label}
                  {sort.key === col.key && (sort.direction === 'asc' ? ' ▲' : ' ▼')}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {filtered.map((stock) => (
              <tr key={stock.code} onClick={() => setSelectedAnalysis(stock._analysis)}>
                <td className="name-cell">
                  <span className="name">{stock.name}</span>
                  <span className="code">{stock.code} · {stock.market}</span>
                </td>
                {COLUMNS.slice(1).map((col) => (
                  <td key={col.key} className={col.key === 'changeRate' ? changeClass(stock[col.key]) : ''}>
                    {formatValue(col.key, stock[col.key])}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default AllStocksView
