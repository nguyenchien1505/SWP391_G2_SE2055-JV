package com.example.SWP391_G2_SE2055_JV.employee.dto;

import com.example.SWP391_G2_SE2055_JV.config.Role;
import com.example.SWP391_G2_SE2055_JV.config.UserStatus;
import com.example.SWP391_G2_SE2055_JV.employee.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private Long tenantId;
    private Long locationId;
    private String fullName;
    private String email;
    private String phone;
    private Role role;
    private UserStatus status;
    private LocalDateTime createdAt;

    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
            .id(user.getId())
            .tenantId(user.getTenantId())
            .locationId(user.getLocationId())
            .fullName(user.getFullName())
            .email(user.getEmail())
            .phone(user.getPhone())
            .role(user.getRole())
            .status(user.getStatus())
            .createdAt(user.getCreatedAt())
            .build();
    }
}
