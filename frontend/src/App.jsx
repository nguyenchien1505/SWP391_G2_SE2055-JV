<<<<<<< HEAD
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import LocationsPage from './pages/LocationsPage';
import AppLayout from './components/AppLayout';

/** Đã đăng nhập mới vào được; còn mật khẩu tạm thì phải đổi trước (BR-USER-07). */
import AdminLayout from './components/AdminLayout';
import TenantsPage from './pages/Admin_platform/TenantsPage';
import TenantDetailPage from './pages/Admin_platform/TenantDetailPage';
import PricingPage from './pages/Admin_platform/PricingPage';
import SystemConfigPage from './pages/Admin_platform/SystemConfigPage';
import { homePathFor } from './homePath';

function RequireAuth({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <div className="boot-screen">Đang tải…</div>;
  }
  if (!user) {
    return <Navigate to="/dang-nhap" replace />;
  }
  return user.mustChangePassword ? <Navigate to="/doi-mat-khau" replace /> : children;
}

/** Màn đổi mật khẩu chỉ cần đăng nhập, không áp thêm rào mật khẩu tạm. */
function RequireLogin({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <div className="boot-screen">Đang tải…</div>;
  }
  return user ? children : <Navigate to="/dang-nhap" replace />;
}

// Khu Quản trị nền tảng chỉ dành cho PLATFORM_ADMIN (BR-PERM-01). Đây chỉ là lớp bảo vệ giao
// diện; quyền thật do backend quyết định (vai trò khác gọi /platform/** nhận 403).
function RequireAdmin({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <div className="boot-screen">Đang tải…</div>;
  }
  if (!user) {
    return <Navigate to="/dang-nhap" replace />;
  }
  return user.role === 'PLATFORM_ADMIN' ? children : <Navigate to="/khach-san" replace />;
}

export default function App() {
  const { user, loading } = useAuth();

  return (
    <Routes>
      <Route
        path="/dang-nhap"
        element={
          loading ? (
            <div className="boot-screen">Đang tải…</div>
          ) : user ? (
            <Navigate to={homePathFor(user)} replace />
          ) : (
            <LoginPage />
          )
        }
      />
      <Route
        path="/doi-mat-khau"
        element={
          <RequireLogin>
            <ChangePasswordPage />
          </RequireLogin>
        }
      />
      <Route
        path="/khach-san"
        element={
          <RequireAuth>
            <AppLayout>
              <LocationsPage />
            </AppLayout>
          </RequireAuth>
        }
      />
      <Route
        element={
          <RequireAdmin>
            <AdminLayout />
          </RequireAdmin>
        }
      >
        <Route path="/quan-tri/tenant" element={<TenantsPage />} />
        <Route path="/quan-tri/tenant/:id" element={<TenantDetailPage />} />
        <Route path="/quan-tri/bang-gia" element={<PricingPage />} />
        <Route path="/quan-tri/cau-hinh" element={<SystemConfigPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/khach-san" replace />} />
    </Routes>
=======
/**
 * Sao Mai Hospitality Operations Pro
 * React + JavaScript Frontend with Spring Boot REST API Service Layer
 */
import React, { useState, useEffect } from 'react';
import { Sidebar } from './components/layout/Sidebar';
import { Header } from './components/layout/Header';
import { CatalogScreen } from './components/screens/CatalogScreen';
import { DashboardScreen } from './components/screens/DashboardScreen';
import { AssetManagementScreen } from './components/screens/AssetManagementScreen';
import { AreaManagementScreen } from './components/screens/AreaManagementScreen';
import { ConsumableInventoryScreen } from './components/screens/ConsumableInventoryScreen';
import { DamageReportScreen } from './components/screens/DamageReportScreen';
import { ApiConfigModal } from './components/modals/ApiConfigModal';
import { LoginScreen } from './components/screens/LoginScreen';
import { ChangePasswordScreen } from './components/screens/ChangePasswordScreen';
import { apiClient } from './services/apiClient';

export default function App() {
  const [currentScreen, setCurrentScreen] = useState('dashboard');
  const [selectedHotelId, setSelectedHotelId] = useState('sm-dn');
  const [isMobileSidebarOpen, setIsMobileSidebarOpen] = useState(false);
  const [isApiConfigOpen, setIsApiConfigOpen] = useState(false);

  // Auth State
  const [user, setUser] = useState(null);
  const [authLoading, setAuthLoading] = useState(true);

  const fetchCurrentUser = async () => {
    try {
      setAuthLoading(true);
      const userData = await apiClient.get('/auth/me');
      setUser(userData);
    } catch (err) {
      setUser(null);
      // If error (like 401), user remains null, which shows LoginScreen
    } finally {
      setAuthLoading(false);
    }
  };

  useEffect(() => {
    // Check if we just got redirected from OAuth2 with a specific path
    const path = window.location.pathname;
    if (path === '/change-password' || path === '/dashboard' || path === '/unauthorized') {
      // Clear path to root so it looks cleaner
      window.history.replaceState({}, document.title, '/');
    }
    
    fetchCurrentUser();
  }, []);

  const handleLogout = async () => {
    try {
      await apiClient.post('/auth/logout', {});
    } catch (e) {
      // ignore
    }
    setUser(null);
  };

  if (authLoading) {
    return (
      <div className="min-h-screen bg-[#F7F8FA] flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-[#00375E]"></div>
      </div>
    );
  }

  if (!user) {
    return <LoginScreen onLoginSuccess={fetchCurrentUser} />;
  }

  if (user.mustChangePassword) {
    return <ChangePasswordScreen onPasswordChanged={fetchCurrentUser} />;
  }

  const renderCurrentScreen = () => {
    switch (currentScreen) {
      case 'catalog':
        return <CatalogScreen userRole={user.role} />;
      case 'dashboard':
        return <DashboardScreen onNavigate={setCurrentScreen} userRole={user.role} />;
      case 'assets':
        return <AssetManagementScreen userRole={user.role} />;
      case 'consumables':
        return <ConsumableInventoryScreen userRole={user.role} />;
      case 'damage-reports':
        return <DamageReportScreen userRole={user.role} />;
      case 'locations':
        return <AreaManagementScreen userRole={user.role} />;
      default:
        return <DashboardScreen onNavigate={setCurrentScreen} userRole={user.role} />;
    }
  };

  return (
    <div className="min-h-screen bg-[#F7F8FA] text-[#1C2330] flex">
      {/* Fixed Navigation Sidebar */}
      <Sidebar
        currentScreen={currentScreen}
        onSelectScreen={setCurrentScreen}
        isOpenMobile={isMobileSidebarOpen}
        onCloseMobile={() => setIsMobileSidebarOpen(false)}
      />

      {/* Main App Content Area */}
      <div className="flex-1 flex flex-col min-w-0 lg:pl-64">
        {/* Sticky Header */}
        <Header
          onToggleMobileMenu={() => setIsMobileSidebarOpen(true)}
          selectedHotelId={selectedHotelId}
          onSelectHotel={setSelectedHotelId}
          onOpenApiConfig={() => setIsApiConfigOpen(true)}
          user={user}
          onLogout={handleLogout}
        />

        {/* Page Container */}
        <main className="flex-1 p-4 sm:p-6 lg:p-7 max-w-[1500px] w-full mx-auto">
          {renderCurrentScreen()}
        </main>
      </div>

      {/* Spring Boot REST API Configuration & Inspection Modal */}
      <ApiConfigModal
        isOpen={isApiConfigOpen}
        onClose={() => setIsApiConfigOpen(false)}
      />
    </div>
>>>>>>> Nguyen
  );
}
