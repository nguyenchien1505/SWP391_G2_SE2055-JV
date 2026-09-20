package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Đổi tên Khu vực — BR-ORG-12.
 *
 * <p>Không cho đổi Location: khu vực là đặc thù vật lý của đúng một khách sạn, và tài
 * sản cố định đang gắn vào không chuyển được sang Location khác (BR-ASSET-13).
 */
@Data
public class UpdateAreaRequest {

    @NotBlank(message = "Tên khu vực là bắt buộc")
    @Size(max = 100, message = "Tên khu vực tối đa 100 ký tự")
    private String name;
}
