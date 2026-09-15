package com.example.SWP391_G2_SE2055_JV.scheduling.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewShiftChangeRequest {

    @Size(max = 500, message = "Review note must not exceed 500 characters")
    private String reviewNote;
}
