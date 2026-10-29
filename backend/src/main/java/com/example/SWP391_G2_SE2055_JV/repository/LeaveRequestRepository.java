package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.LeaveRequest;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveRequestStatus;
import com.example.SWP391_G2_SE2055_JV.enums.LeaveType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Đơn xin nghỉ — BR-SCH-06..08, BR-SCH-16, BR-SCH-23, BR-SCH-27..31.
 *
 * <p>Enum truyền bằng tham số thay vì viết chuỗi thẳng trong JPQL — cùng cách với
 * {@code AssignableStaffRepository}, để đổi tên hằng enum là trình biên dịch báo ngay.
 */
@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    /** Chốt chặn cách ly Tenant: không tải đơn bằng findById trần. */
    Optional<LeaveRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Đơn của chính một người. {@code status} null = mọi trạng thái. */
    @Query("""
        select r from LeaveRequest r
        where r.requesterId = :requesterId
          and (:status is null or r.status = :status)
        """)
    Page<LeaveRequest> findOfRequester(@Param("requesterId") UUID requesterId,
                                       @Param("status") LeaveRequestStatus status,
                                       Pageable pageable);

    /**
     * Hàng đơn của một người duyệt — BR-SCH-08: đơn của người có role {@code requesterRole}, trong khách sạn
     * {@code locationId} ({@code null} = cả Tenant). Manager xem đơn Staff khách sạn mình, Giám đốc xem đơn
     * Manager. Role lấy từ hồ sơ người gửi nên một câu truy vấn, không lọc trong bộ nhớ.
     */
    @Query(value = """
        select r from LeaveRequest r, User u
        where u.id = r.requesterId
          and r.tenantId = :tenantId
          and (:locationId is null or r.locationId = :locationId)
          and u.role = :requesterRole
          and (:status is null or r.status = :status)
        """,
        countQuery = """
        select count(r) from LeaveRequest r, User u
        where u.id = r.requesterId
          and r.tenantId = :tenantId
          and (:locationId is null or r.locationId = :locationId)
          and u.role = :requesterRole
          and (:status is null or r.status = :status)
        """)
    Page<LeaveRequest> findForReview(@Param("tenantId") UUID tenantId,
                                     @Param("locationId") UUID locationId,
                                     @Param("requesterRole") Role requesterRole,
                                     @Param("status") LeaveRequestStatus status,
                                     Pageable pageable);

    /** Đơn của một người ở các trạng thái {@code statuses} có ngày nào trùng khoảng [from, to]. */
    @Query("""
        select r from LeaveRequest r
        where r.requesterId = :requesterId
          and r.status in :statuses
          and r.fromDate <= :to
          and r.toDate >= :from
        order by r.fromDate
        """)
    List<LeaveRequest> findOverlapping(@Param("requesterId") UUID requesterId,
                                       @Param("statuses") Collection<LeaveRequestStatus> statuses,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /**
     * Đơn {@code leaveType} của một người bắt đầu trong [from, to] — cộng ngày phép năm đã dùng / đang chờ
     * của một năm. Đơn phép năm không kéo qua hai năm nên lọc theo ngày bắt đầu là đủ.
     */
    List<LeaveRequest> findByRequesterIdAndLeaveTypeAndStatusInAndFromDateBetween(
        UUID requesterId, LeaveType leaveType, Collection<LeaveRequestStatus> statuses,
        LocalDate from, LocalDate to);
}
