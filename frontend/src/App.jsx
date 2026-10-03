import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import ChangePasswordPage from './pages/ChangePasswordPage';
import LocationsPage from './pages/LocationsPage';
import ManagersPage from './pages/ManagersPage';
import StaffPage from './pages/StaffPage';
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
import SchedulePage from './pages/scheduling/SchedulePage';
import ScheduleRulesPage from './pages/scheduling/ScheduleRulesPage';
import ReserveManagerPage from './pages/ReserveManagerPage';
import AssetCategoriesPage from './pages/AssetCategoriesPage';
import OrganizationCatalogPage from './pages/OrganizationCatalogPage';
import { homePathFor } from './homePath';

/**
 * Đã đăng nhập mới vào được; còn mật khẩu tạm thì phải đổi trước (BR-USER-07). Quản lý dự bị
 * (chưa gán khách sạn) chỉ thấy màn chờ — backend chặn mọi API quản lý của họ.
 */
function RequireAuth({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <div className="boot-screen">Đang tải…</div>;
  }
  if (!user) {
    return <Navigate to="/dang-nhap" replace />;
  }
  if (user.mustChangePassword) {
    return <Navigate to="/doi-mat-khau" replace />;
  }
  return user.role === 'MANAGER' && !user.locationId ? <ReserveManagerPage /> : children;
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

/**
 * Bảng lịch dọn phòng — chỉ Quản lý chi nhánh. Giám đốc bị đưa về trang chủ thay vì thấy màn
 * rỗng không thao tác được: mọi việc trên lịch dọn đều ✖ với DIRECTOR (BR-HK-02, BR-HK-05,
 * BR-HK-06, BR-HK-09). Chặn ở đây để ẩn mục menu không bị đi vòng bằng cách gõ thẳng URL.
 * Chỉ là lớp giao diện; quyền thật do backend quyết định.
 */
function RequireBranchManager({ children }) {
  const { user } = useAuth();
  const allowed = user?.role === 'MANAGER' || user?.role === 'PLATFORM_ADMIN';
  return allowed ? children : <Navigate to={homePathFor(user)} replace />;
}

/**
 * Quy định xếp ca & Mẫu ca — chỉ Giám đốc sửa (BR-SCH-01, BR-PERM-02). Manager đọc quy định ngay trên
 * bảng xếp lịch nên không cần vào đây. Chỉ là lớp giao diện; quyền thật do backend quyết định.
 */
function RequireDirector({ children }) {
  const { user } = useAuth();
  const allowed = user?.role === 'DIRECTOR' || user?.role === 'PLATFORM_ADMIN';
  return allowed ? children : <Navigate to={homePathFor(user)} replace />;
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
/** Chỉ Manager tạo tài sản trong khách sạn của mình (BR-ASSET-09) — vai trò khác về danh sách. */
function BatchCreateWrapper() {
  const { user } = useAuth();
  const onNavigate = useAssetNavigate();
  if (user?.role !== 'MANAGER') return <Navigate to="/tai-san" replace />;
  return <BatchCreateAssetsScreen onNavigate={onNavigate} />;
}
/** Tồn kho tiêu hao chỉ mở cho Giám đốc (xem) và Manager (sửa) — Staff không đụng tới (BR-PERM). */
function ConsumableWrapper() {
  const { user } = useAuth();
  const onNavigate = useAssetNavigate();
  if (user?.role !== 'DIRECTOR' && user?.role !== 'MANAGER') return <Navigate to={homePathFor(user)} replace />;
  return <ConsumableInventoryScreen onNavigate={onNavigate} />;
}
function DetailWrapper() {
  const { ref } = useParams();
  return <AssetDetailScreen key={ref} assetRef={ref} onNavigate={useAssetNavigate()} />;
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
      {/* Quản lý tài khoản nhân viên — BR-PERM-03: Manager CRUD Staff trong khách sạn của mình. */}
      <Route path="/nhan-vien" element={inShell(<RequireManagementRole><StaffPage /></RequireManagementRole>)} />
      {/* Quản lý phòng — BR-ROOM-*. S-02 dành cho Giám đốc/Manager; chi tiết và sơ đồ phòng mọi vai trò. */}
      <Route path="/phong" element={inShell(<RequireManagementRole><RoomsPage /></RequireManagementRole>)} />
      <Route path="/phong/:id" element={inShell(<RoomDetailPage />)} />
      <Route path="/so-do-phong" element={inShell(<RoomBoardPage />)} />
      {/* Dọn phòng — BR-HK-*. Bảng lịch dọn chỉ cho Quản lý chi nhánh; "việc của tôi" không
          chặn vai trò vì backend tự ép nhân viên về việc của chính mình (BR-PERM-05). */}
      <Route path="/don-phong" element={inShell(<RequireBranchManager><HousekeepingPage /></RequireBranchManager>)} />
      <Route path="/don-phong/cua-toi" element={inShell(<MyTasksPage />)} />
      {/* Danh mục tài sản cấp Tenant — BR-ASSET-09: Giám đốc CRUD, Manager chỉ xem. */}
      <Route path="/danh-muc-tai-san" element={inShell(<RequireManagementRole><AssetCategoriesPage /></RequireManagementRole>)} />
      {/* Loại phòng, Phòng ban, Vị trí công việc — BR-ORG-06, BR-ORG-11: chỉ Giám đốc; Manager vào thấy thông báo. */}
      <Route path="/danh-muc" element={inShell(<RequireManagementRole><OrganizationCatalogPage /></RequireManagementRole>)} />
      {/* Lịch làm việc — BR-SCH-*. Manager xếp ca cho khách sạn mình (BR-PERM-03); Giám đốc đặt quy
          định và mẫu ca dùng chung cả chuỗi (BR-PERM-02). */}
      <Route path="/xep-lich" element={inShell(<RequireBranchManager><SchedulePage /></RequireBranchManager>)} />
      <Route path="/quy-dinh-ca" element={inShell(<RequireDirector><ScheduleRulesPage /></RequireDirector>)} />

      {/* Tài sản & dashboard — chỉ Giám đốc / Manager, khớp sidebar. Staff chỉ có quyền báo hỏng
          (BR-ASSET-05), không quản lý tài sản hay tồn kho; gõ thẳng URL thì về trang chủ của mình. */}
      <Route
        element={
          <RequireAuth>
            <RequireManagementRole>
              <AppLayout />
            </RequireManagementRole>
          </RequireAuth>
        }
      >
        <Route path="/tong-quan" element={<OverviewWrapper />} />
        <Route path="/tai-san" element={<FixedAssetWrapper />} />
        <Route path="/tai-san/batch" element={<BatchCreateWrapper />} />
        {/* :ref là id tài sản; mã tài sản (link cũ) vẫn được nhận. */}
        <Route path="/tai-san/:ref" element={<DetailWrapper />} />
        <Route path="/vat-tu" element={<ConsumableWrapper />} />
        <Route path="/bao-hong" element={<IncidentWrapper />} />
        <Route path="/bao-hong/:id" element={<IncidentWrapper />} />
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
