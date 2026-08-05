import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'react-hot-toast';
import './index.css';
import App from './App.jsx';
import { queryClient } from './queryClient.js';

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
        {/* 전역 toast — 색을 리터럴로 박지 않고 @theme 토큰(index.css)을 참조해 팔레트 변경에 자동으로 따라오게 한다 */}
        <Toaster
          position="top-center"
          toastOptions={{
            style: {
              background: 'var(--color-navy-card)',
              color: 'var(--color-ink-hi)',
              border: '1px solid rgba(255,255,255,0.09)',
              boxShadow: 'var(--shadow-card)',
            },
          }}
        />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
