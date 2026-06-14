import { useState } from 'react'
import { formatEokToJo, formatMultiple, formatNumber, formatPercent, formatWon } from '../utils/format'

const SCORE_LABELS = [
  { min: 80, label: '적극 매수', className: 'score-buy' },
  { min: 60, label: '매수 고려', className: 'score-lean-buy' },
  { min: 40, label: '중립', className: 'score-neutral' },
  { min: 20, label: '비중 축소 고려', className: 'score-lean-sell' },
  { min: 0, label: '비추천', className: 'score-sell' },
]

const SUB_SCORE_ITEMS = [
  { key: 'valuationScore', label: '밸류에이션', weight: 25, describe: describeValuation },
  { key: 'qualityScore', label: '수익성/퀄리티', weight: 20, describe: describeQuality },
  { key: 'growthScore', label: '성장성', weight: 18, describe: describeGrowth },
  { key: 'momentumScore', label: '수급/모멘텀', weight: 12, describe: describeMomentum },
  { key: 'financialHealthScore', label: '재무건전성', weight: 10, describe: describeFinancialHealth },
  { key: 'dividendScore', label: '배당', weight: 3, describe: describeDividend },
]

function scoreLabel(total) {
  return SCORE_LABELS.find((item) => total >= item.min) ?? SCORE_LABELS[SCORE_LABELS.length - 1]
}

const NO_DATA = '데이터 없음 → 가중치 제외(나머지 항목으로 비례 재분배)'
// 재무건전성/배당은 세부 항목이 1개뿐이라 "재분배"할 나머지 항목이 없음 → 데이터 없으면 카테고리 점수가 중립(5점)으로 처리됨
const NEUTRAL_NO_DATA = '데이터 없음 → 중립(5점) 처리'
const UNIVERSE = '코스피 시가총액 상위 50종목'

function describeValuation(m) {
  const lines = []
  lines.push(
    m.per == null
      ? `PER ${NO_DATA}`
      : m.per <= 0
        ? `PER ${formatMultiple(m.per)} — 적자 상태로 0점 처리`
        : `PER ${formatMultiple(m.per)} — ${UNIVERSE} 내 백분위로 환산, 낮을수록(저평가) 고득점`
  )
  lines.push(
    m.pbr == null
      ? `PBR ${NO_DATA}`
      : m.pbr <= 0
        ? `PBR ${formatMultiple(m.pbr)} — 적자 상태로 0점 처리`
        : `PBR ${formatMultiple(m.pbr)} — ${UNIVERSE} 내 백분위로 환산, 낮을수록 고득점. ` +
          (m.roe != null
            ? `단 ROE ${formatPercent(m.roe)}이 8% 미만이면 "저PBR=저평가" 가점을 비례 축소 (밸류 트랩 방지)`
            : '단 ROE 데이터가 없어 밸류 트랩 여부를 판단할 수 없으므로 저평가 가점을 그대로 적용')
  )
  lines.push(
    m.financialSector
      ? `PSR — 금융업(은행/지주/증권/보험/카드)은 매출 개념이 일반 제조업과 달라 평가 제외, 가중치를 나머지 항목으로 재분배`
      : m.psr == null
        ? `PSR ${NO_DATA}`
        : m.psr <= 0
          ? `PSR ${formatMultiple(m.psr)} — 적자 상태로 0점 처리`
          : `PSR ${formatMultiple(m.psr)} — ${UNIVERSE}(금융업 제외) 내 백분위로 환산, 낮을수록 고득점`
  )
  if (m.per != null && m.forwardPer != null && m.per > 0 && m.forwardPer > 0) {
    const trend = m.per > m.forwardPer ? '실적 성장 기대 → 가산점' : m.per < m.forwardPer ? '실적 둔화 우려 → 감점' : '변동 없음'
    lines.push(`현재 PER ${formatMultiple(m.per)} vs 추정 PER ${formatMultiple(m.forwardPer)}(${m.forwardPeriod ?? ''}): ${trend} (5점 기준 ±5점)`)
  } else {
    lines.push(`추정 PER 비교 ${NO_DATA}`)
  }
  lines.push('※ "역사적 PER/PBR 밴드 내 위치"는 시계열 데이터 축적 후 추가 예정')
  return lines
}

function describeQuality(m) {
  return [
    m.roe == null
      ? `ROE ${NO_DATA}`
      : `ROE ${formatPercent(m.roe)} — ${UNIVERSE} 내 백분위로 환산, 높을수록 고득점 (가중치 9/20으로 가장 큼)`,
    m.operatingMargin == null
      ? `영업이익률 ${NO_DATA}`
      : `영업이익률 ${formatPercent(m.operatingMargin)} — ${UNIVERSE} 내 백분위로 환산, 높을수록 고득점`,
    m.netMargin == null
      ? `순이익률 ${NO_DATA}`
      : `순이익률 ${formatPercent(m.netMargin)} — ${UNIVERSE} 내 백분위로 환산, 높을수록 고득점`,
  ]
}

