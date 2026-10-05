package com.example.SWP391_G2_SE2055_JV.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Kết quả tạo việc dọn hằng ngày hàng loạt — BR-HK-05, BR-HK-11.
 *
 * <p>{@code skipped}: số phòng đang có khách đã có sẵn việc dọn hằng ngày đang mở nên bỏ qua.
 */
@Data
@Builder
public class StayoverBatchResponse {

    private int                            created;
    private int                            skipped;
    private List<HousekeepingTaskResponse> tasks;
}
