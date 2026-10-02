package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.asset.CreateDamageReportRequest;
import com.example.SWP391_G2_SE2055_JV.dto.asset.DamageReportResponse;
import com.example.SWP391_G2_SE2055_JV.dto.asset.ResolveDamageReportRequest;
import com.example.SWP391_G2_SE2055_JV.entity.DamageReport;
import com.example.SWP391_G2_SE2055_JV.entity.FixedAsset;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.DamageReportStatus;
import com.example.SWP391_G2_SE2055_JV.enums.FixedAssetStatus;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.AreaRepository;
import com.example.SWP391_G2_SE2055_JV.repository.AssetCategoryRepository;
import com.example.SWP391_G2_SE2055_JV.repository.DamageReportRepository;
import com.example.SWP391_G2_SE2055_JV.repository.FixedAssetRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Báo hỏng tài sản — BR-ASSET-05, BR-ASSET-06, BR-ASSET-11, DM-12.
 *
 * <p>Trọng tâm: phạm vi nhìn theo vai trò, chặn báo hỏng tài sản đã thanh lý, và luồng Manager
 * đóng phiếu — trạng thái tài sản chỉ đổi khi Manager CHỌN, cùng một transaction với việc đóng
 * phiếu. {@link FixedAssetService} là bản thật dựng trên repository mock, để luật BR-ASSET-14 và
 * việc tự đóng phiếu khi thanh lý chạy đúng như production.
 */
@ExtendWith(MockitoExtension.class)
class DamageReportServiceTest {

    private static final UUID TENANT         = UUID.randomUUID();
    private static final UUID LOCATION       = UUID.randomUUID();
    private static final UUID OTHER_LOCATION = UUID.randomUUID();
    private static final UUID ROOM           = UUID.randomUUID();
    private static final Pageable PAGE       = PageRequest.of(0, 20);

    @Mock private DamageReportRepository  damageReportRepository;
    @Mock private FixedAssetRepository    fixedAssetRepository;
    @Mock private AssetCategoryRepository categoryRepository;
    @Mock private UserRepository          userRepository;
    @Mock private RoomRepository          roomRepository;
    @Mock private AreaRepository          areaRepository;

    private DamageReportService service;