function describeGrowth(m) {
  const lines = [
    m.operatingIncomeGrowth3y == null
      ? `영업이익 성장률(3개년 CAGR) ${NO_DATA}`
      : `영업이익 성장률(3개년 CAGR) ${formatPercent(m.operatingIncomeGrowth3y)} — ${UNIVERSE} 내 백분위로 환산, 높을수록 고득점`,
    m.revenueGrowth3y == null
      ? `매출 성장률(3개년 CAGR) ${NO_DATA}`
      : `매출 성장률(3개년 CAGR) ${formatPercent(m.revenueGrowth3y)} — ${UNIVERSE} 내 백분위로 환산, 높을수록 고득점`,
    m.netIncomeGrowth3y == null
      ? `순이익 성장률(3개년 CAGR) ${NO_DATA}`
      : `순이익 성장률(3개년 CAGR) ${formatPercent(m.netIncomeGrowth3y)} — ${UNIVERSE} 내 백분위로 환산, 높을수록 고득점`,
  ]
  const growths = [m.revenueGrowth3y, m.operatingIncomeGrowth3y, m.netIncomeGrowth3y].filter((v) => v != null)
  if (growths.length === 0) {
    lines.push(`성장 일관성 ${NO_DATA}`)
  } else {
    const positive = growths.filter((v) => v > 0).length
    lines.push(`성장 일관성: 매출·영업이익·순이익 성장률 중 ${positive}/${growths.length}개가 양(+) → ${formatNumber((positive / growths.length) * 10, 1)}점`)
  }
  lines.push('※ "추정치(컨센서스 EPS 등) 변화율"은 시계열 데이터 축적 후 추가 예정')
  return lines
}

function describeMomentum(m) {
  const lines = []
  lines.push(
    m.institutionalNetBuyRatio == null
      ? `외국인+기관 수급(5일) ${NO_DATA}`
      : `최근 5거래일 외국인+기관 순매수/거래량 비율 ${formatPercent(m.institutionalNetBuyRatio)} — -3%~+3% 구간에서 높을수록 고득점`
  )
  lines.push(
    m.institutionalNetBuyRatio1m == null
      ? `외국인+기관 수급(1개월) ${NO_DATA}`
      : `최근 22거래일(약 1개월) 외국인+기관 순매수/거래량 비율 ${formatPercent(m.institutionalNetBuyRatio1m)} — -3%~+3% 구간에서 높을수록 고득점. 단기 노이즈가 적어 5일 수급보다 더 큰 비중으로 반영`
  )
  if (m.price == null || m.week52High == null || m.week52Low == null || m.week52High <= m.week52Low) {
    lines.push(`52주 가격 위치 ${NO_DATA}`)
  } else {
    const position = ((m.price - m.week52Low) / (m.week52High - m.week52Low)) * 100
    lines.push(
      `52주 최저 ${formatWon(m.week52Low)} ~ 최고 ${formatWon(m.week52High)} 구간 중 현재가 ${formatWon(m.price)}는 ${formatNumber(position, 1)}% 위치 — ` +
        '50~85% 구간이 만점(추세 강함), 95% 이상은 과열 감점, 20% 이하 바닥권은 중립(추세 미확인)'
    )
  }
  lines.push('※ "외인소진율 변화 추이"는 시계열 데이터 축적 후 추가 예정')
  return lines
}

function describeFinancialHealth(m) {
  return [
    m.financialSector
      ? `부채비율 — 금융업(은행/지주/증권/보험/카드)은 예금 등이 부채로 잡혀 부채비율이 구조적으로 높으므로 평가 제외, 중립(5점) 처리`
      : m.debtRatio == null
        ? `부채비율 ${NEUTRAL_NO_DATA}`
        : `부채비율 ${formatPercent(m.debtRatio)} — ${UNIVERSE}(금융업 제외) 내 백분위로 환산, 낮을수록 고득점`,
    '※ "이자보상배율"은 데이터 미제공으로 제외',
  ]
}

function describeDividend(m) {
  return [
    m.dividendYield == null
      ? `배당수익률 ${NEUTRAL_NO_DATA}`
      : `배당수익률 ${formatPercent(m.dividendYield)} — ${UNIVERSE} 내 백분위로 환산, 높을수록 고득점`,
  ]
}

