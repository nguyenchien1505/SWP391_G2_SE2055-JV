package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.enums.Gender;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.enums.UserStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponse {

    private UUID          id;
    private UUID          tenantId;
    private Role          role;
    private String        email;
    private UserStatus    status;
    private boolean       mustChangePassword;
    private String        fullName;
    private String        phone;
    private UUID          locationId;
    private UUID          positionId;
    private LocalDate     startWorkDate;
    private LocalDate     dateOfBirth;
    private Gender        gender;
    private String        address;
    private String        avatarUrl;
    private LocalDateTime terminatedAt;
    private LocalDateTime createdAt;

    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
            .id(user.getId())
            .tenantId(user.getTenantId())
            .role(user.getRole())
            .email(user.getEmail())
            .status(user.getStatus())
            .mustChangePassword(user.isMustChangePassword())
            .fullName(user.getFullName())
            .phone(user.getPhone())
            .locationId(user.getLocationId())
            .positionId(user.getPositionId())
            .startWorkDate(user.getStartWorkDate())
            .dateOfBirth(user.getDateOfBirth())
            .gender(user.getGender())
            .address(user.getAddress())
            .avatarUrl(user.getAvatarUrl())
            .terminatedAt(user.getTerminatedAt())
            .createdAt(user.getCreatedAt())
            .build();
    }
}
