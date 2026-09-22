# Hotel Workforce Management — Frontend

React 18 + Vite + Tailwind v4 + shadcn/ui.

```
npm install
npm run dev      # http://localhost:3000
```

Backend phải chạy ở `http://localhost:8080` (context-path `/api`). Vite proxy `/api` sang backend
nên trình duyệt gọi cùng origin, cookie session `JSESSIONID` hoạt động mà không cần CORS.

```
src/app/
  lib/        axios client (api.ts), type khớp DTO backend (types.ts), format helper
  context/    AuthContext — người dùng hiện tại lấy từ GET /auth/me
  layouts/    AppLayout (sidebar theo role)
  pages/      auth/ (đăng nhập, đăng ký doanh nghiệp, đổi mật khẩu) · Admin_platform/ (quản lý SaaS)
  routes.tsx  route + guard theo role
  components/ui/  shadcn/ui
```