function StockAnalysisDetail({ analysis }) {
  const { metrics, score } = analysis
  const { label, className } = scoreLabel(score.total)
  const [showDetails, setShowDetails] = useState(false)

  return (
    <div className="analysis-result">
      <div className="analysis-header">
        <div className="name-cell">
          <span className="name">{metrics.name}</span>
          <span className="code">{metrics.code} · {metrics.market}</span>
        </div>
        <div className={`score-badge ${className}`}>
          <span className="score-total">{score.total}</span>
          <span className="score-max">/100</span>
          <span className="score-label">{label}</span>
        </div>
      </div>

      <div className="score-breakdown">
        <div className="score-breakdown-header">
          <p className="score-breakdown-desc">
            6개 카테고리를 각각 0~10점으로 환산해 가중합(25/20/18/12/10/3)한 뒤 0~100점 정수로 반올림합니다.
            밸류에이션·수익성·성장성·재무건전성·배당은 절대 수치가 아닌 {UNIVERSE} 내 백분위로 평가하며,
            데이터가 없는 항목은 가중치를 나머지 항목에 비례 재분배합니다. (추정치 변화 등 시계열 기반 항목은 추후 추가 예정)
          </p>
          <button className="details-toggle" onClick={() => setShowDetails((v) => !v)}>
            {showDetails ? '산정 기준 숨기기 ▲' : '산정 기준 보기 ▼'}
          </button>
        </div>
        {SUB_SCORE_ITEMS.map((item) => (
          <div className="score-item" key={item.key}>
            <div className="score-bar-row">
              <span className="score-bar-label">{item.label} <span className="score-weight">({item.weight}%)</span></span>
              <div className="score-bar-track">
                <div className="score-bar-fill" style={{ width: `${(Number(score[item.key]) / 10) * 100}%` }} />
              </div>
              <span className="score-bar-value">{formatNumber(Number(score[item.key]) * 10, 1)}</span>
            </div>
            {showDetails && (
              <ul className="score-detail-list">
                {item.describe(metrics).map((line, i) => (
                  <li key={i}>{line}</li>
                ))}
              </ul>
            )}
          </div>
        ))}
      </div>

      <div className="metrics-grid">
        <MetricItem label="현재가" value={formatWon(metrics.price)} />
        <MetricItem label="등락률" value={formatPercent(metrics.changeRate)} />
        <MetricItem label="시가총액" value={formatEokToJo(metrics.marketCap)} />
        <MetricItem label="PER" value={formatMultiple(metrics.per)} />
        <MetricItem label="PBR" value={formatMultiple(metrics.pbr)} />
        <MetricItem label="PSR" value={formatMultiple(metrics.psr)} />
        <MetricItem label="추정 PER" value={formatMultiple(metrics.forwardPer)} sub={metrics.forwardPeriod} />
        <MetricItem label="추정 EPS" value={formatWon(metrics.forwardEps)} sub={metrics.forwardPeriod} />
        <MetricItem label="추정 순이익" value={formatEokToJo(metrics.forwardNetIncome)} sub={metrics.forwardPeriod} />
        <MetricItem label="ROE" value={formatPercent(metrics.roe)} />
        <MetricItem label="영업이익률" value={formatPercent(metrics.operatingMargin)} />
        <MetricItem label="순이익률" value={formatPercent(metrics.netMargin)} />
        <MetricItem label="매출 성장률(3y CAGR)" value={formatPercent(metrics.revenueGrowth3y)} />
        <MetricItem label="영업이익 성장률(3y CAGR)" value={formatPercent(metrics.operatingIncomeGrowth3y)} />
        <MetricItem label="순이익 성장률(3y CAGR)" value={formatPercent(metrics.netIncomeGrowth3y)} />
        <MetricItem label="부채비율" value={formatPercent(metrics.debtRatio)} />
        <MetricItem label="배당수익률" value={formatPercent(metrics.dividendYield)} />
        <MetricItem label="외국인 소진율" value={formatPercent(metrics.foreignOwnershipRatio)} />
        <MetricItem label="외국인+기관 순매수(5일)" value={formatPercent(metrics.institutionalNetBuyRatio)} />
        <MetricItem label="외국인+기관 순매수(1개월)" value={formatPercent(metrics.institutionalNetBuyRatio1m)} />
        <MetricItem label="52주 최고" value={formatWon(metrics.week52High)} />
        <MetricItem label="52주 최저" value={formatWon(metrics.week52Low)} />
      </div>
    </div>
  )
}

function MetricItem({ label, value, sub }) {
  return (
    <div className="metric-item">
      <span className="metric-label">{label}</span>
      <span className="metric-value">
        {value}
        {sub && <span className="period"> ({sub})</span>}
      </span>
    </div>
  )
}

export default StockAnalysisDetail
