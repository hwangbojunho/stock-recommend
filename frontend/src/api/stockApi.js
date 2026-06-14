// 개발 서버(vite dev)에서는 별도 포트(8080)의 백엔드를 호출하고,
// 빌드된 결과물은 백엔드(Spring Boot)가 같은 origin에서 정적 파일로 서빙하므로 상대 경로를 사용한다.
const BASE_URL = import.meta.env.DEV ? `http://${window.location.hostname}:8080` : ''

export async function fetchAllStocks() {
  const response = await fetch(`${BASE_URL}/api/stocks/all`)
  if (!response.ok) {
    throw new Error(`전체 종목 목록을 불러오지 못했습니다. (status: ${response.status})`)
  }
  return response.json()
}
