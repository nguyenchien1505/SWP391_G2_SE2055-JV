package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Loại tài sản — BR-ASSET-08.
 *
 * <p>Danh mục tài sản dùng CHUNG 1 bảng cho cả hai loại, phân biệt bằng trường này.
 */
public enum AssetKind {

    /** Quản lý theo từng cá thể, có mã riêng và trạng thái riêng — BR-ASSET-01. */
    FIXED,

    /** Chỉ quản lý tồn kho tĩnh ở cấp Location; có đơn vị tính — BR-ASSET-04, DM-11. */
    CONSUMABLE
}
