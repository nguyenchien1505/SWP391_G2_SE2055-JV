package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, UUID> {

    /** Template là danh mục cấp Tenant — không được tham chiếu template của Tenant khác. */
    Optional<ShiftTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Thứ tự sắp xếp do {@code Pageable} quyết định nên KHÔNG đặt {@code OrderBy} vào tên
     * method — hai nguồn sắp xếp gặp nhau sẽ khiến tham số {@code sort} của client bị bỏ qua.
     */
    Page<ShiftTemplate> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * BR-SCH-22: mẫu đã vô hiệu hóa chỉ còn để tra ca lịch sử, không hiện ở màn hình xếp ca.
     *
     * <p>Tên thuộc tính là {@code active} (field), không phải {@code isActive} (getter Lombok).
     */
    Page<ShiftTemplate> findByTenantIdAndActiveTrue(UUID tenantId, Pageable pageable);

    /** Unique theo (tenant_id, name) — trùng với ràng buộc DB. */
    boolean existsByTenantIdAndName(UUID tenantId, String name);

    /** Dùng khi đổi tên: bỏ qua chính bản ghi đang sửa. */
    boolean existsByTenantIdAndNameAndIdNot(UUID tenantId, String name, UUID id);
}
