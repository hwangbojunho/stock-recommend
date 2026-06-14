# stock-dashboard

코스피 상장 전체 종목의 매수 매력도를 0~100점으로 점수화해 랭킹으로 보여주는 서비스.

## 구조

- `backend/` — Spring Boot (Java). 네이버 금융 등에서 시세/재무 데이터를 가져와 가공하고, 점수 산정 로직을 담당. H2 파일 DB에 전체 종목 점수를 영속화.
- `frontend/` — React + Vite. 코스피 전체 종목 점수 랭킹 화면과 종목별 점수 상세 화면 제공.

## 배포

- 루트 `Dockerfile`: 프론트엔드(Vite build) → 백엔드 `src/main/resources/static`에 포함 → Spring Boot가 정적 파일 + `/api/**`를 같은 origin/포트로 서빙하는 단일 이미지 빌드
- `server.port=${PORT:8080}` — Render 등에서 `PORT` 환경변수로 오버라이드 가능
- 프론트 `BASE_URL`(`stockApi.js`)은 `import.meta.env.DEV`일 때만 `http://<host>:8080`을 사용하고, 프로덕션 빌드에서는 같은 origin의 상대경로(`/api/...`)를 사용
- **DB**: 스프링 프로필로 로컬(H2)/운영(PostgreSQL)을 분리
  - `application.properties`: `spring.profiles.active=${SPRING_PROFILES_ACTIVE:local}` (기본값은 `local`)
  - `application-local.properties`: H2 파일 DB (`./data/stockdb`)
  - `application-prod.properties`: PostgreSQL — `spring.datasource.url=${DB_URL}`, `driver-class-name=org.postgresql.Driver`, `username=${DB_USERNAME}`, `password=${DB_PASSWORD}`
  - Render 배포 시 환경변수: `SPRING_PROFILES_ACTIVE=prod`, `DB_URL=jdbc:postgresql://<host>:5432/<db>`, `DB_USERNAME`, `DB_PASSWORD` (Render Postgres 대시보드의 접속 정보 사용)
  - PostgreSQL(Render 무료 플랜)은 영구 저장소이므로, 재배포해도 데이터가 유지되고 24시간 전체 갱신만 다시 일어남 (H2 ephemeral storage 문제 해소)

## 주요 화면 (frontend)

- **전체 종목** (`AllStocksView.jsx`, 단일 화면)
  - 코스피 상장 전체 종목(ETF/ETN 제외, 약 950개)의 시세·밸류에이션 지표(추정PER/EPS/순이익 포함) 및 종합/카테고리별 점수를 한 화면에 표시
  - `/api/stocks/all` 호출, 종목명/코드 검색, 컬럼 헤더 클릭으로 정렬 (기본 정렬: 총점 내림차순)
  - DB에 영속화된 결과를 읽으므로 서버 재시작 후에도 즉시 응답되며, 화면에 "마지막 갱신" 시각 표시
  - 행을 클릭하면 추가 API 호출 없이(이미 응답에 포함된 `{metrics, score}`로) `StockAnalysisDetail.jsx`를 통해 점수 breakdown/지표 상세 화면으로 전환, "목록으로"로 복귀
- **종목 분석 상세** (`StockAnalysisDetail.jsx`)
  - `analysis`(`{metrics, score}`) prop을 받아 점수 총점과 6개 카테고리별 막대그래프, "산정 기준 보기" 토글로 세부 산출 근거 표시
  - `AllStocksView`의 행 클릭으로만 진입 (별도 입력 화면 없음)

## API (backend)

베이스 URL: `http://localhost:8080`

- `GET /api/stocks/all` — `KospiStockList` (= `{ stocks: StockAnalysis[], updatedAt }`), 코스피 전체 종목(ETF/ETN 제외) 목록 (각 종목의 `metrics` + `score` 포함), DB 기반으로 즉시 응답
- `GET /api/stocks/{code}/analysis` — `StockAnalysis` (= `{ metrics, score }`). DB에 있으면 즉시 반환, 없으면(신규 상장 등) 라이브 계산 fallback

