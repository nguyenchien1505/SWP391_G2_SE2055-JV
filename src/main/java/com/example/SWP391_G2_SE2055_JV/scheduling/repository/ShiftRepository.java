package com.example.SWP391_G2_SE2055_JV.scheduling.repository;

import com.example.SWP391_G2_SE2055_JV.scheduling.entity.Shift;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShiftRepository extends JpaRepository<Shift, Long> {
    Page<Shift> findByDeletedFalse(Pageable pageable);
    Page<Shift> findByUserIdAndDeletedFalse(Long userId, Pageable pageable);
    List<Shift> findByUserIdAndShiftDateBetweenAndDeletedFalse(Long userId, LocalDate from, LocalDate to);
    int countByUserIdAndShiftDateBetweenAndDeletedFalse(Long userId, LocalDate from, LocalDate to);
}
