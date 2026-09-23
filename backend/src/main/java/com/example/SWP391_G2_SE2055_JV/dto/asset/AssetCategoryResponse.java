package com.example.SWP391_G2_SE2055_JV.dto.asset;

import com.example.SWP391_G2_SE2055_JV.entity.AssetCategory;
import com.example.SWP391_G2_SE2055_JV.enums.AssetKind;
import com.example.SWP391_G2_SE2055_JV.enums.AssetPurpose;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AssetCategoryResponse {
    private UUID id;
    private String name;
    private AssetKind assetKind;
    private AssetPurpose purpose;
    private String unit;
    private boolean active;

    public static AssetCategoryResponse fromEntity(AssetCategory category) {
        if (category == null) return null;
        return AssetCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .assetKind(category.getAssetKind())
                .purpose(category.getPurpose())
                .unit(category.getUnit())
                .active(category.isActive())
                .build();
    }
}
