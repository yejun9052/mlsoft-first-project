import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import './index.css';
import App from './App.jsx';

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <App />
      {/* 전역 toast — 다크 테마에 맞춘 기본 스타일 */}
      <Toaster
        position="top-center"
        toastOptions={{
          style: {
            background: '#141d2b',
            color: '#e9f0fa',
            border: '1px solid rgba(255,255,255,0.08)',
          },
        }}
      />
    </BrowserRouter>
  </StrictMode>,
);
