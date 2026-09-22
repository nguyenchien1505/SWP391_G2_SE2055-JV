import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
<<<<<<< HEAD

// Gọi thẳng backend ở http://localhost:8080/api thay vì proxy: CORS phía backend đã bật
// allowCredentials nên cookie phiên đi được xuyên origin. Đổi đích bằng biến môi trường
// VITE_API_BASE_URL khi backend chạy ở cổng khác.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
=======
import tailwindcss from '@tailwindcss/vite';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Note: Google OAuth2 redirects to /auth/callback/google on backend, but the proxy might be needed if it passes through frontend.
      // Usually, Spring Security redirects to backend directly, but let's map /auth just in case, wait, backend context path is /api.
      // So /api/auth/login is the URL. The proxy for /api handles everything.
    },
>>>>>>> Nguyen
  },
});
