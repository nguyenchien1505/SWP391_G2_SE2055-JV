package com.example.SWP391_G2_SE2055_JV.enums;

/**
 * Vai trò tài khoản — DM-01.
 *
 * <p>Chỉ 4 giá trị. "Lễ tân" và "Dọn dẹp" KHÔNG phải role: chúng là
 * {@link PositionType} của Position mà Staff được gán (BR-ORG-08). Quyền nghiệp vụ
 * đặc thù gán theo LOẠI Position, không theo tên Position và không theo role.
 */
public enum Role {

    /** Nhà cung cấp SaaS. Đứng ngoài mọi Tenant nên tenant_id NULL — BR-PERM-01. */
    PLATFORM_ADMIN,

    /** Chủ Tenant, phạm vi toàn Tenant. Không có Location/Position — BR-PERM-02. */
    DIRECTOR,

    /** Vận hành đúng 1 Location. Không có Position — BR-USER-05, BR-PERM-03. */
    MANAGER,

    /** Nhân viên. Bắt buộc có Location và Position — BR-USER-01. */
    STAFF
}
