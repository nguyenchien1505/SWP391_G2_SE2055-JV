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

    @Email(message = "Email must be valid")
    @Size(max = 120, message = "Email must not exceed 120 characters")
    private String email;

    @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone must be 10-15 digits")
    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    private Role role;

    private Boolean enabled;
}