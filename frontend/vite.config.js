import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// 개발 서버 설정
// - /api, /oauth2 요청은 백엔드(Spring Boot, 8080)로 프록시
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // OAuth 콜백 방어 — '/login' 전체를 프록시하면 SPA의 /login 라우트가 깨지므로
      // 백엔드 콜백 경로(/login/oauth2/**)만 좁게 전달
      '/login/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
