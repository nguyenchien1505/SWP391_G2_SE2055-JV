package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.config.JpaConfig;
import com.example.SWP391_G2_SE2055_JV.entity.Department;
import com.example.SWP391_G2_SE2055_JV.entity.Location;
import com.example.SWP391_G2_SE2055_JV.entity.Position;
import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import com.example.SWP391_G2_SE2055_JV.entity.ShiftSwapRequest;
import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.LocationStatus;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.SwapRequestStatus;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bảng {@code shift_swap_requests} (V1) trên MySQL thật: BR-SCH-19 ép ở DB qua 2 cột sinh
 * {@code pending_*_shift_id}, và các truy vấn mà {@code ShiftSwapService} dựa vào.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(JpaConfig.class)
class ShiftSwapRequestRepositoryTest {

    @Autowired TestEntityManager          em;
    @Autowired ShiftSwapRequestRepository repository;
    @Autowired ShiftRepository            shiftRepository;

    private UUID tenantId;
    private UUID locationId;
    private UUID an;
    private UUID binh;
    private UUID cuong;
    private UUID shiftOfAn;
    private UUID shiftOfBinh;
    private UUID shiftOfCuong;
    private final LocalDate day = LocalDate.of(2026, 11, 2);

    @BeforeEach
    void setUp() {
        tenantId = persist(Tenant.builder().name("Sao Mai Hotels").contactEmail(uniqueEmail())
            .contactPhone("0900000000").status(TenantStatus.ACTIVE).build()).getId();
        locationId = persist(Location.builder().tenantId(tenantId).name("Sao Mai Hà Nội")
            .address("Địa chỉ test").phone("0900000000").status(LocationStatus.OPERATIONAL).build()).getId();
        UUID department = persist(Department.builder().tenantId(tenantId).name("Lễ tân").build()).getId();
        UUID position = persist(Position.builder().tenantId(tenantId).departmentId(department)
            .name("Nhân viên lễ tân").positionType(PositionType.RECEPTION).build()).getId();
        an = persistStaff(position, "Trần An");
        binh = persistStaff(position, "Lê Bình");
        cuong = persistStaff(position, "Phạm Cường");
        shiftOfAn = persistShift(an, 6, 14);
        shiftOfBinh = persistShift(binh, 14, 22);
        shiftOfCuong = persistShift(cuong, 14, 22);
        em.flush();
    }

    /** BR-SCH-19: một ca không nằm trong 2 yêu cầu đang chờ ở cùng một phía — DB chặn kể cả khi service sót. */
    @Test
    void shouldRejectSecondPendingRequestForSameShift() {
        repository.saveAndFlush(request(an, shiftOfAn, binh, shiftOfBinh, SwapRequestStatus.PENDING_PEER));

        ShiftSwapRequest second = request(an, shiftOfAn, cuong, shiftOfCuong, SwapRequestStatus.PENDING_PEER);

        assertThatThrownBy(() -> repository.saveAndFlush(second))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Yêu cầu đã đóng nhả chỗ (cột sinh về NULL): bị từ chối thì hỏi người khác được. */
    @Test
    void shouldAllowNewRequestAfterPreviousClosed() {
        repository.saveAndFlush(request(an, shiftOfAn, binh, shiftOfBinh, SwapRequestStatus.PEER_REJECTED));

        assertThatCode(() -> repository.saveAndFlush(
                request(an, shiftOfAn, cuong, shiftOfCuong, SwapRequestStatus.PENDING_PEER)))
            .doesNotThrowAnyException();
    }

    /**
     * DB chỉ chặn trùng trong TỪNG cột: ca của Bình vừa là ca nhận ở yêu cầu này vừa là ca gửi ở yêu
     * cầu kia vẫn lưu được — vì vậy service phải hỏi cả hai cột qua {@code findByShiftAndStatusIn}.
     */
    @Test
    void shouldFindPendingRequestsOfShiftOnEitherSide() {
        ShiftSwapRequest binhAsPeer = repository.saveAndFlush(
            request(an, shiftOfAn, binh, shiftOfBinh, SwapRequestStatus.PENDING_MANAGER));
        ShiftSwapRequest binhAsRequester = repository.saveAndFlush(
            request(binh, shiftOfBinh, cuong, shiftOfCuong, SwapRequestStatus.PENDING_PEER));
        repository.saveAndFlush(request(an, shiftOfAn, cuong, shiftOfCuong, SwapRequestStatus.APPROVED));

        assertThat(repository.findByShiftAndStatusIn(shiftOfBinh, SwapRequestStatus.PENDING_STATUSES))
            .extracting(ShiftSwapRequest::getId)
            .containsExactlyInAnyOrder(binhAsPeer.getId(), binhAsRequester.getId());
        assertThat(repository.findByShiftAndStatusIn(shiftOfCuong, SwapRequestStatus.PENDING_STATUSES))
            .extracting(ShiftSwapRequest::getId)
            .containsExactly(binhAsRequester.getId());
    }

    /** Chặn xóa ca: tính cả yêu cầu đã đóng, vì khóa ngoại giữ lịch sử. */
    @Test
    void shouldTellWhetherShiftHasAnySwapHistory() {
        repository.saveAndFlush(request(an, shiftOfAn, binh, shiftOfBinh, SwapRequestStatus.CANCELLED));

        assertThat(repository.existsByRequesterShiftIdOrPeerShiftId(shiftOfBinh, shiftOfBinh)).isTrue();
        assertThat(repository.existsByRequesterShiftIdOrPeerShiftId(shiftOfCuong, shiftOfCuong)).isFalse();
    }

    /** Duyệt đổi ca khóa ghi 2 ca (SELECT … FOR UPDATE) — chỉ trả ca của đúng Tenant. */
    @Test
    void shouldLoadShiftForUpdateOnlyWithinTenant() {
        assertThat(shiftRepository.findForUpdate(shiftOfAn, tenantId)).isPresent();
        assertThat(shiftRepository.findForUpdate(shiftOfAn, UUID.randomUUID())).isEmpty();
    }

    // ── Tiện ích ───────────────────────────────────────────────────────────

    private ShiftSwapRequest request(UUID requester, UUID requesterShift, UUID peer, UUID peerShift,
                                     SwapRequestStatus status) {
        return ShiftSwapRequest.builder()
            .tenantId(tenantId)
            .locationId(locationId)
            .requesterId(requester)
            .requesterShiftId(requesterShift)
            .peerId(peer)
            .peerShiftId(peerShift)
            .status(status)
            .expiresAt(LocalDateTime.of(2026, 11, 1, 12, 0))
            .build();
    }

    private UUID persistStaff(UUID positionId, String fullName) {
        return persist(User.builder().tenantId(tenantId).locationId(locationId).role(Role.STAFF)
            .positionId(positionId).email(uniqueEmail()).passwordHash("x").status(UserStatus.ACTIVE)
            .fullName(fullName).phone("0900000000").build()).getId();
    }

    private UUID persistShift(UUID staffId, int startHour, int endHour) {
        return persist(Shift.builder().tenantId(tenantId).locationId(locationId).staffId(staffId)
            .shiftDate(day).startTime(LocalTime.of(startHour, 0)).endTime(LocalTime.of(endHour, 0))
            .durationHours(BigDecimal.valueOf(endHour - startHour)).build()).getId();
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@test.local";
    }
}
