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

    // Cùng ba phạm vi như trên, giới hạn theo khoảng ngày bắt đầu ca — màn xếp lịch tuần chỉ
    // cần đúng 7 ngày. Lọc theo shift_date là đủ vì ca qua đêm thuộc về ngày bắt đầu (BR-SCH-03).

    Page<Shift> findByTenantIdAndShiftDateBetween(
        UUID tenantId, LocalDate fromDate, LocalDate toDate, Pageable pageable);

    Page<Shift> findByTenantIdAndLocationIdAndShiftDateBetween(
        UUID tenantId, UUID locationId, LocalDate fromDate, LocalDate toDate, Pageable pageable);

    Page<Shift> findByStaffIdAndShiftDateBetween(
        UUID staffId, LocalDate fromDate, LocalDate toDate, Pageable pageable);

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
     * Còn ca nào KHÁC trong ngày không — gỡ / xóa / dời một ca chỉ làm người đó mất ngày làm
     * việc (và chạm BR-HK-03) khi đây là ca duy nhất của họ hôm ấy.
     */
    boolean existsByStaffIdAndShiftDateAndIdNot(UUID staffId, LocalDate shiftDate, UUID id);

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
