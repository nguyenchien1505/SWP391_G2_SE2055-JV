package com.example.SWP391_G2_SE2055_JV.dto.asset;

import com.example.SWP391_G2_SE2055_JV.entity.FixedAsset;
import com.example.SWP391_G2_SE2055_JV.enums.FixedAssetStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class FixedAssetResponse {
    private UUID id;
    private UUID locationId;
    private UUID categoryId;
    private String assetCode;
    private String name;
    private UUID roomId;
    private String roomName;
    private UUID areaId;
    private String areaName;
    private FixedAssetStatus status;
    private String note;

    public static FixedAssetResponse fromEntity(FixedAsset asset) {
        if (asset == null) return null;
        return FixedAssetResponse.builder()
                .id(asset.getId())
                .locationId(asset.getLocationId())
                .categoryId(asset.getCategoryId())
                .assetCode(asset.getAssetCode())
                .name(asset.getName())
                .roomId(asset.getRoomId())
                .areaId(asset.getAreaId())
                .status(asset.getStatus())
                .note(asset.getNote())
                .build();
    }
}
