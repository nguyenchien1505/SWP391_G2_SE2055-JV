package com.example.SWP391_G2_SE2055_JV.dto;

import com.example.SWP391_G2_SE2055_JV.entity.Location;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** Bộ mẫu ca một chi nhánh đang dùng — chung của chuỗi hay riêng của chi nhánh (V6). */
@Data
@Builder
public class LocationTemplateSetResponse {

    private UUID    locationId;
    private String  locationName;
    /** true = chi nhánh chỉ dùng bộ mẫu riêng của nó; false = dùng bộ mẫu chung. */
    private boolean ownShiftTemplates;
    /** Số mẫu đang dùng trong bộ riêng của chi nhánh. */
    private long    ownActiveTemplates;

    public static LocationTemplateSetResponse of(Location location, long ownActiveTemplates) {
        return LocationTemplateSetResponse.builder()
            .locationId(location.getId())
            .locationName(location.getName())
            .ownShiftTemplates(location.isOwnShiftTemplates())
            .ownActiveTemplates(ownActiveTemplates)
            .build();
    }
}
