import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
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
import RoomDetailPage from './pages/rooms/RoomDetailPage';
import RoomBoardPage from './pages/rooms/RoomBoardPage';
import HousekeepingPage from './pages/rooms/HousekeepingPage';
import MyTasksPage from './pages/rooms/MyTasksPage';
import CleaningSchedulePage from './pages/rooms/CleaningSchedulePage';
import SchedulePage from './pages/scheduling/SchedulePage';
import ScheduleRulesPage from './pages/scheduling/ScheduleRulesPage';
import MySchedulePage from './pages/scheduling/MySchedulePage';
import MyLeavePage from './pages/scheduling/MyLeavePage';
import LeaveReviewPage from './pages/scheduling/LeaveReviewPage';
import ShiftSwapsPage from './pages/scheduling/ShiftSwapsPage';
import SwapApprovalsPage from './pages/scheduling/SwapApprovalsPage';
import ChainRoomSummary from './components/rooms/ChainRoomSummary';
import HotelOpsSummary from './components/rooms/HotelOpsSummary';
import ReserveManagerPage from './pages/ReserveManagerPage';
import AssetCategoriesPage from './pages/AssetCategoriesPage';
import OrganizationCatalogPage from './pages/OrganizationCatalogPage';
import MyDamageReportsPage from './pages/MyDamageReportsPage';
import { homePathFor } from './homePath';
import { canReportDamage, hasPermission, isSupervisor } from './permissions';
import RegisterPage from './pages/RegisterPage';
import VerifyEmailPage from './pages/VerifyEmailPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ProfilePage from './pages/ProfilePage';
import BillingPage from './pages/BillingPage';
import PaymentResultPage from './pages/PaymentResultPage';


/** Trang Giám đốc chưa chọn gói được mở: chọn Dùng thử / Mua gói và nhận kết quả VNPay. */
const PLAN_REQUIRED_PATHS = ['/goi-dich-vu', '/thanh-toan/ket-qua'];

/**
 * Đã đăng nhập mới vào được; còn mật khẩu tạm thì phải đổi trước (BR-USER-07). Quản lý dự bị
 * (chưa gán khách sạn) chỉ thấy màn chờ — backend chặn mọi API quản lý của họ.
 * Giám đốc của Tenant mới chưa chọn Dùng thử / Mua gói chỉ vào được Gói dịch vụ và trang kết quả
 * thanh toán ({@link PLAN_REQUIRED_PATHS}) — backend cũng chặn mọi API khác.
 */
