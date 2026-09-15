package com.example.SWP391_G2_SE2055_JV.housekeeping.repository;

import com.example.SWP391_G2_SE2055_JV.housekeeping.entity.CleaningAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface CleaningAssignmentRepository extends JpaRepository<CleaningAssignment, Long> {
    Page<CleaningAssignment> findByDeletedFalse(Pageable pageable);
    Page<CleaningAssignment> findByShiftIdInAndDeletedFalse(Collection<Long> shiftIds, Pageable pageable);
}
