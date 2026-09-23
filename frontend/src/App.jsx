import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import LocationsPage from './pages/LocationsPage';
import ManagersPage from './pages/ManagersPage';
import AppLayout from './components/AppLayout';
import AdminLayout from './components/AdminLayout';
import TenantsPage from './pages/Admin_platform/TenantsPage';
import TenantDetailPage from './pages/Admin_platform/TenantDetailPage';
import PricingPage from './pages/Admin_platform/PricingPage';
import SystemConfigPage from './pages/Admin_platform/SystemConfigPage';
import RoomsPage from './pages/rooms/RoomsPage';
import RoomDetailPage from './pages/rooms/RoomDetailPage';
import RoomBoardPage from './pages/rooms/RoomBoardPage';
import HousekeepingPage from './pages/rooms/HousekeepingPage';
import MyTasksPage from './pages/rooms/MyTasksPage';
import { homePathFor } from './homePath';

/** Đã đăng nhập mới vào được; còn mật khẩu tạm thì phải đổi trước (BR-USER-07). */
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

/**
 * Màn quản trị của Giám đốc / Manager. Staff bị đưa về trang chủ của mình thay vì thấy màn lỗi
 * 403 — áp cho cả các lối vào cứng /khach-san (đăng nhập Google, sau khi đổi mật khẩu). Chỉ là
 * lớp giao diện; quyền thật do backend quyết định. Dùng BÊN TRONG RequireAuth.
 */
function RequireManagementRole({ children }) {
  const { user } = useAuth();
  return user?.role === 'STAFF' ? <Navigate to={homePathFor(user)} replace /> : children;
}

/** Trang nghiệp vụ trong khung AppLayout, bắt buộc đã đăng nhập (và đã đổi mật khẩu tạm). */
function inShell(page) {
  return (
    <RequireAuth>
      <AppLayout>{page}</AppLayout>
    </RequireAuth>
  );
}

import { DashboardScreen } from './components/screens/DashboardScreen';
import { AssetManagementScreen } from './components/screens/AssetManagementScreen';
import { AssetDetailScreen } from './components/screens/AssetDetailScreen';
import { BatchCreateAssetsScreen } from './components/screens/BatchCreateAssetsScreen';
import { ConsumableInventoryScreen } from './components/screens/ConsumableInventoryScreen';
import { DamageReportScreen } from './components/screens/DamageReportScreen';
import { useNavigate, useParams } from 'react-router-dom';

function useAssetNavigate() {
  const navigate = useNavigate();
  return (path, param) => {
    if (path === 'overview') navigate('/tong-quan');
    if (path === 'fixed-assets') navigate('/tai-san');
    if (path === 'asset-detail') navigate(`/tai-san/${param}`);
    if (path === 'batch-create') navigate('/tai-san/batch');
    if (path === 'consumables') navigate('/vat-tu');
    if (path === 'issue-reports') navigate('/bao-hong');
    if (path === 'incident-detail') navigate(`/bao-hong/${param}`);
  };
}

function OverviewWrapper() {
  return <DashboardScreen onNavigate={useAssetNavigate()} />;
}
function FixedAssetWrapper() {
  return <AssetManagementScreen onNavigate={useAssetNavigate()} />;
}
function BatchCreateWrapper() {
  return <BatchCreateAssetsScreen onNavigate={useAssetNavigate()} />;
}
function ConsumableWrapper() {
  return <ConsumableInventoryScreen onNavigate={useAssetNavigate()} />;
}
function DetailWrapper() {
  const { code } = useParams();
  return <AssetDetailScreen assetCode={code} onNavigate={useAssetNavigate()} />;
}
function IncidentWrapper() {
  const { id } = useParams();
  return <DamageReportScreen incidentId={id} onNavigate={useAssetNavigate()} />;
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
            <RequireManagementRole>
              <AppLayout>
                <LocationsPage />
              </AppLayout>
            </RequireManagementRole>
          </RequireAuth>
        }
      />
      {/* Quản lý tài khoản Manager — BR-PERM-02: chỉ Giám đốc; Manager vào thấy thông báo. */}
      <Route path="/quan-ly" element={inShell(<RequireManagementRole><ManagersPage /></RequireManagementRole>)} />
      {/* Quản lý phòng — BR-ROOM-*. S-02 dành cho Giám đốc/Manager; chi tiết và sơ đồ phòng mọi vai trò. */}
      <Route path="/phong" element={inShell(<RequireManagementRole><RoomsPage /></RequireManagementRole>)} />
      <Route path="/phong/:id" element={inShell(<RoomDetailPage />)} />
      <Route path="/so-do-phong" element={inShell(<RoomBoardPage />)} />
      {/* Dọn phòng — BR-HK-*. Bảng lịch dọn cho Quản lý; "việc của tôi" cho nhân viên dọn.
          Không chặn theo vai trò ở đây: backend tự ép phạm vi (nhân viên chỉ thấy việc của
          mình), và mở được cả hai trang giúp Quản lý kiểm chứng nhanh khi có sự cố. */}
      <Route path="/don-phong" element={inShell(<RequireManagementRole><HousekeepingPage /></RequireManagementRole>)} />
      <Route path="/don-phong/cua-toi" element={inShell(<MyTasksPage />)} />

      {/* New Asset Routes */}
      <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
        <Route path="/tong-quan" element={<OverviewWrapper />} />
        <Route path="/tai-san" element={<FixedAssetWrapper />} />
        <Route path="/tai-san/batch" element={<BatchCreateWrapper />} />
        <Route path="/tai-san/:code" element={<DetailWrapper />} />
        <Route path="/vat-tu" element={<ConsumableWrapper />} />
        <Route path="/bao-hong" element={<IncidentWrapper />} />
      </Route>

      {/* Admin Platform Routes */}
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
      {/* Chưa đăng nhập: homePathFor(null) = /khach-san → RequireAuth đưa về /dang-nhap như cũ. */}
      <Route path="*" element={<Navigate to={homePathFor(user)} replace />} />
    </Routes>
  );
}