function RequireAuth({ children }) {
  const { user, loading } = useAuth();
  const { pathname } = useLocation();

  if (loading) {
    return <div className="boot-screen">Đang tải…</div>;
  }
  if (!user) {
    return <Navigate to="/dang-nhap" replace />;
  }
  if (user.mustChangePassword) {
    return <Navigate to="/doi-mat-khau" replace />;
  }
  if (user.planRequired && !PLAN_REQUIRED_PATHS.includes(pathname)) {
    return <Navigate to="/goi-dich-vu" replace />;
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
 * Bảng Công việc dọn phòng — Quản lý chi nhánh và Giám sát vận hành (giao việc, kiểm tra phòng —
 * BR-PERM-07). Giám đốc về trang chủ như {@link RequireBranchManager}. Chỉ là lớp giao diện; quyền
 * thật do backend quyết định.
 */
function RequireHousekeepingBoard({ children }) {
  const { user } = useAuth();
  const allowed = user?.role === 'MANAGER' || user?.role === 'PLATFORM_ADMIN' || isSupervisor(user);
  return allowed ? children : <Navigate to={homePathFor(user)} replace />;
}

/**
 * Danh sách báo hỏng — Manager xử lý, Giám đốc xem, Giám sát vận hành xử lý thay Manager (BR-PERM-07).
 * Nhân viên khác về trang chủ: họ gửi báo hỏng ở "Báo hỏng của tôi". Chỉ là lớp giao diện.
 */
function RequireDamageInbox({ children }) {
  const { user } = useAuth();
  const allowed = user?.role !== 'STAFF' || isSupervisor(user);
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

/**
 * Lịch dọn phòng theo nhân viên — Quản lý chi nhánh (mọi người dọn của khách sạn) và nhân viên có quyền
 * Dọn dẹp (lịch của chính mình). Chỉ là lớp giao diện; phạm vi dữ liệu do backend quyết định.
 */
function RequireCleaningSchedule({ children }) {
  const { user } = useAuth();
  const allowed = user?.role === 'MANAGER' || user?.role === 'PLATFORM_ADMIN'
    || (user?.role === 'STAFF' && hasPermission(user, 'HOUSEKEEPING'));
  return allowed ? children : <Navigate to={homePathFor(user)} replace />;
}

/**
 * Lịch cá nhân — chỉ nhân viên: quản lý khách sạn không có ca (chốt 05/10/2026), Giám đốc cũng không.
 * Vai trò khác về trang chủ của mình. Chỉ là lớp giao diện; backend chỉ trả ca của chính người đăng nhập.
 */
function RequireStaff({ children }) {
  const { user } = useAuth();
  return user?.role === 'STAFF' ? children : <Navigate to={homePathFor(user)} replace />;
}

/**
 * Đơn xin nghỉ của chính mình — nhân viên và quản lý khách sạn (BR-PERM-06, BR-SCH-08). Giám đốc không có cấp
 * trên duyệt đơn nên về trang chủ. Chỉ là lớp giao diện; quyền thật do backend quyết định.
 */
function RequireLeaveRequester({ children }) {
  const { user } = useAuth();
  const allowed = user?.role === 'STAFF' || user?.role === 'MANAGER';
  return allowed ? children : <Navigate to={homePathFor(user)} replace />;
}

/**
 * Duyệt đơn nghỉ theo cấp — quản lý khách sạn duyệt đơn nhân viên, giám đốc duyệt đơn quản lý (BR-SCH-08).
 * Giám sát vận hành là nhân viên nên không vào được (BR-PERM-07). Chỉ là lớp giao diện.
 */
function RequireLeaveApprover({ children }) {
  const { user } = useAuth();
  const allowed = user?.role === 'MANAGER' || user?.role === 'DIRECTOR';
  return allowed ? children : <Navigate to={homePathFor(user)} replace />;
}

/**
 * Hồ sơ cá nhân — ai làm việc trong Tenant (BR-PERM-06). Admin Platform không có hồ sơ nhân sự nên về trang chủ.
 * Chỉ là lớp giao diện; backend chỉ trả hồ sơ của chính người đăng nhập.
 */
function RequireTenantUser({ children }) {
  const { user } = useAuth();
  const allowed = user?.role === 'DIRECTOR' || user?.role === 'MANAGER' || user?.role === 'STAFF';
  return allowed ? children : <Navigate to={homePathFor(user)} replace />;
}

/**
 * Báo hỏng của tôi — chỉ nhân viên có quyền Lễ tân / Dọn dẹp (BR-ASSET-05). Vai trò khác về trang
 * chủ của mình. Chỉ là lớp giao diện; quyền thật do backend quyết định.
 */
function RequireDamageReporter({ children }) {
  const { user } = useAuth();
  return canReportDamage(user) ? children : <Navigate to={homePathFor(user)} replace />;
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
import { ConsumableInventoryScreen } from './components/screens/ConsumableInventoryScreen';
import { DamageReportListScreen } from './components/screens/DamageReportListScreen';
import { AreaManagementScreen } from './components/screens/AreaManagementScreen';
import { useNavigate, useParams } from 'react-router-dom';

function useAssetNavigate() {
  const navigate = useNavigate();
  return (path, param) => {
    if (path === 'overview') navigate('/tong-quan');
    if (path === 'fixed-assets') navigate('/tai-san');
    if (path === 'asset-detail') navigate(`/tai-san/${param}`);
    if (path === 'consumables') navigate('/vat-tu');
    if (path === 'issue-reports') navigate('/bao-hong');
  };
}

function OverviewWrapper() {
  const { user } = useAuth();
  const onNavigate = useAssetNavigate();
  return (
    <>
      {/* BR-DASH-03: Manager thấy số liệu vận hành của khách sạn mình trước phần tài sản. */}
      {user?.role === 'MANAGER' && <HotelOpsSummary />}
      {/* BR-DASH-02: Giám đốc thấy phòng theo trạng thái của cả chuỗi, so sánh giữa các khách sạn. */}
      {user?.role === 'DIRECTOR' && <ChainRoomSummary />}
      <DashboardScreen onNavigate={onNavigate} />
    </>
  );
}
function FixedAssetWrapper() {
  return <AssetManagementScreen onNavigate={useAssetNavigate()} />;
}
/** Tồn kho tiêu hao chỉ mở cho Giám đốc (xem) và Manager (sửa) — Staff không đụng tới (BR-PERM). */
function ConsumableWrapper() {
  const { user } = useAuth();
  const onNavigate = useAssetNavigate();
  if (user?.role !== 'DIRECTOR' && user?.role !== 'MANAGER') return <Navigate to={homePathFor(user)} replace />;
  return <ConsumableInventoryScreen onNavigate={onNavigate} />;
}
/**
 * Khu vực tạo ở cấp khách sạn, Manager CRUD (BR-ORG-12) — chỉ Manager, khớp sidebar; vai trò khác
 * về trang chủ. Chỉ là lớp giao diện; quyền thật do backend quyết định.
 */
function AreaWrapper() {
  const { user } = useAuth();
  if (user?.role !== 'MANAGER') return <Navigate to={homePathFor(user)} replace />;
  return <AreaManagementScreen userRole={user.role} />;
}
function DetailWrapper() {
  const { ref } = useParams();
  return <AssetDetailScreen key={ref} assetRef={ref} onNavigate={useAssetNavigate()} />;
}
function IncidentListWrapper() {
  return <DamageReportListScreen onNavigate={useAssetNavigate()} />;
}
/** Link cũ tới một báo hỏng: mở danh sách báo hỏng kèm hộp thoại xử lý phiếu đó. */
function IncidentWrapper() {
  const { id } = useParams();
  return <DamageReportListScreen key={id} initialIncidentId={id} onNavigate={useAssetNavigate()} />;
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
      {/* BR-SAAS-13: tự đăng ký doanh nghiệp — công khai; đã đăng nhập thì về trang chủ. */}
      <Route
        path="/dang-ky"
        element={
          loading ? (
            <div className="boot-screen">Đang tải…</div>
          ) : user ? (
            <Navigate to={homePathFor(user)} replace />
          ) : (
            <RegisterPage />
          )
        }
      />
      {/* Mở từ nút trong email xác thực — công khai vì tài khoản chưa đăng nhập được. */}
      <Route path="/xac-thuc-email" element={<VerifyEmailPage />} />
      {/* Nhân viên quên mật khẩu — gửi yêu cầu cho Quản lý khách sạn; công khai, đã đăng nhập thì về trang chủ. */}
      <Route
        path="/quen-mat-khau"
        element={
          loading ? (
            <div className="boot-screen">Đang tải…</div>
          ) : user ? (
            <Navigate to={homePathFor(user)} replace />
          ) : (
            <ForgotPasswordPage />
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
      {/* Quản lý phòng — BR-ROOM-*. Danh sách phòng (S-02) đã gộp vào Sơ đồ phòng (nút "Danh sách");
          link cũ /phong chuyển sang dạng danh sách của sơ đồ. Chi tiết và sơ đồ phòng mọi vai trò. */}
      <Route path="/phong" element={<Navigate to="/so-do-phong?view=list" replace />} />
      <Route path="/phong/:id" element={inShell(<RoomDetailPage />)} />
      <Route path="/so-do-phong" element={inShell(<RoomBoardPage />)} />
      {/* Dọn phòng — BR-HK-*. Bảng lịch dọn cho Quản lý chi nhánh và Giám sát vận hành (BR-PERM-07);
          "việc của tôi" không chặn vai trò vì backend tự ép nhân viên về việc của chính mình (BR-PERM-05). */}
      <Route path="/don-phong" element={inShell(<RequireHousekeepingBoard><HousekeepingPage /></RequireHousekeepingBoard>)} />
      <Route path="/don-phong/cua-toi" element={inShell(<MyTasksPage />)} />
      <Route path="/lich-don-phong" element={inShell(<RequireCleaningSchedule><CleaningSchedulePage /></RequireCleaningSchedule>)} />
      {/* Báo hỏng của nhân viên — BR-ASSET-05. Đường tĩnh nên được ưu tiên hơn /bao-hong/:id
          (màn xử lý của Manager / Giám sát) dù khai báo ở đâu. */}
      <Route path="/bao-hong/cua-toi" element={inShell(<RequireDamageReporter><MyDamageReportsPage /></RequireDamageReporter>)} />
      {/* Hộp thư báo hỏng — Manager xử lý, Giám đốc xem, Giám sát vận hành xử lý (BR-PERM-07). Nằm
          ngoài nhóm tài sản bên dưới vì Giám sát là STAFF mà vẫn phải vào được. */}
      <Route path="/bao-hong" element={inShell(<RequireDamageInbox><IncidentListWrapper /></RequireDamageInbox>)} />
      <Route path="/bao-hong/:id" element={inShell(<RequireDamageInbox><IncidentWrapper /></RequireDamageInbox>)} />
      {/* Danh mục tài sản cấp Tenant — BR-ASSET-09: Giám đốc CRUD, Manager chỉ xem. */}
      <Route path="/danh-muc-tai-san" element={inShell(<RequireManagementRole><AssetCategoriesPage /></RequireManagementRole>)} />
      {/* Loại phòng, Phòng ban, Vị trí công việc — BR-ORG-06, BR-ORG-11: chỉ Giám đốc; Manager vào thấy thông báo. */}
      <Route path="/danh-muc" element={inShell(<RequireManagementRole><OrganizationCatalogPage /></RequireManagementRole>)} />
      {/* Lịch làm việc — BR-SCH-*. Manager xếp ca cho khách sạn mình (BR-PERM-03); Giám đốc đặt quy
          định và mẫu ca dùng chung cả chuỗi (BR-PERM-02). */}
      <Route path="/xep-lich" element={inShell(<RequireBranchManager><SchedulePage /></RequireBranchManager>)} />
      <Route path="/quy-dinh-ca" element={inShell(<RequireDirector><ScheduleRulesPage /></RequireDirector>)} />
      {/* Nhân viên xem ca của chính mình (BR-PERM-04..06); chấm công để sau. */}
      <Route path="/lich-ca-nhan" element={inShell(<RequireStaff><MySchedulePage /></RequireStaff>)} />
      {/* Đơn xin nghỉ — BR-SCH-06..08: nhân viên, quản lý gửi đơn của mình; quản lý / giám đốc duyệt theo cấp. */}
      <Route path="/don-nghi" element={inShell(<RequireLeaveRequester><MyLeavePage /></RequireLeaveRequester>)} />
      <Route path="/duyet-don-nghi" element={inShell(<RequireLeaveApprover><LeaveReviewPage /></RequireLeaveApprover>)} />
      {/* Đổi ca — BR-SCH-08..12, 18, 19 (Milestone 2): nhân viên gửi / trả lời yêu cầu, Manager duyệt
          cho khách sạn mình. Quản lý không có ca nên không có trang Đổi ca. */}
      <Route path="/doi-ca" element={inShell(<RequireStaff><ShiftSwapsPage /></RequireStaff>)} />
      <Route path="/duyet-doi-ca" element={inShell(<RequireBranchManager><SwapApprovalsPage /></RequireBranchManager>)} />
      {/* Hồ sơ cá nhân — BR-PERM-06: chỉ xem; mở từ nút «Hồ sơ» trên thanh tiêu đề. */}
      <Route path="/ho-so" element={inShell(<RequireTenantUser><ProfilePage /></RequireTenantUser>)} />
      {/* Gói dịch vụ — chỉ Giám đốc (trang tự đưa vai trò khác về trang chủ). */}
      <Route path="/goi-dich-vu" element={inShell(<BillingPage />)} />
      {/* VNPay đưa Giám đốc về đây sau khi thanh toán (?vnp_...). */}
      <Route path="/thanh-toan/ket-qua" element={inShell(<PaymentResultPage />)} />

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
        {/* Thêm tài sản giờ là hộp thoại; link cũ tới màn riêng về danh sách tài sản. */}
        <Route path="/tai-san/batch" element={<Navigate to="/tai-san" replace />} />
        {/* :ref là id tài sản; mã tài sản (link cũ) vẫn được nhận. */}
        <Route path="/tai-san/:ref" element={<DetailWrapper />} />
        <Route path="/vat-tu" element={<ConsumableWrapper />} />
        <Route path="/khu-vuc" element={<AreaWrapper />} />
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
