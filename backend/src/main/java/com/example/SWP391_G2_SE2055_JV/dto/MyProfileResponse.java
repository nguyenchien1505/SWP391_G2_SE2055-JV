package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Gender;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.StaffPermission;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Hồ sơ của chính người đang đăng nhập — BR-PERM-06 "xem hồ sơ cá nhân". Kèm sẵn tên vị trí, phòng ban, khách
 * sạn vì nhân viên không đọc được danh mục tổ chức. Chỉ để xem: sửa hồ sơ là việc của cấp quản lý.
 */
@Data
@Builder
public class MyProfileResponse {

    private UUID                  id;
    private String                email;
    private Role                  role;
    private String                fullName;
    private String                phone;
    private Gender                gender;
    private LocalDate             dateOfBirth;
    private String                address;
    private String                avatarUrl;
    private LocalDate             startWorkDate;
    private List<StaffPermission> permissions;
    /** {@code null} với Manager và Giám đốc — DM-01: chỉ nhân viên có Position. */
    private String                positionName;
    private String                departmentName;
    /** {@code null} với Giám đốc — làm việc ở cấp chuỗi, không thuộc khách sạn nào. */
    private String                locationName;
    private LocalDateTime         createdAt;

    public static MyProfileResponse of(User user, String positionName, String departmentName, String locationName) {
        return MyProfileResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .role(user.getRole())
            .fullName(user.getFullName())
            .phone(user.getPhone())
            .gender(user.getGender())
            .dateOfBirth(user.getDateOfBirth())
            .address(user.getAddress())
            .avatarUrl(user.getAvatarUrl())
            .startWorkDate(user.getStartWorkDate())
            .permissions(user.getPermissions().stream().sorted().toList())
            .positionName(positionName)
            .departmentName(departmentName)
            .locationName(locationName)
            .createdAt(user.getCreatedAt())
            .build();
    }
}
