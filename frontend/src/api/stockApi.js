// 개발 서버(vite dev)는 /api 요청을 vite.config.js의 proxy로 localhost:8080에 전달하고,
// 빌드된 결과물은 백엔드(Spring Boot)가 같은 origin에서 정적 파일로 서빙하므로 항상 상대 경로를 사용한다.

export async function fetchAllStocks() {
  const response = await fetch('/api/stocks/all')
  if (!response.ok) {
    throw new Error(`전체 종목 목록을 불러오지 못했습니다. (status: ${response.status})`)
  }
  return response.json()
}
