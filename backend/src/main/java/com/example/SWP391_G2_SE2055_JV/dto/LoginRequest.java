package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {
    @NotBlank
    private String gmail;

    @NotBlank
    private String password;
}
