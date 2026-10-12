package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.config.JpaConfig;
import com.example.SWP391_G2_SE2055_JV.entity.AssetCategory;
import com.example.SWP391_G2_SE2055_JV.entity.FixedAsset;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.AssetKind;
import com.example.SWP391_G2_SE2055_JV.enums.AssetPurpose;
import com.example.SWP391_G2_SE2055_JV.enums.FixedAssetStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BR-ROOM-08 — điều kiện "không còn tài sản cố định gắn vào" khi xóa phòng, chạy trên MySQL THẬT
 * (profile "test"). Chỉ phần module phòng dùng: {@link FixedAssetRepository#existsByRoomIdAndStatusNot}.
 *
 * <p>Điểm cần chứng minh: tài sản ĐÃ THANH LÝ không chặn xóa phòng. Thanh lý là trạng thái cuối,
 * không chuyển đi được nữa (BR-ASSET-14), nên nếu tính cả nó thì phòng sẽ kẹt vĩnh viễn.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class FixedAssetRepositoryTest {

    @Autowired TestEntityManager    em;
    @Autowired FixedAssetRepository fixedAssetRepository;

    private UUID tenantId;
    private UUID locationId;
    private UUID categoryId;
    private UUID room101;
    private UUID room102;

    @BeforeEach
    void setUp() {
        tenantId   = em.persist(Tenant.builder()
            .name("Sao Mai Hotels").contactEmail(UUID.randomUUID() + "@test.local").contactPhone("0900000000")
            .status(TenantStatus.ACTIVE).build()).getId();
        locationId = em.persist(Location.builder()
            .tenantId(tenantId).name("Sao Mai Nha Trang").address("Địa chỉ test").phone("0900000000")
            .status(LocationStatus.OPERATIONAL).build()).getId();
        UUID roomType = em.persist(RoomType.builder().tenantId(tenantId).name("Đơn").build()).getId();
        categoryId = em.persist(AssetCategory.builder()
            .tenantId(tenantId).name("Tivi").assetKind(AssetKind.FIXED).purpose(AssetPurpose.GUEST_USE)
            .build()).getId();

        room101 = persistRoom("101", roomType);
        room102 = persistRoom("102", roomType);
    }

    @Test
    void shouldIgnoreDisposedAssetsSoRoomCanBeDeleted() {
        persistAsset(room101, "TS-00001", FixedAssetStatus.DISPOSED);
        persistAsset(room101, "TS-00002", FixedAssetStatus.DISPOSED);

        assertThat(blocksRoomDelete(room101)).isFalse();
    }

    /** Ba trạng thái còn vận hành đều chặn xóa: phải chuyển tài sản đi hoặc thanh lý trước. */
    @ParameterizedTest
    @EnumSource(value = FixedAssetStatus.class, names = {"GOOD", "BROKEN", "UNDER_REPAIR"})
    void shouldBlockWhenAssetNotYetDisposed(FixedAssetStatus status) {
        persistAsset(room101, "TS-00001", FixedAssetStatus.DISPOSED);
        persistAsset(room101, "TS-00002", status);

        assertThat(blocksRoomDelete(room101)).isTrue();
    }

    @Test
    void shouldNotCountAssetsOfAnotherRoom() {
        persistAsset(room102, "TS-00001", FixedAssetStatus.GOOD);

        assertThat(blocksRoomDelete(room101)).isFalse();
        assertThat(blocksRoomDelete(room102)).isTrue();
    }

    /** Đúng lời gọi của {@code RoomValidator.assertDeletable}. */
    private boolean blocksRoomDelete(UUID roomId) {
        return fixedAssetRepository.existsByRoomIdAndStatusNot(roomId, FixedAssetStatus.DISPOSED);
    }

    // ── Dữ liệu mẫu ─────────────────────────────────────────────────────────

    private UUID persistRoom(String number, UUID roomType) {
        return em.persist(Room.builder()
            .tenantId(tenantId).locationId(locationId).roomNumber(number).floor("1")
            .roomTypeId(roomType).capacity(2).status(RoomStatus.AVAILABLE).build()).getId();
    }

    private void persistAsset(UUID roomId, String code, FixedAssetStatus status) {
        em.persist(FixedAsset.builder()
            .tenantId(tenantId).locationId(locationId).categoryId(categoryId)
            .assetCode(code).name("Tivi " + code).roomId(roomId).status(status).build());
        em.flush();
    }
}
