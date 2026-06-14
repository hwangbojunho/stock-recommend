import AllStocksView from './components/AllStocksView'
import './App.css'

function App() {
  return (
    <div className="dashboard">
      <header>
        <h1>코스피 전체 종목 점수 랭킹</h1>
        <p className="subtitle">코스피 상장 전체 종목의 매수 매력도 점수를 한눈에 비교하세요.</p>
      </header>

      <AllStocksView />
    </div>
  )
}

export default App