    @BeforeEach
    void setUp() {
        FixedAssetService fixedAssetService = new FixedAssetService(
            fixedAssetRepository, categoryRepository, roomRepository, areaRepository, damageReportRepository);
        service = new DamageReportService(
            damageReportRepository, fixedAssetRepository, fixedAssetService,
            userRepository, roomRepository, areaRepository);
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    // ── Helper dựng dữ liệu ───────────────────────────────────────────────────

    private FixedAsset asset(FixedAssetStatus status) {
        return FixedAsset.builder()
            .id(UUID.randomUUID()).tenantId(TENANT).locationId(LOCATION).categoryId(UUID.randomUUID())
            .assetCode("TS-00001").name("Tivi Samsung 43\"").roomId(ROOM).status(status)
            .build();
    }

    private DamageReport report(FixedAsset asset, DamageReportStatus status) {
        return DamageReport.builder()
            .id(UUID.randomUUID()).tenantId(TENANT).locationId(LOCATION).fixedAssetId(asset.getId())
            .reporterId(UUID.randomUUID()).description("Không lên hình").status(status)
            .reportedAt(LocalDateTime.now().minusHours(1))
            .build();
    }

    /** Dữ liệu để gắn tên vào response — không phải trọng tâm của mọi test nên để lenient. */
    private void stubLookups(FixedAsset asset) {
        lenient().when(fixedAssetRepository.findAllById(anyIterable())).thenReturn(List.of(asset));
        lenient().when(roomRepository.findAllById(anyIterable()))
            .thenReturn(List.of(Room.builder().id(ROOM).roomNumber("305").build()));
    }

    private void stubSaveEcho() {
        when(damageReportRepository.save(any(DamageReport.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private CreateDamageReportRequest createRequest(UUID assetId, String description) {
        CreateDamageReportRequest request = new CreateDamageReportRequest();
        request.setFixedAssetId(assetId);
        request.setDescription(description);
        return request;
    }

    private ResolveDamageReportRequest resolveRequest(FixedAssetStatus status, String note) {
        ResolveDamageReportRequest request = new ResolveDamageReportRequest();
        request.setNewAssetStatus(status);
        request.setResolutionNote(note);
        return request;
    }

    // ── Tạo báo hỏng ──────────────────────────────────────────────────────────

    @Nested
    class TaoBaoHong {

        @Test
        void nhan_vien_bao_hong_tai_san_trong_khach_san_minh_thi_tao_phieu_NEW() {
            UUID staffId = UUID.randomUUID();
            TestAuth.loginAs(staffId, Role.STAFF, TENANT, LOCATION, PositionType.HOUSEKEEPING);
            FixedAsset tv = asset(FixedAssetStatus.GOOD);
            when(fixedAssetRepository.findByIdAndTenantIdAndLocationId(tv.getId(), TENANT, LOCATION))
                .thenReturn(Optional.of(tv));
            stubSaveEcho();
            stubLookups(tv);

            DamageReportResponse response = service.createDamageReport(createRequest(tv.getId(), "  Vỡ màn hình  "));

            ArgumentCaptor<DamageReport> saved = ArgumentCaptor.forClass(DamageReport.class);
            verify(damageReportRepository).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(DamageReportStatus.NEW);
            assertThat(saved.getValue().getReporterId()).isEqualTo(staffId);
            assertThat(saved.getValue().getLocationId()).isEqualTo(LOCATION);
            assertThat(saved.getValue().getDescription()).isEqualTo("Vỡ màn hình");
            assertThat(response.getAssetCode()).isEqualTo("TS-00001");
            assertThat(response.getRoomName()).isEqualTo("Phòng 305");
        }

        /** BR-ASSET-06: tạo báo hỏng KHÔNG tự đổi trạng thái tài sản. */
        @Test
        void tao_bao_hong_khong_dong_toi_trang_thai_tai_san() {
            TestAuth.loginAsStaff(TENANT, LOCATION, PositionType.RECEPTION);
            FixedAsset tv = asset(FixedAssetStatus.GOOD);
            when(fixedAssetRepository.findByIdAndTenantIdAndLocationId(tv.getId(), TENANT, LOCATION))
                .thenReturn(Optional.of(tv));
            stubSaveEcho();

            service.createDamageReport(createRequest(tv.getId(), "Mất remote"));

            assertThat(tv.getStatus()).isEqualTo(FixedAssetStatus.GOOD);
            verify(fixedAssetRepository, never()).save(any());
        }

        /** Sai Location trả 404 — không lộ việc tài sản có tồn tại ở khách sạn khác. */
        @Test
        void tai_san_cua_khach_san_khac_thi_404() {
            TestAuth.loginAsStaff(TENANT, OTHER_LOCATION, PositionType.HOUSEKEEPING);
            UUID assetId = UUID.randomUUID();
            when(fixedAssetRepository.findByIdAndTenantIdAndLocationId(assetId, TENANT, OTHER_LOCATION))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createDamageReport(createRequest(assetId, "Hỏng")))
                .isInstanceOf(ResourceNotFoundException.class);
            verify(damageReportRepository, never()).save(any());
        }

        /** BR-ASSET-11: không tạo báo hỏng mới cho tài sản đã thanh lý. */
        @Test
        void tai_san_da_thanh_ly_thi_bi_chan() {
            TestAuth.loginAsStaff(TENANT, LOCATION, PositionType.HOUSEKEEPING);
            FixedAsset disposed = asset(FixedAssetStatus.DISPOSED);
            when(fixedAssetRepository.findByIdAndTenantIdAndLocationId(disposed.getId(), TENANT, LOCATION))
                .thenReturn(Optional.of(disposed));

            assertThatThrownBy(() -> service.createDamageReport(createRequest(disposed.getId(), "Hỏng")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BR-ASSET-11");
            verify(damageReportRepository, never()).save(any());
        }
    }

    // ── Phạm vi xem danh sách ─────────────────────────────────────────────────

    @Nested
    class PhamViXem {

        private final Page<DamageReport> empty = new PageImpl<>(List.of(), PAGE, 0);

        @Test
        void nhan_vien_chi_thay_phieu_do_chinh_minh_tao() {
            UUID staffId = UUID.randomUUID();
            TestAuth.loginAs(staffId, Role.STAFF, TENANT, LOCATION, PositionType.RECEPTION);
            when(damageReportRepository.search(TENANT, LOCATION, staffId, null, DamageReportStatus.NEW, PAGE))
                .thenReturn(empty);

            service.getDamageReports(DamageReportStatus.NEW, null, PAGE);

            verify(damageReportRepository).search(TENANT, LOCATION, staffId, null, DamageReportStatus.NEW, PAGE);
        }

        @Test
        void manager_thay_moi_phieu_trong_khach_san_minh() {
            TestAuth.loginAsManager(TENANT, LOCATION);
            UUID assetId = UUID.randomUUID();
            when(damageReportRepository.search(eq(TENANT), eq(LOCATION), isNull(), eq(assetId), isNull(), eq(PAGE)))
                .thenReturn(empty);

            service.getDamageReports(null, assetId, PAGE);

            verify(damageReportRepository).search(TENANT, LOCATION, null, assetId, null, PAGE);
        }

        @Test
        void giam_doc_thay_toan_tenant() {
            TestAuth.loginAsDirector(TENANT);
            when(damageReportRepository.search(eq(TENANT), isNull(), isNull(), isNull(), eq(DamageReportStatus.NEW), eq(PAGE)))
                .thenReturn(empty);

            service.getDamageReports(DamageReportStatus.NEW, null, PAGE);

            verify(damageReportRepository).search(TENANT, null, null, null, DamageReportStatus.NEW, PAGE);
        }

        @Test
        void response_gan_san_ten_tai_san_vi_tri_va_nguoi_bao() {
            TestAuth.loginAsManager(TENANT, LOCATION);
            FixedAsset tv = asset(FixedAssetStatus.BROKEN);
            DamageReport pending = report(tv, DamageReportStatus.NEW);
            User reporter = User.builder().id(pending.getReporterId()).fullName("Nguyễn Văn A")
                .email("a@test.local").build();
            when(damageReportRepository.search(TENANT, LOCATION, null, null, DamageReportStatus.NEW, PAGE))
                .thenReturn(new PageImpl<>(List.of(pending), PAGE, 1));
            stubLookups(tv);
            when(userRepository.findAllById(anyIterable())).thenReturn(List.of(reporter));

            Page<DamageReportResponse> page = service.getDamageReports(DamageReportStatus.NEW, null, PAGE);

            assertThat(page.getTotalElements()).isEqualTo(1);
            DamageReportResponse row = page.getContent().get(0);
            assertThat(row.getAssetName()).isEqualTo("Tivi Samsung 43\"");
            assertThat(row.getAssetStatus()).isEqualTo(FixedAssetStatus.BROKEN);
            assertThat(row.getRoomName()).isEqualTo("Phòng 305");
            assertThat(row.getReporterName()).isEqualTo("Nguyễn Văn A");
        }
    }

    // ── Manager đóng phiếu ────────────────────────────────────────────────────

    @Nested
    class DongPhieu {

        private UUID managerId;
        private FixedAsset tv;
        private DamageReport pending;

        @BeforeEach
        void loginManager() {
            managerId = UUID.randomUUID();
            TestAuth.loginAs(managerId, Role.MANAGER, TENANT, LOCATION, null);
            tv = asset(FixedAssetStatus.GOOD);
            pending = report(tv, DamageReportStatus.NEW);
            stubLookups(tv);
        }

        private void stubOwnedReport() {
            when(damageReportRepository.findByIdAndTenantIdAndLocationId(pending.getId(), TENANT, LOCATION))
                .thenReturn(Optional.of(pending));
        }

        private void stubOwnedAsset() {
            when(fixedAssetRepository.findByIdAndTenantIdAndLocationId(tv.getId(), TENANT, LOCATION))
                .thenReturn(Optional.of(tv));
        }

        /** Báo nhầm: đóng phiếu, tài sản giữ nguyên (BR-ASSET-06 — Manager tự quyết). */
        @Test
        void khong_gui_body_thi_chi_dong_phieu_tai_san_giu_nguyen() {
            stubOwnedReport();
            stubSaveEcho();

            DamageReportResponse response = service.resolveDamageReport(pending.getId(), null);

            assertThat(response.getStatus()).isEqualTo(DamageReportStatus.RESOLVED);
            assertThat(pending.getResolvedBy()).isEqualTo(managerId);
            assertThat(pending.getResolvedAt()).isNotNull();
            assertThat(pending.getResolutionNote()).isNull();
            assertThat(tv.getStatus()).isEqualTo(FixedAssetStatus.GOOD);
            verify(fixedAssetRepository, never()).save(any());
        }

        @Test
        void ghi_chu_duoc_luu_sau_khi_cat_khoang_trang() {
            stubOwnedReport();
            stubSaveEcho();

            DamageReportResponse response = service.resolveDamageReport(
                pending.getId(), resolveRequest(null, "  Đã gọi kỹ thuật thay màn hình  "));

            assertThat(response.getResolutionNote()).isEqualTo("Đã gọi kỹ thuật thay màn hình");
        }

        @Test
        void ghi_chu_chi_co_khoang_trang_thi_luu_null() {
            stubOwnedReport();
            stubSaveEcho();

            service.resolveDamageReport(pending.getId(), resolveRequest(null, "   "));

            assertThat(pending.getResolutionNote()).isNull();
        }

        @Test
        void chon_trang_thai_moi_thi_tai_san_doi_trang_thai_sau_khi_dong_phieu() {
            stubOwnedReport();
            stubOwnedAsset();
            stubSaveEcho();

            service.resolveDamageReport(pending.getId(), resolveRequest(FixedAssetStatus.UNDER_REPAIR, null));

            assertThat(tv.getStatus()).isEqualTo(FixedAssetStatus.UNDER_REPAIR);
            assertThat(pending.getStatus()).isEqualTo(DamageReportStatus.RESOLVED);
            InOrder order = inOrder(damageReportRepository, fixedAssetRepository);
            order.verify(damageReportRepository).save(pending);
            order.verify(fixedAssetRepository).save(tv);
        }

        /**
         * Thanh lý tự đóng các phiếu NEW KHÁC của tài sản; phiếu đang xử lý giữ ghi chú và người
         * đóng của chính nó (đã đóng trước khi đổi trạng thái tài sản).
         */
        @Test
        void chon_thanh_ly_thi_dong_ca_phieu_khac_nhung_giu_ghi_chu_phieu_nay() {
            stubOwnedReport();
            stubOwnedAsset();
            stubSaveEcho();
            DamageReport other = report(tv, DamageReportStatus.NEW);
            when(damageReportRepository.findByFixedAssetIdAndStatus(tv.getId(), DamageReportStatus.NEW))
                .thenReturn(List.of(other));

            service.resolveDamageReport(pending.getId(), resolveRequest(FixedAssetStatus.DISPOSED, "Hỏng nặng"));

            assertThat(tv.getStatus()).isEqualTo(FixedAssetStatus.DISPOSED);
            assertThat(pending.getResolutionNote()).isEqualTo("Hỏng nặng");
            assertThat(other.getStatus()).isEqualTo(DamageReportStatus.RESOLVED);
            assertThat(other.getResolvedBy()).isEqualTo(managerId);
            assertThat(other.getResolutionNote()).isNull();
        }

        /** Phiếu đã đóng: báo lỗi thay vì lặng lẽ bỏ qua trạng thái/ghi chú Manager vừa nhập. */
        @Test
        void phieu_da_dong_thi_bao_loi_va_khong_doi_gi() {
            pending.setStatus(DamageReportStatus.RESOLVED);
            pending.setResolvedBy(UUID.randomUUID());
            pending.setResolvedAt(LocalDateTime.now());
            stubOwnedReport();

            assertThatThrownBy(() -> service.resolveDamageReport(
                pending.getId(), resolveRequest(FixedAssetStatus.BROKEN, "Ghi chú mới")))
                .isInstanceOf(BusinessException.class);
            verify(damageReportRepository, never()).save(any());
            verify(fixedAssetRepository, never()).save(any());
        }

        /** BR-ASSET-14: tài sản không đổi được thì cả phiếu cũng không được tính là đã đóng. */
        @Test
        void tai_san_khong_doi_duoc_trang_thai_thi_nem_loi_de_rollback() {
            tv.setStatus(FixedAssetStatus.DISPOSED);
            stubOwnedReport();
            stubOwnedAsset();
            stubSaveEcho();

            assertThatThrownBy(() -> service.resolveDamageReport(
                pending.getId(), resolveRequest(FixedAssetStatus.GOOD, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BR-ASSET-14");
        }

        @Test
        void phieu_cua_khach_san_khac_thi_404() {
            UUID foreignId = UUID.randomUUID();
            when(damageReportRepository.findByIdAndTenantIdAndLocationId(foreignId, TENANT, LOCATION))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resolveDamageReport(foreignId, null))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
