import React from "react";
import ReactDOM from "react-dom/client";
import "./styles.css";

function App() {
  return (
    <main>
      <section>
        <p className="eyebrow">Operator Console</p>
        <h1>Multi-Asset OMS Admin</h1>
        <p>
          주문 상태, 리스크 거절, 체결 이벤트, 리플레이 결과를 확인하는 운영자 화면입니다.
        </p>
      </section>
    </main>
  );
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
