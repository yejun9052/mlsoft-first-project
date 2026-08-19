import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from 'react-hot-toast';
import './index.css';
import routes from './App.jsx';
import { queryClient } from './queryClient.js';

const router = createBrowserRouter(routes);

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      {/* RouterProvider는 children을 받지 않는다. Toaster는 라우터를 쓰지 않으므로 같은 전역 Provider 아래 형제로 둔다 */}
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
    </QueryClientProvider>
  </StrictMode>,
);