## 코스피 전체 종목 DB 영속화 (`KospiStockListService`)

- 코스피 상장 종목은 약 950개이며, 종목 1개당 네이버 API를 3번(basic/integration/finance) 호출하므로 전체 조회 시 약 2,800회의 외부 호출이 발생함
- 요청-응답 경로에서 이를 수행하면 느리므로, 백그라운드 스케줄러(`@Scheduled`, 24시간 주기)가 전용 스레드풀(20개 동시 호출)로 전체 종목을 수집하면서, 종목별로 점수(`StockScoreCalculator.calculate`)까지 계산해 완료되는 즉시 H2 DB(`stock_analysis` 테이블, `StockAnalysisRepository`)에 upsert함
- DB 영속화 덕분에 서버를 재시작해도 직전 갱신 결과가 즉시 조회됨 — 갱신이 끝나기 전(또는 서버 첫 기동 직후 DB가 비어있을 때)에는 그 시점까지 저장된 종목만 반환됨(`updatedAt`은 저장된 row들의 `updatedAt` 중 최댓값)
- 서버 기동 시 1회 즉시 갱신 시작. 실측 약 22분(948개 종목) 소요. 50개 단위로 진행 로그 출력
- 종목 코드 목록은 네이버 랭킹 API(`marketValue/KOSPI`, 페이지당 최대 100개)를 페이지네이션하여 수집, `stockEndType=stock`만 필터링(ETF/ETN 제외)
- H2 DB 파일은 `backend/data/stockdb.mv.db`(`.gitignore`로 제외), `jdbc:h2:file:./data/stockdb;AUTO_SERVER=TRUE`, `spring.jpa.hibernate.ddl-auto=update`
- `StockUniverseService`의 유니버스 캐시(`getUniverseCodes`/`getUniverseMetrics`)는 `@Cacheable(sync = true)`로 설정. 점수 계산이 내부적으로 유니버스 백분위를 조회하므로, 20개 스레드가 동시에 점수를 계산할 때 캐시 미스 시 한 스레드만 실제로 계산하고 나머지는 결과를 기다리도록 해 "캐시 stampede"(중복 계산)를 방지
- `RestClient`(`RestClientConfig`)는 Apache HttpClient5 + 커넥션 풀(호스트당 최대 30개)을 사용. 기본 JDK HttpClient는 호스트당 HTTP/2 커넥션 1개로 멀티플렉싱하기 때문에, 20개 스레드가 동시에 같은 호스트로 요청하면 서버의 동시 스트림 제한을 초과해 "too many concurrent streams" 오류가 발생함

## 점수 알고리즘 (`StockScoreCalculator`, v2)

**설계 원칙**
- 절대 수치가 아니라 **코스피 시가총액 상위 50종목(유니버스)** 내 백분위로 평가 (`StockUniverseService`)
- 각 세부 항목을 0~10점(높을수록 긍정적)으로 정규화 → 카테고리 내 가중평균 → 카테고리 점수
- 데이터 없는 항목은 가중치 제외 후 나머지 항목으로 비례 재분배 (전부 없으면 중립값 5점)
- 6개 카테고리를 아래 가중치로 가중합(총합 88) → 0~100점 정수로 반올림(`HALF_UP`)

| 카테고리 | 가중치 | 세부 항목 (항목 가중치) |
|---|---|---|
| 밸류에이션 | 25 | PER(7), PBR(5, ROE<8%면 저PBR 가점 축소=밸류트랩 방지), PSR(3), 현재PER vs 추정PER(6) |
| 수익성/퀄리티 | 20 | ROE(9), 영업이익률(6), 순이익률(5) |
| 성장성 | 18 | 영업이익 성장률 3y CAGR(6), 매출 성장률 3y CAGR(5), 순이익 성장률 3y CAGR(4), 성장 일관성(3, 셋 중 양(+)인 비율) |
| 수급/모멘텀 | 12 | 외국인+기관 순매수/거래량 비율(5일, 2 / 22거래일·약1개월, 3, 둘 다 -3%~+3% 스케일), 52주 가격 위치(4) |
| 재무건전성 | 10 | 부채비율(7, 낮을수록 고득점, 백분위) |
| 배당 | 3 | 배당수익률(3, 높을수록 고득점, 백분위) |

