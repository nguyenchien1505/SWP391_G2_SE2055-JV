package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.asset.FixedAssetRequest;
import com.example.SWP391_G2_SE2055_JV.dto.asset.FixedAssetResponse;
import com.example.SWP391_G2_SE2055_JV.entity.Area;
import com.example.SWP391_G2_SE2055_JV.entity.AssetCategory;
import com.example.SWP391_G2_SE2055_JV.entity.FixedAsset;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.enums.AssetKind;
import com.example.SWP391_G2_SE2055_JV.enums.AssetPurpose;
import com.example.SWP391_G2_SE2055_JV.enums.FixedAssetStatus;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.repository.AreaRepository;
import com.example.SWP391_G2_SE2055_JV.repository.AssetCategoryRepository;
import com.example.SWP391_G2_SE2055_JV.repository.FixedAssetRepository;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository;
import com.example.SWP391_G2_SE2055_JV.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Luồng TẠO tài sản cố định — BR-ASSET-03, BR-ASSET-08, BR-ASSET-12, BR-ASSET-13.
 *
 * <p>Trọng tâm là những chốt chặn mà khóa ngoại ở DB không phủ được: vị trí XOR, vị trí
 * phải thuộc đúng Location của người đang đăng nhập, danh mục phải là loại FIXED và còn
 * hiệu lực, mã tài sản unique trong phạm vi Location.
 */
@ExtendWith(MockitoExtension.class)
class FixedAssetServiceTest {

    private static final UUID TENANT   = UUID.randomUUID();
    private static final UUID LOCATION = UUID.randomUUID();
    private static final UUID OTHER_LOCATION = UUID.randomUUID();

    @Mock private FixedAssetRepository    fixedAssetRepository;
    @Mock private AssetCategoryRepository categoryRepository;
    @Mock private RoomRepository          roomRepository;
    @Mock private AreaRepository          areaRepository;

    private FixedAssetService service;

    /**
     * {@code damageReportService} để null có chủ đích: nó chỉ được gọi khi đổi trạng thái
     * sang DISPOSED, không nằm trên luồng tạo. Mock một CLASS (không phải interface) cần
     * bytecode instrumentation mà Mockito của bản Spring Boot này chưa làm được trên JDK
     * đang dùng, nên tự dựng service bằng constructor thay vì {@code @InjectMocks}.
     */
    @BeforeEach
    void setUp() {
        service = new FixedAssetService(
            fixedAssetRepository, categoryRepository, roomRepository, areaRepository, null);
        TestAuth.loginAsManager(TENANT, LOCATION);
    }

    @AfterEach
    void logout() {
        TestAuth.logout();
    }

    // ── Helper dựng dữ liệu ───────────────────────────────────────────────────

    private AssetCategory category(AssetKind kind, boolean active) {
        AssetCategory category = AssetCategory.builder()
            .id(UUID.randomUUID())
            .tenantId(TENANT)
            .name("Điều hòa nhiệt độ")
            .assetKind(kind)
            .purpose(AssetPurpose.GUEST_USE)
            .active(active)
            .build();
        return category;
    }

    private Room room(UUID locationId, boolean active) {
        return Room.builder()
            .id(UUID.randomUUID())
            .tenantId(TENANT)
            .locationId(locationId)
            .roomNumber("310")
            .floor("3")
            .status(RoomStatus.AVAILABLE)
            .active(active)
            .build();
    }

    private Area area(UUID locationId) {
        return Area.builder()
            .id(UUID.randomUUID())
            .tenantId(TENANT)
            .locationId(locationId)
            .name("Sảnh chính")
            .build();
    }

    private FixedAssetRequest request(UUID categoryId, UUID roomId, UUID areaId, String assetCode) {
        FixedAssetRequest request = new FixedAssetRequest();
        request.setCategoryId(categoryId);
        request.setRoomId(roomId);
        request.setAreaId(areaId);
        request.setName("Điều hòa nhiệt độ");
        request.setAssetCode(assetCode);
        return request;
    }

