package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.enums.Gender;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tạo tài khoản nhân sự.
 *
 * <p>Trường bắt buộc khác nhau theo vai trò nên chỉ 4 trường chung được validate ở
 * đây; phần còn lại kiểm ở service (xem {@code UserService.validateProfile}):
 * <ul>
 *   <li>STAFF — đủ 10 trường của BR-USER-01.</li>
 *   <li>MANAGER — như STAFF nhưng KHÔNG có Position (BR-USER-05). Riêng Location được để
 *       trống: đó là Manager DỰ BỊ, gán khách sạn sau hoặc nhận thay khi một Manager nghỉ việc.</li>
 *   <li>DIRECTOR — chỉ họ tên, email, SĐT (BR-USER-05).</li>
 * </ul>
 *
 * <p>{@code tenantId} KHÔNG nhận từ client — luôn lấy từ session của người tạo.
 */
@Data
public class CreateUserRequest {

    @NotNull(message = "role là bắt buộc")
    private Role role;

    @NotBlank(message = "Họ tên là bắt buộc")
    private String fullName;

    /** Username đăng nhập, unique toàn hệ thống — BR-USER-06. */
    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Số điện thoại là bắt buộc")
    private String phone;

    private UUID      locationId;
    /** Vị trí CHÍNH. Department tự suy ra từ Position này, không nhập riêng — BR-ORG-07. */
    private UUID      positionId;
    /**
     * Vị trí KIÊM NHIỆM cho nhân viên đa nhiệm — chỉ STAFF. Không bắt buộc; trùng lặp hoặc trùng
     * vị trí chính thì tự bỏ qua.
     */
    private List<UUID> extraPositionIds;
    private LocalDate startWorkDate;
    private LocalDate dateOfBirth;
    private Gender    gender;
    private String    address;
    private String    avatarUrl;
}
