package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.config.JpaConfig;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Câu đếm quota phòng trên MySQL THẬT (profile "test", DB {@code hotel_workforce_test}) — cùng cách
 * với {@link RoomRepositoryTest}. Các test service chỉ giả lập {@code countRooms}, nên đây là nơi
 * duy nhất chạy câu truy vấn thật.
 *
 * <p>BR-SAAS-07 (M2): quota phòng chỉ đếm phòng đang dùng, không đếm phòng đã xóa mềm.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class TenantUsageRepositoryTest {

    @Autowired TestEntityManager     em;
    @Autowired TenantUsageRepository tenantUsageRepository;

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID room101;

    /**
     * Tenant chính có 3 phòng đang dùng ở 2 khách sạn và 1 phòng đã xóa mềm. Tenant thứ hai có 1 phòng
     * để chứng minh cách ly Tenant.
     */
    @BeforeEach
    void setUp() {
        tenantId = persistTenant("Sao Mai Hotels");
        UUID nhaTrang = persistLocation(tenantId, "Sao Mai Nha Trang");
        UUID daLat    = persistLocation(tenantId, "Sao Mai Đà Lạt");
        UUID standard = persistRoomType(tenantId, "Đơn");

        room101 = persistRoom(tenantId, nhaTrang, "101", standard, true);
        persistRoom(tenantId, nhaTrang, "102", standard, true);
        persistRoom(tenantId, daLat, "G01", standard, true);
        persistRoom(tenantId, nhaTrang, "103", standard, false);   // đã xóa mềm

        otherTenantId = persistTenant("Chuỗi khác");
        UUID otherLocation = persistLocation(otherTenantId, "Khách sạn khác");
        persistRoom(otherTenantId, otherLocation, "101", persistRoomType(otherTenantId, "Đơn"), true);

        em.flush();
        em.clear();
    }

    @Test
    void shouldNotCountSoftDeletedRooms() {
        assertThat(tenantUsageRepository.countRooms(tenantId)).isEqualTo(3);
    }

    @Test
    void shouldNotCountRoomsOfAnotherTenant() {
        assertThat(tenantUsageRepository.countRooms(otherTenantId)).isEqualTo(1);
    }

    /** Xóa phòng (xóa mềm như {@code RoomService.deleteRoom}) là trả lại một chỗ trong quota. */
    @Test
    void shouldFreeQuotaWhenRoomIsDeleted() {
        em.find(Room.class, room101).setActive(false);
        em.flush();

        assertThat(tenantUsageRepository.countRooms(tenantId)).isEqualTo(2);
    }

    // ── dữ liệu test ────────────────────────────────────────────────────────

    private UUID persistTenant(String name) {
        return em.persist(Tenant.builder()
            .name(name).contactEmail(UUID.randomUUID() + "@test.local").contactPhone("0900000000")
            .status(TenantStatus.ACTIVE).build()).getId();
    }

    private UUID persistLocation(UUID tenant, String name) {
        return em.persist(Location.builder()
            .tenantId(tenant).name(name).address("Địa chỉ test").phone("0900000000")
            .status(LocationStatus.OPERATIONAL).build()).getId();
    }

    private UUID persistRoomType(UUID tenant, String name) {
        return em.persist(RoomType.builder().tenantId(tenant).name(name).build()).getId();
    }

    private UUID persistRoom(UUID tenant, UUID location, String number, UUID type, boolean active) {
        return em.persist(Room.builder()
            .tenantId(tenant).locationId(location).roomNumber(number).floor("1")
            .roomTypeId(type).capacity(2).status(RoomStatus.AVAILABLE).active(active).build()).getId();
    }
}
