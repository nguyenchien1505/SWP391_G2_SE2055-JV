package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    Page<Shift> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Shift> findByTenantIdAndLocationId(UUID tenantId, UUID locationId, Pageable pageable);

    Page<Shift> findByStaffId(UUID staffId, Pageable pageable);

    Optional<Shift> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Cửa sổ ca của một nhân viên, dùng cho toàn bộ kiểm tra Schedule Policy
     * (BR-SCH-02). Tải một lần rồi tính trong bộ nhớ thay vì bắn nhiều count query.
     */
    List<Shift> findByStaffIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
        UUID staffId, LocalDate fromDate, LocalDate toDate);

    /** BR-HK-03: chỉ được assign task dọn cho nhân viên đang có ca trong ngày đó. */
    boolean existsByStaffIdAndShiftDate(UUID staffId, LocalDate shiftDate);

    /**
     * BR-SCH-17 + BR-TRF-05 + BR-USER-04: gỡ ca TƯƠNG LAI khi điều chuyển / nghỉ việc.
     * "Tương lai" nghĩa là ngày LỚN HƠN hôm nay theo múi giờ Location — ca của chính
     * hôm nay KHÔNG bị gỡ, nên dùng {@code GreaterThan} chứ không phải {@code GreaterThanEqual}.
     */
    List<Shift> findByStaffIdAndShiftDateGreaterThan(UUID staffId, LocalDate today);

    List<Shift> findByStaffIdAndLocationIdAndShiftDateGreaterThan(
        UUID staffId, UUID locationId, LocalDate today);

    /** BR-DASH-03: số Staff có ca hôm nay tại một Location. */
    long countByLocationIdAndShiftDate(UUID locationId, LocalDate shiftDate);
}
