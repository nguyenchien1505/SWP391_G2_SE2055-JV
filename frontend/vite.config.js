import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Gọi thẳng backend ở http://localhost:8080/api thay vì proxy: CORS phía backend đã bật
// allowCredentials nên cookie phiên đi được xuyên origin. Đổi đích bằng biến môi trường
// VITE_API_BASE_URL khi backend chạy ở cổng khác.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
  },
});
