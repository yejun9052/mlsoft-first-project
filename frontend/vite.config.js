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
    },
  },
});
