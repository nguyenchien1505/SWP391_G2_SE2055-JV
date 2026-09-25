package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.PositionType;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ai nhận được task dọn phòng trong một ngày — BR-HK-02, BR-HK-03, BR-PERM-05.
 *
 * <p>Điều kiện ở đây là BẢN SAO ĐÚNG của {@code HousekeepingService.assertCanReceiveTask}: nhân
 * viên đang làm việc, cùng Location với phòng, có Position loại Dọn dẹp (vị trí chính hoặc kiêm
 * nhiệm), và CÓ CA trong ngày đó.
 * Nhờ vậy mọi người hiện ra trong hộp thoại phân công đều gán được — Manager không bấm rồi mới
 * nhận lỗi. Đổi luật ở một nơi thì phải đổi cả hai.
 *
 * <p>Vì sao là repository riêng thay vì thêm method vào {@code UserRepository} / {@code
 * ShiftRepository}: hai file đó thuộc module khác. Theo tiền lệ {@code TenantUsageRepository},
 * Spring Data chỉ bắt khai báo một kiểu gốc còn {@code @Query} được phép tham chiếu entity nào
 * cũng được, nên gom truy vấn liên module vào file mới là cách ít va chạm nhất khi merge.
 */
public interface AssignableStaffRepository extends Repository<User, UUID> {

    /**
     * Enum truyền bằng tham số thay vì viết chuỗi thẳng trong JPQL — cùng cách với
     * {@code TenantUsageRepository.countUsers}, để đổi tên hằng enum là trình biên dịch báo ngay.
     *
     * <p>Điều kiện có ca và điều kiện Position đều dùng {@code exists} chứ không {@code join}: một
     * người có thể có nhiều ca trong ngày, hoặc giữ nhiều Position cùng Loại (vị trí chính và kiêm
     * nhiệm) — {@code join} sẽ nhân dòng và trả trùng tên.
     */
    @Query("""
        select u from User u
        where u.tenantId = :tenantId
          and u.locationId = :locationId
          and u.role = :role
          and u.status = :status
          and exists (select 1 from Position p
                      where p.positionType = :positionType
                        and (p.id = u.positionId or p.id member of u.extraPositionIds))
          and exists (select 1 from Shift s where s.staffId = u.id and s.shiftDate = :date)
        order by u.fullName
        """)
    List<User> findAssignable(@Param("tenantId") UUID tenantId,
                              @Param("locationId") UUID locationId,
                              @Param("date") LocalDate date,
                              @Param("role") Role role,
                              @Param("status") UserStatus status,
                              @Param("positionType") PositionType positionType);
}
