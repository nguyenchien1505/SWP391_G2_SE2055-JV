package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.config.JpaConfig;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.RoomStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.repository.RoomRepository.RoomStatusCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm tra các {@code @Query} tự viết của module phòng trên MySQL THẬT (profile "test",
 * DB {@code hotel_workforce_test}) — xem {@code application-test.yaml} vì sao không dùng H2.
 *
 * <p>Mỗi test chạy trong transaction và tự rollback, nên dữ liệu không dồn lại giữa các lần
 * chạy. {@link JpaConfig} được nạp để JPA auditing điền {@code created_at} (cột NOT NULL).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class RoomRepositoryTest {

    private static final Pageable ALL = PageRequest.of(0, 100, Sort.by("roomNumber"));

    @Autowired TestEntityManager em;
    @Autowired RoomRepository    roomRepository;

    private UUID tenantId;
    private UUID locationA;
    private UUID locationB;
    private UUID standard;
    private UUID deluxe;

    /**
     * Tenant chính có 2 Location, 2 loại phòng. Tenant thứ hai có 1 phòng để chứng minh
     * cách ly Tenant. Một phòng đã xóa mềm để chứng minh phòng xóa không lọt vào kết quả.
     */
    @BeforeEach
    void setUp() {
        tenantId  = persistTenant("Sao Mai Hotels");
        locationA = persistLocation(tenantId, "Sao Mai Nha Trang");
        locationB = persistLocation(tenantId, "Sao Mai Đà Lạt");
        standard  = persistRoomType(tenantId, "Đơn");
        deluxe    = persistRoomType(tenantId, "Hạng sang");

        persistRoom(tenantId, locationA, "101", "1", standard, RoomStatus.AVAILABLE, true);
        persistRoom(tenantId, locationA, "102", "1", deluxe, RoomStatus.DIRTY, true);
        persistRoom(tenantId, locationA, "201", "2", standard, RoomStatus.DIRTY, true);
        persistRoom(tenantId, locationB, "G01", "G", standard, RoomStatus.OCCUPIED, true);
        persistRoom(tenantId, locationA, "103", "1", standard, RoomStatus.AVAILABLE, false);   // đã xóa mềm

        UUID otherTenant   = persistTenant("Chuỗi khác");
        UUID otherLocation = persistLocation(otherTenant, "Khách sạn khác");
        UUID otherType     = persistRoomType(otherTenant, "Đơn");
        persistRoom(otherTenant, otherLocation, "101", "1", otherType, RoomStatus.DIRTY, true);

        em.flush();
        em.clear();
    }

    // ── search ──────────────────────────────────────────────────────────────

    @Test
    void shouldReturnEveryActiveRoomOfTenantWhenAllFiltersNull() {
        assertThat(roomNumbers(roomRepository.search(tenantId, null, null, null, null, ALL).getContent()))
            .containsExactly("101", "102", "201", "G01");
    }

    @Test
    void shouldExcludeSoftDeletedRooms() {
        assertThat(roomNumbers(roomRepository.search(tenantId, locationA, null, null, null, ALL).getContent()))
            .doesNotContain("103");
    }

    @Test
    void shouldNotReturnRoomsOfAnotherTenant() {
        List<Room> rooms = roomRepository.search(tenantId, null, RoomStatus.DIRTY, null, null, ALL).getContent();
        assertThat(rooms).allSatisfy(room -> assertThat(room.getTenantId()).isEqualTo(tenantId));
        assertThat(roomNumbers(rooms)).containsExactly("102", "201");
    }

    @Test
    void shouldApplyLocationStatusFloorAndRoomTypeFilters() {
        assertThat(roomNumbers(roomRepository.search(tenantId, locationB, null, null, null, ALL).getContent()))
            .containsExactly("G01");
        assertThat(roomNumbers(roomRepository.search(tenantId, locationA, null, "1", null, ALL).getContent()))
            .containsExactly("101", "102");
        assertThat(roomNumbers(roomRepository.search(tenantId, locationA, null, null, deluxe, ALL).getContent()))
            .containsExactly("102");
        assertThat(roomNumbers(roomRepository.search(tenantId, locationA, RoomStatus.DIRTY, "2", standard, ALL).getContent()))
            .containsExactly("201");
    }

    @Test
    void shouldPaginateAndReportTotal() {
        var page = roomRepository.search(tenantId, null, null, null, null, PageRequest.of(0, 3, Sort.by("roomNumber")));
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    // ── findByIdAndTenantIdAndActiveTrue ────────────────────────────────────

    @Test
    void shouldFindRoomByIdOnlyInsideTenantAndWhenActive() {
        Room active  = findByNumber(tenantId, "101");
        Room deleted = findByNumber(tenantId, "103");

        assertThat(roomRepository.findByIdAndTenantIdAndActiveTrue(active.getId(), tenantId)).isPresent();
        assertThat(roomRepository.findByIdAndTenantIdAndActiveTrue(active.getId(), UUID.randomUUID())).isEmpty();
        assertThat(roomRepository.findByIdAndTenantIdAndActiveTrue(deleted.getId(), tenantId)).isEmpty();
    }

    // ── countByStatus ───────────────────────────────────────────────────────

    @Test
    void shouldCountActiveRoomsByStatusForOneLocation() {
        assertThat(asMap(roomRepository.countByStatus(tenantId, locationA)))
            .containsOnly(Map.entry(RoomStatus.AVAILABLE, 1L), Map.entry(RoomStatus.DIRTY, 2L));
    }

    @Test
    void shouldCountWholeTenantWhenLocationIsNull() {
        assertThat(asMap(roomRepository.countByStatus(tenantId, null)))
            .containsOnly(Map.entry(RoomStatus.AVAILABLE, 1L),
                          Map.entry(RoomStatus.DIRTY, 2L),
                          Map.entry(RoomStatus.OCCUPIED, 1L));
    }

    // ── Ràng buộc DB ck_rooms_unavailable_reason — BR-ROOM-07 (F2) ──────────
    // Lưu ý đã chạy thử: MySQL báo vi phạm CHECK bằng mã 3819 / SQLState HY000, nên Spring KHÔNG
    // xếp vào DataIntegrityViolationException (→ 409) mà ra JpaSystemException (→ 500). Vì vậy test
    // chỉ khẳng định "DB từ chối, đúng ràng buộc này"; service phải tự chặn trước (RoomStatusService).

    /** Lưới an toàn của BR-ROOM-07: dù service quên kiểm tra, DB vẫn không nhận phòng khóa thiếu lý do. */
    @Test
    void shouldRejectUnavailableRoomWithoutReasonAtDbLevel() {
        Room locked = newRoom("301", RoomStatus.UNAVAILABLE, null);

        assertThatThrownBy(() -> roomRepository.saveAndFlush(locked))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("ck_rooms_unavailable_reason");
    }

    /** Chiều ngược lại — lý do do RoomStatusService tự xóa khi mở khóa; quên xóa thì DB chặn. */
    @Test
    void shouldRejectReasonOnRoomThatIsNotUnavailable() {
        Room available = newRoom("302", RoomStatus.AVAILABLE, "Lý do còn sót");

        assertThatThrownBy(() -> roomRepository.saveAndFlush(available))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("ck_rooms_unavailable_reason");
    }

    @Test
    void shouldAcceptUnavailableRoomWithReason() {
        Room locked = roomRepository.saveAndFlush(newRoom("303", RoomStatus.UNAVAILABLE, "Hỏng điều hòa"));

        assertThat(roomRepository.findByIdAndTenantIdAndActiveTrue(locked.getId(), tenantId))
            .get().extracting(Room::getUnavailableReason).isEqualTo("Hỏng điều hòa");
    }

    // ── F3: số phòng duy nhất trong Location — BR-ROOM-05 ──────────────────

    @Test
    void shouldDetectDuplicateOnlyAmongActiveRoomsOfSameLocation() {
        // 101 đang hoạt động ở khách sạn A.
        assertThat(roomRepository.existsByLocationIdAndRoomNumberAndActiveTrue(locationA, "101")).isTrue();
        // 103 đã xóa mềm — số này coi như còn trống.
        assertThat(roomRepository.existsByLocationIdAndRoomNumberAndActiveTrue(locationA, "103")).isFalse();
        // G01 nằm ở khách sạn B, không đụng tới khách sạn A.
        assertThat(roomRepository.existsByLocationIdAndRoomNumberAndActiveTrue(locationA, "G01")).isFalse();
        assertThat(roomRepository.existsByLocationIdAndRoomNumberAndActiveTrue(locationB, "101")).isFalse();
    }

    /** Khi SỬA phòng: giữ nguyên số của chính nó không được tính là trùng. */
    @Test
    void shouldIgnoreEditedRoomItselfInUniquenessCheck() {
        Room room101 = findByNumber(tenantId, "101");
        Room room102 = findByNumber(tenantId, "102");

        assertThat(roomRepository.existsByLocationIdAndRoomNumberAndActiveTrueAndIdNot(
            locationA, "101", room101.getId())).isFalse();
        // Nhưng đổi 102 thành 101 thì vẫn là trùng với phòng 101 đang có.
        assertThat(roomRepository.existsByLocationIdAndRoomNumberAndActiveTrueAndIdNot(
            locationA, "101", room102.getId())).isTrue();
    }

    /**
     * Lưới an toàn ở tầng DB (uk_rooms_location_active_number): kể cả khi service quên kiểm tra,
     * hoặc hai request cùng tạo một lúc, DB vẫn chặn. Tầng HTTP trả 409.
     */
    @Test
    void shouldRejectDuplicateActiveRoomNumberInSameLocationAtDbLevel() {
        Room duplicate = newRoom(locationA, "101");

        assertThatThrownBy(() -> roomRepository.saveAndFlush(duplicate))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowSameRoomNumberInDifferentLocations() {
        Room sameNumberElsewhere = roomRepository.saveAndFlush(newRoom(locationB, "101"));

        assertThat(sameNumberElsewhere.getId()).isNotNull();
        assertThat(roomRepository.existsByLocationIdAndRoomNumberAndActiveTrue(locationB, "101")).isTrue();
    }

    /** BR-ROOM-08: xóa mềm nhả lại số phòng — cột sinh active_room_number về NULL. */
    @Test
    void shouldAllowReusingRoomNumberAfterSoftDelete() {
        // 103 trong dữ liệu mẫu đã ở trạng thái xóa mềm.
        Room reused = roomRepository.saveAndFlush(newRoom(locationA, "103"));

        assertThat(reused.getId()).isNotNull();
        assertThat(roomRepository.search(tenantId, locationA, null, null, null, ALL).getContent())
            .extracting(Room::getRoomNumber)
            .contains("103");
    }

    // ── F3: ck_rooms_capacity ──────────────────────────────────────────────

    /**
     * Sức chứa phải lớn hơn 0. DTO đã chặn bằng {@code @Positive} (422); test này chứng minh DB
     * cũng không nhận, để mọi đường ghi khác đều an toàn.
     */
    @Test
    void shouldRejectNonPositiveCapacityAtDbLevel() {
        Room room = newRoom(locationA, "401");
        room.setCapacity(0);

        assertThatThrownBy(() -> roomRepository.saveAndFlush(room))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("ck_rooms_capacity");
    }

    // ── Dữ liệu ─────────────────────────────────────────────────────────────

    /** Phòng mới hợp lệ (Trống / Sẵn sàng) ở một khách sạn cụ thể — dùng cho các test F3. */
    private Room newRoom(UUID location, String number) {
        return Room.builder()
            .tenantId(tenantId).locationId(location).roomNumber(number).floor("4")
            .roomTypeId(standard).capacity(2).status(RoomStatus.AVAILABLE).build();
    }

    private Room newRoom(String number, RoomStatus status, String unavailableReason) {
        return Room.builder()
            .tenantId(tenantId).locationId(locationA).roomNumber(number).floor("3")
            .roomTypeId(standard).capacity(2).status(status).unavailableReason(unavailableReason).build();
    }

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

    private void persistRoom(UUID tenant, UUID location, String number, String floor, UUID type,
                             RoomStatus status, boolean active) {
        em.persist(Room.builder()
            .tenantId(tenant).locationId(location).roomNumber(number).floor(floor)
            .roomTypeId(type).capacity(2).status(status).active(active).build());
    }

    private Room findByNumber(UUID tenant, String number) {
        return em.getEntityManager()
            .createQuery("SELECT r FROM Room r WHERE r.tenantId = :t AND r.roomNumber = :n", Room.class)
            .setParameter("t", tenant).setParameter("n", number)
            .getSingleResult();
    }

    private static List<String> roomNumbers(List<Room> rooms) {
        return rooms.stream().map(Room::getRoomNumber).toList();
    }

    private static Map<RoomStatus, Long> asMap(List<RoomStatusCount> rows) {
        return rows.stream().collect(Collectors.toMap(RoomStatusCount::getStatus, RoomStatusCount::getTotal));
    }
}
