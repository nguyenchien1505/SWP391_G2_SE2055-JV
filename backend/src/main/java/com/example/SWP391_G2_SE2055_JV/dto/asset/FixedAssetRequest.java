package com.example.SWP391_G2_SE2055_JV.dto.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class FixedAssetRequest {

    @NotNull(message = "Danh mục tài sản không được để trống")
    private UUID categoryId;

    @Size(max = 50, message = "Mã tài sản không được vượt quá 50 ký tự")
    private String assetCode; // Optional, nếu trống hệ thống tự sinh TS-xxxx

    @NotBlank(message = "Tên tài sản không được để trống")
    private String name;

    private UUID roomId;
    private UUID areaId;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;
}
