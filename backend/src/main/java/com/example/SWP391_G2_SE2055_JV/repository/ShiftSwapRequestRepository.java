package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.ShiftSwapRequest;
import com.example.SWP391_G2_SE2055_JV.enums.SwapRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftSwapRequestRepository extends JpaRepository<ShiftSwapRequest, UUID> {

    Optional<ShiftSwapRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Yêu cầu ở các trạng thái cho trước có dính tới ca này ở MỘT TRONG HAI phía. DB chỉ chặn một ca
     * nằm trong 2 yêu cầu chờ trên CÙNG một cột (2 cột sinh {@code pending_*_shift_id}); ca vừa là
     * ca của người gửi ở yêu cầu này vừa là ca của đồng nghiệp ở yêu cầu khác thì phải hỏi cả hai cột
     * — BR-SCH-19.
     */
    @Query("""
        SELECT r FROM ShiftSwapRequest r
        WHERE r.status IN :statuses
          AND (r.requesterShiftId = :shiftId OR r.peerShiftId = :shiftId)
        """)
    List<ShiftSwapRequest> findByShiftAndStatusIn(@Param("shiftId") UUID shiftId,
                                                  @Param("statuses") Collection<SwapRequestStatus> statuses);

    /** Ca đã từng có yêu cầu đổi ca nào chưa, mọi trạng thái — khóa ngoại chặn xóa những ca này. */
    boolean existsByRequesterShiftIdOrPeerShiftId(UUID requesterShiftId, UUID peerShiftId);

    Page<ShiftSwapRequest> findByTenantIdAndRequesterIdAndStatusIn(
        UUID tenantId, UUID requesterId, Collection<SwapRequestStatus> statuses, Pageable pageable);

    Page<ShiftSwapRequest> findByTenantIdAndPeerIdAndStatusIn(
        UUID tenantId, UUID peerId, Collection<SwapRequestStatus> statuses, Pageable pageable);

    Page<ShiftSwapRequest> findByTenantIdAndLocationIdAndStatusIn(
        UUID tenantId, UUID locationId, Collection<SwapRequestStatus> statuses, Pageable pageable);

    /** Yêu cầu đang chờ của một khách sạn — để danh sách ca đổi được bỏ những ca đã bị giữ chỗ. */
    List<ShiftSwapRequest> findByLocationIdAndStatusIn(UUID locationId, Collection<SwapRequestStatus> statuses);

    /** Mọi yêu cầu đang chờ, mọi Tenant — job tự hủy chạy ngoài phiên đăng nhập. */
    List<ShiftSwapRequest> findByStatusIn(Collection<SwapRequestStatus> statuses);
}