    /** Lưu là echo lại entity — service trả về chính bản ghi vừa dựng. */
    private void stubSaveEcho() {
        when(fixedAssetRepository.save(any(FixedAsset.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ── Đường thành công ─────────────────────────────────────────────────────

    @Nested
    class TaoThanhCong {

        @Test
        void gan_vao_phong_cung_location_thi_tao_duoc_va_mac_dinh_GOOD() {
            AssetCategory category = category(AssetKind.FIXED, true);
            Room room = room(LOCATION, true);
            when(categoryRepository.findByIdAndTenantId(category.getId(), TENANT))
                .thenReturn(Optional.of(category));
            when(roomRepository.findByIdAndTenantId(room.getId(), TENANT)).thenReturn(Optional.of(room));
            when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
            when(fixedAssetRepository.existsByLocationIdAndAssetCode(LOCATION, "TS-AC-310-001"))
                .thenReturn(false);
            stubSaveEcho();

            FixedAssetResponse response = service.createFixedAsset(
                request(category.getId(), room.getId(), null, "TS-AC-310-001"));

            ArgumentCaptor<FixedAsset> saved = ArgumentCaptor.forClass(FixedAsset.class);
            verify(fixedAssetRepository).save(saved.capture());
            assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT);
            // BR-ASSET-13: locationId lấy từ session, client không truyền được.
            assertThat(saved.getValue().getLocationId()).isEqualTo(LOCATION);
            assertThat(saved.getValue().getStatus()).isEqualTo(FixedAssetStatus.GOOD);
            assertThat(saved.getValue().getAssetCode()).isEqualTo("TS-AC-310-001");
            assertThat(response.getRoomName()).isEqualTo("Phòng 310");
            assertThat(response.getAreaName()).isNull();
        }

        @Test
        void gan_vao_khu_vuc_cung_location_thi_tao_duoc() {
            AssetCategory category = category(AssetKind.FIXED, true);
            Area area = area(LOCATION);
            when(categoryRepository.findByIdAndTenantId(category.getId(), TENANT))
                .thenReturn(Optional.of(category));
            when(areaRepository.findByIdAndTenantId(area.getId(), TENANT)).thenReturn(Optional.of(area));
            when(areaRepository.findById(area.getId())).thenReturn(Optional.of(area));
            stubSaveEcho();

            FixedAssetResponse response = service.createFixedAsset(
                request(category.getId(), null, area.getId(), "TS-LBY-001"));

            assertThat(response.getAreaName()).isEqualTo("Sảnh chính");
            assertThat(response.getRoomName()).isNull();
        }

        /** BR-ASSET-12 — mã trống thì hệ thống sinh TS-00001, đệm 0 cho đủ 5 chữ số. */
        @Test
        void bo_trong_ma_thi_sinh_ma_tu_dong_tu_ma_lon_nhat_dang_dung() {
            AssetCategory category = category(AssetKind.FIXED, true);
            Area area = area(LOCATION);
            when(categoryRepository.findByIdAndTenantId(category.getId(), TENANT))
                .thenReturn(Optional.of(category));
            when(areaRepository.findByIdAndTenantId(area.getId(), TENANT)).thenReturn(Optional.of(area));
            when(areaRepository.findById(area.getId())).thenReturn(Optional.of(area));
            when(fixedAssetRepository
                .findTopByLocationIdAndAssetCodeStartingWithOrderByAssetCodeDesc(LOCATION, "TS-"))
                .thenReturn(Optional.of(FixedAsset.builder().assetCode("TS-00007").build()));
            when(fixedAssetRepository.existsByLocationIdAndAssetCode(LOCATION, "TS-00008"))
                .thenReturn(false);
            stubSaveEcho();

            FixedAssetResponse response = service.createFixedAsset(
                request(category.getId(), null, area.getId(), null));

            assertThat(response.getAssetCode()).isEqualTo("TS-00008");
        }
    }

    // ── Chốt chặn ────────────────────────────────────────────────────────────

    @Nested
    class ChanSaiVaChanXuyenLocation {

        /** BR-ASSET-03 — đúng MỘT trong hai, không cả hai, không bỏ trống. */
        @Test
        void bo_trong_ca_phong_va_khu_vuc_thi_bi_chan() {
            assertThatThrownBy(() -> service.createFixedAsset(
                request(UUID.randomUUID(), null, null, "TS-001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BR-ASSET-03");
            verify(fixedAssetRepository, never()).save(any());
        }

        @Test
        void khai_ca_phong_va_khu_vuc_thi_bi_chan() {
            assertThatThrownBy(() -> service.createFixedAsset(
                request(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "TS-001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BR-ASSET-03");
            verify(fixedAssetRepository, never()).save(any());
        }

        /** BR-ASSET-13 — khóa ngoại chỉ bảo đảm phòng TỒN TẠI, không bảo đảm đúng Location. */
        @Test
        void gan_vao_phong_cua_location_khac_thi_bi_chan() {
            Room foreignRoom = room(OTHER_LOCATION, true);
            when(roomRepository.findByIdAndTenantId(foreignRoom.getId(), TENANT))
                .thenReturn(Optional.of(foreignRoom));

            assertThatThrownBy(() -> service.createFixedAsset(
                request(UUID.randomUUID(), foreignRoom.getId(), null, "TS-001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BR-ASSET-13");
            verify(fixedAssetRepository, never()).save(any());
        }

        @Test
        void gan_vao_khu_vuc_cua_location_khac_thi_bi_chan() {
            Area foreignArea = area(OTHER_LOCATION);
            when(areaRepository.findByIdAndTenantId(foreignArea.getId(), TENANT))
                .thenReturn(Optional.of(foreignArea));

            assertThatThrownBy(() -> service.createFixedAsset(
                request(UUID.randomUUID(), null, foreignArea.getId(), "TS-001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BR-ASSET-13");
        }

        @Test
        void gan_vao_phong_da_xoa_thi_bi_chan() {
            Room deleted = room(LOCATION, false);
            when(roomRepository.findByIdAndTenantId(deleted.getId(), TENANT))
                .thenReturn(Optional.of(deleted));

            assertThatThrownBy(() -> service.createFixedAsset(
                request(UUID.randomUUID(), deleted.getId(), null, "TS-001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã bị xóa");
        }

        /** BR-ASSET-08 — hai loại tài sản dùng chung bảng danh mục, rất dễ chọn nhầm. */
        @Test
        void chon_danh_muc_tieu_hao_thi_bi_chan() {
            AssetCategory consumable = category(AssetKind.CONSUMABLE, true);
            Area area = area(LOCATION);
            when(areaRepository.findByIdAndTenantId(area.getId(), TENANT)).thenReturn(Optional.of(area));
            when(categoryRepository.findByIdAndTenantId(consumable.getId(), TENANT))
                .thenReturn(Optional.of(consumable));

            assertThatThrownBy(() -> service.createFixedAsset(
                request(consumable.getId(), null, area.getId(), "TS-001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tiêu hao");
            verify(fixedAssetRepository, never()).save(any());
        }

        @Test
        void chon_danh_muc_dang_bi_an_thi_bi_chan_khi_tao_moi() {
            AssetCategory hidden = category(AssetKind.FIXED, false);
            Area area = area(LOCATION);
            when(areaRepository.findByIdAndTenantId(area.getId(), TENANT)).thenReturn(Optional.of(area));
            when(categoryRepository.findByIdAndTenantId(hidden.getId(), TENANT))
                .thenReturn(Optional.of(hidden));

            assertThatThrownBy(() -> service.createFixedAsset(
                request(hidden.getId(), null, area.getId(), "TS-001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đang bị ẩn");
        }

        @Test
        void danh_muc_khong_ton_tai_thi_bi_chan() {
            Area area = area(LOCATION);
            UUID unknown = UUID.randomUUID();
            when(areaRepository.findByIdAndTenantId(area.getId(), TENANT)).thenReturn(Optional.of(area));
            when(categoryRepository.findByIdAndTenantId(unknown, TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createFixedAsset(
                request(unknown, null, area.getId(), "TS-001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không tìm thấy danh mục");
        }

        /**
         * BR-ASSET-12 — unique trong phạm vi LOCATION. Đây là lỗi mà màn khai báo hàng
         * loạt đụng phải khi tạo lần thứ hai cho cùng danh mục + vị trí, vì số thứ tự ở
         * client luôn đếm lại từ 001.
         */
        @Test
        void ma_tai_san_trung_trong_cung_location_thi_bi_chan() {
            AssetCategory category = category(AssetKind.FIXED, true);
            Area area = area(LOCATION);
            when(areaRepository.findByIdAndTenantId(area.getId(), TENANT)).thenReturn(Optional.of(area));
            when(categoryRepository.findByIdAndTenantId(category.getId(), TENANT))
                .thenReturn(Optional.of(category));
            when(fixedAssetRepository.existsByLocationIdAndAssetCode(LOCATION, "TS-AC-LBY-001"))
                .thenReturn(true);

            assertThatThrownBy(() -> service.createFixedAsset(
                request(category.getId(), null, area.getId(), "TS-AC-LBY-001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mã tài sản đã tồn tại");
            verify(fixedAssetRepository, never()).save(any());
        }
    }
}
