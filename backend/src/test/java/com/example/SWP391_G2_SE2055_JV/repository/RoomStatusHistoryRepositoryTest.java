package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.config.JpaConfig;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Room;
import com.example.SWP391_G2_SE2055_JV.entity.RoomStatusHistory;
import com.example.SWP391_G2_SE2055_JV.entity.RoomType;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.ChangeSource;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-05 Lịch sử trạng thái — truy vấn {@code findByTenantIdAndRoomIdOrderByChangedAtDesc} trên
 * MySQL THẬT (profile "test"). Mỗi test tự rollback.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class RoomStatusHistoryRepositoryTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 9, 22, 8, 0);

    @Autowired TestEntityManager           em;
    @Autowired RoomStatusHistoryRepository historyRepository;

    private UUID tenantId;
    private Room room;

    /** Phòng chính có 3 dòng lịch sử ghi KHÔNG theo thứ tự thời gian; phòng bên cạnh có 1 dòng. */
    @BeforeEach
    void setUp() {
        tenantId = em.persist(Tenant.builder()
            .name("Sao Mai Hotels").contactEmail(UUID.randomUUID() + "@test.local").contactPhone("0900000000")
            .status(TenantStatus.ACTIVE).build()).getId();
        UUID locationId = em.persist(Location.builder()
            .tenantId(tenantId).name("Sao Mai Nha Trang").address("Địa chỉ test").phone("0900000000")
            .status(LocationStatus.OPERATIONAL).build()).getId();
        UUID roomTypeId = em.persist(RoomType.builder().tenantId(tenantId).name("Đôi").build()).getId();

        room = persistRoom(locationId, roomTypeId, "101");
        Room neighbour = persistRoom(locationId, roomTypeId, "102");

        persistHistory(room, RoomStatus.AVAILABLE, RoomStatus.UNAVAILABLE, DAY.plusHours(2));
        persistHistory(room, null, RoomStatus.DIRTY, DAY);
        persistHistory(room, RoomStatus.UNAVAILABLE, RoomStatus.DIRTY, DAY.plusHours(5));
        persistHistory(neighbour, null, RoomStatus.DIRTY, DAY.plusHours(9));

        em.flush();
        em.clear();
    }

    @Test
    void shouldReturnHistoryOfOneRoomNewestFirst() {
        Page<RoomStatusHistory> page = historyRepository.findByTenantIdAndRoomIdOrderByChangedAtDesc(
            tenantId, room.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent())
            .extracting(RoomStatusHistory::getChangedAt)
            .containsExactly(DAY.plusHours(5), DAY.plusHours(2), DAY);
        assertThat(page.getContent()).allSatisfy(row -> assertThat(row.getRoomId()).isEqualTo(room.getId()));
    }

    @Test
    void shouldPaginateHistory() {
        Page<RoomStatusHistory> second = historyRepository.findByTenantIdAndRoomIdOrderByChangedAtDesc(
            tenantId, room.getId(), PageRequest.of(1, 2));

        assertThat(second.getTotalElements()).isEqualTo(3);
        assertThat(second.getContent()).singleElement()
            .satisfies(row -> assertThat(row.getFromStatus()).isNull());   // dòng đầu tiên của phòng
    }

    @Test
    void shouldReturnNothingWhenTenantDoesNotOwnRoom() {
        assertThat(historyRepository.findByTenantIdAndRoomIdOrderByChangedAtDesc(
            UUID.randomUUID(), room.getId(), PageRequest.of(0, 20))).isEmpty();
    }

    // ── Dữ liệu ─────────────────────────────────────────────────────────────

    private Room persistRoom(UUID locationId, UUID roomTypeId, String number) {
        return em.persist(Room.builder()
            .tenantId(tenantId).locationId(locationId).roomNumber(number).floor("1")
            .roomTypeId(roomTypeId).capacity(2).status(RoomStatus.DIRTY).build());
    }

    private void persistHistory(Room target, RoomStatus from, RoomStatus to, LocalDateTime at) {
        em.persist(RoomStatusHistory.builder()
            .tenantId(tenantId).roomId(target.getId())
            .fromStatus(from).toStatus(to)
            .changedAt(at).changeSource(ChangeSource.SYSTEM)
            .build());
    }
}
