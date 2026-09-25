package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.Gender;
import jakarta.validation.constraints.Email;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cập nhật một phần hồ sơ nhân sự. Trường nào để null thì giữ nguyên.
 *
 * <p>KHÔNG đổi được email (là username unique toàn hệ thống — BR-USER-06), role,
 * và tenantId qua đây. Đổi Location phải đi qua luồng điều chuyển có duyệt (BR-TRF-02),
 * không sửa trực tiếp — ngoại lệ duy nhất là {@code locationId} bên dưới.
 */
@Data
public class UpdateUserRequest {

    private String    fullName;
    private String    phone;
    private UUID      positionId;

    /** Vị trí kiêm nhiệm — {@code null} = giữ nguyên, danh sách rỗng = bỏ hết kiêm nhiệm. */
    private List<UUID> extraPositionIds;

    /**
     * CHỈ để gán khách sạn cho Manager dự bị (đang chưa có Location). Người đã có Location
     * thì đổi phải qua điều chuyển; gửi đúng Location hiện tại thì bỏ qua.
     */
    private UUID      locationId;
    private LocalDate startWorkDate;
    private LocalDate dateOfBirth;
    private Gender    gender;
    private String    address;
    private String    avatarUrl;

    /** Chỉ dùng để bật/tắt tạm tài khoản; nghỉ việc dùng endpoint riêng (BR-USER-04). */
    private Boolean enabled;

    /** Không dùng — giữ lại để lỗi rõ ràng nếu client cũ còn gửi lên. */
    @Email
    private String email;
}