**세부 규칙**
- PER/PBR/PSR이 0 이하(적자)면 해당 항목 0점
- PBR: 백분위 점수가 5점 초과(저평가)일 때만 ROE/8% 비율(0~1)로 가점을 축소 → ROE 낮으면 "싼 이유가 있는 저평가" 페널티. ROE 데이터가 없으면 트랩 여부를 판단할 수 없으므로 가점을 그대로 유지(축소하지 않음)
- 추정PER 비교: `(현재PER - 추정PER) / 현재PER`을 -1~1로 클램프 후 `5 + ratio*5` (실적 성장 기대 시 가점, 둔화 우려 시 감점)
- 52주 가격 위치: 20% 이하 → 중립(5점, 추세 미확인) / 20~50% → 5~10점 / 50~85% → 10점(만점) / 85~95% → 10~5점(과열 감점 시작) / 95~100% → 5~2점
- 수급: 5거래일 수급은 단기 노이즈에 취약하므로, 노이즈가 적은 22거래일(약 1개월) 수급을 더 큰 비중(3 vs 2)으로 함께 반영

**업종(섹터) 보정**
- 네이버 금융 `industryCode`로 은행/금융지주/증권/보험/카드(여신전문)를 `financialSector=true`로 분류 (`NaverStockDataProvider.FINANCIAL_INDUSTRY_CODES`: 301/315/321/330/337)
- **PSR**: 금융업은 매출 개념이 일반 제조업과 달라 평가에서 제외(가중치 6→나머지 밸류에이션 항목으로 재분배)
- **부채비율(재무건전성)**: 금융업은 예금 등이 부채로 잡혀 부채비율이 구조적으로 매우 높으므로 평가하지 않고 중립(5점) 처리
- 위 두 항목의 백분위 비교 유니버스에서도 금융업 종목을 제외하여, 금융업의 이례적인 부채비율·PSR 값이 일반 제조업 종목의 백분위를 왜곡하지 않도록 함

**v1에서 제외 (시계열 데이터 축적 후 추가 예정)**
- 역사적 PER/PBR 밴드 내 위치
- 컨센서스 EPS 등 추정치 변화율
- 외인소진율 변화 추이
- 이자보상배율 (데이터 미제공)

## 점수 → 등급 (frontend `StockAnalysisDetail.jsx`)

| 총점 | 등급 |
|---|---|
| 80~100 | 적극 매수 |
| 60~79 | 매수 고려 |
| 40~59 | 중립 |
| 20~39 | 비중 축소 고려 |
| 0~19 | 비추천 |

## 주요 파일

- `backend/src/main/java/com/stockdashboard/backend/service/StockScoreCalculator.java` — 점수 산정 로직
- `backend/src/main/java/com/stockdashboard/backend/service/StockUniverseService.java` — 유니버스 백분위 계산
- `backend/src/main/java/com/stockdashboard/backend/service/KospiStockListService.java` — 전체 종목 갱신/DB 영속화
- `backend/src/main/java/com/stockdashboard/backend/domain/{StockMetrics,StockScore,StockAnalysis,StockAnalysisEntity}.java` — 데이터 모델/JPA 엔티티
- `backend/src/main/java/com/stockdashboard/backend/repository/StockAnalysisRepository.java` — JPA 리포지토리
- `backend/src/main/java/com/stockdashboard/backend/controller/StockController.java` — REST API
- `frontend/src/components/AllStocksView.jsx` — 전체 종목 랭킹 화면 (메인)
- `frontend/src/components/StockAnalysisDetail.jsx` — 점수 상세 화면 및 산정 기준 설명 텍스트
