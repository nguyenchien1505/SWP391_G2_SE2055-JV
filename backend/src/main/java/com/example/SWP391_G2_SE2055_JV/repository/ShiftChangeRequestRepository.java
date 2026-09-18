package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.ShiftChangeRequest;
import com.example.SWP391_G2_SE2055_JV.enums.ShiftChangeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShiftChangeRequestRepository extends JpaRepository<ShiftChangeRequest, Long> {
    Page<ShiftChangeRequest> findByDeletedFalse(Pageable pageable);
    Page<ShiftChangeRequest> findByRequestedByAndDeletedFalse(Long requestedBy, Pageable pageable);
    Page<ShiftChangeRequest> findByStatusAndDeletedFalse(ShiftChangeRequestStatus status, Pageable pageable);
}
