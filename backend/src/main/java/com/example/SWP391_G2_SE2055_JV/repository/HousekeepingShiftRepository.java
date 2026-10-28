package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Shift;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Ca làm việc nhìn từ phía lịch dọn — "ngày làm hiện tại" của người dọn, kể cả ca qua đêm (BR-SCH-03,
 * BR-HK-03, BR-HK-04).
 *
 * <p>Repository riêng thay vì thêm method vào {@code ShiftRepository}: file đó thuộc module Lịch làm
 * việc. Cùng tiền lệ {@code AssignableStaffRepository} — Spring Data chỉ bắt khai báo một kiểu gốc.
 */
public interface HousekeepingShiftRepository extends Repository<Shift, UUID> {

    /**
     * Ca của MỘT người tại một khách sạn trong khoảng ngày (tính cả hai đầu). Hỏi hôm qua → hôm nay là
     * đủ để tìm ca thuộc ngày làm hiện tại: ca qua đêm bắt đầu hôm qua vẫn có thể đang làm.
     */
    @Query("""
        select s from Shift s
        where s.staffId = :staffId
          and s.locationId = :locationId
          and s.shiftDate between :from and :to
        order by s.shiftDate, s.startTime
        """)
    List<Shift> findOfStaffBetween(@Param("staffId") UUID staffId,
                                   @Param("locationId") UUID locationId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    /**
     * Ca QUA ĐÊM của nhiều người trong một ngày — tính cờ "Tồn đọng" cho cả trang việc dọn bằng một câu
     * truy vấn, không hỏi từng người.
     */
    @Query("""
        select s from Shift s
        where s.staffId in :staffIds
          and s.shiftDate = :date
          and s.overnight = true
        """)
    List<Shift> findOvernightOf(@Param("staffIds") Collection<UUID> staffIds, @Param("date") LocalDate date);
}
