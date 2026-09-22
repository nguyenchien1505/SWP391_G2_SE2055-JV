import { createBrowserRouter, Navigate } from "react-router";
import { RequireAuth } from "./context/RequireAuth";
import { AppLayout } from "./layouts/AppLayout";

import { LoginPage } from "./pages/auth/LoginPage";
import { RegisterTenantPage } from "./pages/auth/RegisterTenantPage";
import { ChangePasswordPage } from "./pages/auth/ChangePasswordPage";
import { UnauthorizedPage } from "./pages/auth/UnauthorizedPage";

import { TenantsPage } from "./pages/Admin_platform/TenantsPage";
import { TenantDetailPage } from "./pages/Admin_platform/TenantDetailPage";
import { PricingPage } from "./pages/Admin_platform/PricingPage";
import { SystemConfigPage } from "./pages/Admin_platform/SystemConfigPage";

export const router = createBrowserRouter([
  { path: "/login", Component: LoginPage },
  { path: "/register", Component: RegisterTenantPage },
  { path: "/change-password", Component: ChangePasswordPage },
  { path: "/unauthorized", Component: UnauthorizedPage },
  {
    element: <RequireAuth roles={["PLATFORM_ADMIN"]} />,
    children: [
      {
        Component: AppLayout,
        children: [
          { path: "/", element: <Navigate to="/tenants" replace /> },
          { path: "/tenants", Component: TenantsPage },
          { path: "/tenants/:id", Component: TenantDetailPage },
          { path: "/pricing", Component: PricingPage },
          { path: "/system-config", Component: SystemConfigPage },
        ],
      },
    ],
  },
  { path: "*", element: <Navigate to="/tenants" replace /> },
]);
