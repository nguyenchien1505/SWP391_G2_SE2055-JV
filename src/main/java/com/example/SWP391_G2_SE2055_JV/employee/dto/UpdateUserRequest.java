package com.example.SWP391_G2_SE2055_JV.employee.dto;

import com.example.SWP391_G2_SE2055_JV.config.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;

    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone must be 10-15 digits")
    @Size(max = 30, message = "Phone must not exceed 30 characters")
    private String phone;

    private Role role;

    private Long locationId;

    private Boolean enabled;
}
