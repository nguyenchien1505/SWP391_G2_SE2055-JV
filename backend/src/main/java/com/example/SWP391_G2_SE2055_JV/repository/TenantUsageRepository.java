package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Đếm mức SỬ DỤNG thực tế của một Tenant để so với quota trong Subscription (BR-SAAS-02, 03).
 *
 * <p><b>Cơ chế theo dõi:</b> đếm trực tiếp trên bảng gốc mỗi khi có yêu cầu, KHÔNG lưu bộ đếm
 * riêng. Bộ đếm lưu sẵn sẽ lệch khi có nơi tạo/xóa dữ liệu mà quên cập nhật, còn DM-17 không cho
 * thêm bảng phụ. Cột {@code tenant_id} đều có index (khóa ngoại) nên mỗi câu đếm rất nhẹ.
 *
 * <p>Tại sao file này kế thừa {@code Repository<Tenant, UUID>} mà lại đếm cả Location, User,
 * Room: Spring Data bắt buộc một repository có kiểu gốc, nhưng {@code @Query} được phép tham
 * chiếu bất kỳ entity nào. Gom cả ba câu đếm vào MỘT file mới giúp không phải sửa
 * {@code LocationRepository}, {@code UserRepository} (code của team).
 */
public interface TenantUsageRepository extends Repository<Tenant, UUID> {

    /** Mọi Location của Tenant. Location không có xóa mềm nên đếm hết. */
    @Query("SELECT COUNT(l) FROM Location l WHERE l.tenantId = :tenantId")
    long countLocations(@Param("tenantId") UUID tenantId);

    /**
     * Số nhân sự thuộc {@code role} mà chưa nghỉ việc. Gọi với {@code role = STAFF} và
     * {@code excluded = TERMINATED}: quota User chỉ tính Staff, Giám đốc và Manager không tính
     * (BR-SAAS-03); người đã nghỉ việc không còn chiếm chỗ (BR-USER-04). Staff {@code INACTIVE}
     * (tạm khóa) VẪN tính vì họ còn giữ tài khoản.
     */
    @Query("SELECT COUNT(u) FROM User u "
         + "WHERE u.tenantId = :tenantId AND u.role = :role AND u.status <> :excluded")
    long countUsers(@Param("tenantId") UUID tenantId,
                    @Param("role") Role role,
                    @Param("excluded") UserStatus excluded);

    /**
     * MỌI phòng của Tenant, kể cả phòng đã xóa mềm ({@code is_active = false}): theo quyết định
     * của team, phòng xóa mềm vẫn chiếm quota.
     */
    @Query("SELECT COUNT(r) FROM Room r WHERE r.tenantId = :tenantId")
    long countRooms(@Param("tenantId") UUID tenantId);
}
