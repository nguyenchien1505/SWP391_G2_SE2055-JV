import { defineConfig } from 'vite'
import path from 'path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    // Backend redirect về http://localhost:3000 sau khi đăng nhập Google (application.yaml).
    port: 3000,
    // Gọi /api cùng origin để cookie JSESSIONID hoạt động mà không cần CORS.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
  assetsInclude: ['**/*.svg', '**/*.csv'],
})
